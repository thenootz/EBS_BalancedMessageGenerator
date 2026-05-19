package ebs.generator;

import java.util.*;

/**
 * BenchmarkRunner runs the generator with different thread counts and prints
 * timing results.  This is the data for the README.
 */
public class BenchmarkRunner {

    public static void run(int totalMessages, Map<String, Double> fieldFreq,
                           Map<String, Double> eqFreq,
                           int[] threadCounts,
                           String outputDir) throws Exception {

        System.out.println("=============================================================");
        System.out.println("  BENCHMARK: " + totalMessages + " publications + " + totalMessages + " subscriptions");
        System.out.println("=============================================================");

        for (int threads : threadCounts) {
            GeneratorConfig cfg = new GeneratorConfig(
                    totalMessages, totalMessages,
                    fieldFreq, eqFreq,
                    threads,
                    outputDir + "/publications_t" + threads + ".txt",
                    outputDir + "/subscriptions_t" + threads + ".txt"
            );

            System.gc(); // suggest GC between runs to get fair timing

            MessageGenerator gen = new MessageGenerator(cfg);

            // ── Publications ──────────────────────────────────────────────────
            long t0 = System.currentTimeMillis();
            List<Publication> pubs = gen.generatePublications();
            long t1 = System.currentTimeMillis();
            FileWriter.write(cfg.publicationsFile, pubs);
            long t2 = System.currentTimeMillis();
            pubs = null; // allow GC

            // ── Subscriptions ─────────────────────────────────────────────────
            System.gc();
            long t3 = System.currentTimeMillis();
            List<Subscription> subs = gen.generateSubscriptions();
            long t4 = System.currentTimeMillis();
            FileWriter.write(cfg.subscriptionsFile, subs);
            long t5 = System.currentTimeMillis();
            subs = null; // allow GC

            // ── Stats ─────────────────────────────────────────────────────────
            System.out.println("\n--- Threads: " + threads + " ---");
            System.out.printf("  Publications  generation: %5d ms%n", t1 - t0);
            System.out.printf("  Publications  file write: %5d ms%n", t2 - t1);
            System.out.printf("  Subscriptions generation: %5d ms%n", t4 - t3);
            System.out.printf("  Subscriptions file write: %5d ms%n", t5 - t4);
            System.out.printf("  Total wall time:          %5d ms%n", (t2 - t0) + (t5 - t3));
        }
        System.out.println("=============================================================");
    }
}
