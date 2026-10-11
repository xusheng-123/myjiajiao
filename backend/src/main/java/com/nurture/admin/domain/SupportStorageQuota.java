package com.nurture.admin.domain;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="support_storage_quotas")
public class SupportStorageQuota {
 @Id private UUID ownerId;@Column(nullable=false) private long storedBytes;
 protected SupportStorageQuota(){}public long getStoredBytes(){return storedBytes;}public void add(long bytes){storedBytes+=bytes;}
}
