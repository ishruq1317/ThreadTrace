package com.threadtrace.lineage;

/**
 * Visitor Pattern interface for traversing lot lineage composites (FR4, FR6).
 */
public interface LineageVisitor {

    void visitLeaf(LotLeafNode leaf);

    void visitComposite(LotCompositeNode composite);
}
