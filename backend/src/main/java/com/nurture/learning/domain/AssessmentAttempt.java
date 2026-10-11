package com.nurture.learning.domain;
import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="assessment_attempts")
public class AssessmentAttempt extends AggregateRoot{
 @Column(nullable=false,unique=true) private UUID assessmentId;@Column(nullable=false) private UUID studentId;
 @Column(nullable=false,columnDefinition="text") private String answersJson;@Column(nullable=false) private int correctCount;@Column(nullable=false) private int totalCount;
 @Column(length=4000) private String teacherComment;@Column(nullable=false) private long commentRevision;
 protected AssessmentAttempt(){}
 public AssessmentAttempt(UUID quiz,UUID student,String answers,int correct,int total){assessmentId=quiz;studentId=student;answersJson=answers;correctCount=correct;totalCount=total;}
 public UUID getAssessmentId(){return assessmentId;}public String getAnswersJson(){return answersJson;}public int getCorrectCount(){return correctCount;}public int getTotalCount(){return totalCount;}public String getTeacherComment(){return teacherComment;}public long getCommentRevision(){return commentRevision;}
 public void comment(String text){teacherComment=text;commentRevision++;}
}
