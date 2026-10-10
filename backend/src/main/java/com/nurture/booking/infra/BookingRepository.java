package com.nurture.booking.infra;

import com.nurture.booking.domain.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

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
