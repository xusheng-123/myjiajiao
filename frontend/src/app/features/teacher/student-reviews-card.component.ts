import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API } from '../../core/api/endpoints';

export interface ReceivedReview {
  reviewId: string;
  bookingId: string;
  direction: string;
  rating: number;
  tags: string[];
  comment: string;
}

@Component({
  selector: 'nur-student-reviews-card',
  standalone: true,
  imports: [],
  templateUrl: './student-reviews-card.component.html',
  styleUrl: './student-reviews-card.component.css',
})
export class StudentReviewsCardComponent implements OnInit {
  private readonly http = inject(HttpClient);

  readonly reviews = signal<ReceivedReview[]>([]);
  readonly loading = signal(false);

  readonly averageRating = computed(() => {
    const list = this.reviews();
    if (list.length === 0) return null;
    const sum = list.reduce((acc, r) => acc + (r.rating || 5), 0);
    return (sum / list.length).toFixed(1);
  });

  ngOnInit(): void {
    this.loadReviews();
  }

  loadReviews(): void {
    this.loading.set(true);
    this.http.get<ReceivedReview[]>(API.review.received).subscribe({
      next: (data) => {
        this.reviews.set(data ?? []);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  starsArray(rating: number): number[] {
    return Array.from({ length: Math.min(5, Math.max(1, rating)) }, (_, i) => i + 1);
  }
}
