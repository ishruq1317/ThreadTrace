package com.threadtrace.compliance;

import com.threadtrace.evidence.CertificateEvents;
import com.threadtrace.lineage.Lot;
import com.threadtrace.lineage.LotEventRecordedEvent;
import com.threadtrace.lineage.LotRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ComplianceEventObserver {

    private final ComplianceAlertRepository alertRepository;
    private final LotRepository lotRepository;

    public ComplianceEventObserver(ComplianceAlertRepository alertRepository, LotRepository lotRepository) {
        this.alertRepository = alertRepository;
        this.lotRepository = lotRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onLotEventRecorded(LotEventRecordedEvent event) {
        if (event.orphan()) {
            alertRepository.save(new ComplianceAlert(
                    AlertType.ORPHAN_LOT,
                    AlertSeverity.CRITICAL,
                    event.brandCode(),
                    event.lotId(),
                    null,
                    "Orphan lot " + event.lotCode() + " recorded without registered supplier"
            ));
        }
        if (event.flagged() && event.flagReason() != null) {
            AlertType type = event.flagReason().toLowerCase().contains("over-claimed")
                    ? AlertType.OVER_CLAIMED_CONTENT
                    : AlertType.MASS_BALANCE_VIOLATION;
            alertRepository.save(new ComplianceAlert(
                    type,
                    AlertSeverity.CRITICAL,
                    event.brandCode(),
                    event.lotId(),
                    null,
                    event.flagReason()
            ));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onCertificateExpiringSoon(CertificateEvents.CertificateExpiringSoonEvent event) {
        String brandCode = resolveBrandCode(event.lotId());
        alertRepository.save(new ComplianceAlert(
                AlertType.CERTIFICATE_EXPIRING_SOON,
                AlertSeverity.WARNING,
                brandCode,
                event.lotId(),
                event.certificateId(),
                "Certificate " + event.certificateNumber() + " (" + event.certificateType()
                        + ") expires on " + event.validTo()
        ));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onCertificateExpired(CertificateEvents.CertificateExpiredEvent event) {
        String brandCode = resolveBrandCode(event.lotId());
        alertRepository.save(new ComplianceAlert(
                AlertType.CERTIFICATE_EXPIRED,
                AlertSeverity.CRITICAL,
                brandCode,
                event.lotId(),
                event.certificateId(),
                "Certificate " + event.certificateNumber() + " (" + event.certificateType()
                        + ") expired on " + event.validTo() + "; dependent claims must be dropped"
        ));
    }

    private String resolveBrandCode(Long lotId) {
        if (lotId == null) {
            return null;
        }
        return lotRepository.findById(lotId).map(Lot::getBrandCode).orElse(null);
    }
}
