package com.nurture.student.domain;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 学生独立档案 — 学习偏好与学情记录，与家长账户通过监护关联解耦绑定。
 */
@Entity
@Table(name = "student_profiles", indexes = @jakarta.persistence.Index(name = "ux_student_account", columnList = "accountId", unique = true))
public class StudentProfile extends AggregateRoot {

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "student_no", nullable = false, length = 24)
    private String studentNo;

    @Column(name = "grade_level", length = 32)
    private String gradeLevel;

    @Column(name = "learning_style", length = 256)
    private String learningStyle;

    @Column(name = "focus_preference", length = 256)
    private String focusPreference;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "student_preferred_subjects", joinColumns = @JoinColumn(name = "studentId"))
    @Column(name = "subject", length = 32)
    private Set<String> preferredSubjects = new HashSet<>();

    protected StudentProfile() {
    }

    public StudentProfile(UUID accountId, String studentNo) {
        this.accountId = accountId;
        this.studentNo = studentNo;
    }

    public void updatePreference(String gradeLevel, String learningStyle, String focusPreference, Set<String> preferredSubjects) {
        if (gradeLevel != null) {
            this.gradeLevel = gradeLevel;
        }
        if (learningStyle != null) {
            this.learningStyle = learningStyle;
        }
        if (focusPreference != null) {
            this.focusPreference = focusPreference;
        }
        if (preferredSubjects != null) {
            this.preferredSubjects = new HashSet<>(preferredSubjects);
        }
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getStudentNo() {
        return studentNo;
    }

    public String getGradeLevel() {
        return gradeLevel;
    }

    public String getLearningStyle() {
        return learningStyle;
    }

    public String getFocusPreference() {
        return focusPreference;
    }

    public Set<String> getPreferredSubjects() {
        return preferredSubjects;
    }
}
