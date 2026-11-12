package com.threadtrace.lineage;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LotRepository extends JpaRepository<Lot, Long> {
    Optional<Lot> findByLotCode(String lotCode);
    boolean existsByLotCode(String lotCode);
    Page<Lot> findByBrandCode(String brandCode, Pageable pageable);
    Page<Lot> findByStatus(LotStatus status, Pageable pageable);
    Page<Lot> findByBrandCodeAndStatus(String brandCode, LotStatus status, Pageable pageable);
    List<Lot> findByIdIn(Collection<Long> ids);
}
