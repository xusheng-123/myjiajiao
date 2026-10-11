import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../core/auth/auth.service';
import { QueuePagerComponent } from '../../shared/components/queue-pager.component';

interface Question {
  prompt: string;
  choices: string[];
  correctIndex: number | null;
}

interface Attempt {
  answers: number[];
  correctCount: number;
  totalCount: number;
  teacherComment: string | null;
  commentRevision: number;
  submittedAt: string;
}

interface Assessment {
  assessmentId: string;
  bookingId: string;
  studentId: string;
  subject: string;
  title: string;
  scope: string;
  status: string;
  revision: number;
  questions: Question[];
  attempt: Attempt | null;
}

interface Page<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
}

interface Score {
  assessmentId: string;
  title: string;
  subject: string;
  correctCount: number;
  totalCount: number;
  submittedAt: string;
  teacherComment: string | null;
}

interface Report {
  tasksTotal: number;
  tasksCompleted: number;
  lessonsCompleted: number;
  scores: Page<Score>;
  classroomNotes: Record<string, string>[];
  notice: string;
}

const BASE = '/api/v1/learning/assessments';

@Component({
  selector: 'nur-assessments',
  standalone: true,
  imports: [FormsModule, DatePipe, RouterLink, QueuePagerComponent],
  templateUrl: './assessments.component.html',
  styleUrl: './assessments.component.css'
})
export class AssessmentsComponent {
  private readonly http = inject(HttpClient);
  readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private version = 0;

  readonly activeTab = signal<'quizzes' | 'report'>('quizzes');
  readonly quizzes = signal<Assessment[]>([]);
  readonly report = signal<Report | null>(null);
  readonly students = signal<{ studentId: string; displayName: string }[]>([]);
  readonly children = signal<{ studentId: string; studentNo: string | null }[]>([]);
  readonly lessons = signal<{ bookingId: string; subject: string; startAt: string }[]>([]);

  readonly page = signal(0);
  readonly pages = signal(0);
  readonly total = signal(0);
  readonly scorePage = signal(0);
  readonly busy = signal(false);
  readonly loading = signal(false);
  readonly notice = signal('');

  student = '';
  child = '';
  bookingId = '';
  subject = '数学';
  title = '';
  scope = '';
  editing = '';
  draftRevision = 0;
  questions: Question[] = [{ prompt: '', choices: ['', '', '', ''], correctIndex: 0 }];
  answers: Partial<Record<string, number[]>> = {};
  comments: Partial<Record<string, string>> = {};

  constructor() {
    this.student = this.route.snapshot.queryParamMap.get('studentId') ?? '';
    this.child = this.route.snapshot.queryParamMap.get('childProfileId') ?? '';

    if (this.auth.role() === 'TEACHER') {
      this.http.get<{ studentId: string; displayName: string }[]>('/api/v1/learning/plans/students').subscribe({
        next: s => {
          this.students.set(s);
          this.student = this.student || s[0]?.studentId || '';
          if (this.student) this.load();
        },
        error: e => this.fail(e)
      });
    } else if (this.auth.role() === 'PARENT') {
      this.http.get<any[]>('/api/v1/parent/me/children').subscribe({
        next: c => {
          const active = c.filter(x => x.status === 'ACTIVE');
          this.children.set(active);
          this.child = this.child || active[0]?.studentId || '';
          if (this.child) this.load();
        },
        error: e => this.fail(e)
      });
    } else {
      this.load();
    }
  }

  private fail(e: any): void {
    this.busy.set(false);
    this.loading.set(false);
    this.notice.set(e.error?.detail ?? '操作失败，请刷新后重试。');
  }

  params(): Record<string, string | number> {
    const p: Record<string, string | number> = {};
    if (this.student) p['studentId'] = this.student;
    if (this.child) p['childProfileId'] = this.child;
    return p;
  }

  selectStudent(id: string): void {
    this.student = id;
    this.page.set(0);
    this.scorePage.set(0);
    this.clearDraft();
    this.answers = {};
    this.comments = {};
    this.quizzes.set([]);
    this.report.set(null);
    this.load();
  }

  selectChild(id: string): void {
    this.child = id;
    this.page.set(0);
    this.scorePage.set(0);
    this.quizzes.set([]);
    this.report.set(null);
    this.load();
  }

  load(): void {
    const v = ++this.version;
    this.loading.set(true);
    this.http.get<Page<Assessment>>(BASE, { params: { ...this.params(), page: this.page(), size: 20 } }).subscribe({
      next: r => {
        if (v !== this.version) return;
        this.quizzes.set(r.content);
        this.pages.set(r.totalPages);
        this.total.set(r.totalElements);
        this.loading.set(false);
      },
      error: e => {
        if (v === this.version) this.fail(e);
      }
    });

    this.loadReport();

    if (this.auth.role() === 'TEACHER') {
      this.http.get<any[]>('/api/v1/learning/files/eligible-lessons', { params: { studentId: this.student } }).subscribe({
        next: l => {
          if (v !== this.version) return;
          this.lessons.set(l);
          if (!this.editing) this.bookingId = l[0]?.bookingId ?? '';
        },
        error: e => {
          if (v === this.version) this.fail(e);
        }
      });
    }
  }

