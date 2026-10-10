package com.nurture.learning.domain;
import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity @Table(name="mistake_notes")
public class MistakeNote extends AggregateRoot {
    public enum Status { UNMASTERED, REVIEWING, MASTERED }
    @Column(nullable=false) private UUID studentId;
    @Column(nullable=false) private UUID creatorId;
    @Column(nullable=false,length=32) private String subject;
    @Column(nullable=false,length=120) private String title;
    @Column(nullable=false,length=4000) private String questionText;
    @Column(nullable=false,length=2000) private String errorReason;
    @Column(nullable=false,length=300) private String knowledgeTags;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private Status status=Status.UNMASTERED;
    private LocalDate reviewDate;
    @Column(nullable=false) private long revision;
    @Column(nullable=false) private boolean deleted;
    private UUID taskId;
    protected MistakeNote() {}
    public MistakeNote(UUID student,UUID creator,String subject,String title,String question,String reason,String tags,LocalDate review) {
        this.studentId=student;this.creatorId=creator;updateText(subject,title,question,reason,tags,review);
    }
    private void updateText(String subject,String title,String question,String reason,String tags,LocalDate review) {
        this.subject=subject.trim();this.title=title.trim();this.questionText=question.trim();this.errorReason=reason.trim();
        this.knowledgeTags=tags.trim();this.reviewDate=review;
    }
    public void update(String subject,String title,String question,String reason,String tags,LocalDate review,Status status) {
        updateText(subject,title,question,reason,tags,review);this.status=status;revision++;
    }
    public void link(UUID taskId) {this.taskId=taskId;revision++;}
    public void remove() {deleted=true;revision++;}
    public UUID getStudentId(){return studentId;} public UUID getCreatorId(){return creatorId;}
    public String getSubject(){return subject;} public String getTitle(){return title;}
    public String getQuestionText(){return questionText;} public String getErrorReason(){return errorReason;}
    public String getKnowledgeTags(){return knowledgeTags;} public Status getStatus(){return status;}
    public LocalDate getReviewDate(){return reviewDate;} public long getRevision(){return revision;}
    public boolean isDeleted(){return deleted;} public UUID getTaskId(){return taskId;}
}
