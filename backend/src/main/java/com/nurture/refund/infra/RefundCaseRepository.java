package com.nurture.refund.infra;

import com.nurture.refund.domain.RefundCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RefundCaseRepository extends JpaRepository<RefundCase, UUID> {

    boolean existsByOrderId(UUID orderId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from RefundCase c where c.id = :id")
    java.util.Optional<RefundCase> lockById(@org.springframework.data.repository.query.Param("id") UUID id);

    Page<RefundCase> findByStatus(com.nurture.refund.domain.RefundCase.RefundStatus status, Pageable pageable);

    Page<RefundCase> findByRequestedByOrderByCreatedAtDesc(UUID requestedBy, Pageable pageable);
}
