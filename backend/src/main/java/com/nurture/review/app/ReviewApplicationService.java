package com.nurture.review.app;

import com.nurture.booking.app.BookingApplicationService;
import com.nurture.review.domain.Review;
import com.nurture.review.infra.ReviewRepository;
import com.nurture.shared.security.CallerContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 评价反馈应用服务 — 课后双向教学评价与结构化学情反馈归档。
 */
@Service
public class ReviewApplicationService {

    private final ReviewRepository reviewRepository;
    private final BookingApplicationService bookingService;

    public ReviewApplicationService(ReviewRepository reviewRepository, BookingApplicationService bookingService) {
        this.reviewRepository = reviewRepository;
        this.bookingService = bookingService;
    }

    /** 双向评价：学生评价导师，或导师提交结构化学情反馈。每方向每课仅一次。 */
    @Transactional
    public Review submit(UUID bookingId, int rating, Set<String> tags, String comment) {
        var caller = CallerContext.require();
        var roles = bookingService.rolesOf(bookingId);
        if (!"COMPLETED".equals(roles.status()) && !"CLOSED".equals(roles.status())) {
            throw new IllegalStateException("课程完成后才能提交评价");
        }

        Review.ReviewDirection direction;
        UUID reviewee;
        if (roles.studentId().equals(caller.accountId())) {
            direction = Review.ReviewDirection.STUDENT_TO_TEACHER;
            reviewee = roles.teacherId();
        } else if (roles.teacherId().equals(caller.accountId())) {
            direction = Review.ReviewDirection.TEACHER_TO_STUDENT;
            reviewee = roles.studentId();
        } else {
            throw new AccessDeniedException("仅本节课的师生双方可提交评价");
        }
        if (reviewRepository.existsByBookingIdAndDirection(bookingId, direction)) {
            throw new IllegalStateException("该课程此方向的评价已提交，请勿重复评价");
        }
        return reviewRepository.save(new Review(bookingId, caller.accountId(), reviewee, direction,
                rating, tags, comment));
    }

    /** 评价统计（供 matching 透明推荐理由使用）。 */
    public record ReviewStats(double averageRating, long reviewCount) {
    }

    @Transactional(readOnly = true)
    public ReviewStats statsOf(UUID revieweeId) {
        List<Review> reviews = reviewRepository.findByRevieweeIdOrderByCreatedAtDesc(revieweeId);
        double avg = reviews.stream().mapToInt(Review::getRating).average().orElse(0);
        return new ReviewStats(avg, reviews.size());
    }

    @Transactional(readOnly = true)
    public List<Review> reviewsOf(UUID revieweeId) {
        List<Review> reviews = reviewRepository.findByRevieweeIdOrderByCreatedAtDesc(revieweeId);
        reviews.forEach(r -> r.getTags().size()); // open-in-view=false：tags 须在会话内初始化，否则序列化时 LazyInitializationException
        return reviews;
    }

    /** 我发出的评价：学生课程表据此把已评价课程标记为「✓ 已评价」。 */
    @Transactional(readOnly = true)
    public List<Review> reviewsGivenBy(UUID reviewerId) {
        List<Review> reviews = reviewRepository.findByReviewerIdOrderByCreatedAtDesc(reviewerId);
        reviews.forEach(r -> r.getTags().size()); // 同上：会话内初始化 tags
        return reviews;
    }
}
