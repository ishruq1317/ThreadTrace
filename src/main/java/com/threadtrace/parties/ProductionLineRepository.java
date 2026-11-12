package com.threadtrace.parties;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductionLineRepository extends JpaRepository<ProductionLine, Long> {
    Optional<ProductionLine> findByLineCode(String lineCode);
    boolean existsByLineCode(String lineCode);
    Page<ProductionLine> findByFactoryId(Long factoryId, Pageable pageable);
}
