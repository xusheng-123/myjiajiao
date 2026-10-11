import { Component, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TeacherCandidate } from './match-recommend-step.component';
import { OpenSlot } from './match-slots-step.component';

/**
 * 步骤四：预约信息确认与提交闭环（方向 D · 黛绿暖金）
 * ADHD 友好：完整呈现前文选择、双通道成功反馈、明确后续动作
 */
@Component({
  selector: 'nur-match-confirm-step',
  standalone: true,
  imports: [DatePipe, RouterLink],
  template: `
    <section class="confirm-step nur-card" aria-labelledby="step4-title">
      @if (wizardSuccess()) {
        <!-- 成功状态卡片 -->
        <div class="success-card" role="status">
          <div class="success-icon" aria-hidden="true">✓</div>
          <h2 class="success-title nur-serif">预约申请已成功提交</h2>
          <p class="success-notice">{{ wizardNotice() }}</p>

          <div class="success-summary">
            <p><strong>授课导师：</strong>{{ teacher().displayName }}</p>
            <p><strong>预约科目：</strong>{{ teacher().subject }}</p>
            <p>
              <strong>上课时段：</strong>
              {{ selectedSlot()?.startAt | date: 'M月d日 EEEE HH:mm' }} – {{ selectedSlot()?.endAt | date: 'HH:mm' }}
            </p>
            <p>
              <strong>预约类型：</strong>
              {{ bookingType() === 'TRIAL' ? '免费试听课' : '正式课程' }}
            </p>
          </div>

          <div class="success-actions">
            <a routerLink="/student" class="nur-btn nur-btn--primary">
              前往我的课程表
            </a>
            <button type="button" class="nur-btn nur-btn--ghost" (click)="finish.emit()">
              再约一门课程
            </button>
          </div>
        </div>
      } @else {
        <!-- 确认信息表单 -->
        <header class="step-head">
          <h2 id="step4-title" class="step-title nur-serif">第四步：核对并确认预约信息</h2>
          <p class="step-lead">
            请最后核对上课时段与课程信息；确认提交后将为你锁定该时段。
          </p>
        </header>

        <dl class="confirm-grid">
          <div class="confirm-row">
            <dt class="confirm-label">授课导师</dt>
            <dd class="confirm-val">
              <strong>{{ teacher().displayName }}</strong>
              <span class="confirm-sub">（{{ teacher().school || '高等院校' }} · {{ teacher().highestDegree || '学士' }}）</span>
            </dd>
          </div>

          <div class="confirm-row">
            <dt class="confirm-label">辅导科目</dt>
            <dd class="confirm-val">{{ teacher().subject }} · {{ teacher().gradeBand ?? '学段不限' }}</dd>
          </div>

          <div class="confirm-row">
            <dt class="confirm-label">预约类型</dt>
            <dd class="confirm-val">
              <span class="type-tag" [class.type-tag--trial]="bookingType() === 'TRIAL'">
                {{ bookingType() === 'TRIAL' ? '免费试听课' : '正式课（需教师确认）' }}
              </span>
            </dd>
          </div>

          <div class="confirm-row">
            <dt class="confirm-label">上课时间</dt>
            <dd class="confirm-val confirm-val--time">
              {{ selectedSlot()?.startAt | date: 'yyyy年M月d日 EEEE HH:mm' }} – {{ selectedSlot()?.endAt | date: 'HH:mm' }}
            </dd>
          </div>

          <div class="confirm-row">
            <dt class="confirm-label">课时费用</dt>
            <dd class="confirm-val">
              @if (bookingType() === 'TRIAL') {
                <span class="price-free">¥0（免费试听，限一次）</span>
              } @else {
                <span class="price-ref">
                  ¥{{ (teacher().pricePerHourCents / 100).toFixed(0) }} / 课时
                  <small class="confirm-sub">（本次提交不扣款，教师确认后由家长在课程表结算付款）</small>
                </span>
              }
            </dd>
          </div>
        </dl>

        <div class="policy-note">
          <p class="policy-title">规则与保障须知：</p>
          <p class="policy-text">
            @if (bookingType() === 'TRIAL') {
              同一学生对同一导师可享受 1 次免费试听，提交后自动确认；开课前支持自主取消重约，开始上课或计划开课时间已过则视为已消费。
            } @else {
              正式课申请提交后由导师在 24 小时内核对并确认；确认后再生成待支付订单，支持随时申请全额退款。
            }
          </p>
        </div>

        <footer class="step-actions">
          <button type="button" class="nur-btn nur-btn--ghost" [disabled]="submitting()" (click)="backToSlots.emit()">
            ← 上一步：重新选择时段
          </button>

          <button
            type="button"
            class="nur-btn nur-btn--primary"
            [disabled]="submitting()"
            (click)="submitBooking.emit()"
          >
            {{ submitting() ? '正在提交中…' : (bookingType() === 'TRIAL' ? '确认预约免费试听' : '确认提交正式课申请') }}
          </button>
        </footer>
      }
    </section>
  `,
  styles: [`
    .confirm-step {
      padding: var(--space-6);
      background: var(--bg-card);
      border: 1px solid var(--line);
      border-radius: var(--radius-card);
      box-shadow: var(--shadow-subtle);
    }

    .step-head {
      margin-bottom: var(--space-5);
    }

    .step-title {
      margin: 0;
      font-size: 1.45rem;
      font-weight: 600;
      color: var(--text-title);
    }

    .step-lead {
      margin: var(--space-1) 0 0;
      color: var(--text-muted);
      font-size: 0.92rem;
    }

    .confirm-grid {
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
      margin: 0;
      padding: var(--space-4);
      background: var(--bg-page);
      border-radius: var(--radius-control);
      border: 1px solid var(--line);
    }

    .confirm-row {
      display: flex;
      align-items: baseline;
      padding: var(--space-2) 0;
      border-bottom: 1px solid var(--line);
    }

    .confirm-row:last-child {
      border-bottom: none;
    }

    .confirm-label {
      width: 110px;
      flex-shrink: 0;
      font-size: 0.9rem;
      color: var(--text-muted);
      font-weight: 500;
    }

    .confirm-val {
      margin: 0;
      font-size: 0.98rem;
      color: var(--text-title);
    }

    .confirm-val--time {
      color: var(--color-primary);
      font-weight: 600;
    }

    .confirm-sub {
      font-size: 0.85rem;
      color: var(--text-muted);
      font-weight: normal;
    }

    .type-tag {
      display: inline-block;
      padding: 2px 10px;
      border-radius: var(--radius-pill);
      font-size: 0.85rem;
      background: var(--color-primary-soft);
      color: var(--color-primary);
      font-weight: 500;
    }

    .type-tag--trial {
      background: var(--color-gold-bg);
      color: var(--color-gold-text);
    }

    .price-free {
      color: var(--color-success);
      font-weight: 600;
    }

    .price-ref {
      color: var(--text-title);
      font-weight: 600;
    }

    .policy-note {
      margin-top: var(--space-5);
      padding: var(--space-4);
      background: var(--bg-card);
      border: 1px dashed var(--line);
      border-radius: var(--radius-control);
    }

    .policy-title {
      margin: 0 0 var(--space-1);
      font-size: 0.88rem;
      font-weight: 600;
      color: var(--text-title);
    }

    .policy-text {
      margin: 0;
      font-size: 0.85rem;
      color: var(--text-muted);
      line-height: 1.6;
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

    /* 成功卡片 */
    .success-card {
      padding: var(--space-7) var(--space-5);
      text-align: center;
      display: flex;
      flex-direction: column;
      align-items: center;
    }

    .success-icon {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 56px;
      height: 56px;
      border-radius: 50%;
      background: var(--color-success-bg);
      color: var(--color-success);
      font-size: 1.8rem;
      font-weight: 600;
      margin-bottom: var(--space-4);
    }

    .success-title {
      margin: 0 0 var(--space-2);
      font-size: 1.5rem;
      color: var(--text-title);
    }

    .success-notice {
      max-width: 500px;
      margin: 0 0 var(--space-5);
      color: var(--text-muted);
      font-size: 0.98rem;
      line-height: 1.7;
    }

    .success-summary {
      width: 100%;
      max-width: 440px;
      text-align: left;
      padding: var(--space-4);
      background: var(--bg-page);
      border-radius: var(--radius-control);
      border: 1px solid var(--line);
      margin-bottom: var(--space-6);
    }

    .success-summary p {
      margin: var(--space-2) 0;
      font-size: 0.92rem;
      color: var(--text-main);
    }

    .success-actions {
      display: flex;
      gap: var(--space-3);
    }

    @media (max-width: 767.98px) {
      .confirm-row {
        flex-direction: column;
        align-items: flex-start;
        gap: var(--space-1);
      }
      .step-actions {
        flex-direction: column-reverse;
        align-items: stretch;
      }
      .step-actions .nur-btn {
        width: 100%;
      }
      .success-actions {
        flex-direction: column;
        width: 100%;
      }
      .success-actions .nur-btn {
        width: 100%;
      }
    }
  `],
})
export class MatchConfirmStepComponent {
  readonly teacher = input.required<TeacherCandidate>();
  readonly bookingType = input.required<'TRIAL' | 'FORMAL'>();
  readonly selectedSlot = input.required<OpenSlot | null>();
  readonly submitting = input<boolean>(false);
  readonly wizardSuccess = input<boolean>(false);
  readonly wizardNotice = input<string>('');

  readonly submitBooking = output<void>();
  readonly backToSlots = output<void>();
  readonly finish = output<void>();
}
