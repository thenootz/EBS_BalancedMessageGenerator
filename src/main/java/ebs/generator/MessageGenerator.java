package ebs.generator;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MessageGenerator creates Publications and Subscriptions respecting the
 * configured field-frequency and equality-frequency constraints.
 *
 * ── Deterministic frequency strategy ──────────────────────────────────────────
 * Instead of relying on random Bernoulli trials (which may miss the target for
 * small N), we pre-compute exactly how many subscriptions must contain each
 * field and how many of those must use "=".  We then shuffle the slot arrays
 * and assign them.  This guarantees exact counts while still appearing random.
 */
public class MessageGenerator {

    // ── company name pool ──────────────────────────────────────────────────────
    private static final String[] COMPANIES = {
        "Google", "Apple", "Microsoft", "Amazon", "Tesla",
        "Meta", "Netflix", "Nvidia", "Intel", "AMD"
    };

    // ── operators per field (non-equality pool) ────────────────────────────────
    private static final String[] NUM_OPS_NO_EQ = {">=", "<=", ">", "<"};
    private static final String[] NUM_OPS = {">=", "<=", ">", "<", "="};
    private static final String[] EQ_OPS  = {"="};

    // ── date pool: 2020-01-01 .. 2024-12-31 ───────────────────────────────────
    private static final LocalDate DATE_START = LocalDate.of(2020, 1, 1);
    private static final int DATE_RANGE_DAYS  = 365 * 5; // ~5 years

    // ── numeric field ranges ───────────────────────────────────────────────────
    private static final double VALUE_MIN     =  10.0;
    private static final double VALUE_MAX     = 500.0;
    private static final double DROP_MIN      =   0.0;
    private static final double DROP_MAX      =  50.0;
    private static final double VARIATION_MIN =  -5.0;
    private static final double VARIATION_MAX =   5.0;

    // ── ordered field list (defines output column order) ─────────────────────
    static final String[] FIELD_NAMES = {"company", "value", "drop", "variation", "date"};

    // ──────────────────────────────────────────────────────────────────────────

    private final GeneratorConfig cfg;

    public MessageGenerator(GeneratorConfig cfg) {
        this.cfg = cfg;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Publications
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Generates all publications in parallel and returns them as an ordered list.
     */
    public List<Publication> generatePublications() throws InterruptedException {
        int total   = cfg.totalPublications;
        int threads = cfg.numThreads;

        // result array pre-allocated for random access (no synchronisation needed)
        Publication[] results = new Publication[total];

        if (threads <= 1) {
            Random rng = new Random();
            for (int i = 0; i < total; i++) {
                results[i] = randomPublication(rng);
            }
        } else {
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            int chunk = (total + threads - 1) / threads;
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final int from = t * chunk;
                final int to   = Math.min(from + chunk, total);
                if (from >= total) break;
                futures.add(pool.submit(() -> {
                    Random rng = new Random(); // each thread owns its RNG
                    for (int i = from; i < to; i++) {
                        results[i] = randomPublication(rng);
                    }
                }));
            }
            for (Future<?> f : futures) {
                try { f.get(); }
                catch (ExecutionException e) { throw new RuntimeException(e); }
            }
            pool.shutdown();
        }

        return Arrays.asList(results);
    }

