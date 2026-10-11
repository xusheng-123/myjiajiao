import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../core/auth/auth.service';
import { API } from '../../core/api/endpoints';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { NoticeCenterComponent } from '../../shared/components/notice-center.component';
import { LearningAttachmentsComponent } from '../../shared/components/learning-attachments.component';

interface QuestionView {
  questionId: string;
  subject: string;
  title: string;
  content: string;
  status: string;
  answer: string | null;
  createdAt: string;
}

const DRAFT_KEY = 'nur_student_question_draft';

@Component({
  selector: 'nur-student-questions',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, NoticeCenterComponent, LearningAttachmentsComponent],
  template: `
    <div class="questions-shell">
      <header class="dash-header">
        <div>
          <div class="dash-eyebrow">
            <span class="eyebrow-dot"></span>
            <span>Q&A CORNER</span>
          </div>
          <h1 class="dash-greeting">课后答疑</h1>
          <p class="dash-subtitle">疑难随时问，授课导师像及时雨般耐心回应。</p>
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

      <!-- 提问与历史主副分栏布局 -->
      <div class="content-grid">
        <!-- 左侧：提出新问题 -->
        <section class="nur-card ask-card" aria-labelledby="ask-title">
          <div class="card-head">
            <h2 id="ask-title" class="card-title">向导师提问</h2>
            @if (hasDraft()) {
              <span class="draft-indicator" title="内容已自动保存在本地，刷新不丢失">
                ✓ 草稿已自动暂存
              </span>
            }
          </div>

          <form (ngSubmit)="ask()" class="ask-form">
            <div class="form-group">
              <label for="q-teacher" class="form-label">授课导师 <span class="required">*</span></label>
              <select
                id="q-teacher"
                class="form-control"
                [ngModel]="teacherId()"
                (ngModelChange)="onTeacherChange($event)"
                name="teacherId"
              >
                <option value="">请选择授课教师…</option>
                @for (t of teachers(); track t.teacherId) {
                  <option [value]="t.teacherId">{{ t.displayName }}</option>
                }
              </select>
              @if (!teachers().length) {
                <p class="form-hint">有已排定或已完成的课程后，可直接向对应的授课教师提问。</p>
              }
            </div>

            <div class="form-group">
              <label for="q-subject" class="form-label">科目</label>
              <input
                id="q-subject"
                type="text"
                class="form-control"
                [ngModel]="subject()"
                (ngModelChange)="onSubjectChange($event)"
                name="subject"
                placeholder="例如：高中数学、物理"
              />
            </div>

            <div class="form-group">
              <label for="q-title" class="form-label">问题简述 <span class="required">*</span></label>
              <input
                id="q-title"
                type="text"
                class="form-control"
                [ngModel]="title()"
                (ngModelChange)="onTitleChange($event)"
                name="title"
                placeholder="简明描述关窍，如：这道二次函数极值步骤卡住了"
              />
            </div>

            <div class="form-group">
              <label for="q-content" class="form-label">详细描述 <span class="required">*</span></label>
              <textarea
                id="q-content"
                class="form-control"
                rows="5"
                [ngModel]="content()"
                (ngModelChange)="onContentChange($event)"
                name="content"
                placeholder="把思考过程和卡住的地方写下来，导师能更针对性解答…"
              ></textarea>
            </div>

            <div class="form-actions">
              <button
                type="submit"
                class="nur-btn nur-btn--primary"
                [disabled]="!title().trim() || !content().trim() || !teacherId() || submitting()"
              >
                {{ submitting() ? '提交中…' : '提交问题' }}
              </button>
              @if (hasDraft()) {
                <button type="button" class="nur-btn nur-btn--ghost nur-btn--sm" (click)="clearDraft()">
                  清空草稿
                </button>
              }
            </div>

            @if (msg(); as m) {
              <div class="feedback-msg" [class.feedback-msg--success]="m.type === 'success'" [class.feedback-msg--error]="m.type === 'error'">
                {{ m.text }}
              </div>
            }
          </form>
        </section>

        <!-- 右侧：我的答疑历史与关闭操作 -->
        <section class="nur-card history-card" aria-labelledby="history-title">
          <div class="card-head">
            <h2 id="history-title" class="card-title">我的答疑记录</h2>
            <span class="count-badge">共 {{ questions().length }} 条</span>
          </div>

          <!-- 状态筛选器 -->
          <div class="status-filter-row" role="tablist" aria-label="答疑状态筛选">
            <button
              type="button"
              class="filter-tab"
              [class.filter-tab--active]="activeFilter() === 'ALL'"
              (click)="activeFilter.set('ALL')"
            >
              全部 ({{ questions().length }})
            </button>
            <button
              type="button"
              class="filter-tab"
              [class.filter-tab--active]="activeFilter() === 'OPEN'"
              (click)="activeFilter.set('OPEN')"
            >
              待回答 ({{ countByStatus('OPEN') }})
            </button>
            <button
              type="button"
              class="filter-tab"
              [class.filter-tab--active]="activeFilter() === 'ANSWERED'"
              (click)="activeFilter.set('ANSWERED')"
            >
              已回答 ({{ countByStatus('ANSWERED') }})
            </button>
            <button
              type="button"
              class="filter-tab"
              [class.filter-tab--active]="activeFilter() === 'CLOSED'"
              (click)="activeFilter.set('CLOSED')"
            >
              已关闭 ({{ countByStatus('CLOSED') }})
            </button>
          </div>

          @if (filteredQuestions().length === 0) {
            <div class="empty-state">
              <p class="quiet-note">
                {{ activeFilter() === 'ALL' ? '还没有提问记录。遇到卡点随时在左侧向导师提问。' : '当前筛选分类下暂无问题。' }}
              </p>
            </div>
          } @else {
            <div class="question-list">
              @for (q of filteredQuestions(); track q.questionId) {
                <article class="question-card">
                  <div class="question-top">
                    <div class="title-meta">
                      <span class="subject-chip">{{ q.subject || '综合' }}</span>
                      <h3 class="q-title">{{ q.title }}</h3>
                    </div>
                    <div class="status-wrapper">
                      <span
                        class="status-pill"
                        [class.status-pill--open]="q.status === 'OPEN'"
                        [class.status-pill--answered]="q.status === 'ANSWERED'"
                        [class.status-pill--closed]="q.status === 'CLOSED'"
                      >
                        {{ q.status === 'OPEN' ? '◷ 待导师回答' : q.status === 'ANSWERED' ? '✔ 已获解答' : '✕ 已关闭' }}
                      </span>
                    </div>
                  </div>

                  <p class="q-content">{{ q.content }}</p>

                  <div class="attachments-wrap">
                    <nur-learning-attachments [questionId]="q.questionId" [status]="q.status" />
                  </div>

                  @if (q.answer) {
                    <div class="teacher-reply-box">
                      <div class="reply-header">
                        <span class="reply-badge">导师答复</span>
                      </div>
                      <p class="reply-body">{{ q.answer }}</p>
                    </div>
                  }

                  <div class="question-footer">
                    <span class="q-date">{{ q.createdAt | date:'yyyy-MM-dd HH:mm' }}</span>

                    <!-- B3 缺口补齐：关闭答疑按钮 -->
                    @if (q.status !== 'CLOSED') {
                      <button
                        type="button"
                        class="nur-btn nur-btn--ghost nur-btn--sm btn-close-q"
                        [disabled]="closingId() === q.questionId"
                        (click)="closeQuestion(q)"
                        title="问题解决后可关闭答疑"
                      >
                        {{ closingId() === q.questionId ? '关闭中…' : '关闭此答疑' }}
                      </button>
                    }
                  </div>
                </article>
              }
            </div>
          }
        </section>
      </div>

      <nur-notice-center />
    </div>
  `,
  styles: [`
    :host {
      display: block;
      min-height: 100vh;
      background: var(--bg-page);
      color: var(--text-main);
    }
    .questions-shell {
      max-width: 980px;
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
    .content-grid {
      display: grid;
      grid-template-columns: 360px 1fr;
      gap: var(--space-5);
      align-items: start;
    }
    @media (max-width: 860px) {
      .content-grid {
        grid-template-columns: 1fr;
      }
    }
    .card-head {
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
    .draft-indicator {
      font-size: 0.78rem;
      color: var(--color-primary);
      background: var(--color-primary-soft);
      padding: 2px 8px;
      border-radius: var(--radius-pill);
    }
    .ask-form {
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
    }
    .form-group {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .form-label {
      font-size: 0.88rem;
      font-weight: 500;
      color: var(--text-main);
    }
    .required {
      color: var(--color-gold-text);
    }
    .form-control {
      width: 100%;
      padding: var(--space-2) var(--space-3);
      border: 1px solid var(--line);
      border-radius: var(--radius-control);
      background: var(--bg-card);
      color: var(--text-main);
      font-family: inherit;
      font-size: 0.92rem;
      box-sizing: border-box;
      transition: border-color var(--duration-enter) var(--ease-nurture);
    }
    .form-control:focus {
      outline: none;
      border-color: var(--color-primary);
      box-shadow: 0 0 0 2px var(--color-primary-soft);
    }
    select.form-control {
      min-height: 42px;
    }
    .form-hint {
      margin: 4px 0 0;
      font-size: 0.8rem;
      color: var(--text-muted);
    }
    .form-actions {
      display: flex;
      align-items: center;
      gap: var(--space-3);
      margin-top: var(--space-2);
    }
    .feedback-msg {
      padding: var(--space-3);
      border-radius: var(--radius-control);
      font-size: 0.88rem;
      margin-top: var(--space-2);
    }
    .feedback-msg--success {
      background: var(--color-primary-soft);
      color: var(--color-primary);
    }
    .feedback-msg--error {
      background: #fdf2f2;
      color: #b91c1c;
    }
    .count-badge {
      font-size: 0.82rem;
      color: var(--text-muted);
    }
    .status-filter-row {
      display: flex;
      gap: var(--space-2);
      flex-wrap: wrap;
      margin-bottom: var(--space-4);
      padding-bottom: var(--space-2);
      border-bottom: 1px solid var(--line);
    }
    .filter-tab {
      padding: var(--space-1) var(--space-3);
      border: 1px solid var(--line);
      border-radius: var(--radius-pill);
      background: var(--bg-card);
      color: var(--text-muted);
      font-size: 0.85rem;
      cursor: pointer;
      transition: all var(--duration-enter) var(--ease-nurture);
    }
    .filter-tab:hover {
      border-color: var(--color-primary);
      color: var(--color-primary);
    }
    .filter-tab--active {
      background: var(--color-primary);
      border-color: var(--color-primary);
      color: #fff;
      font-weight: 500;
    }
    .empty-state {
      padding: var(--space-6) 0;
      text-align: center;
    }
    .quiet-note {
      color: var(--text-muted);
      font-size: 0.92rem;
      margin: 0;
      line-height: 1.8;
    }
    .question-list {
      display: flex;
      flex-direction: column;
      gap: var(--space-4);
    }
    .question-card {
      padding: var(--space-4);
      border: 1px solid var(--line);
      border-radius: var(--radius-card);
      background: var(--bg-page);
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
    }
    .question-top {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: var(--space-3);
    }
    .title-meta {
      display: flex;
      align-items: center;
      gap: var(--space-2);
      flex-wrap: wrap;
    }
    .subject-chip {
      padding: 2px 8px;
      border-radius: var(--radius-pill);
      background: var(--color-primary-soft);
      color: var(--color-primary);
      font-size: 0.78rem;
      font-weight: 500;
    }
    .q-title {
      margin: 0;
      font-size: 1.05rem;
      font-weight: 600;
      color: var(--text-title);
    }
    .status-pill {
      padding: 2px 10px;
      border-radius: var(--radius-pill);
      font-size: 0.8rem;
      white-space: nowrap;
    }
    .status-pill--open {
      background: rgba(168, 135, 90, 0.15);
      color: var(--color-gold-text);
      font-weight: 500;
    }
    .status-pill--answered {
      background: var(--color-primary-soft);
      color: var(--color-primary);
      font-weight: 500;
    }
    .status-pill--closed {
      background: #e5e7eb;
      color: #6b7280;
    }
    .q-content {
      margin: 0;
      font-size: 0.92rem;
      color: var(--text-main);
      line-height: 1.7;
      white-space: pre-wrap;
    }
    .attachments-wrap {
      margin-top: -4px;
    }
    .teacher-reply-box {
      padding: var(--space-3) var(--space-4);
      border-radius: var(--radius-control);
      background: #faf8f3;
      border-left: 3px solid var(--color-gold);
    }
    .reply-header {
      margin-bottom: 4px;
    }
    .reply-badge {
      font-size: 0.8rem;
      color: var(--color-gold-text);
      font-weight: 600;
    }
    .reply-body {
      margin: 0;
      font-size: 0.9rem;
      color: var(--text-title);
      line-height: 1.7;
      white-space: pre-wrap;
    }
    .question-footer {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-3);
      padding-top: var(--space-2);
      border-top: 1px dashed var(--line);
    }
    .q-date {
      font-size: 0.8rem;
      color: var(--text-muted);
    }
    .btn-close-q {
      font-size: 0.82rem;
      padding: var(--space-1) var(--space-3);
    }
  `],
})
export class StudentQuestionsComponent implements OnInit {
  private readonly http = inject(HttpClient);
  readonly auth = inject(AuthService);

