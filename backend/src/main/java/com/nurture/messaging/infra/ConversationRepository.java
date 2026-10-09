package com.nurture.messaging.infra;

import com.nurture.messaging.domain.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findByStudentIdOrderByLastMessageAtDesc(UUID studentId);

    List<Conversation> findByTeacherIdOrderByLastMessageAtDesc(UUID teacherId);
}
