package com.nurture.learning.domain;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="learning_storage_quotas")
public class LearningStorageQuota {
    @Id private UUID ownerId;
    @Column(nullable=false) private long storedBytes;
    protected LearningStorageQuota() {}
    public long getStoredBytes(){return storedBytes;}
    public void add(long bytes){storedBytes+=bytes;}
}
