import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { API } from '../../core/api/endpoints';
import { NoticeCenterComponent } from '../../shared/components/notice-center.component';

interface NextLesson {
  bookingId: string;
  subject: string;
  teacherName: string;
  startAt: string | null;
  status: string;
}

interface TodayTask {
  taskId: string;
  title: string;
  status: string;
}

interface WeekStats {
  lessonsCompleted: number;
  minutes: number;
  taskCompleted: number;
  taskTotal: number;
}

interface RecentItem {
  type: string;
  text: string;
  at: string;
}

@Component({
  selector: 'nur-student-growth',
  standalone: true,
  imports: [DatePipe, RouterLink, NoticeCenterComponent],
  template: `
    <div class="growth-shell">
      <header class="dash-header">
        <div>
          <div class="dash-eyebrow">
            <span class="eyebrow-dot"></span>
            <span>GROWTH CENTER</span>
          </div>
          <h1 class="dash-greeting">学习成长 · {{ auth.displayName() }}</h1>
          <p class="dash-subtitle">每一步都算数，看见持续积累的力量。</p>
        </div>
        <div class="header-actions">
          <a routerLink="/student/dashboard" class="nur-btn nur-btn--ghost nur-btn--sm">
            ← 返回工作台
          </a>
          <button type="button" class="nur-btn nur-btn--ghost nur-btn--sm" (click)="auth.logout()" aria-label="退出登录">
            退出
          </button>
        </div>
      </header>

      <nav class="sub-nav" aria-label="学习管理导航">
        <a routerLink="/learning/plans" class="sub-nav-item">
          <span class="sub-nav-icon">📋</span>
          <span>学习计划与目标</span>
        </a>
        <a routerLink="/learning/mistakes" class="sub-nav-item">
          <span class="sub-nav-icon">📖</span>
          <span>错题本</span>
        </a>
        <a routerLink="/learning/materials" class="sub-nav-item">
          <span class="sub-nav-icon">📁</span>
          <span>教学资料与作业</span>
        </a>
      </nav>

      @if (loading()) {
        <div class="quiet-box" role="status">
          <p class="quiet-note">正在加载你的成长足迹…</p>
        </div>
      } @else {
        <!-- 下一节课程 -->
        @if (nextLesson(); as lesson) {
          <section class="nur-card next-lesson-card nur-enter" aria-labelledby="next-title">
            <div class="card-header-row">
              <h2 id="next-title" class="card-title">下一节课程</h2>
              <span class="status-tag status-tag--active">待开课</span>
            </div>
            <div class="next-lesson-body">
              <div class="next-lesson-info">
                <p class="next-lesson-subject">{{ lesson.subject }}</p>
                <p class="next-lesson-meta">授课导师：{{ lesson.teacherName }} · {{ lesson.startAt ? (lesson.startAt | date:'M月d日 HH:mm') : '时间待定' }}</p>
              </div>
              <a routerLink="/student/dashboard" class="nur-btn nur-btn--primary nur-btn--sm">
                前往课堂准备 →
              </a>
            </div>
          </section>
        } @else {
          <section class="nur-card quiet-empty-card">
            <p class="quiet-note">最近暂无排定的课程。保持节奏，随时可以找导师预约新课。</p>
          </section>
        }

        <!-- 本周学习数据指标大盘 -->
        <section class="nur-card stats-section nur-enter" aria-labelledby="week-title">
          <div class="card-header-row">
            <h2 id="week-title" class="card-title">本周学习总览</h2>
            <span class="gold-badge">本周活跃</span>
          </div>

          <div class="stats-grid">
            <div class="stat-card">
              <span class="stat-label">完成课程</span>
              <div class="stat-value-row">
                <span class="stat-number">{{ week()?.lessonsCompleted ?? 0 }}</span>
                <span class="stat-unit">节</span>
              </div>
              <p class="stat-hint">扎实掌握每一堂课</p>
            </div>

            <div class="stat-card">
              <span class="stat-label">学习时长</span>
              <div class="stat-value-row">
                <span class="stat-number">{{ week()?.minutes ?? 0 }}</span>
                <span class="stat-unit">分钟</span>
              </div>
              <p class="stat-hint">专注投入的时间</p>
            </div>

            <div class="stat-card">
              <span class="stat-label">任务完成度</span>
              <div class="stat-value-row">
                <span class="stat-number">{{ week()?.taskCompleted ?? 0 }}</span>
                <span class="stat-divider">/</span>
                <span class="stat-total">{{ week()?.taskTotal ?? 0 }}</span>
              </div>
              <div class="progress-bar-track" aria-hidden="true">
                <div class="progress-bar-fill" [style.width.%]="taskPercentage()"></div>
              </div>
              <p class="stat-hint">完成率 {{ taskPercentage() }}%</p>
            </div>

            <div class="stat-card stat-card--streak">
              <span class="stat-label">连续打卡</span>
              <div class="stat-value-row">
                <span class="stat-number stat-number--gold">{{ streak() }}</span>
                <span class="stat-unit">天</span>
              </div>
              <p class="stat-hint">持之以恒，终见花开</p>
            </div>
          </div>
        </section>

        <!-- 今日待办与最近动态分栏 -->
        <div class="two-col-grid">
          <!-- 今日待完成任务 -->
          <section class="nur-card col-card" aria-labelledby="task-title">
            <h2 id="task-title" class="card-title">今日待完成任务</h2>
            @if (todayTasks().length === 0) {
              <div class="empty-state-inner">
                <p class="quiet-note">今日暂无待办任务，享受轻快时光。</p>
              </div>
            } @else {
              <ul class="task-list">
                @for (t of todayTasks(); track t.taskId) {
                  <li class="task-item">
                    <span class="task-bullet"></span>
                    <span class="task-name">{{ t.title }}</span>
                  </li>
                }
              </ul>
            }
          </section>

          <!-- 最近动态 -->
          <section class="nur-card col-card" aria-labelledby="recent-title">
            <h2 id="recent-title" class="card-title">最近动态</h2>
            @if (recent().length === 0) {
              <div class="empty-state-inner">
                <p class="quiet-note">暂无动态记录。</p>
              </div>
            } @else {
              <ul class="recent-list">
                @for (r of recent(); track r.at) {
                  <li class="recent-item">
                    <div class="recent-header">
                      <span class="status-tag" [class.status-tag--record]="r.type === 'LESSON_RECORD'">
                        {{ r.type === 'LESSON_RECORD' ? '课堂记录' : '任务反馈' }}
                      </span>
                      <span class="recent-time">{{ r.at | date:'MM-dd HH:mm' }}</span>
                    </div>
                    <p class="recent-text">{{ r.text }}</p>
                  </li>
                }
              </ul>
            }
          </section>
        </div>

        <nur-notice-center />
      }
    </div>
  `,
  styles: [`
    :host {
      display: block;
      min-height: 100vh;
      background: var(--bg-page);
      color: var(--text-main);
    }
    .growth-shell {
      max-width: 820px;
      margin: 0 auto;
      padding: var(--space-6) var(--space-4) var(--space-8);
      display: flex;
      flex-direction: column;
      gap: var(--space-5);
    }
    .dash-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: var(--space-4);
      flex-wrap: wrap;
    }
    .dash-eyebrow {
      display: inline-flex;
      align-items: center;
      gap: var(--space-2);
      font-size: 0.8rem;
      letter-spacing: 0.25em;
      color: var(--color-gold-text);
      font-family: var(--font-serif);
      margin-bottom: var(--space-1);
    }
    .eyebrow-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: var(--color-gold);
    }
    .dash-greeting {
      margin: 0;
      font-family: var(--font-serif);
      font-size: 1.6rem;
      color: var(--text-title);
      font-weight: 600;
      letter-spacing: 0.06em;
    }
    .dash-subtitle {
      margin: var(--space-1) 0 0;
      color: var(--text-muted);
      font-size: 0.95rem;
    }
    .header-actions {
      display: flex;
      gap: var(--space-2);
      align-items: center;
    }
    .sub-nav {
      display: flex;
      gap: var(--space-3);
      flex-wrap: wrap;
    }
    .sub-nav-item {
      display: inline-flex;
      align-items: center;
      gap: var(--space-2);
      padding: var(--space-2) var(--space-4);
      border: 1px solid var(--line);
      border-radius: var(--radius-pill);
      background: var(--bg-card);
      color: var(--text-main);
      font-size: 0.9rem;
      text-decoration: none;
      transition: all var(--duration-enter) var(--ease-nurture);
    }
    .sub-nav-item:hover {
      border-color: var(--color-primary);
      color: var(--color-primary);
      box-shadow: var(--shadow-subtle);
    }
    .sub-nav-icon {
      font-size: 1rem;
    }
    .quiet-box {
      padding: var(--space-6);
      text-align: center;
    }
    .quiet-note {
      color: var(--text-muted);
      font-size: 0.92rem;
      line-height: 1.8;
      margin: 0;
    }
    .card-header-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-3);
      margin-bottom: var(--space-4);
    }
    .card-title {
      margin: 0;
      font-family: var(--font-serif);
      font-size: 1.25rem;
      color: var(--text-title);
      font-weight: 600;
      letter-spacing: 0.04em;
    }
    .next-lesson-card {
      border-left: 4px solid var(--color-primary);
    }
    .next-lesson-body {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-4);
      flex-wrap: wrap;
    }
    .next-lesson-subject {
      margin: 0 0 var(--space-1);
      font-size: 1.25rem;
      font-weight: 600;
      color: var(--text-title);
    }
    .next-lesson-meta {
      margin: 0;
      color: var(--text-muted);
      font-size: 0.9rem;
    }
    .quiet-empty-card {
      padding: var(--space-5);
    }
    .status-tag {
      display: inline-flex;
      align-items: center;
      padding: 2px 10px;
      border-radius: var(--radius-pill);
      font-size: 0.8rem;
      background: var(--color-primary-soft);
      color: var(--color-primary);
    }
    .status-tag--active {
      background: var(--color-primary-soft);
      color: var(--color-primary);
      font-weight: 600;
    }
    .status-tag--record {
      background: rgba(168, 135, 90, 0.15);
      color: var(--color-gold-text);
    }
    .gold-badge {
      display: inline-flex;
      align-items: center;
      padding: 2px 10px;
      border-radius: var(--radius-pill);
      font-size: 0.8rem;
      background: rgba(168, 135, 90, 0.12);
      color: var(--color-gold-text);
      font-weight: 600;
    }
    .stats-grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: var(--space-3);
    }
    @media (max-width: 768px) {
      .stats-grid {
        grid-template-columns: repeat(2, 1fr);
      }
    }
    .stat-card {
      padding: var(--space-4);
      border: 1px solid var(--line);
      border-radius: var(--radius-control);
      background: var(--bg-page);
      display: flex;
      flex-direction: column;
      gap: var(--space-1);
    }
    .stat-card--streak {
      background: #faf8f3;
      border-color: rgba(168, 135, 90, 0.3);
    }
    .stat-label {
      font-size: 0.82rem;
      color: var(--text-muted);
    }
    .stat-value-row {
      display: flex;
      align-items: baseline;
      gap: 4px;
    }
    .stat-number {
      font-family: var(--font-serif);
      font-size: 1.8rem;
      font-weight: 600;
      color: var(--text-title);
      line-height: 1.1;
    }
    .stat-number--gold {
      color: var(--color-gold-text);
    }
    .stat-unit, .stat-total {
      font-size: 0.85rem;
      color: var(--text-muted);
    }
    .stat-divider {
      color: var(--text-muted);
      font-size: 0.9rem;
    }
    .stat-hint {
      margin: 4px 0 0;
      font-size: 0.78rem;
      color: var(--text-muted);
    }
    .progress-bar-track {
      width: 100%;
      height: 6px;
      border-radius: var(--radius-pill);
      background: rgba(30, 58, 49, 0.1);
      overflow: hidden;
      margin-top: 4px;
    }
    .progress-bar-fill {
      height: 100%;
      border-radius: var(--radius-pill);
      background: var(--color-primary);
      transition: width var(--duration-enter) var(--ease-nurture);
    }
    .two-col-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: var(--space-4);
    }
    @media (max-width: 768px) {
      .two-col-grid {
        grid-template-columns: 1fr;
      }
    }
    .col-card {
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
    }
    .empty-state-inner {
      padding: var(--space-4) 0;
    }
    .task-list {
      list-style: none;
      padding: 0;
      margin: 0;
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
    }
    .task-item {
      display: flex;
      align-items: center;
      gap: var(--space-2);
      padding: var(--space-2) var(--space-3);
      border-radius: var(--radius-control);
      background: var(--bg-page);
      border: 1px solid var(--line);
      font-size: 0.9rem;
    }
    .task-bullet {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--color-primary);
      flex-shrink: 0;
    }
    .task-name {
      color: var(--text-main);
    }
    .recent-list {
      list-style: none;
      padding: 0;
      margin: 0;
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
    }
    .recent-item {
      padding: var(--space-3);
      border-radius: var(--radius-control);
      background: var(--bg-page);
      border: 1px solid var(--line);
      display: flex;
      flex-direction: column;
      gap: var(--space-1);
    }
    .recent-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-2);
    }
    .recent-time {
      font-size: 0.8rem;
      color: var(--text-muted);
    }
    .recent-text {
      margin: 0;
      font-size: 0.88rem;
      color: var(--text-main);
      line-height: 1.6;
    }

    /* ============ 桌面宽屏（仅 ≥1024px 生效，手机端零改动）：统一 1560px 容器 ============ */
    @media (min-width: 1024px) {
      .growth-shell {
        width: calc(100% - 80px);
        max-width: 1560px;
      }
    }
  `],
})
export class StudentGrowthComponent implements OnInit {
  private readonly http = inject(HttpClient);
  readonly auth = inject(AuthService);
  readonly loading = signal(true);
  readonly nextLesson = signal<NextLesson | null>(null);
  readonly todayTasks = signal<TodayTask[]>([]);
  readonly week = signal<WeekStats | null>(null);
  readonly streak = signal(0);
  readonly recent = signal<RecentItem[]>([]);

  taskPercentage(): number {
    const total = this.week()?.taskTotal ?? 0;
    const completed = this.week()?.taskCompleted ?? 0;
    if (total <= 0) return 0;
    return Math.min(100, Math.round((completed / total) * 100));
  }

  ngOnInit(): void {
    this.http.get<any>(API.learning.growth).subscribe({
      next: (d) => {
        this.nextLesson.set(d['nextLesson'] as NextLesson | null);
        this.todayTasks.set((d['todayTasks'] as TodayTask[]) ?? []);
        this.week.set(d['week'] as WeekStats);
        this.streak.set((d['streakDays'] as number) ?? 0);
        this.recent.set((d['recent'] as RecentItem[]) ?? []);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
