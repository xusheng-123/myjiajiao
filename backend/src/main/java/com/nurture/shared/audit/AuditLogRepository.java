package com.nurture.shared.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByOccurredAtBetween(Instant from, Instant to, Pageable pageable);

    Page<AuditLog> findByCallerId(UUID callerId, Pageable pageable);

    Page<AuditLog> findByActionType(String actionType, Pageable pageable);
}
