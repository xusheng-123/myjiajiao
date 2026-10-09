package com.nurture.review.domain;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 课后双向教学评价 — 学生打分与教师结构化学情反馈归档。
 */
@Entity
@Table(name = "reviews", indexes = {
        @Index(name = "ux_review_booking_direction", columnList = "bookingId, direction", unique = true),
        @Index(name = "ix_review_reviewee", columnList = "revieweeId")
})
public class Review extends AggregateRoot {

    public enum ReviewDirection { STUDENT_TO_TEACHER, TEACHER_TO_STUDENT }

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "reviewer_id", nullable = false)
    private UUID reviewerId;

    @Column(name = "reviewee_id", nullable = false)
    private UUID revieweeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 24)
    private ReviewDirection direction;

    @Column(name = "rating", nullable = false)
    private int rating;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "review_tags", joinColumns = @JoinColumn(name = "reviewId"))
    @Column(name = "tag", length = 32)
    private Set<String> tags = new HashSet<>();

    @Column(name = "comment", length = 1024)
    private String comment;

    protected Review() {
    }

    public Review(UUID bookingId, UUID reviewerId, UUID revieweeId, ReviewDirection direction,
                  int rating, Set<String> tags, String comment) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("评分取值 1~5");
        }
        this.bookingId = bookingId;
        this.reviewerId = reviewerId;
        this.revieweeId = revieweeId;
        this.direction = direction;
        this.rating = rating;
        this.tags = tags == null ? new HashSet<>() : new HashSet<>(tags);
        this.comment = comment;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getReviewerId() {
        return reviewerId;
    }

    public UUID getRevieweeId() {
        return revieweeId;
    }

    public ReviewDirection getDirection() {
        return direction;
    }

    public int getRating() {
        return rating;
    }

    public Set<String> getTags() {
        return tags;
    }

    public String getComment() {
        return comment;
    }
}
