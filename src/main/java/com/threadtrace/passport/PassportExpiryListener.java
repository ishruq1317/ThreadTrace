package com.threadtrace.passport;

import com.threadtrace.evidence.CertificateEvents;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;

/**
 * Observer Pattern listener: automatically regenerates passports and drops claims
 * when supporting certificate evidence expires (FR5).
 */
@Component
public class PassportExpiryListener {

    private final PassportService passportService;

    public PassportExpiryListener(PassportService passportService) {
        this.passportService = passportService;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onCertificateExpired(CertificateEvents.CertificateExpiredEvent event) {
        LocalDate effectiveDate = event.evaluationDate() != null
                ? event.evaluationDate()
                : event.validTo().plusDays(1);
        passportService.recalculateAllForExpiredEvidence(
                event.supplierId(),
                event.lotId(),
                effectiveDate);
    }
}
