package com.threadtrace.compliance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplianceAlertRepository extends JpaRepository<ComplianceAlert, Long> {
    Page<ComplianceAlert> findByResolved(boolean resolved, Pageable pageable);
    Page<ComplianceAlert> findByBrandCode(String brandCode, Pageable pageable);
    Page<ComplianceAlert> findByBrandCodeAndResolved(String brandCode, boolean resolved, Pageable pageable);
    Page<ComplianceAlert> findByAlertType(AlertType alertType, Pageable pageable);
}
