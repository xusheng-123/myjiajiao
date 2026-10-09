package com.nurture.learning.domain;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.util.UUID;

/** 教师课堂记录 — 每节已结课程一条；沉淀知识点 / 表现 / 问题 / 下节建议，进入学习档案。 */
@Entity
@Table(name = "lesson_records", indexes = {
        @Index(name = "ix_lrecord_student", columnList = "studentId"),
        @Index(name = "ix_lrecord_teacher", columnList = "teacherId")
})
public class LessonRecord extends AggregateRoot {

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "subject", length = 60)
    private String subject;

    @Column(name = "knowledge_points", length = 400)
    private String knowledgePoints;

    @Column(name = "performance", length = 400)
    private String performance;

    @Column(name = "problems", length = 400)
    private String problems;

    @Column(name = "next_suggestion", length = 400)
    private String nextSuggestion;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    protected LessonRecord() {
    }

    public LessonRecord(UUID bookingId, UUID teacherId, UUID studentId,
                        String subject, String knowledgePoints, String performance,
                        String problems, String nextSuggestion, String note) {
        this.bookingId = bookingId;
        this.teacherId = teacherId;
        this.studentId = studentId;
        this.subject = subject;
        this.knowledgePoints = knowledgePoints;
        this.performance = performance;
        this.problems = problems;
        this.nextSuggestion = nextSuggestion;
        this.note = note;
    }

    public UUID getBookingId() { return bookingId; }
    public UUID getTeacherId() { return teacherId; }
    public UUID getStudentId() { return studentId; }
    public String getSubject() { return subject; }
    public String getKnowledgePoints() { return knowledgePoints; }
    public String getPerformance() { return performance; }
    public String getProblems() { return problems; }
    public String getNextSuggestion() { return nextSuggestion; }
    public String getNote() { return note; }
}
