package com.nurture.admin.infra;
import com.nurture.admin.domain.SupportCase;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface SupportCaseRepository extends JpaRepository<SupportCase,UUID>,JpaSpecificationExecutor<SupportCase>{
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from SupportCase c where c.id=:id") Optional<SupportCase> locked(UUID id);
}