  private reportVersion = 0;

  loadReport(): void {
    const v = ++this.reportVersion;
    this.http.get<Report>(BASE + '/report', { params: { ...this.params(), page: this.scorePage(), size: 20 } }).subscribe({
      next: r => {
        if (v === this.reportVersion) this.report.set(r);
      },
      error: e => {
        if (v === this.reportVersion) this.fail(e);
      }
    });
  }

  turn(n: number): void {
    if (this.loading() || this.page() + n < 0 || this.page() + n >= this.pages()) return;
    this.page.update(p => p + n);
    this.load();
  }

  turnScores(n: number): void {
    if (this.scorePage() + n < 0 || this.scorePage() + n >= (this.report()?.scores.totalPages ?? 0)) return;
    this.scorePage.update(p => p + n);
    this.loadReport();
  }

  choiceMarker(j: number): string {
    return ['A', 'B', 'C', 'D'][j] ?? String(j + 1);
  }

  status(s: string): string {
    return ({ DRAFT: '教师草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' } as Record<string, string>)[s] ?? s;
  }

  addQuestion(): void {
    if (this.questions.length < 20) {
      this.questions.push({ prompt: '', choices: ['', '', '', ''], correctIndex: 0 });
    }
  }

  removeQuestion(n: number): void {
    if (this.questions.length > 1) {
      this.questions.splice(n, 1);
    }
  }

  clearDraft(): void {
    this.editing = '';
    this.title = '';
    this.scope = '';
    this.questions = [{ prompt: '', choices: ['', '', '', ''], correctIndex: 0 }];
    this.bookingId = this.lessons()[0]?.bookingId ?? '';
  }

  edit(q: Assessment): void {
    this.editing = q.assessmentId;
    this.draftRevision = q.revision;
    this.bookingId = q.bookingId;
    this.subject = q.subject;
    this.title = q.title;
    this.scope = q.scope;
    this.questions = q.questions.map(x => ({ ...x, choices: [...x.choices] }));
    this.activeTab.set('quizzes');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  saveDraft(): void {
    if (this.busy()) return;
    this.busy.set(true);
    const body = {
      bookingId: this.bookingId,
      subject: this.subject,
      title: this.title,
      scope: this.scope,
      questions: this.questions,
      revision: this.draftRevision
    };
    const req = this.editing ? this.http.put(BASE + '/' + this.editing, body) : this.http.post(BASE, body);
    req.subscribe({
      next: () => {
        this.busy.set(false);
        this.clearDraft();
        this.notice.set('草稿已保存，发布前可继续编辑题目。');
        this.page.set(0);
        this.load();
      },
      error: e => this.fail(e)
    });
  }

  action(q: Assessment, act: string): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.http.post(BASE + '/' + q.assessmentId + '/' + act, { revision: q.revision }).subscribe({
      next: () => {
        this.busy.set(false);
        this.notice.set(act === 'publish' ? '小测已发布，题目与答案已固定。' : '小测已归档，既有成绩保留。');
        this.load();
      },
      error: e => this.fail(e)
    });
  }

  setAnswer(q: Assessment, n: number, j: number): void {
    (this.answers[q.assessmentId] ??= [])[n] = j;
  }

  complete(q: Assessment): boolean {
    return q.questions.every((_, n) => Number.isInteger(this.answers[q.assessmentId]?.[n]));
  }

  submit(q: Assessment): void {
    if (this.busy() || !this.complete(q)) return;
    this.busy.set(true);
    this.http.post(BASE + '/' + q.assessmentId + '/submit', { answers: this.answers[q.assessmentId] }).subscribe({
      next: () => {
        this.busy.set(false);
        this.notice.set('作答已提交，以下为本次题目范围的真实得分。');
        this.load();
      },
      error: e => this.fail(e)
    });
  }

  comment(q: Assessment): void {
    const com = (this.comments[q.assessmentId] ?? q.attempt?.teacherComment ?? '').trim();
    if (this.busy() || !com || !q.attempt) return;
    this.busy.set(true);
    this.http.post(BASE + '/' + q.assessmentId + '/comment', { revision: q.attempt.commentRevision, comment: com }).subscribe({
      next: () => {
        this.busy.set(false);
        this.notice.set('教师批注已保存。');
        this.load();
      },
      error: e => this.fail(e)
    });
  }
}
