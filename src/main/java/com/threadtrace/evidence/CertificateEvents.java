package com.threadtrace.evidence;

import java.time.LocalDate;

public final class CertificateEvents {

    private CertificateEvents() {
    }

    public record CertificateExpiringSoonEvent(
            Long certificateId,
            String certificateNumber,
            String certificateType,
            Long supplierId,
            Long lotId,
            LocalDate validTo
    ) {
    }

    public record CertificateExpiredEvent(
            Long certificateId,
            String certificateNumber,
            String certificateType,
            String claimedAttribute,
            Long supplierId,
            Long lotId,
            LocalDate validTo
    ) {
    }
}
