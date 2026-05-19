package ebs.generator;

import java.util.*;

/**
 * Main entry point for the EBS Message Generator.
 *
 * Usage (after compiling):
 *   java -cp <jar> ebs.generator.Main [totalMessages] [threads] [outputDir]
 *
 * Defaults:
 *   totalMessages = 1_000_000
 *   threads       = 4
 *   outputDir     = ./output
 *
 * The program also runs a benchmark comparing 1 thread vs. 4 threads.
 */
public class Main {

    public static void main(String[] args) throws Exception {

        // ── Parse arguments (all optional) ────────────────────────────────────
        int    totalMessages = args.length > 0 ? Integer.parseInt(args[0]) : 1_000_000;
        int    threads       = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        String outputDir     = args.length > 2 ? args[2] : "output";

        // ── Create output directory ────────────────────────────────────────────
        java.io.File dir = new java.io.File(outputDir);
        if (!dir.exists()) dir.mkdirs();

        // ── Field-frequency configuration (sum ≥ 1 across separate fields is OK) ──
        // Each value is the fraction of subscriptions that MUST contain the field.
        Map<String, Double> fieldFreq = new LinkedHashMap<>();
        fieldFreq.put("company",   0.90);   // 90 % of subscriptions include "company"
        fieldFreq.put("value",     0.80);   // 80 %
        fieldFreq.put("drop",      0.60);   // 60 %
        fieldFreq.put("variation", 0.50);   // 50 %
        fieldFreq.put("date",      0.40);   // 40 %

        // ── Equality-frequency configuration ─────────────────────────────────
        // For each field listed here, at least this fraction of subscriptions
        // that contain the field must use the "=" operator.
        Map<String, Double> eqFreq = new LinkedHashMap<>();
        eqFreq.put("company", 0.70);   // 70 % of company-containing subs use "="
        eqFreq.put("value",   0.30);   // 30 % of value-containing subs use "="

        // ── Single run (user-configured threads) ──────────────────────────────
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║   EBS Balanced Message Generator                 ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println("Configuration:");
        System.out.println("  Total publications  : " + totalMessages);
        System.out.println("  Total subscriptions : " + totalMessages);
        System.out.println("  Threads             : " + threads);
        System.out.println("  Output directory    : " + outputDir);
        System.out.println("  Field frequencies   : " + fieldFreq);
        System.out.println("  Equality frequencies: " + eqFreq);
        System.out.println();

        GeneratorConfig cfg = new GeneratorConfig(
                totalMessages, totalMessages,
                fieldFreq, eqFreq,
                threads,
                outputDir + "/publications.txt",
                outputDir + "/subscriptions.txt"
        );

        MessageGenerator gen = new MessageGenerator(cfg);

        System.out.print("Generating publications... ");
        long t0 = System.currentTimeMillis();
        List<Publication> pubs = gen.generatePublications();
        long t1 = System.currentTimeMillis();
        System.out.println("done in " + (t1 - t0) + " ms");
        FileWriter.write(cfg.publicationsFile, pubs);

        System.out.print("Generating subscriptions... ");
        long t2 = System.currentTimeMillis();
        List<Subscription> subs = gen.generateSubscriptions();
        long t3 = System.currentTimeMillis();
        System.out.println("done in " + (t3 - t2) + " ms");
        FileWriter.write(cfg.subscriptionsFile, subs);

        // ── Quick verification ─────────────────────────────────────────────────
        verifySubscriptions(subs, fieldFreq, eqFreq);

        // ── Benchmark (always runs 1 vs 4 for the README) ─────────────────────
        System.out.println();
        BenchmarkRunner.run(totalMessages, fieldFreq, eqFreq,
                new int[]{1, 4},
                outputDir);
    }

    // ── Verification: check actual frequencies match targets ──────────────────
    private static void verifySubscriptions(
            List<Subscription> subs,
            Map<String, Double> fieldFreq,
            Map<String, Double> eqFreq) {

        int N = subs.size();
        // count how many subscriptions contain each field, and how many use "="
        Map<String, Integer> fieldCount = new LinkedHashMap<>();
        Map<String, Integer> eqCount    = new LinkedHashMap<>();
        for (String f : MessageGenerator.FIELD_NAMES) {
            fieldCount.put(f, 0);
            eqCount.put(f, 0);
        }

        for (Subscription sub : subs) {
            String s = sub.toString();
            for (String f : MessageGenerator.FIELD_NAMES) {
                if (s.contains("(" + f + ",")) {
                    fieldCount.merge(f, 1, Integer::sum);
                    // check for equality operator (exact match ",=," not "!=")
                    if (s.contains("(" + f + ",=,")) {
                        eqCount.merge(f, 1, Integer::sum);
                    }
                }
            }
        }

        System.out.println("\n── Verification (field frequencies) ──────────────────");
        for (String f : MessageGenerator.FIELD_NAMES) {
            double target  = fieldFreq.getOrDefault(f, 0.0);
            double actual  = (double) fieldCount.get(f) / N;
            System.out.printf("  %-12s target=%5.1f%%  actual=%5.1f%%  count=%d%n",
                    f, target * 100, actual * 100, fieldCount.get(f));
        }

        System.out.println("\n── Verification (equality frequencies) ───────────────");
        for (Map.Entry<String, Double> e : eqFreq.entrySet()) {
            String f       = e.getKey();
            double target  = e.getValue();
            int    fc      = fieldCount.get(f);
            double actual  = fc == 0 ? 0 : (double) eqCount.get(f) / fc;
            System.out.printf("  %-12s target=%5.1f%%  actual=%5.1f%%  eq=%d / total=%d%n",
                    f, target * 100, actual * 100, eqCount.get(f), fc);
        }
    }
}
