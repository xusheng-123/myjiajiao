package com.nurture.refund.infra;

import com.nurture.refund.domain.RefundCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RefundCaseRepository extends JpaRepository<RefundCase, UUID> {

    Page<RefundCase> findByStatus(com.nurture.refund.domain.RefundCase.RefundStatus status, Pageable pageable);

    Page<RefundCase> findByRequestedByOrderByCreatedAtDesc(UUID requestedBy, Pageable pageable);
}
