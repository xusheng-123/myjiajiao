package com.nurture.learning.infra;
import com.nurture.learning.domain.LearningStorageQuota;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface LearningStorageQuotaRepository extends JpaRepository<LearningStorageQuota,UUID> {
    @Modifying @Query(value="INSERT INTO learning_storage_quotas(owner_id,stored_bytes) VALUES (:owner,0) ON CONFLICT(owner_id) DO NOTHING",nativeQuery=true)
    void ensure(UUID owner);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select q from LearningStorageQuota q where q.ownerId=:owner") Optional<LearningStorageQuota> locked(UUID owner);
}
