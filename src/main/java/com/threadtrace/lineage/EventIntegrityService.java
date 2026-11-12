package com.threadtrace.lineage;

import com.threadtrace.shared.DomainExceptions;
import com.threadtrace.shared.HashUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class EventIntegrityService {

    private final LotRepository lotRepository;
    private final LotEventRepository lotEventRepository;

    public EventIntegrityService(LotRepository lotRepository, LotEventRepository lotEventRepository) {
        this.lotRepository = lotRepository;
        this.lotEventRepository = lotEventRepository;
    }

    public LineageDtos.LotChainVerificationResult verifyLotChain(String lotCode) {
        Lot lot = lotRepository.findByLotCode(lotCode)
                .orElseThrow(() -> new DomainExceptions.ResourceNotFoundException("Lot not found: " + lotCode));
        return verifyLot(lot);
    }

    public LineageDtos.LotChainVerificationResult verifyLot(Lot lot) {
        List<LotEvent> events = lotEventRepository.findByLotIdOrderBySequenceNumberAsc(lot.getId());
        if (events.isEmpty()) {
            boolean ok = lot.getEventSequence() == 0 && HashUtils.GENESIS_HASH.equals(lot.getLatestEventHash());
            return new LineageDtos.LotChainVerificationResult(
                    lot.getLotCode(), ok, 0, lot.getLatestEventHash(),
                    ok ? null : "Lot has no events but non-genesis metadata");
        }

        String expectedPrevHash = HashUtils.GENESIS_HASH;
        long expectedSeq = 1L;
        for (LotEvent ev : events) {
            if (ev.getSequenceNumber() != expectedSeq) {
                return new LineageDtos.LotChainVerificationResult(
                        lot.getLotCode(), false, events.size(), lot.getLatestEventHash(),
                        "Sequence gap at event " + ev.getEventCode() + ": expected " + expectedSeq + " but found " + ev.getSequenceNumber());
            }
            if (!expectedPrevHash.equals(ev.getPreviousHash())) {
                return new LineageDtos.LotChainVerificationResult(
                        lot.getLotCode(), false, events.size(), lot.getLatestEventHash(),
                        "Previous hash mismatch at event " + ev.getEventCode());
            }
            String recomputed = HashUtils.sha256(LotEvent.canonicalPayload(
                    lot.getLotCode(),
                    ev.getSequenceNumber(),
                    ev.getPreviousHash(),
                    ev.getEventType(),
                    ev.getProcessStep(),
                    ev.getInputQuantity(),
                    ev.getOutputQuantity(),
                    ev.getUnit(),
                    ev.getOccurredAt(),
                    ev.getRecordedBy(),
                    ev.getCorrectsEventId()
            ));
            if (!recomputed.equals(ev.getEventHash())) {
                return new LineageDtos.LotChainVerificationResult(
                        lot.getLotCode(), false, events.size(), lot.getLatestEventHash(),
                        "Tampered event payload detected at event " + ev.getEventCode());
            }
            expectedPrevHash = ev.getEventHash();
            expectedSeq++;
        }

        if (lot.getEventSequence() != events.size() || !expectedPrevHash.equals(lot.getLatestEventHash())) {
            return new LineageDtos.LotChainVerificationResult(
                    lot.getLotCode(), false, events.size(), lot.getLatestEventHash(),
                    "Lot head hash or sequence does not match latest event");
        }

        return new LineageDtos.LotChainVerificationResult(
                lot.getLotCode(), true, events.size(), expectedPrevHash, null);
    }

    public List<LineageDtos.LotChainVerificationResult> verifyLots(List<Lot> lots) {
        List<LineageDtos.LotChainVerificationResult> results = new ArrayList<>(lots.size());
        for (Lot lot : lots) {
            results.add(verifyLot(lot));
        }
        return results;
    }
}
