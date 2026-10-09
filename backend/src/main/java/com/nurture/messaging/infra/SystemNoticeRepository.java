package com.nurture.messaging.infra;

import com.nurture.messaging.domain.SystemNotice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SystemNoticeRepository extends JpaRepository<SystemNotice, UUID> {

    Page<SystemNotice> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);
}
