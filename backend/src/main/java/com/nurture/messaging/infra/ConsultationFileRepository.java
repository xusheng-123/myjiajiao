package com.nurture.messaging.infra;
import com.nurture.messaging.domain.ConsultationFile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface ConsultationFileRepository extends JpaRepository<ConsultationFile,UUID>{long countByConversationId(UUID conversationId);}