    private Publication randomPublication(Random rng) {
        String    company   = COMPANIES[rng.nextInt(COMPANIES.length)];
        double    value     = randomDouble(rng, VALUE_MIN,     VALUE_MAX);
        double    drop      = randomDouble(rng, DROP_MIN,      DROP_MAX);
        double    variation = randomDouble(rng, VARIATION_MIN, VARIATION_MAX);
        LocalDate date      = DATE_START.plusDays(rng.nextInt(DATE_RANGE_DAYS));
        return new Publication(company, value, drop, variation, date);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Subscriptions – deterministic frequency allocation
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Generates all subscriptions.
     *
     * Strategy:
     *  1. For each field f, compute exactly  countF = ceil(freq_f * N) subscriptions
     *     that must contain f.
     *  2. For each equality-configured field f, compute exactly
     *     countEq = ceil(eqFreq_f * countF) of those that use "=".
     *  3. Build per-field boolean arrays (present[], useEq[]), shuffle them,
     *     then in a single pass over [0, N) assemble each subscription.
     *
     * This avoids the synchronisation bottleneck of a shared counter-per-field
     * while still guaranteeing exact frequencies.
     */
    public List<Subscription> generateSubscriptions() throws InterruptedException {
        int N       = cfg.totalSubscriptions;
        int threads = cfg.numThreads;

        // ── 1.  Pre-compute which subscription indices must have each field ───
        //        and which of those must use "="
        // fieldSlots[f] = sorted int array of subscription indices that include field f
        Map<String, int[]> fieldSlots = buildFieldSlots(N);
        // eqSlots[f]    = sorted int array of subscription indices that use "=" for field f
        Map<String, Set<Integer>> eqSlotSets = buildEqSlotSets(fieldSlots);

        // ── 2.  Generate subscriptions in parallel ────────────────────────────
        Subscription[] results = new Subscription[N];

        if (threads <= 1) {
            Random rng = new Random();
            for (int i = 0; i < N; i++) {
                results[i] = buildSubscription(i, fieldSlots, eqSlotSets, rng);
            }
        } else {
            // Convert fieldSlots int[] -> Set<Integer> for O(1) lookup across threads
            // (Sets are read-only after construction – no synchronisation needed)
            Map<String, Set<Integer>> fieldSlotSets = new HashMap<>();
            for (Map.Entry<String, int[]> e : fieldSlots.entrySet()) {
                Set<Integer> s = new HashSet<>();
                for (int idx : e.getValue()) s.add(idx);
                fieldSlotSets.put(e.getKey(), s);
            }

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            int chunk = (N + threads - 1) / threads;
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final int from = t * chunk;
                final int to   = Math.min(from + chunk, N);
                if (from >= N) break;
                futures.add(pool.submit(() -> {
                    Random rng = new Random();
                    for (int i = from; i < to; i++) {
                        results[i] = buildSubscriptionFromSets(i, fieldSlotSets, eqSlotSets, rng);
                    }
                }));
            }
            for (Future<?> f : futures) {
                try { f.get(); }
                catch (ExecutionException e) { throw new RuntimeException(e); }
            }
            pool.shutdown();
        }

        return Arrays.asList(results);
    }

    // ── helper: build a single Subscription (sequential path) ─────────────────
    private Subscription buildSubscription(
            int idx,
            Map<String, int[]> fieldSlots,
            Map<String, Set<Integer>> eqSlotSets,
            Random rng) {

        Subscription sub = new Subscription();
        for (String field : FIELD_NAMES) {
            int[] slots = fieldSlots.get(field);
            if (slots == null) continue;
            if (Arrays.binarySearch(slots, idx) >= 0) {
                Set<Integer> eqSet = eqSlotSets.get(field);
                boolean useEq = (eqSet != null) && eqSet.contains(idx);
                addFieldToSubscription(sub, field, useEq, rng);
            }
        }
        return sub;
    }

    // ── helper: build a single Subscription (parallel path, uses Set lookup) ──
    private Subscription buildSubscriptionFromSets(
            int idx,
            Map<String, Set<Integer>> fieldSlotSets,
            Map<String, Set<Integer>> eqSlotSets,
            Random rng) {

        Subscription sub = new Subscription();
        for (String field : FIELD_NAMES) {
            Set<Integer> slots = fieldSlotSets.get(field);
            if (slots == null || !slots.contains(idx)) continue;
            Set<Integer> eqSet = eqSlotSets.get(field);
            boolean useEq = (eqSet != null) && eqSet.contains(idx);
            addFieldToSubscription(sub, field, useEq, rng);
        }
        return sub;
    }

