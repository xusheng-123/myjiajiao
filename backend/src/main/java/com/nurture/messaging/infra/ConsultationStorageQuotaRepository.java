package com.nurture.messaging.infra;
import com.nurture.messaging.domain.ConsultationStorageQuota;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface ConsultationStorageQuotaRepository extends JpaRepository<ConsultationStorageQuota,UUID>{
 @Modifying @Query(value="INSERT INTO consultation_storage_quotas(owner_id,stored_bytes) VALUES (:owner,0) ON CONFLICT(owner_id) DO NOTHING",nativeQuery=true) void ensure(UUID owner);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select q from ConsultationStorageQuota q where q.ownerId=:owner") Optional<ConsultationStorageQuota> locked(UUID owner);
}
