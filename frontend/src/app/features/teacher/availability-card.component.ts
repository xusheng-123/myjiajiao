import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { API } from '../../core/api/endpoints';

export interface AvailabilitySlot {
  slotId: string;
  startAt: string;
  endAt: string;
  status: string;
}

@Component({
  selector: 'nur-availability-card',
  standalone: true,
  imports: [DatePipe, RouterLink],
  templateUrl: './availability-card.component.html',
  styleUrl: './availability-card.component.css',
})
export class AvailabilityCardComponent implements OnInit {
  private readonly http = inject(HttpClient);

  readonly slots = signal<AvailabilitySlot[]>([]);
  readonly loading = signal(false);
  readonly publishing = signal(false);
  readonly notice = signal<{ type: 'info' | 'error' | 'success'; text: string } | null>(null);

  ngOnInit(): void {
    this.refreshSlots();
  }

  refreshSlots(): void {
    this.loading.set(true);
    const from = new Date();
    const to = new Date(Date.now() + 14 * 24 * 3600 * 1000);
    this.http
      .get<AvailabilitySlot[]>(`${API.teacher.availabilityCalendar}?from=${from.toISOString()}&to=${to.toISOString()}`)
      .subscribe({
        next: (data) => {
          this.slots.set(data ?? []);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  publishSlot(dayOffset: number, hour: number): void {
    if (this.publishing()) return;
    this.publishing.set(true);
    this.notice.set(null);

    const start = new Date();
    start.setDate(start.getDate() + dayOffset);
    start.setHours(hour, 0, 0, 0);
    const end = new Date(start.getTime() + 3600 * 1000);

    this.http
      .post(API.teacher.availabilityPublish, { startAt: start.toISOString(), endAt: end.toISOString() })
      .subscribe({
        next: () => {
          this.publishing.set(false);
          this.notice.set({ type: 'success', text: '可用时段已发布，学员可即刻检索与预约。' });
          this.refreshSlots();
        },
        error: () => {
          this.publishing.set(false);
          this.notice.set({ type: 'error', text: '发布未成功：时段可能与现有档期重叠，请调整时间。' });
        },
      });
  }
}
