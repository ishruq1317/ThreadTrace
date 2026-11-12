package com.threadtrace.evidence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    Optional<Certificate> findByCertificateNumber(String certificateNumber);
    boolean existsByCertificateNumber(String certificateNumber);
    Page<Certificate> findByStatus(CertificateStatus status, Pageable pageable);
    List<Certificate> findBySupplierIdInOrLotIdIn(Collection<Long> supplierIds, Collection<Long> lotIds);
    List<Certificate> findByStatusIn(Collection<CertificateStatus> statuses);
}
