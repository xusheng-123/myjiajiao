package com.nurture.matching.domain;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.util.UUID;

/** 学生收藏教师 — 用于对比与再次选择。 */
@Entity
@Table(name = "favorite_teachers",
        indexes = {
                @Index(name = "ux_favorite", columnList = "studentId, teacherId", unique = true),
                @Index(name = "ix_favorite_student", columnList = "studentId, createdAt")
        })
public class FavoriteTeacher extends AggregateRoot {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    protected FavoriteTeacher() {
    }

    public FavoriteTeacher(UUID studentId, UUID teacherId) {
        this.studentId = studentId;
        this.teacherId = teacherId;
    }

    public UUID getStudentId() { return studentId; }
    public UUID getTeacherId() { return teacherId; }
}
