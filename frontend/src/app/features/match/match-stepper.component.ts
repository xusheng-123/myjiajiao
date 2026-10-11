import { Component, input, output } from '@angular/core';

export interface StepItem {
  step: number;
  label: string;
  desc: string;
}

const STEPS: StepItem[] = [
  { step: 1, label: '填写需求', desc: '科目与学习目标' },
  { step: 2, label: '挑选导师', desc: '推荐比选与沟通' },
  { step: 3, label: '选择档期', desc: '可约时段与试听' },
  { step: 4, label: '确认预约', desc: '信息核对与提交' },
];

/**
 * 找老师 · 四步向导进度指示器（方向 D · 黛绿暖金）
 * ADHD 友好：清晰进度感、明确当前步骤与前置/后置状态、支持可达步骤回退
 */
@Component({
  selector: 'nur-match-stepper',
  standalone: true,
  template: `
    <nav class="stepper" aria-label="找老师与预约流程">
      <ol class="stepper-list">
        @for (s of steps; track s.step) {
          <li
            class="stepper-item"
            [class.stepper-item--active]="currentStep() === s.step"
            [class.stepper-item--done]="currentStep() > s.step"
            [attr.aria-current]="currentStep() === s.step ? 'step' : null"
          >
            <button
              type="button"
              class="step-btn"
              [disabled]="s.step > maxReachedStep()"
              (click)="onStepClick(s.step)"
              [attr.aria-label]="'第 ' + s.step + ' 步：' + s.label + (currentStep() > s.step ? '（已完成，可点击返回）' : '')"
            >
              <span class="step-num" aria-hidden="true">
                @if (currentStep() > s.step) {
                  ✓
                } @else {
                  {{ s.step }}
                }
              </span>
              <div class="step-text">
                <span class="step-label">{{ s.label }}</span>
                <span class="step-desc">{{ s.desc }}</span>
              </div>
            </button>
            @if (s.step < steps.length) {
              <span class="step-line" aria-hidden="true"></span>
            }
          </li>
        }
      </ol>
    </nav>
  `,
  styles: [`
    .stepper {
      margin-bottom: var(--space-6);
      padding: var(--space-4) var(--space-5);
      background: var(--bg-card);
      border: 1px solid var(--line);
      border-radius: var(--radius-card);
      box-shadow: var(--shadow-subtle);
    }

    .stepper-list {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin: 0;
      padding: 0;
      list-style: none;
      gap: var(--space-2);
    }

    .stepper-item {
      display: flex;
      align-items: center;
      flex: 1;
      position: relative;
    }

    .step-btn {
      display: flex;
      align-items: center;
      gap: var(--space-3);
      padding: var(--space-2);
      border: none;
      background: transparent;
      color: inherit;
      font: inherit;
      cursor: pointer;
      text-align: left;
      border-radius: var(--radius-control);
      transition: background-color var(--duration-rise) var(--ease);
    }

    .step-btn:hover:not(:disabled) {
      background: var(--bg-page);
    }

    .step-btn:disabled {
      cursor: not-allowed;
      opacity: 0.6;
    }

    .step-num {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: var(--bg-page);
      border: 1.5px solid var(--line);
      color: var(--text-muted);
      font-size: 0.88rem;
      font-weight: 600;
      transition: all var(--duration-rise) var(--ease);
      flex-shrink: 0;
    }

    .stepper-item--active .step-num {
      background: var(--color-primary);
      border-color: var(--color-primary);
      color: #fff;
      box-shadow: 0 0 0 4px var(--color-primary-soft);
    }

    .stepper-item--done .step-num {
      background: var(--color-success-bg);
      border-color: var(--color-success);
      color: var(--color-success);
    }

    .step-text {
      display: flex;
      flex-direction: column;
    }

    .step-label {
      font-size: 0.95rem;
      font-weight: 600;
      color: var(--text-muted);
    }

    .stepper-item--active .step-label {
      color: var(--color-primary);
    }

    .stepper-item--done .step-label {
      color: var(--text-title);
    }

    .step-desc {
      font-size: 0.78rem;
      color: var(--text-placeholder);
    }

    .step-line {
      flex: 1;
      height: 2px;
      margin: 0 var(--space-3);
      background: var(--line);
      transition: background-color var(--duration-rise) var(--ease);
    }

    .stepper-item--done .step-line {
      background: var(--color-success);
    }

    @media (max-width: 767.98px) {
      .step-desc {
        display: none;
      }
      .step-line {
        margin: 0 var(--space-1);
      }
      .step-btn {
        gap: var(--space-2);
        padding: var(--space-1);
      }
      .step-label {
        font-size: 0.82rem;
      }
      .step-num {
        width: 26px;
        height: 26px;
        font-size: 0.78rem;
      }
    }
  `],
})
export class MatchStepperComponent {
  readonly currentStep = input.required<number>();
  readonly maxReachedStep = input<number>(1);
  readonly stepChange = output<number>();

  readonly steps = STEPS;

  onStepClick(step: number): void {
    if (step <= this.maxReachedStep() && step !== this.currentStep()) {
      this.stepChange.emit(step);
    }
  }
}
