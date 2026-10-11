package com.nurture.admin.infra;
import com.nurture.admin.domain.SupportReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.UUID;
public interface SupportReplyRepository extends JpaRepository<SupportReply,UUID>{Page<SupportReply> findByCaseId(UUID caseId,Pageable page);}
