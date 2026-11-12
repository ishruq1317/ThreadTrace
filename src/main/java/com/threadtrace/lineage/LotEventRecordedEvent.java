package com.threadtrace.lineage;

public record LotEventRecordedEvent(
        Long lotId,
        String lotCode,
        Long eventId,
        String eventCode,
        EventType eventType,
        String brandCode,
        boolean flagged,
        String flagReason,
        boolean orphan
) {
}
