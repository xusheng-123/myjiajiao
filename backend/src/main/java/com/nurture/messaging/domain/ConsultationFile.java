package com.nurture.messaging.domain;
import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="consultation_files")
public class ConsultationFile extends AggregateRoot{
 @Column(nullable=false) private UUID conversationId;@Column(nullable=false) private UUID ownerId;
 @Column(nullable=false,length=512) private String storageKey;@Column(nullable=false,length=180) private String filename;
 @Column(nullable=false,length=100) private String contentType;@Column(nullable=false) private long sizeBytes;@Column(nullable=false,length=64) private String sha256;
 protected ConsultationFile(){}public ConsultationFile(UUID c,UUID owner,String key,String name,String type,long bytes,String hash){conversationId=c;ownerId=owner;storageKey=key;filename=name;contentType=type;sizeBytes=bytes;sha256=hash;}
 public UUID getConversationId(){return conversationId;}public String getStorageKey(){return storageKey;}public String getFilename(){return filename;}public long getSizeBytes(){return sizeBytes;}public String getSha256(){return sha256;}
}
