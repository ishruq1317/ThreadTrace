package com.threadtrace.lineage;

import com.threadtrace.config.ThreadTraceProperties;
import com.threadtrace.parties.Party;
import com.threadtrace.parties.PartyService;
import com.threadtrace.parties.ProductionLine;
import com.threadtrace.shared.Audience;
import com.threadtrace.shared.AudienceAccessService;
import com.threadtrace.shared.DomainExceptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class LineageService {

    private final LotRepository lotRepository;
    private final LotEventRepository lotEventRepository;
    private final LotEdgeRepository lotEdgeRepository;
    private final LineageQueryRepository lineageQueryRepository;
    private final PartyService partyService;
    private final ThreadTraceProperties properties;
    private final AudienceAccessService audienceAccessService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectProvider<MassBalanceCheckPort> massBalancePortProvider;

    public LineageService(
            LotRepository lotRepository,
            LotEventRepository lotEventRepository,
            LotEdgeRepository lotEdgeRepository,
            LineageQueryRepository lineageQueryRepository,
            PartyService partyService,
            ThreadTraceProperties properties,
            AudienceAccessService audienceAccessService,
            ApplicationEventPublisher eventPublisher,
            ObjectProvider<MassBalanceCheckPort> massBalancePortProvider) {
        this.lotRepository = lotRepository;
        this.lotEventRepository = lotEventRepository;
        this.lotEdgeRepository = lotEdgeRepository;
        this.lineageQueryRepository = lineageQueryRepository;
        this.partyService = partyService;
        this.properties = properties;
        this.audienceAccessService = audienceAccessService;
        this.eventPublisher = eventPublisher;
        this.massBalancePortProvider = massBalancePortProvider;
    }

    @Transactional
    public LineageDtos.LotView recordReceive(LineageDtos.ReceiveLotRequest req) {
        if (lotRepository.existsByLotCode(req.lotCode())) {
            throw new DomainExceptions.DuplicateResourceException("Lot already exists: " + req.lotCode());
        }
        Party supplier = (req.supplierCode() != null && !req.supplierCode().isBlank())
                ? partyService.requirePartyByCode(req.supplierCode())
                : null;
        Party factory = (req.factoryCode() != null && !req.factoryCode().isBlank())
                ? partyService.requirePartyByCode(req.factoryCode())
                : null;
        ProductionLine line = (req.productionLineCode() != null && !req.productionLineCode().isBlank())
                ? partyService.requireLineByCode(req.productionLineCode())
                : null;

        boolean orphan = supplier == null;
        boolean flagged = orphan;
        String flagReason = orphan ? "Orphan lot: received without a registered supplier" : null;

        if (!orphan && req.claimedAttribute() != null && !req.claimedAttribute().isBlank()) {
            MassBalanceCheckPort port = massBalancePortProvider.getIfAvailable();
            if (port != null) {
                MassBalanceCheckPort.MassBalanceDecision claimDecision = port.evaluateTransformation(
                        "receive",
                        List.of(),
                        req.quantity(),
                        req.quantity(),
                        req.unit(),
                        req.claimedAttribute(),
                        req.claimedPct(),
                        req.massKg());
                if (claimDecision.flagged()) {
                    flagged = true;
                    flagReason = claimDecision.reason();
                }
            }
        }

        Lot lot = new Lot(
                req.lotCode(),
                req.lotType(),
                req.materialType(),
                req.styleCode(),
                req.brandCode(),
                supplier != null ? supplier.getId() : null,
                factory != null ? factory.getId() : null,
                req.quantity(),
                req.massKg(),
                req.pieceCount(),
                req.unit(),
                req.claimedAttribute(),
                req.claimedPct(),
                orphan
        );
        lot = lotRepository.save(lot);

        LotEvent event = appendEvent(
                lot,
                EventType.RECEIVE,
                "receive",
                supplier != null ? supplier.getId() : (factory != null ? factory.getId() : null),
                line != null ? line.getId() : null,
                req.quantity(),
                req.quantity(),
                req.unit(),
                req.waterLiters(),
                req.energyKwh(),
                req.co2eKg(),
                null,
                flagged,
                flagReason,
                req.notes(),
                req.occurredAt()
        );

        eventPublisher.publishEvent(new LotEventRecordedEvent(
                lot.getId(), lot.getLotCode(), event.getId(), event.getEventCode(),
                EventType.RECEIVE, lot.getBrandCode(), flagged, flagReason, orphan));

        return toLotView(lot);
    }

    @Transactional
    public LineageDtos.LotView recordTransform(LineageDtos.TransformLotRequest req) {
        if (req.eventType() == EventType.RECEIVE || req.eventType() == EventType.SPLIT
                || req.eventType() == EventType.MERGE || req.eventType() == EventType.CORRECTION) {
            throw new DomainExceptions.DomainValidationException(
                    "Use dedicated endpoint for " + req.eventType() + " events");
        }
        if (lotRepository.existsByLotCode(req.outputLotCode())) {
            throw new DomainExceptions.DuplicateResourceException("Output lot already exists: " + req.outputLotCode());
        }

        Lot inputLot = requireLotByCode(req.inputLotCode());
        LotState.of(inputLot.getStatus()).assertCanTransform(req.eventType());

        Party factory = (req.factoryCode() != null && !req.factoryCode().isBlank())
                ? partyService.requirePartyByCode(req.factoryCode())
                : (inputLot.getFactoryId() != null ? partyService.requirePartyById(inputLot.getFactoryId()) : null);
        ProductionLine line = (req.productionLineCode() != null && !req.productionLineCode().isBlank())
                ? partyService.requireLineByCode(req.productionLineCode())
                : null;

        String processStep = (req.processStep() != null && !req.processStep().isBlank())
                ? req.processStep().toLowerCase()
                : req.eventType().name().toLowerCase();

        String effectiveClaimAttr = req.claimedAttribute() != null ? req.claimedAttribute() : inputLot.getClaimedAttribute();
        BigDecimal effectiveClaimPct = req.claimedPct() != null ? req.claimedPct() : inputLot.getClaimedPct();

        MassBalanceCheckPort.MassBalanceDecision decision = evaluateMassBalance(
                processStep,
                List.of(inputLot),
                req.inputQuantity(),
                req.outputQuantity(),
                req.unit(),
                effectiveClaimAttr,
                effectiveClaimPct,
                req.outputMassKg());

        Lot outputLot = new Lot(
                req.outputLotCode(),
                req.outputLotType(),
                inputLot.getMaterialType(),
                req.styleCode() != null ? req.styleCode() : inputLot.getStyleCode(),
                inputLot.getBrandCode(),
                inputLot.getSupplierId(),
                factory != null ? factory.getId() : null,
                req.outputQuantity(),
                req.outputMassKg(),
                req.outputPieceCount(),
                req.unit(),
                effectiveClaimAttr,
                effectiveClaimPct,
                false
        );
        outputLot = lotRepository.save(outputLot);

        LotEvent event = appendEvent(
                outputLot,
                req.eventType(),
                processStep,
                factory != null ? factory.getId() : null,
                line != null ? line.getId() : null,
                req.inputQuantity(),
                req.outputQuantity(),
                req.unit(),
                req.waterLiters(),
                req.energyKwh(),
                req.co2eKg(),
                null,
                decision.flagged(),
                decision.reason(),
                req.notes(),
                req.occurredAt()
        );

        lotEdgeRepository.save(new LotEdge(
                event.getId(),
                inputLot.getId(),
                outputLot.getId(),
                req.inputQuantity(),
                req.unit(),
                req.unit() == LotUnit.KG ? req.inputQuantity() : inputLot.getMassKg(),
                req.outputPieceCount() > 0 ? req.outputPieceCount() : inputLot.getPieceCount()
        ));

        eventPublisher.publishEvent(new LotEventRecordedEvent(
                outputLot.getId(), outputLot.getLotCode(), event.getId(), event.getEventCode(),
                req.eventType(), outputLot.getBrandCode(), decision.flagged(), decision.reason(), false));

        return toLotView(outputLot);
    }

    @Transactional
    public List<LineageDtos.LotView> recordSplit(LineageDtos.SplitLotRequest req) {
        Lot parentLot = requireLotByCode(req.parentLotCode());
        LotState.of(parentLot.getStatus()).assertCanTransform(EventType.SPLIT);

        Party factory = (req.factoryCode() != null && !req.factoryCode().isBlank())
                ? partyService.requirePartyByCode(req.factoryCode())
                : null;
        ProductionLine line = (req.productionLineCode() != null && !req.productionLineCode().isBlank())
                ? partyService.requireLineByCode(req.productionLineCode())
                : null;

        String processStep = (req.processStep() != null && !req.processStep().isBlank())
                ? req.processStep().toLowerCase()
                : "split";

        BigDecimal totalOutputQty = req.outputs().stream()
                .map(LineageDtos.SplitOutputItem::outputQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalOutputMass = req.outputs().stream()
                .map(LineageDtos.SplitOutputItem::outputMassKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        MassBalanceCheckPort.MassBalanceDecision decision = evaluateMassBalance(
                processStep,
                List.of(parentLot),
                req.inputQuantity(),
                totalOutputQty,
                parentLot.getUnit(),
                parentLot.getClaimedAttribute(),
                parentLot.getClaimedPct(),
                totalOutputMass);

        LotEvent splitEvent = appendEvent(
                parentLot,
                EventType.SPLIT,
                processStep,
                factory != null ? factory.getId() : parentLot.getFactoryId(),
                line != null ? line.getId() : null,
                req.inputQuantity(),
                totalOutputQty,
                parentLot.getUnit(),
                req.waterLiters(),
                req.energyKwh(),
                req.co2eKg(),
                null,
                decision.flagged(),
                decision.reason(),
                req.notes(),
                req.occurredAt()
        );

        List<LineageDtos.LotView> results = new ArrayList<>(req.outputs().size());
        for (LineageDtos.SplitOutputItem item : req.outputs()) {
            if (lotRepository.existsByLotCode(item.outputLotCode())) {
                throw new DomainExceptions.DuplicateResourceException("Lot already exists: " + item.outputLotCode());
            }
            String attr = item.claimedAttribute() != null ? item.claimedAttribute() : parentLot.getClaimedAttribute();
            BigDecimal pct = item.claimedPct() != null ? item.claimedPct() : parentLot.getClaimedPct();

            Lot child = lotRepository.save(new Lot(
                    item.outputLotCode(),
                    item.outputLotType(),
                    parentLot.getMaterialType(),
                    item.styleCode() != null ? item.styleCode() : parentLot.getStyleCode(),
                    parentLot.getBrandCode(),
                    parentLot.getSupplierId(),
                    factory != null ? factory.getId() : parentLot.getFactoryId(),
                    item.outputQuantity(),
                    item.outputMassKg(),
                    item.outputPieceCount(),
                    item.unit(),
                    attr,
                    pct,
                    false
            ));

            LotEvent childEvent = appendEvent(
                    child,
                    EventType.RECEIVE,
                    "split",
                    factory != null ? factory.getId() : parentLot.getFactoryId(),
                    line != null ? line.getId() : null,
                    item.outputQuantity(),
                    item.outputQuantity(),
                    item.unit(),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    null,
                    decision.flagged(),
                    decision.reason(),
                    "Created from split of " + parentLot.getLotCode(),
                    req.occurredAt()
            );

            lotEdgeRepository.save(new LotEdge(
                    splitEvent.getId(),
                    parentLot.getId(),
                    child.getId(),
                    item.outputQuantity(),
                    item.unit(),
                    item.outputMassKg(),
                    item.outputPieceCount()
            ));

            eventPublisher.publishEvent(new LotEventRecordedEvent(
                    child.getId(), child.getLotCode(), childEvent.getId(), childEvent.getEventCode(),
                    EventType.SPLIT, child.getBrandCode(), decision.flagged(), decision.reason(), false));

            results.add(toLotView(child));
        }

        return results;
    }

    @Transactional
    public LineageDtos.LotView recordMerge(LineageDtos.MergeLotRequest req) {
        if (lotRepository.existsByLotCode(req.outputLotCode())) {
            throw new DomainExceptions.DuplicateResourceException("Output lot already exists: " + req.outputLotCode());
        }

        List<Lot> parents = new ArrayList<>(req.inputs().size());
        BigDecimal totalInputQty = BigDecimal.ZERO;
        String brandCode = null;
        for (LineageDtos.MergeInputItem item : req.inputs()) {
            Lot p = requireLotByCode(item.parentLotCode());
            LotState.of(p.getStatus()).assertCanTransform(EventType.MERGE);
            if (brandCode == null) {
                brandCode = p.getBrandCode();
            } else if (!brandCode.equals(p.getBrandCode())) {
                throw new DomainExceptions.BrandAccessDeniedException(
                        "Cannot merge lots from different brands: " + brandCode + " and " + p.getBrandCode());
            }
            parents.add(p);
            totalInputQty = totalInputQty.add(item.inputQuantity());
        }

        Party factory = (req.factoryCode() != null && !req.factoryCode().isBlank())
                ? partyService.requirePartyByCode(req.factoryCode())
                : null;
        ProductionLine line = (req.productionLineCode() != null && !req.productionLineCode().isBlank())
                ? partyService.requireLineByCode(req.productionLineCode())
                : null;

        String processStep = (req.processStep() != null && !req.processStep().isBlank())
                ? req.processStep().toLowerCase()
                : "merge";

        MassBalanceCheckPort.MassBalanceDecision decision = evaluateMassBalance(
                processStep,
                parents,
                totalInputQty,
                req.outputQuantity(),
                req.unit(),
                req.claimedAttribute(),
                req.claimedPct(),
                req.outputMassKg());

        Lot outputLot = lotRepository.save(new Lot(
                req.outputLotCode(),
                req.outputLotType(),
                req.materialType(),
                req.styleCode(),
                brandCode,
                parents.getFirst().getSupplierId(),
                factory != null ? factory.getId() : parents.getFirst().getFactoryId(),
                req.outputQuantity(),
                req.outputMassKg(),
                req.outputPieceCount(),
                req.unit(),
                req.claimedAttribute(),
                req.claimedPct(),
                false
        ));

        LotEvent mergeEvent = appendEvent(
                outputLot,
                EventType.MERGE,
                processStep,
                factory != null ? factory.getId() : null,
                line != null ? line.getId() : null,
                totalInputQty,
                req.outputQuantity(),
                req.unit(),
                req.waterLiters(),
                req.energyKwh(),
                req.co2eKg(),
                null,
                decision.flagged(),
                decision.reason(),
                req.notes(),
                req.occurredAt()
        );

        for (int i = 0; i < req.inputs().size(); i++) {
            LineageDtos.MergeInputItem item = req.inputs().get(i);
            Lot parent = parents.get(i);
            lotEdgeRepository.save(new LotEdge(
                    mergeEvent.getId(),
                    parent.getId(),
                    outputLot.getId(),
                    item.inputQuantity(),
                    req.unit(),
                    item.inputMassKg(),
                    item.inputPieceCount()
            ));
        }

        eventPublisher.publishEvent(new LotEventRecordedEvent(
                outputLot.getId(), outputLot.getLotCode(), mergeEvent.getId(), mergeEvent.getEventCode(),
                EventType.MERGE, outputLot.getBrandCode(), decision.flagged(), decision.reason(), false));

        return toLotView(outputLot);
    }

    @Transactional
    public LineageDtos.LotEventView recordCorrection(LineageDtos.CorrectionEventRequest req) {
        Lot lot = requireLotByCode(req.lotCode());
        LotEvent original = lotEventRepository.findByEventCode(req.correctsEventCode())
                .orElseThrow(() -> new DomainExceptions.ResourceNotFoundException(
                        "Original event not found: " + req.correctsEventCode()));
        if (!original.getLotId().equals(lot.getId())) {
            throw new DomainExceptions.DomainValidationException(
                    "Event " + req.correctsEventCode() + " does not belong to lot " + req.lotCode());
        }

        String processStep = (req.processStep() != null && !req.processStep().isBlank())
                ? req.processStep().toLowerCase()
                : "correction";

        boolean flagged = !req.clearFlag() && lot.getStatus() == LotStatus.FLAGGED;
        lot.adjustQuantityAndClaim(
                req.correctedOutputQuantity(),
                req.correctedMassKg(),
                req.correctedPieceCount(),
                req.correctedClaimedAttribute(),
                req.correctedClaimedPct(),
                flagged
        );

        LotEvent correction = appendEvent(
                lot,
                EventType.CORRECTION,
                processStep,
                original.getPartyId(),
                original.getProductionLineId(),
                req.correctedInputQuantity(),
                req.correctedOutputQuantity(),
                req.unit(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                original.getId(),
                flagged,
                flagged ? "Flag retained after correction" : null,
                req.notes(),
                req.occurredAt()
        );

        eventPublisher.publishEvent(new LotEventRecordedEvent(
                lot.getId(), lot.getLotCode(), correction.getId(), correction.getEventCode(),
                EventType.CORRECTION, lot.getBrandCode(), flagged, correction.getFlagReason(), lot.isOrphan()));

        return toEventView(correction);
    }

    public Page<LineageDtos.LotView> listLots(LotStatus status, Pageable pageable) {
        Audience audience = audienceAccessService.resolveAudience();
        if (audience == Audience.BRAND) {
            String brandCode = audienceAccessService.currentBrandScope().orElse("");
            Page<Lot> page = status != null
                    ? lotRepository.findByBrandCodeAndStatus(brandCode, status, pageable)
                    : lotRepository.findByBrandCode(brandCode, pageable);
            return page.map(this::toLotView);
        }
        Page<Lot> page = status != null
                ? lotRepository.findByStatus(status, pageable)
                : lotRepository.findAll(pageable);
        return page.map(this::toLotView);
    }

    public LineageDtos.LotView getLot(String lotCode) {
        Lot lot = requireLotByCode(lotCode);
        audienceAccessService.assertBrandAccess(lot.getBrandCode());
        return toLotView(lot);
    }

    public Page<LineageDtos.LotEventView> listEventsForLot(String lotCode, Pageable pageable) {
        Lot lot = requireLotByCode(lotCode);
        audienceAccessService.assertBrandAccess(lot.getBrandCode());
        return lotEventRepository.findByLotId(lot.getId(), pageable).map(this::toEventView);
    }

    public LineageDtos.TraceResponse traceBackward(String lotCode, int maxDepth) {
        Lot target = requireLotByCode(lotCode);
        audienceAccessService.assertBrandAccess(target.getBrandCode());
        List<LineageQueryRepository.TraceStepRow> rows = lineageQueryRepository.traceBackward(target.getId(), maxDepth);
        return toTraceResponse(lotCode, "BACKWARD", target.getBrandCode(), rows);
    }

    public LineageDtos.TraceResponse traceForward(String lotCode, int maxDepth) {
        Lot source = requireLotByCode(lotCode);
        audienceAccessService.assertBrandAccess(source.getBrandCode());
        List<LineageQueryRepository.TraceStepRow> rows = lineageQueryRepository.traceForward(source.getId(), maxDepth);
        return toTraceResponse(lotCode, "FORWARD", source.getBrandCode(), rows);
    }

    public Lot requireLotByCode(String lotCode) {
        return lotRepository.findByLotCode(lotCode)
                .orElseThrow(() -> new DomainExceptions.ResourceNotFoundException("Lot not found: " + lotCode));
    }

    private MassBalanceCheckPort.MassBalanceDecision evaluateMassBalance(
            String processStep,
            List<Lot> inputLots,
            BigDecimal inputQty,
            BigDecimal outputQty,
            LotUnit unit,
            String claimedAttribute,
            BigDecimal claimedPct,
            BigDecimal outputMassKg) {
        MassBalanceCheckPort port = massBalancePortProvider.getIfAvailable();
        if (port == null) {
            return MassBalanceCheckPort.MassBalanceDecision.ok();
        }
        return port.evaluateTransformation(
                processStep, inputLots, inputQty, outputQty, unit, claimedAttribute, claimedPct, outputMassKg);
    }

    private LotEvent appendEvent(
            Lot lot,
            EventType eventType,
            String processStep,
            Long partyId,
            Long productionLineId,
            BigDecimal inputQty,
            BigDecimal outputQty,
            LotUnit unit,
            BigDecimal waterOverride,
            BigDecimal energyOverride,
            BigDecimal co2eOverride,
            Long correctsEventId,
            boolean flagged,
            String flagReason,
            String notes,
            Instant occurredAt) {

        ThreadTraceProperties.ProcessRuleProperties rule = properties.resolveProcessRule(processStep);
        BigDecimal water = waterOverride != null ? waterOverride : rule.getWaterLitersPerUnit().multiply(outputQty);
        BigDecimal energy = energyOverride != null ? energyOverride : rule.getEnergyKwhPerUnit().multiply(outputQty);
        BigDecimal co2e = co2eOverride != null ? co2eOverride : rule.getCo2eKgPerUnit().multiply(outputQty);

        long nextSeq = lot.getEventSequence() + 1;
        String prevHash = lot.getLatestEventHash();
        String eventCode = "EVT-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        String actor = audienceAccessService.currentUsername();

        LotEvent event = new LotEvent(
                eventCode,
                lot.getId(),
                lot.getLotCode(),
                eventType,
                processStep,
                partyId,
                productionLineId,
                inputQty,
                outputQty,
                unit,
                water,
                energy,
                co2e,
                correctsEventId,
                nextSeq,
                prevHash,
                flagged,
                flagReason,
                notes,
                actor,
                occurredAt
        );
        event = lotEventRepository.save(event);
        lot.applyEventState(eventType, flagged, nextSeq, event.getEventHash());
        lotRepository.save(lot);
        return event;
    }

    private LineageDtos.TraceResponse toTraceResponse(
            String targetLotCode,
            String direction,
            String brandCode,
            List<LineageQueryRepository.TraceStepRow> rows) {
        List<LineageDtos.TraceStepView> steps = rows.stream()
                .map(r -> new LineageDtos.TraceStepView(
                        r.depth(),
                        r.parentLotCode(),
                        r.parentLotType(),
                        r.parentMaterialType(),
                        r.parentStatus(),
                        r.parentSupplierCode(),
                        r.parentSupplierName(),
                        r.parentSupplierCountry(),
                        r.parentSupplierRegion(),
                        r.childLotCode(),
                        r.childLotType(),
                        r.eventCode(),
                        r.eventType(),
                        r.processStep(),
                        r.allocatedQuantity(),
                        r.unit(),
                        r.allocatedMassKg(),
                        r.allocatedPieces(),
                        r.path()
                ))
                .toList();
        return new LineageDtos.TraceResponse(targetLotCode, direction, brandCode, steps.size(), steps);
    }

    public LineageDtos.LotView toLotView(Lot lot) {
        return new LineageDtos.LotView(
                lot.getId(),
                lot.getLotCode(),
                lot.getLotType(),
                lot.getMaterialType(),
                lot.getStyleCode(),
                lot.getBrandCode(),
                lot.getQuantity(),
                lot.getMassKg(),
                lot.getPieceCount(),
                lot.getUnit(),
                lot.getClaimedAttribute(),
                lot.getClaimedPct(),
                lot.getStatus(),
                lot.isOrphan(),
                lot.getEventSequence(),
                lot.getLatestEventHash(),
                lot.getCreatedAt()
        );
    }

    public LineageDtos.LotEventView toEventView(LotEvent ev) {
        return new LineageDtos.LotEventView(
                ev.getId(),
                ev.getEventCode(),
                ev.getLotId(),
                ev.getEventType(),
                ev.getProcessStep(),
                ev.getInputQuantity(),
                ev.getOutputQuantity(),
                ev.getUnit(),
                ev.getWaterLiters(),
                ev.getEnergyKwh(),
                ev.getCo2eKg(),
                ev.getCorrectsEventId(),
                ev.getSequenceNumber(),
                ev.getPreviousHash(),
                ev.getEventHash(),
                ev.isFlagged(),
                ev.getFlagReason(),
                ev.getNotes(),
                ev.getRecordedBy(),
                ev.getOccurredAt(),
                ev.getRecordedAt()
        );
    }
}
