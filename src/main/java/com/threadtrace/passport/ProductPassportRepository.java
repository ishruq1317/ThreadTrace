package com.threadtrace.passport;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductPassportRepository extends JpaRepository<ProductPassport, Long> {
    Optional<ProductPassport> findByPassportCodeAndLatestVersionTrue(String passportCode);
    Optional<ProductPassport> findByPassportCodeAndVersion(String passportCode, int version);
    Page<ProductPassport> findByPassportCodeOrderByVersionDesc(String passportCode, Pageable pageable);
    List<ProductPassport> findByLatestVersionTrue();
    Page<ProductPassport> findByLatestVersionTrue(Pageable pageable);
    Page<ProductPassport> findByBrandCodeAndLatestVersionTrue(String brandCode, Pageable pageable);
}
