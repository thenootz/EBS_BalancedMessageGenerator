package ebs.generator;

import java.util.Map;

/**
 * Holds all configuration parameters for the generator.
 */
public class GeneratorConfig {

    // ── total counts ───────────────────────────────────────────────────────────
    /** Total number of publications to generate. */
    public final int totalPublications;
    /** Total number of subscriptions to generate. */
    public final int totalSubscriptions;

    // ── field-frequency weights for subscriptions ──────────────────────────────
    /**
     * For each field name: the fraction (0–1) of subscriptions that must include it.
     * E.g. {"company":0.9, "value":0.7, ...}
     * Note: sum of values CAN exceed 1.0 (multiple fields present per subscription is normal).
     */
    public final Map<String, Double> fieldFrequency;

    /**
     * For each field name that supports equality operator configuration:
     * the minimum fraction of subscriptions *that contain the field* which must use "=".
     * E.g. {"company":0.70} means 70 % of subscriptions that have "company" use "=".
     */
    public final Map<String, Double> equalityFrequency;

    // ── parallelism ────────────────────────────────────────────────────────────
    /** Number of worker threads. 1 = sequential. */
    public final int numThreads;

    // ── output paths ───────────────────────────────────────────────────────────
    public final String publicationsFile;
    public final String subscriptionsFile;

    public GeneratorConfig(
            int totalPublications,
            int totalSubscriptions,
            Map<String, Double> fieldFrequency,
            Map<String, Double> equalityFrequency,
            int numThreads,
            String publicationsFile,
            String subscriptionsFile) {
        this.totalPublications  = totalPublications;
        this.totalSubscriptions = totalSubscriptions;
        this.fieldFrequency     = fieldFrequency;
        this.equalityFrequency  = equalityFrequency;
        this.numThreads         = numThreads;
        this.publicationsFile   = publicationsFile;
        this.subscriptionsFile  = subscriptionsFile;
    }
}
