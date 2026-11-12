package com.threadtrace.parties;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PartyRepository extends JpaRepository<Party, Long> {
    Optional<Party> findByCode(String code);
    boolean existsByCode(String code);
    Page<Party> findByPartyType(PartyType partyType, Pageable pageable);
}
