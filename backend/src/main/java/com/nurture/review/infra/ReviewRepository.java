package com.nurture.review.infra;

import com.nurture.review.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    List<Review> findByRevieweeIdOrderByCreatedAtDesc(UUID revieweeId);

    List<Review> findByReviewerIdOrderByCreatedAtDesc(UUID reviewerId);

    boolean existsByBookingIdAndDirection(UUID bookingId, Review.ReviewDirection direction);
}
