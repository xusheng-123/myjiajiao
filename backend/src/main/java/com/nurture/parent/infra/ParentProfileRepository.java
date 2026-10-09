package com.nurture.parent.infra;

import com.nurture.parent.domain.ParentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ParentProfileRepository extends JpaRepository<ParentProfile, UUID> {

    Optional<ParentProfile> findByAccountId(UUID accountId);
}
