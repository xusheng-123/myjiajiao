import { Component, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
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
  imports: [DatePipe, RouterLink, TeacherCompareComponent],
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
                  <div class="candidate-name-row">
                    <span class="cand-avatar-wrap">
                      <span class="nur-avatar cand-avatar" aria-hidden="true">{{ t.displayName.slice(0, 1) }}</span>
                      @if (t.verificationApproved) {
                        <span class="cand-verify-badge" aria-hidden="true">✓</span>
                      }
                    </span>
                    <div>
                      <div class="cand-name-line">
                        <h3 class="cand-name nur-serif">{{ t.displayName }} · {{ t.subject }}</h3>
                        @if (t.verificationApproved) {
                          <span class="nur-cert nur-cert--ok"><span aria-hidden="true">✓</span> 已认证</span>
                        } @else {
                          <span class="nur-cert nur-cert--pending"><span aria-hidden="true">◔</span> 审核中</span>
                        }
                      </div>
                      <p class="cand-sub">
                        {{ t.school || '高等院校' }} · {{ t.highestDegree || '学士' }} · {{ t.yearsOfTeaching }} 年教龄
                      </p>
                    </div>
                  </div>

                  <div class="candidate-side">
                    @if (t.averageRating !== null) {
                      <span class="cand-rating">
                        <span aria-hidden="true">★</span> {{ t.averageRating }}
                        <small>({{ t.reviewCount }} 评价)</small>
                      </span>
                    }
                    <span class="cand-price">
                      ¥{{ (t.pricePerHourCents / 100).toFixed(0) }}<small>/课时</small>
                    </span>
                  </div>
                </div>

                <div class="cand-tags">
                  <span class="cand-tag cand-tag--grade">{{ t.gradeBand ?? '学段不限' }}</span>
                  @for (tag of t.styleTags.slice(0, 4); track tag) {
                    <span class="cand-tag">{{ tag }}</span>
                  }
                </div>

                <div class="cand-info-band">
                  <div class="info-cell">
                    <p class="info-cell-title"><span class="info-icon" aria-hidden="true">✦</span> 匹配依据</p>
                    @if (t.reasons && t.reasons.length > 0) {
                      <p class="reason-line">{{ t.reasons[0] }}</p>
                      @if (t.reasons.length > 1) {
                        <p class="reason-line">{{ t.reasons[1] }}</p>
                      }
                    } @else {
                      <p class="reason-line reason-empty">按科目与空档与你匹配</p>
                    }
                  </div>
                  <div class="info-cell info-cell--slot">
                    <p class="info-cell-title"><span class="info-icon" aria-hidden="true">◔</span> 最近可约</p>
                    @if (t.nextOpenSlotAt) {
                      <p class="slot-line">{{ t.nextOpenSlotAt | date: 'M月d日 EEE HH:mm' }}</p>
                    } @else {
                      <p class="reason-empty">近期档期暂未开放</p>
                    }
                  </div>
                </div>
              </div>

              <!-- 卡片操作区 -->
              <footer class="candidate-actions">
                <div class="actions-left">
                  <button
                    type="button"
                    class="nur-btn nur-btn--ghost nur-btn--sm"
                    [class.btn-active]="isCompared(t.teacherId)"
                    (click)="toggleCompare.emit(t)"
                  >
                    {{ isCompared(t.teacherId) ? '✓ 已加入对比' : '+ 加入对比' }}
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
                </div>
                <div class="actions-right">
                  <button
                    type="button"
                    class="nur-btn nur-btn--ghost nur-btn--sm"
                    [disabled]="consultBusy()"
                    (click)="consult.emit(t)"
                  >
                    课前咨询
                  </button>
                  <a
                    class="nur-btn nur-btn--ghost nur-btn--sm cand-detail-link"
                    [routerLink]="['/teachers', t.teacherId]"
                  >
                    查看详情
                  </a>
                  <button
                    type="button"
                    class="nur-btn nur-btn--ghost"
                    (click)="onSelect(t, 'FORMAL')"
                  >
                    预约正式课
                  </button>
                  <button
                    type="button"
                    class="nur-btn nur-btn--primary"
                    (click)="onSelect(t, 'TRIAL')"
                  >
                    预约免费试听
                  </button>
                </div>
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

    .cand-avatar-wrap {
      position: relative;
      flex: none;
    }

    .cand-avatar {
      width: 56px;
      height: 56px;
      font-size: 1.35rem;
    }

    .cand-verify-badge {
      position: absolute;
      right: -3px;
      bottom: -3px;
      width: 20px;
      height: 20px;
      border-radius: 50%;
      background: var(--color-primary);
      color: #fff;
      font-size: 0.72rem;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border: 2px solid var(--bg-card);
    }

    .cand-name-line {
      display: flex;
      align-items: center;
      gap: var(--space-2);
      flex-wrap: wrap;
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

    .candidate-side {
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      gap: 4px;
      flex: none;
    }

    .cand-rating {
      color: var(--color-gold-text);
      font-weight: 600;
      font-size: 1.05rem;
      font-family: var(--font-serif);
    }

    .cand-rating small {
      color: var(--text-muted);
      font-weight: normal;
      font-size: 0.8rem;
      font-family: var(--font-sans);
    }

    .cand-price {
      font-size: 1.4rem;
      font-weight: 600;
      color: var(--color-primary);
      font-family: var(--font-serif);
    }

    .cand-price small {
      font-size: 0.8rem;
      font-weight: normal;
      color: var(--text-muted);
      font-family: var(--font-sans);
    }

    .cand-tags {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2);
      margin-top: var(--space-3);
    }

    .cand-tag {
      font-size: 0.8rem;
      padding: 2px 10px;
      border-radius: var(--radius-pill);
      background: var(--color-primary-soft);
      color: var(--color-primary);
    }

    .cand-tag--grade {
      background: var(--bg-page);
      border: 1px solid var(--line);
      color: var(--text-muted);
    }

    /* 双格信息带：匹配依据 | 最近可约 */
    .cand-info-band {
      margin-top: var(--space-4);
      display: grid;
      grid-template-columns: 1.6fr 1fr;
      border-radius: var(--radius-control);
      background: var(--bg-page);
      border: 1px solid var(--line);
      overflow: hidden;
    }

    .info-cell {
      padding: var(--space-3) var(--space-4);
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .info-cell--slot {
      border-left: 1px solid var(--line);
    }

    .info-cell-title {
      margin: 0;
      font-size: 0.8rem;
      font-weight: 600;
      color: var(--color-primary);
      display: flex;
      align-items: center;
      gap: 6px;
    }

    .info-icon {
      color: var(--color-gold);
    }

    .reason-line {
      margin: 0;
      font-size: 0.86rem;
      color: var(--text-main);
      line-height: 1.6;
    }

    .reason-empty {
      color: var(--text-placeholder);
    }

    .slot-line {
      margin: 0;
      font-size: 0.92rem;
      font-weight: 600;
      color: var(--color-primary);
    }

    .candidate-actions {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-2);
      padding-top: var(--space-3);
      border-top: 1px solid var(--line);
      flex-wrap: wrap;
    }

    .actions-left,
    .actions-right {
      display: flex;
      align-items: center;
      gap: var(--space-2);
      flex-wrap: wrap;
    }

    .cand-detail-link {
      text-decoration: none;
    }

    .btn-active {
      background: var(--color-primary-soft);
      border-color: var(--color-primary);
      color: var(--color-primary);
      font-weight: 600;
    }

    .btn-fav-icon {
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
      .candidate-side {
        flex-direction: row;
        align-items: baseline;
        gap: var(--space-3);
      }
      .cand-info-band {
        grid-template-columns: 1fr;
      }
      .info-cell--slot {
        border-left: none;
        border-top: 1px solid var(--line);
      }
      .candidate-actions {
        flex-direction: column;
        align-items: stretch;
      }
      .actions-left,
      .actions-right {
        width: 100%;
      }
      .actions-right .nur-btn {
        flex: 1;
        text-align: center;
        justify-content: center;
      }
      .btn-fav-icon {
        flex: 1;
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
