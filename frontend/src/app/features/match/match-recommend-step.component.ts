import { Component, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { TeacherCompareComponent, CompareTeacher } from '../../shared/components/teacher-compare.component';

export interface TeacherCandidate extends CompareTeacher {
  teacherId: string;
  subject: string;
  gradeBand: string | null;
  pricePerHourCents: number;
  verificationApproved: boolean;
  averageRating: number | null;
  reviewCount: number;
  nextOpenSlotAt: string | null;
  reasons: string[];
}

export type TimeFilter = 'any' | 'today' | '3d' | '7d';

/**
 * 步骤二：推荐导师与多维比选（方向 D · 黛绿暖金）
 * 展示匹配到的导师、支持时间窗口过滤、对比抽屉、收藏、课前咨询与选择试听/正式课
 */
@Component({
  selector: 'nur-match-recommend-step',
  standalone: true,
  imports: [DatePipe, TeacherCompareComponent],
  template: `
    <section class="recommend-step" aria-labelledby="step2-title">
      <!-- 头部与时间筛选 -->
      <header class="step-head nur-card">
        <div class="head-title-row">
          <div>
            <h2 id="step2-title" class="step-title nur-serif">第二步：挑选合适的导师</h2>
            <p class="step-lead">
              已依据「{{ demandSubject() || '你的需求' }}」筛选出导师。支持按空闲时段筛选、最多 3 人横向对比。
            </p>
          </div>
          <button type="button" class="nur-btn nur-btn--ghost nur-btn--sm" (click)="backToDemand.emit()">
            ← 调整需求参数
          </button>
        </div>

        <!-- 最近可约时间过滤 -->
        <div class="time-filter-row" role="group" aria-label="按可约时间筛选">
          <span class="filter-label">可约时间：</span>
          <div class="filter-chips">
            <button
              type="button"
              class="nur-chip-btn"
              [attr.aria-pressed]="timeFilter() === 'any'"
              (click)="timeFilterChange.emit('any')"
            >
              不限时段
            </button>
            <button
              type="button"
              class="nur-chip-btn"
              [attr.aria-pressed]="timeFilter() === 'today'"
              (click)="timeFilterChange.emit('today')"
            >
              今日可约
            </button>
            <button
              type="button"
              class="nur-chip-btn"
              [attr.aria-pressed]="timeFilter() === '3d'"
              (click)="timeFilterChange.emit('3d')"
            >
              3 天内可约
            </button>
            <button
              type="button"
              class="nur-chip-btn"
              [attr.aria-pressed]="timeFilter() === '7d'"
              (click)="timeFilterChange.emit('7d')"
            >
              7 天内可约
            </button>
          </div>
          @if (timeFilter() !== 'any' && !loading()) {
            <span class="filter-hint">
              已筛选出 {{ filteredCandidates().length }}/{{ candidates().length }} 位导师
            </span>
          }
        </div>
      </header>

      <!-- 对比表挂载区 -->
      @if (compared().length > 0) {
        <nur-teacher-compare [teachers]="compared()" (remove)="removeCompare.emit($event)" />
      }

      <!-- 状态：加载中 / 空结果 / 候选列表 -->
      @if (loading()) {
        <div class="loading-state nur-card" role="status">
          <div class="loading-spinner" aria-hidden="true"></div>
          <p class="loading-text">正在为你匹配最合适的导师库数据…</p>
        </div>
      } @else if (candidates().length === 0) {
        <div class="empty-state nur-card">
          <h3 class="empty-title nur-serif">暂未找到完全匹配的导师</h3>
          <p class="empty-desc">建议放宽预算上限或修改科目名称，也可以返回上一步调整参数。</p>
          <button type="button" class="nur-btn nur-btn--primary" (click)="backToDemand.emit()">
            ← 返回调整需求
          </button>
        </div>
      } @else if (filteredCandidates().length === 0) {
        <div class="empty-state nur-card">
          <h3 class="empty-title nur-serif">当前时间段暂无导师发布档期</h3>
          <p class="empty-desc">该时段没有已排出的空闲时段，可切换回「不限时段」或直接与老师课前咨询预约。</p>
          <button type="button" class="nur-btn nur-btn--ghost" (click)="timeFilterChange.emit('any')">
            查看全部导师（不限时段）
          </button>
        </div>
      } @else {
        <ul class="candidates-list">
          @for (t of filteredCandidates(); track t.teacherId + t.subject) {
            <li class="candidate-card nur-card nur-card--interactive">
              <div class="candidate-body">
                <div class="candidate-header">
                  <div class="candidate-meta-col">
                    <div class="candidate-name-row">
                      <span class="nur-avatar cand-avatar" aria-hidden="true">{{ t.displayName.slice(0, 1) }}</span>
                      <div>
                        <h3 class="cand-name nur-serif">{{ t.displayName }} · {{ t.subject }}</h3>
                        <p class="cand-sub">
                          {{ t.school || '高等院校' }} · {{ t.highestDegree || '学士' }} · {{ t.yearsOfTeaching }} 年教龄
                        </p>
                      </div>
                    </div>
                  </div>

                  <div class="candidate-badges">
                    @if (t.verificationApproved) {
                      <span class="nur-cert nur-cert--ok"><span aria-hidden="true">✓</span> 已认证</span>
                    } @else {
                      <span class="nur-cert nur-cert--pending"><span aria-hidden="true">◔</span> 审核中</span>
                    }
                    @if (t.averageRating !== null) {
                      <span class="cand-rating">
                        <span aria-hidden="true">★</span> {{ t.averageRating }}
                        <small>({{ t.reviewCount }} 评)</small>
                      </span>
                    }
                  </div>
                </div>

                <div class="cand-details-row">
                  <span class="cand-price">
                    ¥{{ (t.pricePerHourCents / 100).toFixed(0) }}<small>/课时</small>
                  </span>
                  <span class="cand-grade">{{ t.gradeBand ?? '学段不限' }}</span>
                  @if (t.nextOpenSlotAt) {
                    <span class="cand-slot">
                      <span aria-hidden="true">◔</span> 最近可约 {{ t.nextOpenSlotAt | date: 'M月d日 HH:mm' }}
                    </span>
                  } @else {
                    <span class="cand-slot-none">近期档期暂未开放</span>
                  }
                </div>

                @if (t.reasons && t.reasons.length > 0) {
                  <div class="cand-reasons">
                    <p class="reason-line">
                      <span class="reason-bullet">✦</span> 匹配依据：{{ t.reasons[0] }}
                    </p>
                    @if (t.reasons.length > 1) {
                      <p class="reason-line">
                        <span class="reason-bullet">✦</span> {{ t.reasons[1] }}
                      </p>
                    }
                  </div>
                }
              </div>

              <!-- 卡片操作区 -->
              <footer class="candidate-actions">
                <button
                  type="button"
                  class="nur-btn nur-btn--primary"
                  (click)="onSelect(t, 'TRIAL')"
                >
                  预约免费试听
                </button>
                <button
                  type="button"
                  class="nur-btn nur-btn--ghost"
                  (click)="onSelect(t, 'FORMAL')"
                >
                  预约正式课
                </button>
                <button
                  type="button"
                  class="nur-btn nur-btn--ghost nur-btn--sm"
                  [disabled]="consultBusy()"
                  (click)="consult.emit(t)"
                >
                  课前咨询
                </button>
                <button
                  type="button"
                  class="nur-btn nur-btn--ghost nur-btn--sm"
                  [class.btn-active]="isCompared(t.teacherId)"
                  (click)="toggleCompare.emit(t)"
                >
                  {{ isCompared(t.teacherId) ? '✓ 已对比' : '+ 对比' }}
                </button>
                <button
                  type="button"
                  class="btn-fav-icon"
                  [class.btn-fav-icon--active]="isFavorite(t.teacherId)"
                  [disabled]="favBusyIds().has(t.teacherId)"
                  [attr.aria-label]="(isFavorite(t.teacherId) ? '取消收藏 ' : '收藏 ') + t.displayName"
                  (click)="toggleFavorite.emit(t)"
                >
                  {{ isFavorite(t.teacherId) ? '♥' : '♡' }}
                </button>
              </footer>
            </li>
          }
        </ul>
      }

      <div class="step-footer-actions">
        <button type="button" class="nur-btn nur-btn--ghost" (click)="backToDemand.emit()">
          ← 上一步：修改学习需求
        </button>
      </div>
    </section>
  `,
  styles: [`
    .recommend-step {
      display: flex;
      flex-direction: column;
      gap: var(--space-5);
    }

    .step-head {
      padding: var(--space-5);
      background: var(--bg-card);
      border: 1px solid var(--line);
      border-radius: var(--radius-card);
      box-shadow: var(--shadow-subtle);
    }

    .head-title-row {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: var(--space-4);
      margin-bottom: var(--space-4);
    }

    .step-title {
      margin: 0;
      font-size: 1.35rem;
      font-weight: 600;
      color: var(--text-title);
    }

    .step-lead {
      margin: var(--space-1) 0 0;
      color: var(--text-muted);
      font-size: 0.92rem;
    }

    .time-filter-row {
      display: flex;
      align-items: center;
      gap: var(--space-3);
      padding-top: var(--space-4);
      border-top: 1px dashed var(--line);
      flex-wrap: wrap;
    }

    .filter-label {
      font-size: 0.88rem;
      color: var(--text-muted);
      font-weight: 500;
    }

    .filter-chips {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2);
    }

    .filter-hint {
      font-size: 0.82rem;
      color: var(--text-placeholder);
    }

    /* 候选列表 */
    .candidates-list {
      display: flex;
      flex-direction: column;
      gap: var(--space-4);
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .candidate-card {
      display: flex;
      flex-direction: column;
      gap: var(--space-4);
      padding: var(--space-5);
      background: var(--bg-card);
      border: 1px solid var(--line);
      border-radius: var(--radius-card);
      box-shadow: var(--shadow-subtle);
      transition: transform var(--duration-enter) var(--ease),
                  box-shadow var(--duration-enter) var(--ease);
    }

    .candidate-card:hover {
      transform: translateY(-2px);
      box-shadow: var(--shadow-hover);
    }

    .candidate-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: var(--space-3);
    }

    .candidate-name-row {
      display: flex;
      align-items: center;
      gap: var(--space-3);
    }

    .cand-avatar {
      width: 48px;
      height: 48px;
      font-size: 1.2rem;
    }

    .cand-name {
      margin: 0;
      font-size: 1.18rem;
      color: var(--text-title);
    }

    .cand-sub {
      margin: 2px 0 0;
      font-size: 0.85rem;
      color: var(--text-muted);
    }

    .candidate-badges {
      display: flex;
      align-items: center;
      gap: var(--space-2);
      flex-wrap: wrap;
    }

    .cand-rating {
      color: var(--color-gold-text);
      font-weight: 600;
      font-size: 0.95rem;
    }

    .cand-rating small {
      color: var(--text-muted);
      font-weight: normal;
    }

    .cand-details-row {
      display: flex;
      align-items: baseline;
      gap: var(--space-3);
      margin-top: var(--space-3);
      flex-wrap: wrap;
    }

    .cand-price {
      font-size: 1.25rem;
      font-weight: 600;
      color: var(--color-primary);
    }

    .cand-price small {
      font-size: 0.8rem;
      font-weight: normal;
      color: var(--text-muted);
    }

    .cand-grade {
      font-size: 0.82rem;
      padding: 1px 8px;
      border-radius: var(--radius-pill);
      background: var(--bg-page);
      color: var(--text-muted);
    }

    .cand-slot {
      font-size: 0.85rem;
      color: var(--color-primary);
      background: var(--color-primary-soft);
      padding: 2px 10px;
      border-radius: var(--radius-pill);
    }

    .cand-slot-none {
      font-size: 0.82rem;
      color: var(--text-placeholder);
    }

    .cand-reasons {
      margin-top: var(--space-3);
      padding: var(--space-3);
      border-radius: var(--radius-control);
      background: var(--bg-page);
    }

    .reason-line {
      margin: 0;
      font-size: 0.88rem;
      color: var(--text-main);
      line-height: 1.6;
    }

    .reason-bullet {
      color: var(--color-gold);
    }

    .candidate-actions {
      display: flex;
      align-items: center;
      gap: var(--space-2);
      padding-top: var(--space-3);
      border-top: 1px solid var(--line);
      flex-wrap: wrap;
    }

    .btn-active {
      background: var(--color-primary-soft);
      border-color: var(--color-primary);
      color: var(--color-primary);
      font-weight: 600;
    }

    .btn-fav-icon {
      margin-left: auto;
      min-width: 38px;
      height: 38px;
      border-radius: 50%;
      border: 1px solid var(--line);
      background: var(--bg-card);
      color: var(--text-muted);
      font-size: 1.2rem;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      transition: all var(--duration-rise) var(--ease);
    }

    .btn-fav-icon:hover {
      border-color: var(--color-danger);
      color: var(--color-danger);
    }

    .btn-fav-icon--active {
      color: var(--color-danger);
      background: var(--color-danger-bg);
      border-color: rgba(176, 82, 76, 0.3);
    }

    /* 空态与加载 */
    .loading-state,
    .empty-state {
      padding: var(--space-8) var(--space-5);
      text-align: center;
      background: var(--bg-card);
      border: 1px dashed var(--line);
      border-radius: var(--radius-card);
    }

    .loading-spinner {
      width: 32px;
      height: 32px;
      margin: 0 auto var(--space-3);
      border: 3px solid var(--line);
      border-top-color: var(--color-primary);
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    .empty-title {
      margin: 0 0 var(--space-2);
      font-size: 1.2rem;
      color: var(--text-title);
    }

    .empty-desc {
      margin: 0 0 var(--space-4);
      color: var(--text-muted);
      font-size: 0.92rem;
    }

    .step-footer-actions {
      padding-top: var(--space-4);
    }

    @media (max-width: 767.98px) {
      .head-title-row {
        flex-direction: column;
      }
      .candidate-header {
        flex-direction: column;
      }
      .candidate-actions {
        flex-direction: column;
        align-items: stretch;
      }
      .candidate-actions .nur-btn {
        width: 100%;
      }
      .btn-fav-icon {
        margin-left: 0;
        width: 100%;
        border-radius: var(--radius-pill);
      }
    }
  `],
})
export class MatchRecommendStepComponent {
  readonly candidates = input.required<TeacherCandidate[]>();
  readonly filteredCandidates = input.required<TeacherCandidate[]>();
  readonly loading = input<boolean>(false);
  readonly timeFilter = input<TimeFilter>('any');
  readonly favoriteIds = input<Set<string>>(new Set());
  readonly favBusyIds = input<Set<string>>(new Set());
  readonly consultBusy = input<boolean>(false);
  readonly compared = input<TeacherCandidate[]>([]);
  readonly demandSubject = input<string>('');

  readonly selectTeacher = output<{ teacher: TeacherCandidate; bookingType: 'TRIAL' | 'FORMAL' }>();
  readonly toggleFavorite = output<TeacherCandidate>();
  readonly consult = output<TeacherCandidate>();
  readonly toggleCompare = output<TeacherCandidate>();
  readonly removeCompare = output<string>();
  readonly timeFilterChange = output<TimeFilter>();
  readonly backToDemand = output<void>();

  isFavorite(id: string): boolean {
    return this.favoriteIds().has(id);
  }

  isCompared(id: string): boolean {
    return this.compared().some((t) => t.teacherId === id);
  }

  onSelect(teacher: TeacherCandidate, bookingType: 'TRIAL' | 'FORMAL'): void {
    this.selectTeacher.emit({ teacher, bookingType });
  }
}
