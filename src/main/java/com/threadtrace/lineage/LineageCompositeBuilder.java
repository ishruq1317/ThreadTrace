package com.threadtrace.lineage;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the LineageNode Composite DAG for a target lot using a single recursive trace query
 * plus batched Lot and LotEvent loads (avoiding N+1 queries).
 */
@Component
public class LineageCompositeBuilder {

    private final LineageQueryRepository lineageQueryRepository;
    private final LotRepository lotRepository;
    private final LotEventRepository lotEventRepository;

    public LineageCompositeBuilder(
            LineageQueryRepository lineageQueryRepository,
            LotRepository lotRepository,
            LotEventRepository lotEventRepository) {
        this.lineageQueryRepository = lineageQueryRepository;
        this.lotRepository = lotRepository;
        this.lotEventRepository = lotEventRepository;
    }

    public LineageResult buildBackwardTree(Lot targetLot) {
        List<LineageQueryRepository.TraceStepRow> edges = lineageQueryRepository.traceBackward(targetLot.getId(), 20);
        Set<Long> allLotIds = new HashSet<>();
        allLotIds.add(targetLot.getId());
        for (LineageQueryRepository.TraceStepRow edge : edges) {
            allLotIds.add(edge.parentLotId());
            allLotIds.add(edge.childLotId());
        }

        Map<Long, Lot> lotsById = lotRepository.findByIdIn(allLotIds).stream()
                .collect(Collectors.toMap(Lot::getId, Function.identity()));
        Map<Long, List<LotEvent>> eventsByLotId = lotEventRepository
                .findByLotIdInOrderByLotIdAscSequenceNumberAsc(allLotIds).stream()
                .collect(Collectors.groupingBy(LotEvent::getLotId));

        Map<Long, List<LineageQueryRepository.TraceStepRow>> parentsByChild = new LinkedHashMap<>();
        for (LineageQueryRepository.TraceStepRow edge : edges) {
            parentsByChild.computeIfAbsent(edge.childLotId(), k -> new ArrayList<>()).add(edge);
        }

        Map<Long, LineageNode> memo = new HashMap<>();
        LineageNode root = buildNode(
                targetLot.getId(),
                targetLot.getMassKg(),
                targetLot.getPieceCount(),
                lotsById,
                eventsByLotId,
                parentsByChild,
                memo,
                new HashSet<>());

        return new LineageResult(root, new ArrayList<>(lotsById.values()), edges);
    }

    private LineageNode buildNode(
            Long lotId,
            BigDecimal allocatedMass,
            int allocatedPieces,
            Map<Long, Lot> lotsById,
            Map<Long, List<LotEvent>> eventsByLotId,
            Map<Long, List<LineageQueryRepository.TraceStepRow>> parentsByChild,
            Map<Long, LineageNode> memo,
            Set<Long> visiting) {
        if (memo.containsKey(lotId)) {
            return memo.get(lotId);
        }
        Lot lot = lotsById.get(lotId);
        List<LotEvent> events = eventsByLotId.getOrDefault(lotId, List.of());
        List<LineageQueryRepository.TraceStepRow> parentEdges = parentsByChild.getOrDefault(lotId, List.of());

        if (parentEdges.isEmpty() || !visiting.add(lotId)) {
            LotLeafNode leaf = new LotLeafNode(lot, events, allocatedMass, allocatedPieces);
            memo.put(lotId, leaf);
            return leaf;
        }

        LotCompositeNode composite = new LotCompositeNode(lot, events, allocatedMass, allocatedPieces);
        Set<Long> addedParents = new HashSet<>();
        for (LineageQueryRepository.TraceStepRow edge : parentEdges) {
            if (addedParents.add(edge.parentLotId())) {
                composite.addParent(buildNode(
                        edge.parentLotId(),
                        edge.allocatedMassKg(),
                        edge.allocatedPieces(),
                        lotsById,
                        eventsByLotId,
                        parentsByChild,
                        memo,
                        visiting));
            }
        }
        visiting.remove(lotId);
        memo.put(lotId, composite);
        return composite;
    }

    public record LineageResult(
            LineageNode root,
            List<Lot> allLotsInLineage,
            List<LineageQueryRepository.TraceStepRow> traceSteps
    ) {
    }
}
