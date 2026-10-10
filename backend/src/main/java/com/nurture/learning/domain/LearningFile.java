package com.nurture.learning.domain;
import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="learning_files")
public class LearningFile extends AggregateRoot {
    public enum Category { MATERIAL, TASK_MATERIAL, TASK_SUBMIT, QUESTION }
    @Column(nullable=false) private UUID ownerId;
    @Column(nullable=false) private UUID studentId;
    @Column(nullable=false) private UUID teacherId;
    private UUID bookingId; private UUID taskId; private UUID questionId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private Category category;
    @Column(nullable=false,length=255) private String storageKey;
    @Column(nullable=false,length=180) private String originalFilename;
    @Column(nullable=false,length=120) private String contentType;
    @Column(nullable=false) private long sizeBytes;
    @Column(nullable=false,length=64) private String sha256;
    @Column(nullable=false) private boolean deleted;
    protected LearningFile() {}
    public LearningFile(UUID owner,UUID student,UUID teacher,UUID booking,UUID task,UUID question,Category category,
            String key,String filename,String type,long size,String hash) {
        ownerId=owner;studentId=student;teacherId=teacher;bookingId=booking;taskId=task;questionId=question;
        this.category=category;storageKey=key;originalFilename=filename;contentType=type;sizeBytes=size;sha256=hash;
    }
    public void remove(){deleted=true;}
    public UUID getOwnerId(){return ownerId;} public UUID getStudentId(){return studentId;}
    public UUID getTeacherId(){return teacherId;} public UUID getBookingId(){return bookingId;}
    public UUID getTaskId(){return taskId;} public UUID getQuestionId(){return questionId;}
    public Category getCategory(){return category;} public String getStorageKey(){return storageKey;}
    public String getOriginalFilename(){return originalFilename;} public String getContentType(){return contentType;}
    public long getSizeBytes(){return sizeBytes;} public String getSha256(){return sha256;} public boolean isDeleted(){return deleted;}
}
