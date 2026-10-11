import { Component, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';

export interface DemandDraft {
  subject: string;
  gradeBand: string;
  budget: string;
  preferredTime: string;
  note: string;
}

export interface DemandView {
  demandId: string;
  subject: string;
  status: string;
}

const QUICK_SUBJECTS = ['数学', '英语', '物理', '化学', '语文', '生物', '历史', '地理', '信息学'];
const GRADE_OPTIONS = ['小学', '初一', '初二', '初三', '高一', '高二', '高三'];

/**
 * 步骤一：学习需求与偏好录入（方向 D · 黛绿暖金）
 * ADHD 友好：支持一键选择标签、草稿持久驻留内存、不限制必填项，几句即可开启匹配
 */
@Component({
  selector: 'nur-match-demand-step',
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="demand-step nur-card" aria-labelledby="step1-title">
      <header class="step-head">
        <div>
          <h2 id="step1-title" class="step-title nur-serif">第一步：告诉我你的学习需求</h2>
          <p class="step-lead">
            不用填写繁琐的长问卷，写清学科与预期即可；填好后系统将为你自动检索最同频的导师。
          </p>
        </div>
      </header>

      @if (demandSuccess()) {
        <div class="status-banner status-banner--success" role="status">
          <span aria-hidden="true">✓</span> 需求已同步发布至需求池，导师可查看并主动接单；你也可直接在下一步自主挑选。
        </div>
      }

      <form class="demand-form" (submit)="$event.preventDefault(); onProceed()">
        <!-- 科目选择 -->
        <div class="form-group">
          <label class="form-label" for="demand-subject">
            辅导科目 <span class="required-star">*</span>
          </label>
          <div class="quick-tags" role="group" aria-label="快捷选择科目">
            @for (s of quickSubjects; track s) {
              <button
                type="button"
                class="nur-chip-btn"
                [attr.aria-pressed]="draft().subject === s"
                (click)="setSubject(s)"
              >
                {{ s }}
              </button>
            }
          </div>
          <input
            id="demand-subject"
            type="text"
            class="form-input"
            placeholder="或手动输入科目，例如：高中物理、竞赛数学"
            [ngModel]="draft().subject"
            (ngModelChange)="updateField('subject', $event)"
            name="subject"
            required
          />
        </div>

        <div class="form-row">
          <!-- 学段选择 -->
          <div class="form-group form-col">
            <label class="form-label" for="demand-grade">学段 / 年级（选填）</label>
            <div class="quick-tags">
              @for (g of gradeOptions; track g) {
                <button
                  type="button"
                  class="nur-chip-btn"
                  [attr.aria-pressed]="draft().gradeBand === g"
                  (click)="setGrade(g)"
                >
                  {{ g }}
                </button>
              }
            </div>
            <input
              id="demand-grade"
              type="text"
              class="form-input"
              placeholder="例如：初三冲刺、高二"
              [ngModel]="draft().gradeBand"
              (ngModelChange)="updateField('gradeBand', $event)"
              name="gradeBand"
            />
          </div>

          <!-- 预算上限 -->
          <div class="form-group form-col">
            <label class="form-label" for="demand-budget">单价预期上限（元/课时，选填）</label>
            <input
              id="demand-budget"
              type="number"
              min="0"
              step="10"
              class="form-input"
              placeholder="例如：200（不限则留空）"
              [ngModel]="draft().budget"
              (ngModelChange)="updateField('budget', $event)"
              name="budget"
            />
          </div>
        </div>

        <!-- 方便时间 -->
        <div class="form-group">
          <label class="form-label" for="demand-time">期望上课时段（选填）</label>
          <input
            id="demand-time"
            type="text"
            class="form-input"
            placeholder="例如：周中晚上 19:00 后、周末全天均可"
            [ngModel]="draft().preferredTime"
            (ngModelChange)="updateField('preferredTime', $event)"
            name="preferredTime"
          />
        </div>

        <!-- 详细情况 -->
        <div class="form-group">
          <label class="form-label" for="demand-note">希望解决的问题或教学风格偏好（选填）</label>
          <textarea
            id="demand-note"
            rows="3"
            class="form-textarea"
            placeholder="例如：基础公式记得住，但遇到综合大题缺乏切入思路；希望老师循循善诱、善于画图拆解…"
            [ngModel]="draft().note"
            (ngModelChange)="updateField('note', $event)"
            name="note"
          ></textarea>
        </div>

        <!-- 操作栏 -->
        <div class="form-actions">
          <button
            type="submit"
            class="nur-btn nur-btn--primary"
            [disabled]="!draft().subject.trim()"
          >
            下一步：匹配导师 →
          </button>

          <button
            type="button"
            class="nur-btn nur-btn--ghost"
            [disabled]="!draft().subject.trim() || demandPublishing()"
            (click)="onPublishDemand()"
          >
            {{ demandPublishing() ? '发布中…' : '同时登记到公开需求池' }}
          </button>
        </div>
      </form>

      @if (myDemands().length > 0) {
        <div class="my-demands-block">
          <h3 class="demands-title nur-serif">我已发布的需求</h3>
          <ul class="demands-list">
            @for (d of myDemands(); track d.demandId) {
              <li class="demand-chip-item">
                <span class="demand-subject">{{ d.subject }}</span>
                <span class="demand-status">
                  {{ d.status === 'OPEN' ? '开放接单中' : d.status === 'MATCHED' ? '已撮合' : '已归档' }}
                </span>
              </li>
            }
          </ul>
        </div>
      }
    </section>
  `,
  styles: [`
    .demand-step {
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
      letter-spacing: 0.04em;
    }

    .step-lead {
      margin: var(--space-2) 0 0;
      color: var(--text-muted);
      line-height: 1.7;
      font-size: 0.95rem;
    }

    .status-banner {
      padding: var(--space-3) var(--space-4);
      margin-bottom: var(--space-5);
      border-radius: var(--radius-control);
      font-size: 0.92rem;
    }

    .status-banner--success {
      background: var(--color-success-bg);
      color: var(--color-success);
      border: 1px solid rgba(53, 112, 90, 0.2);
    }

    .demand-form {
      display: flex;
      flex-direction: column;
      gap: var(--space-5);
    }

    .form-group {
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
    }

    .form-row {
      display: flex;
      gap: var(--space-5);
    }

    .form-col {
      flex: 1;
    }

    .form-label {
      font-size: 0.92rem;
      font-weight: 500;
      color: var(--text-title);
    }

    .required-star {
      color: var(--color-danger);
    }

    .quick-tags {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2);
      margin-bottom: var(--space-1);
    }

    .form-input,
    .form-textarea {
      width: 100%;
      min-height: var(--touch-target);
      padding: var(--space-2) var(--space-4);
      border: 1.5px solid var(--line);
      border-radius: var(--radius-control);
      background: var(--bg-page);
      color: var(--text-main);
      font: inherit;
      transition: border-color var(--duration-enter) var(--ease),
                  box-shadow var(--duration-enter) var(--ease);
    }

    .form-input:focus,
    .form-textarea:focus {
      outline: none;
      border-color: var(--color-primary);
      background: var(--bg-card);
      box-shadow: 0 0 0 3px var(--color-primary-soft);
    }

    .form-textarea {
      line-height: 1.6;
      resize: vertical;
    }

    .form-actions {
      display: flex;
      align-items: center;
      gap: var(--space-3);
      padding-top: var(--space-4);
      border-top: 1px solid var(--line);
    }

    .my-demands-block {
      margin-top: var(--space-6);
      padding-top: var(--space-5);
      border-top: 1px dashed var(--line);
    }

    .demands-title {
      margin: 0 0 var(--space-3);
      font-size: 1.1rem;
      color: var(--text-title);
    }

    .demands-list {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2);
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .demand-chip-item {
      display: inline-flex;
      align-items: center;
      gap: var(--space-2);
      padding: var(--space-2) var(--space-4);
      border: 1px solid var(--line);
      border-radius: var(--radius-pill);
      background: var(--bg-page);
      font-size: 0.88rem;
    }

    .demand-subject {
      font-weight: 500;
      color: var(--text-title);
    }

    .demand-status {
      font-size: 0.78rem;
      color: var(--color-primary);
      background: var(--color-primary-soft);
      padding: 1px 6px;
      border-radius: var(--radius-pill);
    }

    @media (max-width: 767.98px) {
      .form-row {
        flex-direction: column;
        gap: var(--space-4);
      }
      .form-actions {
        flex-direction: column;
        align-items: stretch;
      }
      .form-actions .nur-btn {
        width: 100%;
      }
    }
  `],
})
export class MatchDemandStepComponent {
  readonly draft = input.required<DemandDraft>();
  readonly demandSuccess = input<boolean>(false);
  readonly demandPublishing = input<boolean>(false);
  readonly myDemands = input<DemandView[]>([]);

  readonly draftChange = output<DemandDraft>();
  readonly proceed = output<void>();
  readonly publish = output<void>();

  readonly quickSubjects = QUICK_SUBJECTS;
  readonly gradeOptions = GRADE_OPTIONS;

  setSubject(s: string): void {
    const current = this.draft();
    const newSubject = current.subject === s ? '' : s;
    this.draftChange.emit({ ...current, subject: newSubject });
  }

  setGrade(g: string): void {
    const current = this.draft();
    const newGrade = current.gradeBand === g ? '' : g;
    this.draftChange.emit({ ...current, gradeBand: newGrade });
  }

  updateField(field: keyof DemandDraft, value: string): void {
    this.draftChange.emit({ ...this.draft(), [field]: value });
  }

  onProceed(): void {
    if (this.draft().subject.trim()) {
      this.proceed.emit();
    }
  }

  onPublishDemand(): void {
    if (this.draft().subject.trim()) {
      this.publish.emit();
    }
  }
}
