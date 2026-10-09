package com.nurture.review.api;

import com.nurture.review.app.ReviewApplicationService;
import com.nurture.review.domain.Review;
import com.nurture.shared.security.CallerContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 评价反馈 API — 双向教学评价。
 */
@RestController
@RequestMapping("/api/v1/review")
@Tag(name = "review / 评价反馈", description = "课后双向教学评价、结构化学情反馈")
@SecurityRequirement(name = "bearerJWT")
public class ReviewController {

    private final ReviewApplicationService reviewService;

    public ReviewController(ReviewApplicationService reviewService) {
        this.reviewService = reviewService;
    }

    public record SubmitReviewRequest(UUID bookingId, int rating, Set<String> tags, String comment) {
    }

    public record ReviewView(UUID reviewId, UUID bookingId, String direction, int rating,
                             Set<String> tags, String comment) {
        static ReviewView from(Review r) {
            return new ReviewView(r.getId(), r.getBookingId(), r.getDirection().name(), r.getRating(),
                    r.getTags(), r.getComment());
        }
    }

    @PostMapping("/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "提交课后评价", description = "学生打分教师 / 教师结构化学情反馈，双向各限一次")
    public ReviewView submit(@RequestBody SubmitReviewRequest request) {
        Review review = reviewService.submit(request.bookingId(), request.rating(),
                request.tags(), request.comment());
        return ReviewView.from(review);
    }

    @GetMapping("/reviewees/{revieweeId}/reviews")
    @Operation(summary = "查询某用户收到的评价")
    public List<ReviewView> of(@PathVariable UUID revieweeId) {
        return reviewService.reviewsOf(revieweeId).stream().map(ReviewView::from).toList();
    }

    @GetMapping("/me/reviews-received")
    @Operation(summary = "我收到的评价")
    public List<ReviewView> mine() {
        return reviewService.reviewsOf(CallerContext.require().accountId()).stream()
                .map(ReviewView::from).toList();
    }

    @GetMapping("/me/reviews-given")
    @Operation(summary = "我发出的评价", description = "学生课程表据此将已评价课程标记为「✓ 已评价」")
    public List<ReviewView> given() {
        return reviewService.reviewsGivenBy(CallerContext.require().accountId()).stream()
                .map(ReviewView::from).toList();
    }
}
