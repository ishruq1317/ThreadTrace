package com.threadtrace.evidence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    Optional<Certificate> findByCertificateNumber(String certificateNumber);
    boolean existsByCertificateNumber(String certificateNumber);
    Page<Certificate> findByStatus(CertificateStatus status, Pageable pageable);

    @Query("""
            SELECT c FROM Certificate c
            WHERE (c.lotId IS NULL OR c.lotId IN (SELECT l.id FROM Lot l WHERE UPPER(l.brandCode) = UPPER(:brandCode)))
            """)
    Page<Certificate> findVisibleToBrand(@Param("brandCode") String brandCode, Pageable pageable);

    @Query("""
            SELECT c FROM Certificate c
            WHERE c.status = :status
              AND (c.lotId IS NULL OR c.lotId IN (SELECT l.id FROM Lot l WHERE UPPER(l.brandCode) = UPPER(:brandCode)))
            """)
    Page<Certificate> findVisibleToBrandByStatus(
            @Param("brandCode") String brandCode,
            @Param("status") CertificateStatus status,
            Pageable pageable);

    List<Certificate> findBySupplierIdInOrLotIdIn(Collection<Long> supplierIds, Collection<Long> lotIds);
    List<Certificate> findByStatusIn(Collection<CertificateStatus> statuses);
}
