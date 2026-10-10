package com.nurture.learning.domain;
import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name="learning_plans")
public class LearningPlan extends AggregateRoot {
    public enum Status { ACTIVE, ARCHIVED }
    @Column(nullable=false) private UUID studentId;
    @Column(nullable=false,length=32) private String subject;
    @Column(nullable=false,length=120) private String title;
    @Column(nullable=false,length=2000) private String longTermGoal;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private Status status=Status.ACTIVE;
    @Column(nullable=false) private long revision;
    protected LearningPlan() {}
    public LearningPlan(UUID studentId,String subject,String title,String goal) {
        this.studentId=studentId; this.subject=subject; this.title=title; this.longTermGoal=goal;
    }
    public void update(String subject,String title,String goal,Status status) {
        this.subject=subject; this.title=title; this.longTermGoal=goal; this.status=status; advance();
    }
    public void advance() { revision++; }
    public UUID getStudentId() { return studentId; }
    public String getSubject() { return subject; }
    public String getTitle() { return title; }
    public String getLongTermGoal() { return longTermGoal; }
    public Status getStatus() { return status; }
    public long getRevision() { return revision; }
}
