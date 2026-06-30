package br.com.murilocb.springdbbenchmark.metrics;

import br.com.murilocb.springdbbenchmark.model.Operation;
import br.com.murilocb.springdbbenchmark.model.RunType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class BenchmarkResult {
    private String strategy;
    private Operation operation;
    private int volume;
    private RunType runType;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant executedAt;

    private long elapsedMs;

    /** Aproximação via Runtime.totalMemory-freeMemory. Pode ser negativo (GC não-determinístico). */
    private long memoryDeltaKb;

    private double throughputPerSecond;

    private long walGeneratedBytes;
    private double walGeneratedMb;
    private long heapBlocksRead;
    private long heapBlocksHit;
    private String cacheHitPercent;
    private long tuplesAffected;
    private long checkpointReq;
    private long buffersWritten;

    public static BenchmarkResult from(
            String strategy, Operation operation, int volume, RunType runType,
            long elapsedMs, long memoryDeltaKb, long rowsAffected,
            long heapBlocksHit, long heapBlocksRead, DatabaseMetricsDelta delta) {
        double throughput = elapsedMs == 0 ? 0 : volume * 1000.0 / elapsedMs;
        long totalIo = heapBlocksHit + heapBlocksRead;
        String cacheHit = totalIo == 0 ? "N/A" : String.format("%.2f%%", heapBlocksHit * 100.0 / totalIo);
        return BenchmarkResult.builder()
                .strategy(strategy)
                .operation(operation)
                .volume(volume)
                .runType(runType)
                .executedAt(Instant.now())
                .elapsedMs(elapsedMs)
                .memoryDeltaKb(memoryDeltaKb)
                .throughputPerSecond(Math.round(throughput * 100.0) / 100.0)
                .walGeneratedBytes(delta.walGeneratedBytes())
                .walGeneratedMb(Math.round(delta.walGeneratedMb() * 100.0) / 100.0)
                .heapBlocksRead(heapBlocksRead)
                .heapBlocksHit(heapBlocksHit)
                .cacheHitPercent(cacheHit)
                .tuplesAffected(rowsAffected)
                .checkpointReq(delta.checkpointReq())
                .buffersWritten(delta.buffersWritten())
                .build();
    }
}