  readonly teacherId = signal('');
  readonly subject = signal('');
  readonly title = signal('');
  readonly content = signal('');
  readonly msg = signal<{ type: 'success' | 'error'; text: string } | null>(null);
  readonly submitting = signal(false);
  readonly closingId = signal<string | null>(null);

  readonly questions = signal<QuestionView[]>([]);
  readonly teachers = signal<{ teacherId: string; displayName: string }[]>([]);
  readonly activeFilter = signal<'ALL' | 'OPEN' | 'ANSWERED' | 'CLOSED'>('ALL');

  readonly hasDraft = computed(() => {
    return Boolean(this.teacherId() || this.subject() || this.title() || this.content());
  });

  readonly filteredQuestions = computed(() => {
    const list = this.questions();
    const filter = this.activeFilter();
    if (filter === 'ALL') return list;
    return list.filter((q) => q.status === filter);
  });

  countByStatus(status: string): number {
    return this.questions().filter((q) => q.status === status).length;
  }

  ngOnInit(): void {
    this.restoreDraft();
    this.load();
    this.http.get<{ teacherId: string; displayName: string }[]>('/api/v1/learning/files/teachers').subscribe({
      next: (r) => this.teachers.set(r ?? []),
      error: () => this.msg.set({ type: 'error', text: '教师列表暂时不可用。' }),
    });
  }

