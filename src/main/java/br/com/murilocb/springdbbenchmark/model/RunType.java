package br.com.murilocb.springdbbenchmark.model;

public enum RunType {
    /** Clean table — full DB reset (TRUNCATE + VACUUM FULL ANALYZE + CHECKPOINT) before the run. */
    COLD,
    /** Continues from the COLD state — measures warm-cache behaviour. */
    WARM
}