    // ── add one field clause to a subscription ────────────────────────────────
    private void addFieldToSubscription(Subscription sub, String field, boolean forceEq, Random rng) {
        switch (field) {
            case "company": {
                // company supports equality (=) and inequality (!=) operators
                String op  = forceEq ? "=" : "!=";
                String val = "\"" + COMPANIES[rng.nextInt(COMPANIES.length)] + "\"";
                sub.addField(field, op, val);
                break;
            }
            case "value": {
                String op  = forceEq ? "=" : pickOp(NUM_OPS_NO_EQ, rng);
                String val = String.format("%.2f", randomDouble(rng, VALUE_MIN, VALUE_MAX));
                sub.addField(field, op, val);
                break;
            }
            case "drop": {
                String op  = forceEq ? "=" : pickOp(NUM_OPS_NO_EQ, rng);
                String val = String.format("%.2f", randomDouble(rng, DROP_MIN, DROP_MAX));
                sub.addField(field, op, val);
                break;
            }
            case "variation": {
                String op  = forceEq ? "=" : pickOp(NUM_OPS_NO_EQ, rng);
                String val = String.format("%.4f", randomDouble(rng, VARIATION_MIN, VARIATION_MAX));
                sub.addField(field, op, val);
                break;
            }
            case "date": {
                String op  = forceEq ? "=" : pickOp(NUM_OPS_NO_EQ, rng);
                LocalDate d = DATE_START.plusDays(rng.nextInt(DATE_RANGE_DAYS));
                sub.addField(field, op, d.toString());
                break;
            }
        }
    }

    // ── build fieldSlots: for each field, which subscription indices include it ─
    private Map<String, int[]> buildFieldSlots(int N) {
        Map<String, int[]> result = new HashMap<>();
        Random rng = new Random(42); // fixed seed for reproducibility of slot assignment
        for (String field : FIELD_NAMES) {
            Double freq = cfg.fieldFrequency.get(field);
            if (freq == null || freq <= 0) continue;
            int count = (int) Math.ceil(freq * N);
            count = Math.min(count, N);
            // create an array of all indices, shuffle, take first 'count'
            int[] indices = new int[N];
            for (int i = 0; i < N; i++) indices[i] = i;
            shuffleArray(indices, rng);
            int[] slots = Arrays.copyOf(indices, count);
            Arrays.sort(slots); // sorted for binarySearch in sequential path
            result.put(field, slots);
        }
        return result;
    }

    // ── build eqSlotSets: for each eq-configured field, which of its present
    //    subscription indices must use "=" ────────────────────────────────────
    private Map<String, Set<Integer>> buildEqSlotSets(Map<String, int[]> fieldSlots) {
        Map<String, Set<Integer>> result = new HashMap<>();
        Random rng = new Random(99); // fixed seed
        for (Map.Entry<String, Double> e : cfg.equalityFrequency.entrySet()) {
            String field   = e.getKey();
            double eqFreq  = e.getValue();
            int[] slots    = fieldSlots.get(field);
            if (slots == null || slots.length == 0) continue;
            int count = (int) Math.ceil(eqFreq * slots.length);
            count = Math.min(count, slots.length);
            // pick 'count' random indices from slots
            int[] copy = Arrays.copyOf(slots, slots.length);
            shuffleArray(copy, rng);
            Set<Integer> eqSet = new HashSet<>();
            for (int i = 0; i < count; i++) eqSet.add(copy[i]);
            result.put(field, eqSet);
        }
        return result;
    }

    // ── utilities ─────────────────────────────────────────────────────────────

    private static double randomDouble(Random rng, double min, double max) {
        return min + (max - min) * rng.nextDouble();
    }

    private static String pickOp(String[] ops, Random rng) {
        return ops[rng.nextInt(ops.length)];
    }

    /** Fisher-Yates shuffle for int arrays. */
    private static void shuffleArray(int[] arr, Random rng) {
        for (int i = arr.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp;
        }
    }
}
