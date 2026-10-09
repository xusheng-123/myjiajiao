import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { API } from '../../core/api/endpoints';
import { NoticeCenterComponent } from '../../shared/components/notice-center.component';

interface NextLesson { bookingId: string; subject: string; teacherName: string; startAt: string | null; status: string; }
interface TodayTask { taskId: string; title: string; status: string; }
interface WeekStats { lessonsCompleted: number; minutes: number; taskCompleted: number; taskTotal: number; }
interface RecentItem { type: string; text: string; at: string; }

@Component({
  selector: 'nur-student-growth',
  standalone: true,
  imports: [DatePipe, RouterLink, NoticeCenterComponent],
  styleUrl: './student-dashboard.component.css',
  template: `
<div class="dashboard-shell">
  <header class="dash-header">
    <div>
      <p class="dash-greeting nur-serif">学习成长 · {{ auth.displayName() }}</p>
      <p class="dash-subtitle">每一步都算数。</p>
    </div>
    <div style="display:flex;gap:8px">
      <a routerLink="/student/dashboard" style="color:var(--text-muted);text-decoration:none;font-size:.9rem;line-height:var(--touch-target)">← 返回</a>
      <button type="button" class="btn-ghost-sm" (click)="auth.logout()" aria-label="退出">退出</button>
    </div>
  </header>

  @if (loading()) { <p class="quiet-note" role="status">正在加载学习成长数据…</p> }

  @if (!loading() && nextLesson(); as lesson) {
    <section class="nur-card nur-enter" aria-labelledby="next-title">
      <h2 id="next-title" class="nur-serif">下一节课程</h2>
      <p style="font-size:1.3rem;margin:.4rem 0">{{ lesson.subject }} · {{ lesson.teacherName }}</p>
      <p class="quiet-note">{{ lesson.startAt ? (lesson.startAt | date:'M月d日 HH:mm') : '待定' }}</p>
      <a [routerLink]="['/student','dashboard']" class="btn-ghost-sm" style="margin-top:8px;display:inline-block">进入课程</a>
    </section>
  } @else if (!loading()) {
    <section class="nur-card"><p class="quiet-note">最近没有排定的课程。</p></section>
  }

  @if (todayTasks().length > 0) {
    <section class="nur-card" aria-labelledby="task-title">
      <h2 id="task-title" class="nur-serif">今日待完成任务</h2>
      @for (t of todayTasks(); track t.taskId) {
        <p style="margin:6px 0">· {{ t.title }}</p>
      }
    </section>
  }

  <section class="nur-card" aria-labelledby="week-title">
    <h2 id="week-title" class="nur-serif">本周学习</h2>
    <div style="display:flex;gap:24px;margin:12px 0">
      <div><p style="font-size:1.6rem;font-weight:700">{{ week()?.lessonsCompleted ?? 0 }}</p><p class="quiet-note">完成课程</p></div>
      <div><p style="font-size:1.6rem;font-weight:700">{{ week()?.minutes ?? 0 }}min</p><p class="quiet-note">学习时长</p></div>
      <div><p style="font-size:1.6rem;font-weight:700">{{ week()?.taskCompleted ?? 0 }}/{{ week()?.taskTotal ?? 0 }}</p><p class="quiet-note">任务完成</p></div>
      <div><p style="font-size:1.6rem;font-weight:700">{{ streak() }}</p><p class="quiet-note">连续学习天数</p></div>
    </div>
  </section>

  @if (recent().length > 0) {
    <section class="nur-card" aria-labelledby="recent-title">
      <h2 id="recent-title" class="nur-serif">最近动态</h2>
      @for (r of recent(); track r.at) {
        <p style="margin:8px 0;font-size:.92rem">
          <span class="status-pill">{{ r.type === 'LESSON_RECORD' ? '课堂记录' : '任务反馈' }}</span>
          {{ r.text }}
        </p>
      }
    </section>
  }

  <nur-notice-center />
</div>
`,
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
