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
}
