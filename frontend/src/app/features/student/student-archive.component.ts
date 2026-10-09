import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { API } from '../../core/api/endpoints';
import { NoticeCenterComponent } from '../../shared/components/notice-center.component';

interface ArchiveProfile { goal: string; stage: string; targetSubjects: string; }
interface ArchiveStats { completedLessons: number; totalLessons: number; taskCompleted: number; taskTotal: number; lessonRecords: number; }
interface LessonRecordView { recordId: string; subject: string; knowledgePoints: string; performance: string; problems: string; nextSuggestion: string; createdAt: string; }
interface HistoryItem { bookingId: string; subject: string; status: string; teacherName: string; }

@Component({
  selector: 'nur-student-archive',
  standalone: true,
  imports: [NoticeCenterComponent],
  styleUrl: './student-dashboard.component.css',
  template: `
<div class="dashboard-shell">
  <header class="dash-header">
    <div>
      <p class="dash-greeting nur-serif">学习档案</p>
      <p class="dash-subtitle">看得见的每一步。</p>
    </div>
    <div style="display:flex;gap:8px">
      <a routerLink="/student/dashboard" style="color:var(--text-muted);text-decoration:none;font-size:.9rem;line-height:var(--touch-target)">← 返回</a>
      <button type="button" class="btn-ghost-sm" (click)="auth.logout()" aria-label="退出">退出</button>
    </div>
  </header>

  @if (profile(); as p) {
    <section class="nur-card" aria-labelledby="goal-title">
      <h2 id="goal-title" class="nur-serif">学习目标</h2>
      <p style="margin:8px 0">{{ p.goal || '还没有设定学习目标。' }}</p>
      @if (p.stage) { <p class="quiet-note">当前阶段：{{ p.stage }}</p> }
      @if (p.targetSubjects) { <p class="quiet-note">目标科目：{{ p.targetSubjects }}</p> }
    </section>
  }

  <section class="nur-card" aria-labelledby="stats-title">
    <h2 id="stats-title" class="nur-serif">学习统计</h2>
    <div style="display:flex;gap:24px;margin:12px 0">
      <div><p style="font-size:1.5rem;font-weight:700">{{ stats()?.completedLessons ?? 0 }}/{{ stats()?.totalLessons ?? 0 }}</p><p class="quiet-note">已完成课程</p></div>
      <div><p style="font-size:1.5rem;font-weight:700">{{ stats()?.taskCompleted ?? 0 }}/{{ stats()?.taskTotal ?? 0 }}</p><p class="quiet-note">完成任务</p></div>
      <div><p style="font-size:1.5rem;font-weight:700">{{ stats()?.lessonRecords ?? 0 }}</p><p class="quiet-note">课堂记录</p></div>
    </div>
  </section>

  @if (records().length > 0) {
    <section class="nur-card" aria-labelledby="record-title">
      <h2 id="record-title" class="nur-serif">教师课堂记录</h2>
      @for (r of records(); track r.recordId) {
        <div style="margin:12px 0;padding:12px;border-left:3px solid var(--color-secondary);border-radius:0 8px 8px 0;background:var(--color-secondary-bg)">
          <p style="font-weight:600;margin:0 0 4px">{{ r.subject || '课程' }}</p>
          @if (r.knowledgePoints) { <p class="quiet-note">知识点：{{ r.knowledgePoints }}</p> }
          @if (r.performance) { <p class="quiet-note">课堂表现：{{ r.performance }}</p> }
          @if (r.problems) { <p class="quiet-note">存在问题：{{ r.problems }}</p> }
          @if (r.nextSuggestion) { <p class="quiet-note">下节建议：{{ r.nextSuggestion }}</p> }
        </div>
      }
    </section>
  }

  @if (history().length > 0) {
    <section class="nur-card" aria-labelledby="history-title">
      <h2 id="history-title" class="nur-serif">教学历史</h2>
      @for (h of history(); track h.bookingId) {
        <p style="margin:8px 0">
          {{ h.subject }} · {{ h.teacherName }} ·
          <span class="status-pill">{{ h.status === 'COMPLETED' ? '已完成' : h.status }}</span>
        </p>
      }
    </section>
  }

  <nur-notice-center />
</div>
`,
})
export class StudentArchiveComponent implements OnInit {
  private readonly http = inject(HttpClient);
  readonly auth = inject(AuthService);
  readonly profile = signal<ArchiveProfile | null>(null);
  readonly stats = signal<ArchiveStats | null>(null);
  readonly records = signal<LessonRecordView[]>([]);
  readonly history = signal<HistoryItem[]>([]);

  ngOnInit(): void {
    this.http.get<any>(API.learning.archive).subscribe({
      next: (d) => {
        this.profile.set(d['profile'] as ArchiveProfile);
        this.stats.set(d['stats'] as ArchiveStats);
        this.records.set((d['lessonRecords'] as LessonRecordView[]) ?? []);
        this.history.set((d['history'] as HistoryItem[]) ?? []);
      },
    });
  }
}
