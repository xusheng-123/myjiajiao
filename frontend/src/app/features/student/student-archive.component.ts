import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { AuthService } from '../../core/auth/auth.service';
import { API } from '../../core/api/endpoints';
import { NoticeCenterComponent } from '../../shared/components/notice-center.component';

interface ArchiveProfile {
  goal: string;
  stage: string;
  targetSubjects: string;
}

interface ArchiveStats {
  completedLessons: number;
  totalLessons: number;
  taskCompleted: number;
  taskTotal: number;
  lessonRecords: number;
}

interface LessonRecordView {
  recordId: string;
  subject: string;
  knowledgePoints: string;
  performance: string;
  problems: string;
  nextSuggestion: string;
  createdAt: string;
}

interface HistoryItem {
  bookingId: string;
  subject: string;
  status: string;
  teacherName: string;
}

@Component({
  selector: 'nur-student-archive',
  standalone: true,
  imports: [RouterLink, DatePipe, NoticeCenterComponent],
  template: `
    <div class="archive-shell">
      <header class="dash-header">
        <div>
          <div class="dash-eyebrow">
            <span class="eyebrow-dot"></span>
            <span>ACADEMIC ARCHIVE</span>
          </div>
          <h1 class="dash-greeting">学习档案</h1>
          <p class="dash-subtitle">看得见的每一步，沉淀学业底色。</p>
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

      @if (loading()) {
        <div class="quiet-box" role="status">
          <p class="quiet-note">正在加载你的学情成长档案…</p>
        </div>
      } @else {
        <!-- 学习目标与阶段画像 -->
        @if (profile(); as p) {
          <section class="nur-card nur-enter" aria-labelledby="goal-title">
            <div class="card-header-row">
              <h2 id="goal-title" class="card-title">学习目标与学段</h2>
              <a routerLink="/student/dashboard" class="link-edit-hint">在工作台可调整画像 →</a>
            </div>
            <div class="profile-content-box">
              <div class="goal-highlight">
                <span class="goal-label">当前核心目标</span>
                <p class="goal-text">{{ p.goal || '尚未设定长期目标，可在工作台设置目标画像。' }}</p>
              </div>
              <div class="profile-tags-row">
                <div class="profile-tag-item">
                  <span class="tag-label">当前阶段</span>
                  <span class="tag-val">{{ p.stage || '未指定' }}</span>
                </div>
                <div class="profile-tag-item">
                  <span class="tag-label">重点提升科目</span>
                  <span class="tag-val">{{ p.targetSubjects || '未指定' }}</span>
                </div>
              </div>
            </div>
          </section>
        }

        <!-- 累计学习数据盘 -->
        <section class="nur-card nur-enter" aria-labelledby="stats-title">
          <div class="card-header-row">
            <h2 id="stats-title" class="card-title">学业积累总览</h2>
            <span class="stat-badge">全周期留痕</span>
          </div>
          <div class="stats-row">
            <div class="stat-cell">
              <span class="stat-label">已完成课程</span>
              <div class="stat-num-box">
                <span class="stat-num">{{ stats()?.completedLessons ?? 0 }}</span>
                <span class="stat-den">/ {{ stats()?.totalLessons ?? 0 }} 节</span>
              </div>
              <p class="stat-hint">履约完成率 {{ lessonRate() }}%</p>
            </div>
            <div class="stat-cell">
              <span class="stat-label">完成任务数</span>
              <div class="stat-num-box">
                <span class="stat-num">{{ stats()?.taskCompleted ?? 0 }}</span>
                <span class="stat-den">/ {{ stats()?.taskTotal ?? 0 }} 项</span>
              </div>
              <p class="stat-hint">任务完成率 {{ taskRate() }}%</p>
            </div>
            <div class="stat-cell">
              <span class="stat-label">导师课堂记录</span>
              <div class="stat-num-box">
                <span class="stat-num stat-num--gold">{{ stats()?.lessonRecords ?? 0 }}</span>
                <span class="stat-den">篇</span>
              </div>
              <p class="stat-hint">教师结构化学情评语</p>
            </div>
          </div>
        </section>

        <!-- 教师课堂记录（时间线表达） -->
        <section class="nur-card timeline-section nur-enter" aria-labelledby="record-title">
          <div class="card-header-row">
            <h2 id="record-title" class="card-title">教师课堂记录时间线</h2>
            <span class="quiet-count">共 {{ records().length }} 条记录</span>
          </div>

          @if (records().length === 0) {
            <div class="empty-timeline">
              <p class="quiet-note">暂无教师课堂记录。上完课后导师会为你撰写针对性教学小结。</p>
            </div>
          } @else {
            <div class="timeline-container">
              @for (r of records(); track r.recordId) {
                <div class="timeline-item">
                  <div class="timeline-marker" aria-hidden="true">
                    <span class="timeline-dot"></span>
                    <span class="timeline-stem"></span>
                  </div>
                  <div class="timeline-card">
                    <div class="timeline-card-header">
                      <h3 class="record-subject">{{ r.subject || '课程小结' }}</h3>
                      @if (r.createdAt) {
                        <span class="record-date">{{ r.createdAt | date:'yyyy年M月d日' }}</span>
                      }
                    </div>

                    <div class="record-grid">
                      @if (r.knowledgePoints) {
                        <div class="record-field">
                          <span class="field-title">重点知识点</span>
                          <p class="field-desc">{{ r.knowledgePoints }}</p>
                        </div>
                      }
                      @if (r.performance) {
                        <div class="record-field">
                          <span class="field-title">课堂表现与亮点</span>
                          <p class="field-desc">{{ r.performance }}</p>
                        </div>
                      }
                      @if (r.problems) {
                        <div class="record-field">
                          <span class="field-title">存在的困惑点</span>
                          <p class="field-desc">{{ r.problems }}</p>
                        </div>
                      }
                      @if (r.nextSuggestion) {
                        <div class="record-field record-field--suggestion">
                          <span class="field-title">下一阶段建议</span>
                          <p class="field-desc">{{ r.nextSuggestion }}</p>
                        </div>
                      }
                    </div>
                  </div>
                </div>
              }
            </div>
          }
        </section>

        <!-- 教学历史列表 -->
        @if (history().length > 0) {
          <section class="nur-card nur-enter" aria-labelledby="history-title">
            <div class="card-header-row">
              <h2 id="history-title" class="card-title">历史授课师生谱系</h2>
              <span class="quiet-count">共 {{ history().length }} 门课程</span>
            </div>
            <ul class="history-list">
              @for (h of history(); track h.bookingId) {
                <li class="history-item">
                  <div class="history-main">
                    <span class="history-subject">{{ h.subject }}</span>
                    <span class="history-teacher">授课导师：{{ h.teacherName }}</span>
                  </div>
                  <span class="status-pill" [class.status-pill--completed]="h.status === 'COMPLETED'">
                    {{ h.status === 'COMPLETED' ? '✔ 已完成' : h.status }}
                  </span>
                </li>
              }
            </ul>
          </section>
        }

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
    .archive-shell {
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
    .link-edit-hint {
      color: var(--color-primary);
      font-size: 0.85rem;
      text-decoration: none;
    }
    .link-edit-hint:hover {
      text-decoration: underline;
    }
    .profile-content-box {
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
    }
    .goal-highlight {
      padding: var(--space-4);
      background: var(--bg-page);
      border-radius: var(--radius-control);
      border-left: 3px solid var(--color-primary);
    }
    .goal-label {
      font-size: 0.8rem;
      color: var(--text-muted);
      display: block;
      margin-bottom: 4px;
    }
    .goal-text {
      margin: 0;
      font-size: 1.05rem;
      color: var(--text-title);
      line-height: 1.6;
      font-weight: 500;
    }
    .profile-tags-row {
      display: flex;
      gap: var(--space-3);
      flex-wrap: wrap;
    }
    .profile-tag-item {
      padding: var(--space-2) var(--space-4);
      border-radius: var(--radius-pill);
      background: var(--bg-page);
      border: 1px solid var(--line);
      display: flex;
      align-items: center;
      gap: var(--space-2);
      font-size: 0.88rem;
    }
    .tag-label {
      color: var(--text-muted);
    }
    .tag-val {
      color: var(--text-title);
      font-weight: 500;
    }
    .stat-badge {
      padding: 2px 10px;
      border-radius: var(--radius-pill);
      font-size: 0.8rem;
      background: var(--color-primary-soft);
      color: var(--color-primary);
    }
    .stats-row {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: var(--space-4);
    }
    @media (max-width: 768px) {
      .stats-row {
        grid-template-columns: 1fr;
      }
    }
    .stat-cell {
      padding: var(--space-4);
      border-radius: var(--radius-control);
      background: var(--bg-page);
      border: 1px solid var(--line);
      display: flex;
      flex-direction: column;
      gap: var(--space-1);
    }
    .stat-label {
      font-size: 0.85rem;
      color: var(--text-muted);
    }
    .stat-num-box {
      display: flex;
      align-items: baseline;
      gap: 4px;
    }
    .stat-num {
      font-family: var(--font-serif);
      font-size: 1.8rem;
      font-weight: 600;
      color: var(--text-title);
    }
    .stat-num--gold {
      color: var(--color-gold-text);
    }
    .stat-den {
      font-size: 0.85rem;
      color: var(--text-muted);
    }
    .stat-hint {
      margin: 2px 0 0;
      font-size: 0.78rem;
      color: var(--text-muted);
    }
    .timeline-section {
      position: relative;
    }
    .quiet-count {
      font-size: 0.85rem;
      color: var(--text-muted);
    }
    .timeline-container {
      display: flex;
      flex-direction: column;
      gap: var(--space-4);
      padding-left: var(--space-2);
    }
    .timeline-item {
      display: flex;
      gap: var(--space-4);
      position: relative;
    }
    .timeline-marker {
      display: flex;
      flex-direction: column;
      align-items: center;
      width: 16px;
      flex-shrink: 0;
    }
    .timeline-dot {
      width: 12px;
      height: 12px;
      border-radius: 50%;
      border: 2.5px solid var(--color-primary);
      background: var(--bg-card);
      flex-shrink: 0;
      margin-top: 6px;
    }
    .timeline-stem {
      flex: 1;
      width: 2px;
      background: var(--line);
      margin-top: 4px;
    }
    .timeline-card {
      flex: 1;
      padding: var(--space-4);
      border-radius: var(--radius-control);
      background: var(--bg-page);
      border: 1px solid var(--line);
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
    }
    .timeline-card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-2);
    }
    .record-subject {
      margin: 0;
      font-size: 1.05rem;
      font-weight: 600;
      color: var(--text-title);
    }
    .record-date {
      font-size: 0.82rem;
      color: var(--text-muted);
    }
    .record-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: var(--space-3);
    }
    @media (max-width: 768px) {
      .record-grid {
        grid-template-columns: 1fr;
      }
    }
    .record-field {
      padding: var(--space-3);
      border-radius: 8px;
      background: var(--bg-card);
      border: 1px solid var(--line);
    }
    .record-field--suggestion {
      background: #faf8f3;
      border-color: rgba(168, 135, 90, 0.25);
    }
    .field-title {
      font-size: 0.8rem;
      color: var(--text-muted);
      display: block;
      margin-bottom: 2px;
    }
    .field-desc {
      margin: 0;
      font-size: 0.9rem;
      color: var(--text-main);
      line-height: 1.6;
    }
    .history-list {
      list-style: none;
      padding: 0;
      margin: 0;
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
    }
    .history-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-3);
      padding: var(--space-3) var(--space-4);
      border-radius: var(--radius-control);
      background: var(--bg-page);
      border: 1px solid var(--line);
    }
    .history-main {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .history-subject {
      font-weight: 600;
      font-size: 0.95rem;
      color: var(--text-title);
    }
    .history-teacher {
      font-size: 0.85rem;
      color: var(--text-muted);
    }
    .status-pill {
      padding: 2px 10px;
      border-radius: var(--radius-pill);
      font-size: 0.82rem;
      background: var(--bg-card);
      border: 1px solid var(--line);
      color: var(--text-muted);
    }
    .status-pill--completed {
      background: var(--color-primary-soft);
      border-color: transparent;
      color: var(--color-primary);
      font-weight: 500;
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
    .empty-timeline {
      padding: var(--space-5) 0;
      text-align: center;
    }
  `],
})
export class StudentArchiveComponent implements OnInit {
  private readonly http = inject(HttpClient);
  readonly auth = inject(AuthService);
  readonly loading = signal(true);
  readonly profile = signal<ArchiveProfile | null>(null);
  readonly stats = signal<ArchiveStats | null>(null);
  readonly records = signal<LessonRecordView[]>([]);
  readonly history = signal<HistoryItem[]>([]);

  lessonRate(): number {
    const total = this.stats()?.totalLessons ?? 0;
    const completed = this.stats()?.completedLessons ?? 0;
    if (total <= 0) return 0;
    return Math.min(100, Math.round((completed / total) * 100));
  }

  taskRate(): number {
    const total = this.stats()?.taskTotal ?? 0;
    const completed = this.stats()?.taskCompleted ?? 0;
    if (total <= 0) return 0;
    return Math.min(100, Math.round((completed / total) * 100));
  }

  ngOnInit(): void {
    this.http.get<any>(API.learning.archive).subscribe({
      next: (d) => {
        this.profile.set(d['profile'] as ArchiveProfile);
        this.stats.set(d['stats'] as ArchiveStats);
        this.records.set((d['lessonRecords'] as LessonRecordView[]) ?? []);
        this.history.set((d['history'] as HistoryItem[]) ?? []);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
