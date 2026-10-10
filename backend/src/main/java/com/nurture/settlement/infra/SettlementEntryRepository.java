package com.nurture.settlement.infra;

import com.nurture.settlement.domain.SettlementEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SettlementEntryRepository extends JpaRepository<SettlementEntry, UUID> {

    java.util.Optional<SettlementEntry> findByOrderIdAndEntryKind(UUID orderId, SettlementEntry.EntryKind kind);
    java.util.Optional<SettlementEntry> findByReversesEntryId(UUID entryId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from SettlementEntry e where e.orderId = :orderId and e.entryKind = :kind")
    java.util.Optional<SettlementEntry> lockEarning(@Param("orderId") UUID orderId,
            @Param("kind") SettlementEntry.EntryKind kind);

    Page<SettlementEntry> findByTeacherIdOrderByCreatedAtDesc(UUID teacherId, Pageable pageable);

    List<SettlementEntry> findByTeacherIdAndStatus(UUID teacherId, SettlementEntry.SettlementStatus status);

    /** 汇总用全量净额聚合（DB 端 SUM，走 ix_settlement_teacher(teacherId, status)）；与明细分页互不依赖。 */
    @Query("select coalesce(sum(e.netCents), 0) from SettlementEntry e "
            + "where e.teacherId = :teacherId and e.status = :status")
    long sumNetCentsByTeacherIdAndStatus(@Param("teacherId") UUID teacherId,
                                         @Param("status") SettlementEntry.SettlementStatus status);
}
