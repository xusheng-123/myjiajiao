package com.nurture.learning.infra;
import com.nurture.learning.domain.MistakeNote;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface MistakeNoteRepository extends JpaRepository<MistakeNote,UUID>,JpaSpecificationExecutor<MistakeNote> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select n from MistakeNote n where n.id=:id") Optional<MistakeNote> locked(UUID id);
}
