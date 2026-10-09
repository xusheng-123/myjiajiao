package com.nurture.learning.domain;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.util.UUID;

/** 学习档案画像 — 学生维护学习目标 / 当前阶段 / 目标科目；长期记录由课程与任务数据聚合。 */
@Entity
@Table(name = "learning_profiles",
        indexes = @Index(name = "ux_learning_profile_student", columnList = "studentId", unique = true))
public class LearningProfile extends AggregateRoot {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "goal", length = 200)
    private String goal;

    @Column(name = "stage", length = 60)
    private String stage;

    @Column(name = "target_subjects", length = 200)
    private String targetSubjects;

    protected LearningProfile() {
    }

    public LearningProfile(UUID studentId, String goal, String stage, String targetSubjects) {
        this.studentId = studentId;
        this.goal = goal;
        this.stage = stage;
        this.targetSubjects = targetSubjects;
    }

    public void update(String goal, String stage, String targetSubjects) {
        if (goal != null) this.goal = goal;
        if (stage != null) this.stage = stage;
        if (targetSubjects != null) this.targetSubjects = targetSubjects;
    }

    public UUID getStudentId() { return studentId; }
    public String getGoal() { return goal; }
    public String getStage() { return stage; }
    public String getTargetSubjects() { return targetSubjects; }
}
