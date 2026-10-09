package com.nurture.teacher.domain;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 教师资质档案 — 擅长科目、年级、定价与教学风格标签的基础事实源。
 */
@Entity
@Table(name = "teacher_profiles", indexes = @Index(name = "ux_teacher_account", columnList = "accountId", unique = true))
public class TeacherProfile extends AggregateRoot {

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "teacher_no", nullable = false, length = 24)
    private String teacherNo;

    @Column(name = "school", length = 128)
    private String school;

    @Column(name = "highest_degree", length = 32)
    private String highestDegree;

    @Column(name = "years_of_teaching")
    private int yearsOfTeaching;

    @Column(name = "intro", length = 1024)
    private String intro;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "teacher_style_tags", joinColumns = @JoinColumn(name = "teacherId"))
    @Column(name = "style_tag", length = 32)
    private Set<String> teachingStyleTags = new HashSet<>();

    protected TeacherProfile() {
    }

    public TeacherProfile(UUID accountId, String teacherNo) {
        this.accountId = accountId;
        this.teacherNo = teacherNo;
    }

    public void update(String school, String highestDegree, Integer yearsOfTeaching, String intro, Set<String> styleTags) {
        if (school != null) {
            this.school = school;
        }
        if (highestDegree != null) {
            this.highestDegree = highestDegree;
        }
        if (yearsOfTeaching != null) {
            this.yearsOfTeaching = yearsOfTeaching;
        }
        if (intro != null) {
            this.intro = intro;
        }
        if (styleTags != null) {
            this.teachingStyleTags = new HashSet<>(styleTags);
        }
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getTeacherNo() {
        return teacherNo;
    }

    public String getSchool() {
        return school;
    }

    public String getHighestDegree() {
        return highestDegree;
    }

    public int getYearsOfTeaching() {
        return yearsOfTeaching;
    }

    public String getIntro() {
        return intro;
    }

    public Set<String> getTeachingStyleTags() {
        return teachingStyleTags;
    }
}
