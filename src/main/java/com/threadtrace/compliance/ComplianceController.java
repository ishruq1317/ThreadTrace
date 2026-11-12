package com.threadtrace.compliance;

import com.threadtrace.lineage.Lot;
import com.threadtrace.lineage.LotRepository;
import com.threadtrace.shared.Audience;
import com.threadtrace.shared.AudienceAccessService;
import com.threadtrace.shared.DomainExceptions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/compliance")
@Tag(name = "Compliance & Mass Balance", description = "FR4, FR5: Verified claim roll-up and compliance alerts")
public class ComplianceController {

    private final ComplianceAlertRepository alertRepository;
    private final MassBalanceService massBalanceService;
    private final LotRepository lotRepository;
    private final AudienceAccessService audienceAccessService;

    public ComplianceController(
            ComplianceAlertRepository alertRepository,
            MassBalanceService massBalanceService,
            LotRepository lotRepository,
            AudienceAccessService audienceAccessService) {
        this.alertRepository = alertRepository;
        this.massBalanceService = massBalanceService;
        this.lotRepository = lotRepository;
        this.audienceAccessService = audienceAccessService;
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'OPERATOR', 'BRAND')")
    @Operation(summary = "List compliance alerts (brand-scoped for BRAND role)")
    public Page<AlertView> listAlerts(
            @RequestParam(required = false) Boolean resolved,
            @PageableDefault(size = 20) Pageable pageable) {
        Audience audience = audienceAccessService.resolveAudience();
        Page<ComplianceAlert> page;
        if (audience == Audience.BRAND) {
            String brand = audienceAccessService.currentBrandScope().orElse("");
            page = resolved != null
                    ? alertRepository.findByBrandCodeAndResolved(brand, resolved, pageable)
                    : alertRepository.findByBrandCode(brand, pageable);
        } else {
            page = resolved != null
                    ? alertRepository.findByResolved(resolved, pageable)
                    : alertRepository.findAll(pageable);
        }
        return page.map(a -> new AlertView(
                a.getId(), a.getAlertType(), a.getSeverity(), a.getBrandCode(),
                a.getLotId(), a.getCertificateId(), a.getMessage(), a.isResolved(), a.getCreatedAt()));
    }

    @GetMapping("/lots/{lotCode}/claims")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'OPERATOR', 'BRAND')")
    @Operation(summary = "Evaluate verified claims and violations across full lineage of a lot")
    public LotClaimEvaluationView evaluateLotClaims(
            @PathVariable String lotCode,
            @RequestParam(required = false) LocalDate asOfDate) {
        Lot lot = lotRepository.findByLotCode(lotCode)
                .orElseThrow(() -> new DomainExceptions.ResourceNotFoundException("Lot not found: " + lotCode));
        MassBalanceService.ClaimRollupResult result = massBalanceService.evaluateClaimsForLot(
                lot, asOfDate != null ? asOfDate : LocalDate.now());
        List<String> validCertNumbers = result.validCertificates().stream()
                .map(c -> c.getCertificateNumber() + " (" + c.getCertificateType() + ")")
                .toList();
        return new LotClaimEvaluationView(
                result.lotCode(),
                result.verifiedClaimPercentages(),
                validCertNumbers,
                result.violations(),
                result.violations().isEmpty()
        );
    }

    public record AlertView(
            Long id,
            AlertType alertType,
            AlertSeverity severity,
            String brandCode,
            Long lotId,
            Long certificateId,
            String message,
            boolean resolved,
            Instant createdAt
    ) {
    }

    public record LotClaimEvaluationView(
            String lotCode,
            Map<String, BigDecimal> verifiedClaimPercentages,
            List<String> validCertificates,
            List<String> violations,
            boolean compliant
    ) {
    }
}
