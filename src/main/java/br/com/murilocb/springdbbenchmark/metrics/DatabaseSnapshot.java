package br.com.murilocb.springdbbenchmark.metrics;

public record DatabaseSnapshot(
        long walLsnBytes,
        long heapBlksRead,
        long heapBlksHit,
        long tupIns,
        long tupUpd,
        long tupDel,
        long checkpointsReq,
        long buffersWritten
) {}
