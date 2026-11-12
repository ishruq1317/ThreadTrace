package com.threadtrace.lineage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LotEdgeRepository extends JpaRepository<LotEdge, Long> {
    List<LotEdge> findByChildLotId(Long childLotId);
    List<LotEdge> findByParentLotId(Long parentLotId);
}
