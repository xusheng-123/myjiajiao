import { Component, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
export interface CompareTeacher {
  teacherId: string; displayName: string; school: string | null; highestDegree: string | null;
  yearsOfTeaching: number; subject: string; gradeBand: string | null; pricePerHourCents: number;
  verificationApproved: boolean; averageRating: number | null; reviewCount: number;
  nextOpenSlotAt: string | null; styleTags: string[];
}
@Component({
  selector: 'nur-teacher-compare',
  standalone: true,
  imports: [DatePipe],
  template: `
    @if (teachers().length) {
      <section class="compare-card" aria-label="教师对比">
        <div class="compare-head">
          <div>
            <h2 class="compare-title nur-serif">导师对比（最多 3 位）</h2>
            <p class="compare-desc">资料与价格以当前检索结果为参考，预约与下单以服务端最新信息为准。</p>
          </div>
          <span class="compare-count">已选 {{ teachers().length }}/3 位</span>
        </div>

        <div class="scroll-wrapper" tabindex="0" role="region" aria-label="教师对比表，可横向滚动">
          <table class="compare-table">
            <caption class="sr-only">当前选中的 {{ teachers().length }} 位教师横向对比</caption>
            <thead>
              <tr>
                <th scope="col" class="col-feature">对比维度</th>
                @for (t of teachers(); track t.teacherId) {
                  <th scope="col" class="col-teacher">
                    <div class="teacher-head-cell">
                      <span class="teacher-name">{{ t.displayName }}</span>
                      <button
                        type="button"
                        class="btn-remove"
                        (click)="remove.emit(t.teacherId)"
                        [attr.aria-label]="'移除对比 ' + t.displayName"
                      >
                        移除
                      </button>
                    </div>
                  </th>
                }
              </tr>
            </thead>
            <tbody>
              <tr>
                <th scope="row">课程与价格</th>
                @for (t of teachers(); track t.teacherId) {
                  <td>
                    <span class="cell-main">{{ t.subject }} · {{ t.gradeBand || '学段不限' }}</span>
                    <span class="cell-price">¥{{ (t.pricePerHourCents / 100).toFixed(2) }}<small>/小时</small></span>
                  </td>
                }
              </tr>
              <tr>
                <th scope="row">院校 / 学历</th>
                @for (t of teachers(); track t.teacherId) {
                  <td>{{ t.school || '未填写' }} / {{ t.highestDegree || '未填写' }}</td>
                }
              </tr>
              <tr>
                <th scope="row">教龄（教师填报）</th>
                @for (t of teachers(); track t.teacherId) {
                  <td>{{ t.yearsOfTeaching }} 年教龄</td>
                }
              </tr>
              <tr>
                <th scope="row">认证状态</th>
                @for (t of teachers(); track t.teacherId) {
                  <td>
                    <span class="status-tag" [class.status-tag--ok]="t.verificationApproved">
                      {{ t.verificationApproved ? '✓ 已认证' : '◔ 审核中' }}
                    </span>
                  </td>
                }
              </tr>
              <tr>
                <th scope="row">教学风格</th>
                @for (t of teachers(); track t.teacherId) {
                  <td>{{ t.styleTags.join(' · ') || '未填写' }}</td>
                }
              </tr>
              <tr>
                <th scope="row">评价证据</th>
                @for (t of teachers(); track t.teacherId) {
                  <td>
                    @if (t.averageRating !== null) {
                      <span class="rating-text">★ {{ t.averageRating }} · {{ t.reviewCount }} 条评价</span>
                    } @else {
                      <span class="cell-muted">暂无评价</span>
                    }
                  </td>
                }
              </tr>
              <tr>
                <th scope="row">最近可约</th>
                @for (t of teachers(); track t.teacherId) {
                  <td>
                    @if (t.nextOpenSlotAt) {
                      <span class="slot-text">{{ t.nextOpenSlotAt | date: 'M月d日 HH:mm' }}</span>
                    } @else {
                      <span class="cell-muted">暂无可约时段</span>
                    }
                  </td>
                }
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    }
  `,
  styles: [`
    .compare-card {
      margin: var(--space-5) 0;
      padding: var(--space-5);
      background: var(--bg-card);
      border: 1px solid var(--line);
      border-radius: var(--radius-card);
      box-shadow: var(--shadow-subtle);
    }

    .compare-head {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: var(--space-4);
      margin-bottom: var(--space-4);
    }

    .compare-title {
      margin: 0;
      font-size: 1.25rem;
      font-weight: 600;
      color: var(--text-title);
      letter-spacing: 0.04em;
    }

    .compare-desc {
      margin: var(--space-1) 0 0;
      font-size: 0.88rem;
      color: var(--text-muted);
    }

    .compare-count {
      padding: 3px 10px;
      font-size: 0.82rem;
      border-radius: var(--radius-pill);
      background: var(--color-primary-soft);
      color: var(--color-primary);
      white-space: nowrap;
    }

    .scroll-wrapper {
      max-width: 100%;
      overflow-x: auto;
      border: 1px solid var(--line);
      border-radius: var(--radius-control);
      -webkit-overflow-scrolling: touch;
    }

    .compare-table {
      width: 100%;
      min-width: 620px;
      border-collapse: collapse;
      font-size: 0.92rem;
    }

    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      padding: 0;
      margin: -1px;
      overflow: hidden;
      clip: rect(0, 0, 0, 0);
      white-space: nowrap;
      border: 0;
    }

    th, td {
      padding: var(--space-3) var(--space-4);
      text-align: left;
      vertical-align: middle;
      border-bottom: 1px solid var(--line);
    }

    thead th {
      background: var(--bg-page);
      font-weight: 600;
      color: var(--text-title);
    }

    .col-feature {
      width: 150px;
      color: var(--text-muted);
    }

    .col-teacher {
      min-width: 180px;
    }

    .teacher-head-cell {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-2);
    }

    .teacher-name {
      font-family: var(--font-serif);
      font-size: 1.05rem;
      color: var(--color-primary);
    }

    .btn-remove {
      padding: 2px 8px;
      min-height: 28px;
      border: 1px solid var(--line);
      border-radius: var(--radius-pill);
      background: var(--bg-card);
      color: var(--color-danger);
      font-size: 0.78rem;
      cursor: pointer;
      transition: background-color var(--duration-rise) var(--ease), border-color var(--duration-rise) var(--ease);
    }

    .btn-remove:hover {
      background: var(--color-danger-bg);
      border-color: var(--color-danger);
    }

    tbody th {
      color: var(--text-muted);
      font-weight: 500;
      background: rgba(242, 243, 238, 0.4);
      border-right: 1px solid var(--line);
    }

    .cell-main {
      display: block;
      color: var(--text-title);
      font-weight: 500;
    }

    .cell-price {
      display: block;
      margin-top: 2px;
      font-weight: 600;
      color: var(--text-title);
    }

    .cell-price small {
      font-size: 0.8rem;
      font-weight: normal;
      color: var(--text-muted);
    }

    .cell-muted {
      color: var(--text-placeholder);
    }

    .status-tag {
      display: inline-block;
      padding: 2px 8px;
      font-size: 0.8rem;
      border-radius: var(--radius-pill);
      background: var(--color-gold-bg);
      color: var(--color-gold-text);
    }

    .status-tag--ok {
      background: var(--color-success-bg);
      color: var(--color-success);
    }

    .rating-text {
      color: var(--color-gold-text);
      font-weight: 600;
    }

    .slot-text {
      color: var(--color-primary);
    }
  `],
})
export class TeacherCompareComponent {
  readonly teachers = input.required<CompareTeacher[]>();
  readonly remove = output<string>();
}
