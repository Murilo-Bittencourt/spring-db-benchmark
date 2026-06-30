package br.com.murilocb.springdbbenchmark.metrics;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.List;

/** Full benchmark run: metadata + all results. Serialized to a JSON file. */
public record BenchmarkReport(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        Instant executedAt,
        int volume,
        String strategy,
        long totalBenchmarkMs,
        int totalResults,
        List<BenchmarkResult> results
) {}
