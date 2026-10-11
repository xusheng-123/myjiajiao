package com.nurture.admin.domain;
import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="support_replies")
public class SupportReply extends AggregateRoot {
 @Column(nullable=false) private UUID caseId;
 @Column(nullable=false) private UUID authorId;
 @Column(nullable=false,length=16) private String authorRole;
 @Column(nullable=false,length=4000) private String body;
 @Column(nullable=false,length=16) private String kind;
 protected SupportReply(){}
 public SupportReply(UUID caseId,UUID author,String role,String body,String kind){this.caseId=caseId;authorId=author;authorRole=role;this.body=body;this.kind=kind;}
 public UUID getAuthorId(){return authorId;}public String getAuthorRole(){return authorRole;}public String getBody(){return body;}public String getKind(){return kind;}
}
