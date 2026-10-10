package com.nurture.booking.domain;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** Free trial allowance for a participant pair; locked for every claim/release/consumption. */
@Entity
@Table(name = "trial_entitlements")
public class TrialEntitlement extends AggregateRoot {
    @Column(name = "student_id", nullable = false)
    private UUID studentId;
    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;
    @Column(nullable = false)
    private boolean consumed;
    @Column(name = "current_booking_id")
    private UUID currentBookingId;

    protected TrialEntitlement() { }
    public boolean isConsumed() { return consumed; }
    public UUID getCurrentBookingId() { return currentBookingId; }
    public void claim(UUID bookingId) { currentBookingId = bookingId; }
    public void consume() { consumed = true; }
    public void release(UUID bookingId) {
        if (bookingId.equals(currentBookingId)) currentBookingId = null;
    }
}
