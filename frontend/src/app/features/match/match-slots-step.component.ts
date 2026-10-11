import { Component, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TeacherCandidate } from './match-recommend-step.component';

export interface OpenSlot {
  slotId: string;
  teacherId: string;
  startAt: string;
  endAt: string;
  status: string;
}

/**
 * 步骤三：选择可约档期与试听名额核查（方向 D · 黛绿暖金）
 * ADHD 友好：清晰的时间块点击感、试听资格即时反馈、冲突提示即时恢复
 */
@Component({
  selector: 'nur-match-slots-step',
  standalone: true,
  imports: [DatePipe, RouterLink],
  template: `
    <section class="slots-step nur-card" aria-labelledby="step3-title">
      <header class="step-head">
        <div class="teacher-summary-banner">
          <div class="banner-avatar nur-avatar" aria-hidden="true">
            {{ teacher().displayName.slice(0, 1) }}
          </div>
          <div class="banner-info">
            <h2 id="step3-title" class="step-title nur-serif">
              第三步：为 {{ teacher().displayName }} 老师选择上课时间
            </h2>
            <p class="banner-meta">
              科目：<strong>{{ teacher().subject }}</strong> · 
              类型：<span class="type-pill">{{ bookingType() === 'TRIAL' ? '免费试听' : '正式课' }}</span> · 
              课时参考：¥{{ (teacher().pricePerHourCents / 100).toFixed(0) }}/小时
            </p>
          </div>
        </div>
      </header>

      <!-- 试听资格状态提示 -->
      @if (bookingType() === 'TRIAL') {
        <div
          class="trial-banner"
          [class.trial-banner--ok]="trialAvailable() === true"
          [class.trial-banner--warn]="trialAvailable() === false"
          role="status"
        >
          <span class="trial-icon" aria-hidden="true">
            {{ trialAvailable() === true ? '✓' : trialAvailable() === false ? 'ℹ' : '◔' }}
          </span>
          <div class="trial-text">
            <strong>免费试听名额：</strong>
            <span>{{ trialReason() }}</span>
            @if (trialAvailable() === false) {
              <div class="trial-actions">
                <button
                  type="button"
                  class="nur-btn nur-btn--primary nur-btn--sm"
                  (click)="switchType.emit('FORMAL')"
                >
                  改约正式课 / 续课
                </button>
                <a routerLink="/student" class="nur-btn nur-btn--ghost nur-btn--sm">前往课程表</a>
              </div>
            }
          </div>
        </div>
      }

      <!-- 冲突失效提示 -->
      @if (slotInvalid()) {
        <div class="conflict-alert" role="alert">
          <span aria-hidden="true">⚠</span> {{ slotInvalid() }}
        </div>
      }

      <!-- 可选档期列表 -->
      <div class="slots-container">
        <h3 class="slots-heading nur-serif">
          请选择一个可约时段（共 {{ slots().length }} 个时段可用）：
        </h3>

        @if (slots().length === 0) {
          <div class="empty-slots">
            <p>该老师近期暂未发布可约时段。你可以先返回重新挑选老师，或与老师发起课前咨询。</p>
          </div>
        } @else {
          <div class="slots-grid" role="radiogroup" aria-label="可约时间段选择">
            @for (s of slots(); track s.slotId) {
              <button
                type="button"
                class="slot-card-btn"
                [class.slot-card-btn--selected]="selectedSlot()?.slotId === s.slotId"
                [attr.aria-checked]="selectedSlot()?.slotId === s.slotId"
                (click)="pickSlot.emit(s)"
              >
                <span class="slot-date">{{ s.startAt | date: 'M月d日 EEEE' }}</span>
                <span class="slot-hours">
                  {{ s.startAt | date: 'HH:mm' }} – {{ s.endAt | date: 'HH:mm' }}
                </span>
                <span class="slot-select-status" aria-hidden="true">
                  {{ selectedSlot()?.slotId === s.slotId ? '✓ 已选择' : '点击选择' }}
                </span>
              </button>
            }
          </div>
        }
      </div>

      <!-- 底部动作栏 -->
      <footer class="step-actions">
        <button type="button" class="nur-btn nur-btn--ghost" (click)="backToRecommend.emit()">
          ← 返回候选导师
        </button>

        <button
          type="button"
          class="nur-btn nur-btn--primary"
          [disabled]="!selectedSlot() || (bookingType() === 'TRIAL' && trialAvailable() !== true)"
          (click)="toConfirmStep.emit()"
        >
          下一步：确认预约信息 →
        </button>
      </footer>
    </section>
  `,
  styles: [`
    .slots-step {
      padding: var(--space-6);
      background: var(--bg-card);
      border: 1px solid var(--line);
      border-radius: var(--radius-card);
      box-shadow: var(--shadow-subtle);
    }

    .teacher-summary-banner {
      display: flex;
      align-items: center;
      gap: var(--space-4);
      padding: var(--space-4);
      background: var(--bg-page);
      border-radius: var(--radius-control);
      border: 1px solid var(--line);
    }

    .banner-avatar {
      width: 56px;
      height: 56px;
      font-size: 1.4rem;
      border: 2px solid var(--color-gold);
      flex-shrink: 0;
    }

    .step-title {
      margin: 0;
      font-size: 1.35rem;
      color: var(--text-title);
    }

    .banner-meta {
      margin: var(--space-1) 0 0;
      font-size: 0.92rem;
      color: var(--text-muted);
    }

    .type-pill {
      font-weight: 600;
      color: var(--color-primary);
    }

    /* 试听与冲突提示 */
    .trial-banner {
      display: flex;
      align-items: flex-start;
      gap: var(--space-3);
      margin-top: var(--space-4);
      padding: var(--space-4);
      border-radius: var(--radius-control);
      background: var(--bg-page);
      border: 1px solid var(--line);
      font-size: 0.92rem;
    }

    .trial-banner--ok {
      background: var(--color-success-bg);
      border-color: rgba(53, 112, 90, 0.25);
      color: var(--color-success);
    }

    .trial-banner--warn {
      background: var(--color-gold-bg);
      border-color: rgba(168, 135, 90, 0.3);
      color: var(--color-gold-text);
    }

    .trial-icon {
      font-size: 1.1rem;
      font-weight: 600;
    }

    .trial-actions {
      display: flex;
      gap: var(--space-2);
      margin-top: var(--space-2);
    }

    .conflict-alert {
      margin-top: var(--space-4);
      padding: var(--space-3) var(--space-4);
      background: var(--color-danger-bg);
      border: 1px solid rgba(176, 82, 76, 0.25);
      color: var(--color-danger);
      border-radius: var(--radius-control);
      font-size: 0.92rem;
    }

    /* 档期网格 */
    .slots-container {
      margin-top: var(--space-5);
    }

    .slots-heading {
      margin: 0 0 var(--space-3);
      font-size: 1.1rem;
      color: var(--text-title);
    }

    .slots-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
      gap: var(--space-3);
    }

    .slot-card-btn {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      padding: var(--space-4);
      border: 1.5px solid var(--line);
      border-radius: var(--radius-control);
      background: var(--bg-card);
      color: var(--text-main);
      cursor: pointer;
      text-align: left;
      transition: all var(--duration-enter) var(--ease);
    }

    .slot-card-btn:hover {
      border-color: var(--color-primary);
      box-shadow: var(--shadow-subtle);
    }

    .slot-card-btn--selected {
      border-color: var(--color-primary);
      background: var(--color-primary-soft);
      box-shadow: 0 0 0 2px var(--color-primary);
    }

    .slot-date {
      font-size: 0.88rem;
      color: var(--text-muted);
    }

    .slot-hours {
      font-size: 1.1rem;
      font-weight: 600;
      color: var(--text-title);
      margin: var(--space-1) 0;
    }

    .slot-select-status {
      font-size: 0.78rem;
      color: var(--color-primary);
      margin-top: auto;
    }

    .empty-slots {
      padding: var(--space-6);
      text-align: center;
      background: var(--bg-page);
      border: 1px dashed var(--line);
      border-radius: var(--radius-control);
      color: var(--text-muted);
    }

    .step-actions {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-4);
      margin-top: var(--space-6);
      padding-top: var(--space-5);
      border-top: 1px solid var(--line);
    }

    @media (max-width: 767.98px) {
      .teacher-summary-banner {
        flex-direction: column;
        align-items: flex-start;
      }
      .slots-grid {
        grid-template-columns: 1fr;
      }
      .step-actions {
        flex-direction: column-reverse;
        align-items: stretch;
      }
      .step-actions .nur-btn {
        width: 100%;
      }
    }
  `],
})
export class MatchSlotsStepComponent {
  readonly teacher = input.required<TeacherCandidate>();
  readonly bookingType = input.required<'TRIAL' | 'FORMAL'>();
  readonly slots = input.required<OpenSlot[]>();
  readonly selectedSlot = input<OpenSlot | null>(null);
  readonly trialAvailable = input<boolean | null>(null);
  readonly trialReason = input<string>('');
  readonly slotInvalid = input<string | null>(null);

  readonly pickSlot = output<OpenSlot>();
  readonly switchType = output<'TRIAL' | 'FORMAL'>();
  readonly toConfirmStep = output<void>();
  readonly backToRecommend = output<void>();
}
