package com.nurture.shared.twophase;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConfirmationGrantRepository extends JpaRepository<ConfirmationGrant, UUID> {

    Optional<ConfirmationGrant> findByTokenHash(String tokenHash);
}
