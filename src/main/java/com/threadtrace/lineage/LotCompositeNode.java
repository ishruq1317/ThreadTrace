package com.threadtrace.lineage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LotCompositeNode implements LineageNode {

    private final Lot lot;
    private final List<LotEvent> events;
    private final BigDecimal allocatedMassKgFromParent;
    private final int allocatedPiecesFromParent;
    private final List<ParentEdge> parentEdges = new ArrayList<>();

    public LotCompositeNode(Lot lot, List<LotEvent> events, BigDecimal allocatedMassKgFromParent, int allocatedPiecesFromParent) {
        this.lot = lot;
        this.events = List.copyOf(events);
        this.allocatedMassKgFromParent = allocatedMassKgFromParent != null ? allocatedMassKgFromParent : lot.getMassKg();
        this.allocatedPiecesFromParent = allocatedPiecesFromParent > 0 ? allocatedPiecesFromParent : lot.getPieceCount();
    }

    public void addParent(LineageNode parent) {
        this.parentEdges.add(new ParentEdge(
                parent,
                parent.getAllocatedMassKgFromParent(),
                parent.getAllocatedPiecesFromParent()));
    }

    public void addParent(LineageNode parent, BigDecimal allocatedMassKg, int allocatedPieces) {
        this.parentEdges.add(new ParentEdge(parent, allocatedMassKg, allocatedPieces));
    }

    @Override
    public Lot getLot() {
        return lot;
    }

    @Override
    public List<LotEvent> getEvents() {
        return events;
    }

    @Override
    public BigDecimal getAllocatedMassKgFromParent() {
        return allocatedMassKgFromParent;
    }

    @Override
    public int getAllocatedPiecesFromParent() {
        return allocatedPiecesFromParent;
    }

    @Override
    public List<LineageNode> getParents() {
        return parentEdges.stream().map(ParentEdge::parent).toList();
    }

    public List<ParentEdge> getParentEdges() {
        return Collections.unmodifiableList(parentEdges);
    }

    @Override
    public void accept(LineageVisitor visitor) {
        for (ParentEdge edge : parentEdges) {
            edge.parent().accept(visitor);
        }
        visitor.visitComposite(this);
    }

    public record ParentEdge(LineageNode parent, BigDecimal allocatedMassKg, int allocatedPieces) {
    }
}
