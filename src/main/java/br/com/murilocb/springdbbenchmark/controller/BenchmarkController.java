package br.com.murilocb.springdbbenchmark.controller;

import br.com.murilocb.springdbbenchmark.metrics.BenchmarkReport;
import br.com.murilocb.springdbbenchmark.metrics.BenchmarkResult;
import br.com.murilocb.springdbbenchmark.result.ResultStorageService;
import br.com.murilocb.springdbbenchmark.runner.BenchmarkOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/benchmark")
@RequiredArgsConstructor
public class BenchmarkController {

    private final BenchmarkOrchestrator orchestrator;
    private final ResultStorageService storage;

    @GetMapping("/strategies")
    public ResponseEntity<Map<String, Object>> strategies() {
        return ResponseEntity.ok(Map.of("strategies", orchestrator.strategyIds()));
    }

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> run(
            @RequestParam(defaultValue = "10000") int volume,
            @RequestParam(required = false) String strategy) {

        long t0 = System.currentTimeMillis();
        List<BenchmarkResult> results = orchestrator.run(volume, strategy);
        long totalMs = System.currentTimeMillis() - t0;

        BenchmarkReport report = new BenchmarkReport(
                Instant.now(), volume, strategy, totalMs, results.size(), results);
        String savedAs = storage.save(report);

        return ResponseEntity.ok(Map.of(
                "results", results,
                "totalResults", results.size(),
                "totalBenchmarkMs", totalMs,
                "savedAs", savedAs
        ));
    }

    @GetMapping("/results")
    public ResponseEntity<Map<String, Object>> listResults() {
        return ResponseEntity.ok(Map.of("files", storage.list()));
    }

    @GetMapping(value = "/results/latest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> latest() {
        String json = storage.latestJson();
        if (json == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(json);
    }

    @GetMapping(value = "/results/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> byName(@PathVariable String name) {
        return ResponseEntity.ok(storage.read(name));
    }
}
