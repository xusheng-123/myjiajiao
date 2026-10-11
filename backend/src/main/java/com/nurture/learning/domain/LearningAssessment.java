package com.nurture.learning.domain;
import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="learning_assessments")
public class LearningAssessment extends AggregateRoot{
 public enum Status{DRAFT,PUBLISHED,ARCHIVED}
 @Column(nullable=false) private UUID bookingId;@Column(nullable=false) private UUID teacherId;@Column(nullable=false) private UUID studentId;
 @Column(nullable=false,length=60) private String subject;@Column(nullable=false,length=120) private String title;
 @Column(nullable=false,length=1000) private String scope;@Column(nullable=false,columnDefinition="text") private String questionsJson;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private Status status=Status.DRAFT;
 @Column(nullable=false) private long revision;
 protected LearningAssessment(){}
 public LearningAssessment(UUID b,UUID t,UUID s,String subject,String title,String scope,String bank){bookingId=b;teacherId=t;studentId=s;this.subject=subject;this.title=title;this.scope=scope;questionsJson=bank;}
 public UUID getBookingId(){return bookingId;}public UUID getTeacherId(){return teacherId;}public UUID getStudentId(){return studentId;}
 public String getSubject(){return subject;}public String getTitle(){return title;}public String getScope(){return scope;}public String getQuestionsJson(){return questionsJson;}
 public Status getStatus(){return status;}public long getRevision(){return revision;}
 public void edit(String title,String scope,String bank){this.title=title;this.scope=scope;questionsJson=bank;revision++;}
 public void publish(){status=Status.PUBLISHED;revision++;}public void archive(){status=Status.ARCHIVED;revision++;}
}
