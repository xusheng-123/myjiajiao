package com.nurture.admin.domain;
import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="support_cases")
public class SupportCase extends AggregateRoot {
 public enum Category {SCHEDULE,PAYMENT,LEARNING,ACCOUNT,OTHER}
 public enum Status {OPEN,IN_PROGRESS,WAITING_USER,RESOLVED,CLOSED}
 @Column(nullable=false) private UUID ownerId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private Category category;
 @Column(nullable=false,length=120) private String title;
 @Column(nullable=false,length=4000) private String description;
 private UUID bookingId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status=Status.OPEN;
 @Column(nullable=false) private long revision;
 protected SupportCase(){}
 public SupportCase(UUID owner,Category category,String title,String description,UUID bookingId){this.ownerId=owner;this.category=category;this.title=title;this.description=description;this.bookingId=bookingId;}
 public UUID getOwnerId(){return ownerId;}public Category getCategory(){return category;}public String getTitle(){return title;}
 public String getDescription(){return description;}public UUID getBookingId(){return bookingId;}public Status getStatus(){return status;}public long getRevision(){return revision;}
 public boolean closed(){return status==Status.CLOSED;}
 public void touch(){revision++;}public void change(Status status){this.status=status;touch();}
}
