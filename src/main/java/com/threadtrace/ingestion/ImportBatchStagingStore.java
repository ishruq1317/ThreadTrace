package com.threadtrace.ingestion;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ImportBatchStagingStore {

    private final Map<String, List<FactoryImportRow>> rowsByBatch = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> acceptedCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> rejectedCounters = new ConcurrentHashMap<>();

    public void init(String batchCode, List<FactoryImportRow> rows) {
        rowsByBatch.put(batchCode, List.copyOf(rows));
        acceptedCounters.put(batchCode, new AtomicInteger(0));
        rejectedCounters.put(batchCode, new AtomicInteger(0));
    }

    public List<FactoryImportRow> getRows(String batchCode) {
        return rowsByBatch.getOrDefault(batchCode, List.of());
    }

    public void incrementAccepted(String batchCode) {
        acceptedCounters.computeIfAbsent(batchCode, k -> new AtomicInteger(0)).incrementAndGet();
    }

    public void incrementRejected(String batchCode) {
        rejectedCounters.computeIfAbsent(batchCode, k -> new AtomicInteger(0)).incrementAndGet();
    }

    public int acceptedCount(String batchCode) {
        return acceptedCounters.getOrDefault(batchCode, new AtomicInteger(0)).get();
    }

    public int rejectedCount(String batchCode) {
        return rejectedCounters.getOrDefault(batchCode, new AtomicInteger(0)).get();
    }

    public void clear(String batchCode) {
        rowsByBatch.remove(batchCode);
        acceptedCounters.remove(batchCode);
        rejectedCounters.remove(batchCode);
    }
}
