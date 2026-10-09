package com.nurture.settlement.infra;

import com.nurture.settlement.domain.SettlementEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SettlementEntryRepository extends JpaRepository<SettlementEntry, UUID> {

    Page<SettlementEntry> findByTeacherIdOrderByCreatedAtDesc(UUID teacherId, Pageable pageable);

    List<SettlementEntry> findByTeacherIdAndStatus(UUID teacherId, SettlementEntry.SettlementStatus status);
}
