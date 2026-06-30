package br.com.murilocb.springdbbenchmark.result;

import br.com.murilocb.springdbbenchmark.metrics.BenchmarkReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/** Persists benchmark reports as timestamped JSON files and reads them back for the dashboard. */
@Slf4j
@Service
public class ResultStorageService {

    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final ObjectMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
    private final Path dir;

    public ResultStorageService(@Value("${benchmark.results.dir:results}") String dir) {
        this.dir = Paths.get(dir);
    }

    public String save(BenchmarkReport report) {
        try {
            Files.createDirectories(dir);
            String name = "benchmark-" + FILE_TS.format(report.executedAt()) + ".json";
            Path file = dir.resolve(name);
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), report);
            log.info("Benchmark report saved: {}", file.toAbsolutePath());
            return name;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save benchmark report", e);
        }
    }

    public List<String> list() {
        if (!Files.isDirectory(dir)) return List.of();
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString())
                    .sorted(Comparator.reverseOrder())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list reports", e);
        }
    }

    /** Raw JSON of the newest report, or null if none. */
    public String latestJson() {
        List<String> files = list();
        if (files.isEmpty()) return null;
        return read(files.get(0));
    }

    public String read(String name) {
        // Guard against path traversal — only a bare file name within the dir.
        Path file = dir.resolve(name).normalize();
        if (!file.startsWith(dir.normalize()) || !Files.exists(file)) {
            throw new IllegalArgumentException("Report not found: " + name);
        }
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read report", e);
        }
    }
}
