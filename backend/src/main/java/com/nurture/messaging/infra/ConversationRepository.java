package com.nurture.messaging.infra;

import com.nurture.messaging.domain.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Conversation c where c.id=:id") java.util.Optional<Conversation> locked(UUID id);


    List<Conversation> findByStudentIdOrderByLastMessageAtDesc(UUID studentId);

    List<Conversation> findByTeacherIdOrderByLastMessageAtDesc(UUID teacherId);
}
