package com.nurture.admin.domain;
import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="support_evidence")
public class SupportEvidence extends AggregateRoot {
 @Column(nullable=false) private UUID caseId;
 @Column(nullable=false) private UUID ownerId;
 @Column(nullable=false,length=512) private String storageKey;
 @Column(nullable=false,length=180) private String filename;
 @Column(nullable=false,length=100) private String contentType;
 @Column(nullable=false) private long sizeBytes;
 @Column(nullable=false,length=64) private String sha256;
 @Column(nullable=false) private boolean deleted;
 protected SupportEvidence(){}
 public SupportEvidence(UUID c,UUID o,String key,String name,String type,long bytes,String hash){caseId=c;ownerId=o;storageKey=key;filename=name;contentType=type;sizeBytes=bytes;sha256=hash;}
 public UUID getCaseId(){return caseId;}public UUID getOwnerId(){return ownerId;}public String getStorageKey(){return storageKey;}
 public String getFilename(){return filename;}public String getContentType(){return contentType;}public long getSizeBytes(){return sizeBytes;}public String getSha256(){return sha256;}
 public boolean isDeleted(){return deleted;}public void remove(){deleted=true;}
}