  load(): void {
    this.http.get<QuestionView[]>(API.learning.questions).subscribe({
      next: (d) => this.questions.set(d ?? []),
    });
  }

  // --- 本地草稿暂存机制 ---
  onTeacherChange(val: string): void {
    this.teacherId.set(val);
    this.saveDraft();
  }

  onSubjectChange(val: string): void {
    this.subject.set(val);
    this.saveDraft();
  }

  onTitleChange(val: string): void {
    this.title.set(val);
    this.saveDraft();
  }

  onContentChange(val: string): void {
    this.content.set(val);
    this.saveDraft();
  }

  private saveDraft(): void {
    try {
      const draft = {
        teacherId: this.teacherId(),
        subject: this.subject(),
        title: this.title(),
        content: this.content(),
      };
      localStorage.setItem(DRAFT_KEY, JSON.stringify(draft));
    } catch {
      // 容错处理
    }
  }

  private restoreDraft(): void {
    try {
      const raw = localStorage.getItem(DRAFT_KEY);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (parsed.teacherId) this.teacherId.set(parsed.teacherId);
        if (parsed.subject) this.subject.set(parsed.subject);
        if (parsed.title) this.title.set(parsed.title);
        if (parsed.content) this.content.set(parsed.content);
      }
    } catch {
      // 容错处理
    }
  }

  clearDraft(): void {
    try {
      localStorage.removeItem(DRAFT_KEY);
    } catch {
      // 容错处理
    }
    this.teacherId.set('');
    this.subject.set('');
    this.title.set('');
    this.content.set('');
  }

  // --- 提问与关闭 ---
  ask(): void {
    if (!this.title().trim() || !this.content().trim() || !this.teacherId() || this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.msg.set(null);

    this.http
      .post(API.learning.questions, {
        teacherId: this.teacherId(),
        subject: this.subject().trim() || '综合',
        title: this.title().trim(),
        content: this.content().trim(),
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.msg.set({ type: 'success', text: '问题已成功提交，等待老师回答。' });
          this.clearDraft();
          this.load();
        },
        error: (e) => {
          this.submitting.set(false);
          const detail = e?.error?.detail ?? e?.error?.message ?? '提交失败，请稍后重试。';
          this.msg.set({ type: 'error', text: detail });
        },
      });
  }

  /** B3 缺口补齐：关闭答疑按钮 */
  closeQuestion(q: QuestionView): void {
    if (this.closingId()) return;
    this.closingId.set(q.questionId);

    this.http.post(API.learning.closeQuestion(q.questionId), {}).subscribe({
      next: () => {
        this.closingId.set(null);
        q.status = 'CLOSED';
        this.questions.update((list) => [...list]);
      },
      error: (e) => {
        this.closingId.set(null);
        const detail = e?.error?.detail ?? '关闭失败，请稍后重试。';
        this.msg.set({ type: 'error', text: detail });
      },
    });
  }
}
