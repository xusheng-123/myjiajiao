import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../core/auth/auth.service';
import { API } from '../../core/api/endpoints';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { NoticeCenterComponent } from '../../shared/components/notice-center.component';
import { LearningAttachmentsComponent } from '../../shared/components/learning-attachments.component';

interface QuestionView {
  questionId: string; subject: string; title: string; content: string;
  status: string; answer: string | null; createdAt: string;
}

@Component({
  selector: 'nur-student-questions',
  standalone: true,
  imports: [FormsModule, RouterLink, NoticeCenterComponent, LearningAttachmentsComponent],
  template: `
<div class="dashboard-shell">
  <header class="dash-header">
    <div>
      <p class="dash-greeting nur-serif">课后答疑</p>
      <p class="dash-subtitle">有问题，随时问。</p>
    </div>
    <a routerLink="/student/dashboard" style="color:var(--text-muted);text-decoration:none;font-size:.9rem;line-height:var(--touch-target)">← 返回</a>
  </header>

  <section class="nur-card" aria-labelledby="ask-title">
    <h2 id="ask-title" class="nur-serif">提出问题</h2>
    <label for="q-teacher">授课教师</label>
    <select id="q-teacher" [ngModel]="teacherId()" (ngModelChange)="teacherId.set($event)" style="width:100%;min-height:44px;margin:4px 0 12px"><option value="">请选择教师</option>@for(t of teachers();track t.teacherId){<option [value]="t.teacherId">{{t.displayName}}</option>}</select>
    @if(!teachers().length){<p>有已确认或已完成课程后，可以向授课教师提问。</p>}
    <label for="q-subject">科目</label>
    <input id="q-subject" [(ngModel)]="subject" placeholder="如：数学"
      style="width:100%;padding:8px 12px;margin:4px 0 12px;border:1px solid var(--border-subtle);border-radius:8px;background:var(--bg-card);color:var(--text-main)" />
    <label for="q-title">问题标题</label>
    <input id="q-title" [(ngModel)]="title" placeholder="如：二次函数顶点公式怎么推"
      style="width:100%;padding:8px 12px;margin:4px 0 12px;border:1px solid var(--border-subtle);border-radius:8px;background:var(--bg-card);color:var(--text-main)" />
    <label for="q-content">详细描述</label>
    <textarea id="q-content" [(ngModel)]="content" rows="4" placeholder="详细描述你的问题…"
      style="width:100%;padding:8px 12px;margin:4px 0 12px;border:1px solid var(--border-subtle);border-radius:8px;background:var(--bg-card);color:var(--text-main)"></textarea>
    <button type="button" class="btn-primary-sm" [disabled]="!title() || !content() || !teacherId()" (click)="ask()">提交问题</button>
    @if (msg(); as m) { <p class="quiet-note" style="margin-top:8px">{{ m }}</p> }
  </section>

  <section class="nur-card" aria-labelledby="myq-title">
    <h2 id="myq-title" class="nur-serif">我的答疑</h2>
    @if (questions().length === 0) {
      <p class="quiet-note">还没有提问记录。</p>
    } @else {
      @for (q of questions(); track q.questionId) {
        <div style="margin:12px 0;padding:12px;border-left:3px solid var(--color-secondary);border-radius:0 8px 8px 0;background:var(--color-secondary-bg)">
          <p style="font-weight:600;margin:0 0 4px">{{ q.title }} <span class="status-pill">{{ q.status === 'OPEN' ? '待回答' : q.status === 'ANSWERED' ? '已回答' : '已关闭' }}</span></p>
          <p class="quiet-note">{{ q.content }}</p>
          <nur-learning-attachments [questionId]="q.questionId" [status]="q.status" />
          @if (q.answer) { <p style="margin:8px 0 0;padding:8px;background:var(--color-secondary-bg);border-radius:6px">{{ q.answer }}</p> }
        </div>
      }
    }
  </section>

  <nur-notice-center />
</div>
`,
})
export class StudentQuestionsComponent implements OnInit {
  private readonly http = inject(HttpClient);
  readonly auth = inject(AuthService);
  readonly teacherId = signal('');
  readonly subject = signal('');
  readonly title = signal('');
  readonly content = signal('');
  readonly msg = signal('');
  readonly questions = signal<QuestionView[]>([]);
  readonly teachers = signal<{teacherId:string;displayName:string}[]>([]);

  ngOnInit(): void { this.load();this.http.get<{teacherId:string;displayName:string}[]>('/api/v1/learning/files/teachers').subscribe({next:r=>this.teachers.set(r),error:()=>this.msg.set('教师列表暂时不可用。')}); }

  load(): void {
    this.http.get<QuestionView[]>(API.learning.questions).subscribe({
      next: (d) => this.questions.set(d ?? []),
    });
  }

  ask(): void {
    this.http.post(API.learning.questions, {
      teacherId: this.teacherId(), subject: this.subject() || '综合',
      title: this.title(), content: this.content(),
    }).subscribe({
      next: () => { this.msg.set('问题已提交，等待老师回答。'); this.load(); },
      error: (e) => this.msg.set(e?.error?.detail ?? '提交失败，请稍后重试。'),
    });
  }
}
