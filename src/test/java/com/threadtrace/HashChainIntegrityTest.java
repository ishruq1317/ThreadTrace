package com.threadtrace;

import com.threadtrace.lineage.EventIntegrityService;
import com.threadtrace.lineage.EventType;
import com.threadtrace.lineage.LineageDtos;
import com.threadtrace.lineage.Lot;
import com.threadtrace.lineage.LotEvent;
import com.threadtrace.lineage.LotEventRepository;
import com.threadtrace.lineage.LotRepository;
import com.threadtrace.lineage.LotType;
import com.threadtrace.lineage.LotUnit;
import com.threadtrace.shared.HashUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class HashChainIntegrityTest {

    @Test
    void detectsBrokenPreviousHashAndTamperedSequence() throws Exception {
        LotRepository lotRepo = Mockito.mock(LotRepository.class);
        LotEventRepository eventRepo = Mockito.mock(LotEventRepository.class);
        EventIntegrityService service = new EventIntegrityService(lotRepo, eventRepo);

        Lot lot = new Lot("LOT-H1", LotType.FABRIC, "Cotton", "ST-1", "BRAND_A",
                1L, 2L, BigDecimal.TEN, BigDecimal.TEN, 0, LotUnit.KG, null, BigDecimal.ZERO, false);
        var idField = Lot.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(lot, 100L);

        LotEvent e1 = new LotEvent("E1", 100L, "LOT-H1", EventType.RECEIVE, "receive", 1L, null,
                BigDecimal.TEN, BigDecimal.TEN, LotUnit.KG, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, 1L, HashUtils.GENESIS_HASH, false, null, null, "op", Instant.now());
        LotEvent e2 = new LotEvent("E2", 100L, "LOT-H1", EventType.CORRECTION, "correction", 1L, null,
                BigDecimal.TEN, BigDecimal.TEN, LotUnit.KG, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                1L, 2L, e1.getEventHash(), false, null, "fix", "op", Instant.now());

        lot.applyEventState(EventType.RECEIVE, false, 1L, e1.getEventHash());
        lot.applyEventState(EventType.CORRECTION, false, 2L, e2.getEventHash());

        when(eventRepo.findByLotIdOrderBySequenceNumberAsc(100L)).thenReturn(List.of(e1, e2));
        LineageDtos.LotChainVerificationResult valid = service.verifyLot(lot);
        assertTrue(valid.verified());

        // Break chain with wrong previous hash
        LotEvent tampered = new LotEvent("E2", 100L, "LOT-H1", EventType.CORRECTION, "correction", 1L, null,
                BigDecimal.TEN, BigDecimal.TEN, LotUnit.KG, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                1L, 2L, "f".repeat(64), false, null, "fix", "op", Instant.now());
        when(eventRepo.findByLotIdOrderBySequenceNumberAsc(100L)).thenReturn(List.of(e1, tampered));
        LineageDtos.LotChainVerificationResult invalid = service.verifyLot(lot);
        assertFalse(invalid.verified());

        // Tamper with footprint metric directly on stored event row
        LotEvent metricTampered = new LotEvent("E1", 100L, "LOT-H1", EventType.RECEIVE, "receive", 1L, null,
                BigDecimal.TEN, BigDecimal.TEN, LotUnit.KG, BigDecimal.valueOf(999), BigDecimal.ZERO, BigDecimal.ZERO,
                null, 1L, HashUtils.GENESIS_HASH, false, null, null, "op", Instant.now());
        var hashField = LotEvent.class.getDeclaredField("eventHash");
        hashField.setAccessible(true);
        hashField.set(metricTampered, e1.getEventHash());
        when(eventRepo.findByLotIdOrderBySequenceNumberAsc(100L)).thenReturn(List.of(metricTampered, e2));
        LineageDtos.LotChainVerificationResult invalidMetric = service.verifyLot(lot);
        assertFalse(invalidMetric.verified());
    }
}
