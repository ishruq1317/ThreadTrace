package com.threadtrace.lineage;

import java.math.BigDecimal;
import java.util.List;

/**
 * Composite Pattern component representing a node in the garment lot lineage DAG (FR3, FR4, FR6).
 */
public interface LineageNode {

    Lot getLot();

    List<LotEvent> getEvents();

    BigDecimal getAllocatedMassKgFromParent();

    int getAllocatedPiecesFromParent();

    List<LineageNode> getParents();

    void accept(LineageVisitor visitor);
}
