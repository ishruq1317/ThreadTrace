package com.threadtrace.evidence;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class EvidenceExpiryScheduler {

    private final EvidenceService evidenceService;

    public EvidenceExpiryScheduler(EvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void runHourlyExpiryCheck() {
        evidenceService.evaluateExpiry(LocalDate.now());
    }
}
