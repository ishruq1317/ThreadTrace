package com.threadtrace.lineage;

import java.math.BigDecimal;
import java.util.List;

public class LotLeafNode implements LineageNode {

    private final Lot lot;
    private final List<LotEvent> events;
    private final BigDecimal allocatedMassKgFromParent;
    private final int allocatedPiecesFromParent;

    public LotLeafNode(Lot lot, List<LotEvent> events, BigDecimal allocatedMassKgFromParent, int allocatedPiecesFromParent) {
        this.lot = lot;
        this.events = List.copyOf(events);
        this.allocatedMassKgFromParent = allocatedMassKgFromParent != null ? allocatedMassKgFromParent : lot.getMassKg();
        this.allocatedPiecesFromParent = allocatedPiecesFromParent > 0 ? allocatedPiecesFromParent : lot.getPieceCount();
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
        return List.of();
    }

    @Override
    public void accept(LineageVisitor visitor) {
        visitor.visitLeaf(this);
    }
}
