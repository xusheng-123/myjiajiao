import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { API } from '../../core/api/endpoints';

export interface BookingRequest {
  bookingId: string;
  studentId: string;
  subject: string;
  type: string;
  status: string;
  createdAt: string;
}

@Component({
  selector: 'nur-booking-requests-card',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './booking-requests-card.component.html',
  styleUrl: './booking-requests-card.component.css',
})
export class BookingRequestsCardComponent implements OnInit {
  private readonly http = inject(HttpClient);

  readonly bookingRequests = signal<BookingRequest[]>([]);
  readonly loading = signal(false);
  readonly busyId = signal<string | null>(null);
  readonly notice = signal<{ type: 'info' | 'error' | 'success'; text: string } | null>(null);

  ngOnInit(): void {
    this.refreshRequests();
  }

  refreshRequests(): void {
    this.loading.set(true);
    this.http.get<BookingRequest[]>(`${API.teacher.bookingRequests}?page=0&size=20`).subscribe({
      next: (data) => {
        this.bookingRequests.set(data ?? []);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  decide(bookingId: string, decision: 'approve' | 'reject'): void {
    if (this.busyId()) return;
    this.busyId.set(bookingId);
    this.notice.set(null);

    this.http
      .post(API.teacher.decideBooking(bookingId) + `?decision=${decision}`, {})
      .subscribe({
        next: () => {
          this.busyId.set(null);
          this.notice.set({
            type: 'success',
            text: decision === 'approve' ? '已确认该预约，时段已锁定并通知学员。' : '已驳回该预约，档期已恢复释放。',
          });
          this.refreshRequests();
        },
        error: (err) => {
          this.busyId.set(null);
          this.notice.set({
            type: 'error',
            text: err?.error?.detail ?? '审批操作未成功，请稍后重试。',
          });
        },
      });
  }
}
