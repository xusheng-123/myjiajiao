import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { AuthService } from '../../core/auth/auth.service';

interface Note {
  noteId: string;
  studentId: string;
  subject: string;
  title: string;
  questionText: string;
  errorReason: string;
  knowledgeTags: string;
  status: string;
  reviewDate: string | null;
  revision: number;
  taskId: string | null;
  taskStatus: string | null;
  canEdit: boolean;
}

interface Lesson {
  bookingId: string;
  subject: string;
  startAt: string | null;
}

@Component({
  selector: 'nur-mistake-notes',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe],
  templateUrl: './mistake-notes.component.html',
  styleUrl: './mistake-notes.component.css'
})
export class MistakeNotesComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private version = 0;

  readonly auth = inject(AuthService);
  readonly student = signal('');
  readonly child = signal('');
  readonly students = signal<{ studentId: string; displayName: string }[]>([]);

  readonly showForm = signal(false);
  readonly notes = signal<Note[]>([]);
  readonly page = signal(0);
  readonly pages = signal(0);
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly notice = signal('');

  readonly editable = computed(() => this.auth.role() === 'STUDENT' || this.auth.role() === 'TEACHER');
  readonly home = computed(() => '/' + this.auth.role()?.toLowerCase() + '/dashboard');

  readonly filterSubject = signal('');
  readonly filterStatus = signal('');
  readonly filterTag = signal('');
  readonly due = signal('');

  readonly editing = signal<Note | null>(null);
  readonly subject = signal('');
  readonly title = signal('');
  readonly question = signal('');
  readonly reason = signal('');
  readonly tags = signal('');
  readonly review = signal('');
  readonly status = signal('UNMASTERED');

  readonly converting = signal<Note | null>(null);
  readonly lessons = signal<Lesson[]>([]);
  readonly lesson = signal('');

  ngOnInit(): void {
    this.student.set(this.route.snapshot.queryParamMap.get('studentId') ?? '');
    this.child.set(this.route.snapshot.queryParamMap.get('childProfileId') ?? '');

    if (this.auth.role() === 'TEACHER') {
      this.http.get<{ studentId: string; displayName: string }[]>('/api/v1/learning/plans/students').subscribe({
        next: r => {
          this.students.set(r);
          if (!this.student()) this.student.set(r[0]?.studentId ?? '');
          if (this.student()) this.load();
        },
        error: () => this.notice.set('暂时无法获取学生。')
      });
    } else {
      this.load();
    }
  }

  toggleForm(): void {
    this.showForm.set(!this.showForm());
    if (!this.showForm() && this.editing()) {
      this.reset();
    }
  }

  select(id: string): void {
    this.student.set(id);
    this.reset();
    this.converting.set(null);
    this.page.set(0);
    this.load();
  }

  filter(): void {
    this.page.set(0);
    this.load();
  }

  load(): void {
    const v = ++this.version;
    this.loading.set(true);
    const params: Record<string, string> = { page: String(this.page()), size: '20' };

    if (this.student()) params['studentId'] = this.student();
    if (this.child()) params['childProfileId'] = this.child();
    if (this.filterSubject()) params['subject'] = this.filterSubject().trim();
    if (this.filterStatus()) params['status'] = this.filterStatus();
    if (this.filterTag()) params['tag'] = this.filterTag().trim();
    if (this.due()) params['dueBefore'] = this.due();

    this.http.get<{ content: Note[]; totalPages: number }>('/api/v1/learning/mistakes', { params }).subscribe({
      next: r => {
        if (v !== this.version) return;
        this.notes.set(r.content);
        this.pages.set(r.totalPages);
        this.loading.set(false);
      },
      error: e => {
        if (v !== this.version) return;
        this.loading.set(false);
        this.notes.set([]);
        this.notice.set(e.error?.detail ?? '错题加载失败。');
      }
    });
  }

  turn(d: number): void {
    this.page.update(p => p + d);
    this.load();
  }

  reset(): void {
    this.editing.set(null);
    this.showForm.set(false);
    this.subject.set('');
    this.title.set('');
    this.question.set('');
    this.reason.set('');
    this.tags.set('');
    this.review.set('');
    this.status.set('UNMASTERED');
  }

  edit(n: Note): void {
    this.editing.set(n);
    this.showForm.set(true);
    this.subject.set(n.subject);
    this.title.set(n.title);
    this.question.set(n.questionText);
    this.reason.set(n.errorReason);
    this.tags.set(n.knowledgeTags);
    this.review.set(n.reviewDate ?? '');
    this.status.set(n.status);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  save(): void {
    const n = this.editing();
    const body: Record<string, unknown> = {
      subject: this.subject().trim(),
      title: this.title().trim(),
      questionText: this.question().trim(),
      errorReason: this.reason().trim(),
      knowledgeTags: this.tags().trim(),
      reviewDate: this.review() || null
    };

    if (n) {
      body['revision'] = n.revision;
      body['status'] = this.status();
    } else if (this.student()) {
      body['studentId'] = this.student();
    }

    this.mutate(n ? 'PUT' : 'POST', '/api/v1/learning/mistakes' + (n ? '/' + n.noteId : ''), body, () => this.reset());
  }

  remove(n: Note): void {
    if (window.confirm('移除此错题？已创建的复习任务将保留。')) {
      this.mutate('DELETE', '/api/v1/learning/mistakes/' + n.noteId + '?revision=' + n.revision);
    }
  }

  convert(n: Note): void {
    this.converting.set(n);
    this.lessons.set([]);
    this.lesson.set('');

    this.http.get<Lesson[]>('/api/v1/learning/mistakes/' + n.noteId + '/eligible-lessons').subscribe({
      next: r => {
        if (this.converting()?.noteId === n.noteId) {
          this.lessons.set(r);
          this.lesson.set(r[0]?.bookingId ?? '');
        }
      },
      error: e => this.notice.set(e.error?.detail ?? '没有可用课程。')
    });
  }

  publish(): void {
    const n = this.converting();
    if (n && this.lesson()) {
      this.mutate('POST', '/api/v1/learning/mistakes/' + n.noteId + '/task', { revision: n.revision, bookingId: this.lesson() }, () =>
        this.converting.set(null)
      );
    }
  }

  private mutate(method: string, url: string, body?: unknown, after?: () => void): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.notice.set('');

    this.http.request(method, url, { body }).subscribe({
      next: () => {
        this.busy.set(false);
        after?.();
        this.load();
        this.notice.set('已保存。');
      },
      error: e => {
        this.busy.set(false);
        this.notice.set(e.error?.detail ?? '操作失败，请重试。');
        if (e.status === 409) this.load();
      }
    });
  }

  label(s: string): string {
    return (
      ({
        UNMASTERED: '待巩固',
        REVIEWING: '复习中',
        MASTERED: '已掌握（人工确认）',
        PENDING: '待提交',
        SUBMITTED: '待反馈',
        COMPLETED: '已完成'
      } as Record<string, string>)[s] ?? s
    );
  }
}
