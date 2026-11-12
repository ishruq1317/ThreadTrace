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
    private final List<LineageNode> parents = new ArrayList<>();

    public LotCompositeNode(Lot lot, List<LotEvent> events, BigDecimal allocatedMassKgFromParent, int allocatedPiecesFromParent) {
        this.lot = lot;
        this.events = List.copyOf(events);
        this.allocatedMassKgFromParent = allocatedMassKgFromParent != null ? allocatedMassKgFromParent : lot.getMassKg();
        this.allocatedPiecesFromParent = allocatedPiecesFromParent > 0 ? allocatedPiecesFromParent : lot.getPieceCount();
    }

    public void addParent(LineageNode parent) {
        this.parents.add(parent);
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
        return Collections.unmodifiableList(parents);
    }

    @Override
    public void accept(LineageVisitor visitor) {
        for (LineageNode parent : parents) {
            parent.accept(visitor);
        }
        visitor.visitComposite(this);
    }
}
