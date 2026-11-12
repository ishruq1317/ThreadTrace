package com.threadtrace.ingestion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
    Optional<ImportBatch> findByBatchCode(String batchCode);
    Page<ImportBatch> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
