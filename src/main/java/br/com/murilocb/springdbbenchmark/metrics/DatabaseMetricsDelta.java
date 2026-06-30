package br.com.murilocb.springdbbenchmark.metrics;

/**
 * Delta between two snapshots. WAL is computed from LSN diff (reliable, not
 * stats-collector based). Block counts are kept for write ops as INDICATIVE
 * only — pg_statio flush lags ~1s, so short ops may under-report. Tuple counts
 * are taken from the operation's own rowcount, not from here.
 */
public record DatabaseMetricsDelta(
        long walGeneratedBytes,
        double walGeneratedMb,
        long heapBlocksRead,
        long heapBlocksHit,
        long checkpointReq,
        long buffersWritten
) {
    public static DatabaseMetricsDelta of(DatabaseSnapshot before, DatabaseSnapshot after) {
        long walBytes = after.walLsnBytes() - before.walLsnBytes();
        return new DatabaseMetricsDelta(
                walBytes,
                walBytes / (1024.0 * 1024.0),
                after.heapBlksRead() - before.heapBlksRead(),
                after.heapBlksHit() - before.heapBlksHit(),
                after.checkpointsReq() - before.checkpointsReq(),
                after.buffersWritten() - before.buffersWritten()
        );
    }
}
