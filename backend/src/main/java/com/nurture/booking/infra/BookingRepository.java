package com.nurture.booking.infra;

import com.nurture.booking.domain.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    @org.springframework.data.jpa.repository.Query(value="SELECT DISTINCT teacher_id FROM bookings WHERE student_id=:student AND (status IN ('CONFIRMED','IN_PROGRESS','COMPLETED') OR (status='CLOSED' AND cancel_reason IS NULL))",nativeQuery=true)
    List<UUID> learningTeachers(UUID student);
    @org.springframework.data.jpa.repository.Query(value="SELECT b.* FROM bookings b JOIN availability_slots s ON s.id=b.slot_id WHERE b.student_id=:student AND b.status<>'CANCELLED' AND NOT(b.status='CLOSED' AND b.cancel_reason IS NOT NULL) AND s.start_at>=:from AND s.start_at<:to ORDER BY s.start_at,b.id LIMIT 2001",nativeQuery=true)
    List<Booking> calendarBookings(UUID student,java.time.OffsetDateTime from,java.time.OffsetDateTime to);

    Page<Booking> findByStudentIdOrderByCreatedAtDesc(UUID studentId, Pageable pageable);

    Page<Booking> findByTeacherIdAndStatusOrderByCreatedAtDesc(UUID teacherId, Booking.BookingStatus status, Pageable pageable);

    Page<Booking> findByTeacherIdOrderByCreatedAtDesc(UUID teacherId, Pageable pageable);

    /** D12：提醒扫描用——某状态的预约集合（量级有限，全量扫描可接受）。 */
    java.util.List<Booking> findByStatus(Booking.BookingStatus status);
    java.util.List<Booking> findByTeacherId(UUID teacherId);
    java.util.List<Booking> findByTeacherIdAndStudentId(UUID teacherId, UUID studentId);
    boolean existsByTeacherIdAndStudentId(UUID teacherId, UUID studentId);
    @org.springframework.data.jpa.repository.Query(value="select count(*) from bookings where student_id=:student and subject=:subject "
            +"and (status='COMPLETED' or (status='CLOSED' and cancel_reason is null))",nativeQuery=true)
    long completedLessons(@org.springframework.data.repository.query.Param("student") UUID studentId,
            @org.springframework.data.repository.query.Param("subject") String subject);
    @org.springframework.data.jpa.repository.Query(value="select exists(select 1 from bookings where teacher_id=:teacher and student_id=:student "
            +"and (status in ('CONFIRMED','IN_PROGRESS','COMPLETED') or (status='CLOSED' and cancel_reason is null)))",nativeQuery=true)
    boolean hasLearningRelation(@org.springframework.data.repository.query.Param("teacher") UUID teacherId,
            @org.springframework.data.repository.query.Param("student") UUID studentId);
    boolean existsByStudentIdAndTeacherIdAndTypeAndStatusNot(UUID studentId, UUID teacherId,
            Booking.BookingType type, Booking.BookingStatus status);
}
