package com.nurture.admin.infra;
import com.nurture.admin.domain.SupportEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SupportEvidenceRepository extends JpaRepository<SupportEvidence,UUID>{
 @org.springframework.data.jpa.repository.Query("select f.caseId from SupportEvidence f where f.id=:id") Optional<UUID> caseIdOf(UUID id);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select f from SupportEvidence f where f.id=:id") Optional<SupportEvidence> locked(UUID id);
 List<SupportEvidence> findByCaseIdAndDeletedFalseOrderByCreatedAtAscIdAsc(UUID caseId);
 long countByCaseId(UUID caseId);
}
