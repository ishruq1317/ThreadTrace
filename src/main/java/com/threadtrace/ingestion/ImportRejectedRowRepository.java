package com.threadtrace.ingestion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportRejectedRowRepository extends JpaRepository<ImportRejectedRow, Long> {
    List<ImportRejectedRow> findByBatchIdOrderByRowNumberAsc(Long batchId);
    Page<ImportRejectedRow> findByBatchIdOrderByRowNumberAsc(Long batchId, Pageable pageable);
}
