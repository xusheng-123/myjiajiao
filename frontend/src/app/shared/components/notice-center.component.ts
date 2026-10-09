import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { API } from '../../core/api/endpoints';

interface NoticeView {
  noticeId: string;
  type: string;
  title: string;
  body: string | null;
  createdAt: string;
  read: boolean;
}

/**
 * 通知中心 — 集中、分级、可静默（PRD 6.1）：折叠收纳，不抢主任务注意力；
 * 无红点海洋、无循环动画。
 */
@Component({
  selector: 'nur-notice-center',
  standalone: true,
  imports: [DatePipe],
  template: `
    <section class="notice-card nur-card" aria-labelledby="notice-title">
      <button
        type="button"
        class="notice-toggle"
        (click)="toggle()"
        [attr.aria-expanded]="open()"
        aria-controls="notice-list"
      >
        <span class="notice-icon" aria-hidden="true">🔔</span>
        <span id="notice-title">通知</span>
        @if (unreadCount() > 0) {
          <span class="notice-count">{{ unreadCount() }} 条未读</span>
        }
        <span class="notice-caret" aria-hidden="true">{{ open() ? '▴' : '▾' }}</span>
      </button>
      @if (open()) {
        <div id="notice-list" class="notice-list">
          @if (loading()) {
            <p class="notice-empty">正在获取通知…</p>
          } @else if (notices().length === 0) {
            <p class="notice-empty">暂无通知。有预约、支付或审核动态时会在这里安静地出现。</p>
          } @else {
            @for (n of notices(); track n.noticeId) {
              <article class="notice-item" [class.notice-item--unread]="!n.read">
                <p class="notice-item-title">
                  <span class="notice-type" aria-hidden="true">{{ typeIcon(n.type) }}</span>
                  {{ n.title }}
                </p>
                @if (n.body) {
                  <p class="notice-item-body">{{ n.body }}</p>
                }
                <p class="notice-item-time">{{ n.createdAt | date: 'M月d日 HH:mm' }}</p>
              </article>
            }
          }
        </div>
      }
    </section>
  `,
  styles: [
    `
      .notice-card {
        padding: 0;
        overflow: hidden;
      }

      .notice-toggle {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        width: 100%;
        min-height: var(--touch-target);
        padding: var(--space-3) var(--space-5);
        border: none;
        background: transparent;
        color: var(--text-main);
        font-size: 0.95rem;
      }

      .notice-count {
        color: var(--color-secondary);
        font-size: 0.85rem;
      }

      .notice-caret {
        margin-left: auto;
        color: var(--text-muted);
      }

      .notice-list {
        max-height: 320px;
        overflow-y: auto;
        border-top: 1px solid var(--border-subtle);
        padding: var(--space-3) var(--space-5);
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }

      .notice-empty {
        margin: 0;
        padding: var(--space-3) 0;
        color: var(--text-muted);
        font-size: 0.9rem;
      }

      .notice-item {
        padding: var(--space-3) var(--space-4);
        border-radius: var(--radius-control);
        background: var(--bg-page);
      }

      .notice-item--unread {
        background: var(--color-primary-bg);
      }

      .notice-item-title {
        margin: 0;
        font-weight: 600;
        font-size: 0.92rem;
      }

      .notice-type {
        margin-right: var(--space-1);
      }

      .notice-item-body {
        margin: var(--space-1) 0 0;
        color: var(--text-muted);
        font-size: 0.88rem;
        line-height: 1.7;
      }

      .notice-item-time {
        margin: var(--space-1) 0 0;
        color: var(--text-placeholder);
        font-size: 0.78rem;
      }
    `,
  ],
})
export class NoticeCenterComponent {
  private readonly http = inject(HttpClient);

  readonly open = signal(false);
  readonly loading = signal(false);
  readonly notices = signal<NoticeView[]>([]);
  readonly unreadCount = signal(0);

  toggle(): void {
    this.open.update((v) => !v);
    if (this.open() && this.notices().length === 0) {
      this.load();
    }
  }

  load(): void {
    this.loading.set(true);
    this.http
      .get<{ total: number; items: NoticeView[] }>(`${API.messaging.notifications}?page=0&size=10`)
      .subscribe({
        next: (data) => {
          this.notices.set(data.items ?? []);
          this.unreadCount.set((data.items ?? []).filter((n) => !n.read).length);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  typeIcon(type: string): string {
    switch (type) {
      case 'PAYMENT':
        return '💰';
      case 'SETTLEMENT':
        return '📊';
      case 'REFUND':
        return '↩️';
      case 'VERIFICATION':
        return '🪪';
      case 'BOOKING':
        return '📅';
      default:
        return '📌';
    }
  }
}
