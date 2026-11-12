package com.threadtrace.lineage;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LotEventRepository extends JpaRepository<LotEvent, Long> {
    Optional<LotEvent> findByEventCode(String eventCode);
    List<LotEvent> findByLotIdOrderBySequenceNumberAsc(Long lotId);
    List<LotEvent> findByLotIdInOrderByLotIdAscSequenceNumberAsc(Collection<Long> lotIds);
    Page<LotEvent> findByLotId(Long lotId, Pageable pageable);
}
