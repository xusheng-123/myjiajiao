package com.nurture.messaging.infra;

import com.nurture.messaging.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByConversationIdOrderBySentAtDescIdDesc(UUID conversationId, org.springframework.data.domain.Pageable pageable);

    long countByConversationIdAndSenderIdNotAndReadAtIsNull(UUID conversationId, UUID senderId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update Message m set m.readAt = :at where m.conversationId = :conversationId and m.senderId <> :readerId and m.readAt is null")
    int markRead(UUID conversationId, UUID readerId, java.time.Instant at);
}
