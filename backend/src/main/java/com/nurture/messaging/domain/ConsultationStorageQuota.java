package com.nurture.messaging.domain;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="consultation_storage_quotas")
public class ConsultationStorageQuota{
 @Id private UUID ownerId;@Column(nullable=false) private long storedBytes;
 protected ConsultationStorageQuota(){}public long getStoredBytes(){return storedBytes;}public void add(long bytes){storedBytes+=bytes;}
}
