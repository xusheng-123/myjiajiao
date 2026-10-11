package com.nurture.admin.infra;
import com.nurture.admin.domain.SupportStorageQuota;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface SupportStorageQuotaRepository extends JpaRepository<SupportStorageQuota,UUID>{
 @Modifying @Query(value="INSERT INTO support_storage_quotas(owner_id,stored_bytes) VALUES (:owner,0) ON CONFLICT(owner_id) DO NOTHING",nativeQuery=true) void ensure(UUID owner);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select q from SupportStorageQuota q where q.ownerId=:owner") Optional<SupportStorageQuota> locked(UUID owner);
}
