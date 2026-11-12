package com.threadtrace.compliance;

import com.threadtrace.evidence.CertificateEvents;
import com.threadtrace.lineage.LotEventRecordedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ComplianceEventObserver {

    private final ComplianceAlertRepository alertRepository;

    public ComplianceEventObserver(ComplianceAlertRepository alertRepository) {
        this.alertRepository = alertRepository;
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
        alertRepository.save(new ComplianceAlert(
                AlertType.CERTIFICATE_EXPIRING_SOON,
                AlertSeverity.WARNING,
                null,
                event.lotId(),
                event.certificateId(),
                "Certificate " + event.certificateNumber() + " (" + event.certificateType()
                        + ") expires on " + event.validTo()
        ));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onCertificateExpired(CertificateEvents.CertificateExpiredEvent event) {
        alertRepository.save(new ComplianceAlert(
                AlertType.CERTIFICATE_EXPIRED,
                AlertSeverity.CRITICAL,
                null,
                event.lotId(),
                event.certificateId(),
                "Certificate " + event.certificateNumber() + " (" + event.certificateType()
                        + ") expired on " + event.validTo() + "; dependent claims must be dropped"
        ));
    }
}
