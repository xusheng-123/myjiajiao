import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { API, API_BASE } from '../../core/api/endpoints';
import { AuthService } from '../../core/auth/auth.service';
import { Role } from '../../core/auth/auth.models';
import { SiteHeaderComponent } from '../../layout/site-header/site-header.component';
import { SiteFooterComponent } from '../../layout/site-footer/site-footer.component';

interface Detail {
  teacherId: string;
  displayName: string;
  school: string | null;
  highestDegree: string | null;
  yearsOfTeaching: number;
  intro: string | null;
  styleTags: string[];
  averageRating: number | null;
  reviewCount: number;
  offerings: { subject: string; gradeBand: string; pricePerHourCents: number }[];
  openSlots: { slotId: string; startAt: string; endAt: string }[];
}

interface ReviewItem {
  reviewId: string;
  bookingId: string;
  direction: string;
  rating: number;
  tags?: string[];
  comment?: string | null;
  createdAt?: string;
}

/**
 * 导师公开详情页（方向 D · 黛绿暖金）
 * - 卡片化信息结构（头像、认证、评分、在售课程、可约档期）
 * - C1 缺口补齐：学员真实评价列表（分页展示）
 * - 全站导航 SiteHeader / SiteFooter
 */
@Component({
  selector: 'nur-teacher-detail',
  standalone: true,
  imports: [RouterLink, DatePipe, SiteHeaderComponent, SiteFooterComponent],
  templateUrl: './teacher-detail.component.html',
  styleUrl: './teacher-detail.component.css',
})
export class TeacherDetailComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly auth = inject(AuthService);
  readonly Role = Role;

  readonly detail = signal<Detail | null>(null);
  readonly loading = signal(true);
  readonly notice = signal('');
  readonly busy = signal(false);

  // ---------- C1 学员评价列表状态 ----------
  readonly reviews = signal<ReviewItem[]>([]);
  readonly reviewsLoading = signal(false);
  readonly reviewPage = signal(0);
  readonly reviewPageSize = 5;

  readonly filteredReviews = computed(() => {
    // 优先展示学生评价教师的条目
    const all = this.reviews();
    const studentReviews = all.filter((r) => r.direction === 'STUDENT_TO_TEACHER');
    return studentReviews.length > 0 ? studentReviews : all;
  });

  readonly totalReviewPages = computed(() =>
    Math.ceil(this.filteredReviews().length / this.reviewPageSize) || 1,
  );

  readonly pagedReviews = computed(() => {
    const start = this.reviewPage() * this.reviewPageSize;
    return this.filteredReviews().slice(start, start + this.reviewPageSize);
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    const teacherId = this.route.snapshot.paramMap.get('teacherId');
    if (!teacherId) return;

    this.loading.set(true);
    this.notice.set('');
    this.http.get<Detail>(`${API_BASE}/matching/public/teachers/${teacherId}`).subscribe({
      next: (t) => {
        this.detail.set(t);
        this.loading.set(false);
        this.loadReviews(teacherId);
      },
      error: (e) => {
        this.loading.set(false);
        this.notice.set(e.error?.detail ?? '导师资料读取失败，请返回列表重试。');
      },
    });
  }

  loadReviews(teacherId: string): void {
    this.reviewsLoading.set(true);
    this.http.get<ReviewItem[]>(API.review.publicList(teacherId)).subscribe({
      next: (list) => {
        this.reviews.set(Array.isArray(list) ? list : []);
        this.reviewsLoading.set(false);
      },
      error: () => {
        this.reviews.set([]);
        this.reviewsLoading.set(false);
      },
    });
  }

  nextReviewPage(): void {
    if (this.reviewPage() + 1 < this.totalReviewPages()) {
      this.reviewPage.update((p) => p + 1);
    }
  }

  prevReviewPage(): void {
    if (this.reviewPage() > 0) {
      this.reviewPage.update((p) => p - 1);
    }
  }

  consult(): void {
    const t = this.detail();
    if (!t || this.busy()) return;
    this.busy.set(true);
    this.http
      .post<{ conversationId: string }>(API.messaging.conversations, {
        teacherId: t.teacherId,
        subject: t.offerings[0]?.subject ?? '课程咨询',
      })
      .subscribe({
        next: (c) => {
          this.busy.set(false);
          void this.router.navigate(['/messages'], { queryParams: { conversation: c.conversationId } });
        },
        error: (e) => {
          this.busy.set(false);
          this.notice.set(e.error?.detail ?? '咨询开启失败，请重试。');
        },
      });
  }

  yuan(cents: number): string {
    return (cents / 100).toFixed(2);
  }
}
