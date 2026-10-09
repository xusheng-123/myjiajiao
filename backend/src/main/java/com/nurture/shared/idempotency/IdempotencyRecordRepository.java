package com.nurture.shared.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByCallerIdAndHttpMethodAndRequestUriAndIdempotencyKey(
            UUID callerId, String httpMethod, String requestUri, String idempotencyKey);
}
