package br.com.murilocb.springdbbenchmark.runner;

import br.com.murilocb.springdbbenchmark.metrics.BenchmarkResult;
import br.com.murilocb.springdbbenchmark.model.Operation;
import br.com.murilocb.springdbbenchmark.strategy.BenchmarkStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BenchmarkOrchestrator {

    private final BenchmarkRunner runner;
    private final List<BenchmarkStrategy> allStrategies;

    public List<BenchmarkResult> run(int volume, String strategyId) {
        List<BenchmarkStrategy> targets = resolveStrategies(strategyId);
        List<BenchmarkResult> results = new ArrayList<>();

        for (BenchmarkStrategy strategy : targets) {
            for (Operation operation : Operation.values()) {
                try {
                    results.addAll(runner.run(strategy, operation, volume));
                } catch (Exception e) {
                    log.error("Benchmark failed [{} {}]: {}", strategy.id(), operation, e.getMessage(), e);
                }
            }
        }
        return results;
    }

    public List<String> strategyIds() {
        return allStrategies.stream().map(BenchmarkStrategy::id).toList();
    }

    private List<BenchmarkStrategy> resolveStrategies(String strategyId) {
        if (strategyId == null || strategyId.isBlank()) return allStrategies;
        Map<String, BenchmarkStrategy> byId = allStrategies.stream()
                .collect(Collectors.toMap(BenchmarkStrategy::id, Function.identity()));
        BenchmarkStrategy found = byId.get(strategyId.toUpperCase());
        if (found == null) throw new IllegalArgumentException("Unknown strategy: " + strategyId);
        return List.of(found);
    }
}
