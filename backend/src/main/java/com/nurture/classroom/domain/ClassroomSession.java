package com.nurture.classroom.domain;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 课堂会话 — 首版对接外部会议 SaaS（凭证转发），严禁自研 WebRTC 网关。
 */
@Entity
@Table(name = "classroom_sessions", indexes = @Index(name = "ux_session_booking", columnList = "bookingId", unique = true))
public class ClassroomSession extends AggregateRoot {

    public enum SessionStatus { SCHEDULED, LIVE, ENDED }

    /** 考勤状态：由导师结课时标记，为退款/结算争议提供事实依据（PRD 验收：迟到/缺席有明确状态）。 */
    public enum AttendanceStatus { UNKNOWN, PRESENT, LATE, ABSENT }

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "meeting_provider", nullable = false, length = 32)
    private String meetingProvider;

    @Column(name = "meeting_url", nullable = false, length = 512)
    private String meetingUrl;

    /** 入会凭证由服务端按需签发转发，不落明文敏感信息。 */
    @Column(name = "join_credential", length = 256)
    private String joinCredential;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SessionStatus status = SessionStatus.SCHEDULED;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status", nullable = false)
    private AttendanceStatus attendanceStatus = AttendanceStatus.UNKNOWN;

    protected ClassroomSession() {
    }

    public ClassroomSession(UUID bookingId, String meetingProvider, String meetingUrl) {
        this.bookingId = bookingId;
        this.meetingProvider = meetingProvider;
        this.meetingUrl = meetingUrl;
    }

    public void markLive() {
        if (status == SessionStatus.LIVE || status == SessionStatus.ENDED) {
            throw new IllegalStateException("课堂会话状态不允许变更为直播中");
        }
        this.status = SessionStatus.LIVE;
        this.startedAt = Instant.now();
    }

    public void end() {
        if (status != SessionStatus.LIVE && status != SessionStatus.SCHEDULED) {
            throw new IllegalStateException("课堂已结束");
        }
        this.status = SessionStatus.ENDED;
        this.endedAt = Instant.now();
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public String getMeetingProvider() {
        return meetingProvider;
    }

    public String getMeetingUrl() {
        return meetingUrl;
    }

    public String getJoinCredential() {
        return joinCredential;
    }

    public void setJoinCredential(String joinCredential) {
        this.joinCredential = joinCredential;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public AttendanceStatus getAttendanceStatus() {
        return attendanceStatus;
    }

    public void markAttendance(AttendanceStatus attendanceStatus) {
        if (status != SessionStatus.ENDED) {
            throw new IllegalStateException("课程结束后才能标记考勤");
        }
        this.attendanceStatus = attendanceStatus;
    }
}
