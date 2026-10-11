import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../core/auth/auth.service';
import { LearningAttachmentsComponent, LearningFile } from '../../shared/components/learning-attachments.component';
import { StorageQuotaComponent } from '../../shared/components/storage-quota.component';

interface Task {
  taskId: string;
  title: string;
  description: string;
  status: string;
  submission: string | null;
  feedback: string | null;
  canSubmit: boolean;
  canFeedback: boolean;
}

interface Lesson {
  bookingId: string;
  subject: string;
  startAt: string | null;
}

interface Question {
  questionId: string;
  title: string;
  content: string;
  answer: string | null;
  status: string;
  canAnswer: boolean;
}

@Component({
  selector: 'nur-learning-materials',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, LearningAttachmentsComponent, StorageQuotaComponent],
  templateUrl: './learning-materials.component.html',
  styleUrl: './learning-materials.component.css'
})
export class LearningMaterialsComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private version = 0;

  readonly auth = inject(AuthService);
  readonly home = computed(() => '/' + this.auth.role()?.toLowerCase() + '/dashboard');

  readonly activeTab = signal<'materials' | 'tasks' | 'questions'>('materials');

  readonly student = signal('');
  readonly child = signal('');
  readonly students = signal<{ studentId: string; displayName: string }[]>([]);

  readonly files = signal<LearningFile[]>([]);
  readonly tasks = signal<Task[]>([]);
  readonly page = signal(0);
  readonly pages = signal(0);
  readonly taskPage = signal(0);
  readonly taskPages = signal(0);

  readonly quotaRevision = signal(0);
  readonly lessons = signal<Lesson[]>([]);
  readonly lesson = signal('');
  readonly stored = signal(0);
  readonly limit = signal(209715200);
  readonly busy = signal(false);
  readonly loading = signal(false);
  readonly notice = signal('');
  readonly editor = signal('');
  readonly work = signal('');

  readonly questions = signal<Question[]>([]);
  readonly questionPage = signal(0);
  readonly questionPages = signal(0);

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
        error: () => this.notice.set('学生列表暂时不可用。')
      });
    } else {
      this.load();
    }
  }

  select(id: string): void {
    this.student.set(id);
    this.page.set(0);
    this.taskPage.set(0);
    this.questionPage.set(0);
    this.editor.set('');
    this.load();
  }

  params(): Record<string, string> {
    const p: Record<string, string> = {};
    if (this.student()) p['studentId'] = this.student();
    if (this.child()) p['childProfileId'] = this.child();
    return p;
  }

  load(): void {
    this.quotaRevision.update(x => x + 1);
    const v = ++this.version;
    this.loading.set(true);
    this.files.set([]);
    this.tasks.set([]);
    this.questions.set([]);

    this.http
      .get<{ content: Question[]; totalPages: number }>('/api/v1/learning/files/questions', {
        params: { ...this.params(), page: String(this.questionPage()), size: '20' }
      })
      .subscribe({
        next: r => {
          if (v === this.version) {
            this.questions.set(r.content);
            this.questionPages.set(r.totalPages);
          }
        },
        error: e => {
          if (v === this.version) this.notice.set(e.error?.detail ?? '答疑加载失败。');
        }
      });

    this.http
      .get<{ content: LearningFile[]; totalPages: number }>('/api/v1/learning/files', {
        params: { ...this.params(), page: String(this.page()), size: '20' }
      })
      .subscribe({
        next: r => {
          if (v !== this.version) return;
          this.files.set(r.content);
          this.pages.set(r.totalPages);
          this.loading.set(false);
        },
        error: e => {
          if (v !== this.version) return;
          this.loading.set(false);
          this.notice.set(e.error?.detail ?? '资料加载失败。');
        }
      });

    this.http
      .get<{ content: Task[]; totalPages: number }>('/api/v1/learning/files/work', {
        params: { ...this.params(), page: String(this.taskPage()), size: '20' }
      })
      .subscribe({
        next: r => {
          if (v !== this.version) return;
          this.tasks.set(r.content);
          this.taskPages.set(r.totalPages);
        },
        error: e => {
          if (v === this.version) this.notice.set(e.error?.detail ?? '任务加载失败。');
        }
      });

    if (this.auth.role() === 'TEACHER') {
      this.http
        .get<Lesson[]>('/api/v1/learning/files/eligible-lessons', { params: { studentId: this.student() } })
        .subscribe({
          next: r => {
            if (v === this.version) {
              this.lessons.set(r);
              this.lesson.set(r[0]?.bookingId ?? '');
            }
          },
          error: () => this.notice.set('暂无可上传资料的课程。')
        });
    }
  }

  turn(d: number, task = false): void {
    (task ? this.taskPage : this.page).update(p => p + d);
    this.load();
  }

  readonly increment = (n: number) => n + 1;

  size(n: number): string {
    return n < 1048576 ? (n / 1024).toFixed(1) + ' KB' : (n / 1048576).toFixed(1) + ' MB';
  }

  upload(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    if (!this.lesson() || file.size === 0 || file.size > 20971520) {
      input.value = '';
      this.notice.set('请选择课程及不超过20MB的非空文件。');
      return;
    }
    const form = new FormData();
    form.append('category', 'MATERIAL');
    form.append('contextId', this.lesson());
    form.append('file', file);
    this.busy.set(true);

    this.http.post('/api/v1/learning/files', form).subscribe({
      next: () => {
        this.busy.set(false);
        input.value = '';
        this.notice.set('教学资料已保存。');
        this.load();
      },
      error: e => {
        this.busy.set(false);
        input.value = '';
        this.notice.set(e.error?.detail ?? '上传失败。');
      }
    });
  }

  download(f: LearningFile): void {
    this.busy.set(true);
    this.http.get('/api/v1/learning/files/' + f.fileId + '/download', { responseType: 'blob' }).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = f.filename;
        a.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
        this.busy.set(false);
      },
      error: () => {
        this.busy.set(false);
        this.notice.set('下载失败，请刷新权限后重试。');
      }
    });
  }

  remove(f: LearningFile): void {
    if (!window.confirm('移除此文件？文件仍会保留供审计并计入容量。')) return;
    this.busy.set(true);
    this.http.delete('/api/v1/learning/files/' + f.fileId).subscribe({
      next: () => {
        this.busy.set(false);
        this.load();
      },
      error: e => {
        this.busy.set(false);
        this.notice.set(e.error?.detail ?? '移除失败。');
      }
    });
  }

  begin(t: Task): void {
    this.editor.set(t.taskId);
    this.work.set('');
  }

  save(t: Task): void {
    if (this.busy() || !this.work().trim()) return;
    const submit = t.canSubmit;
    this.busy.set(true);
    this.http
      .post(
        '/api/v1/learning/tasks/' + t.taskId + (submit ? ':submit' : ':feedback'),
        submit ? { content: this.work().trim() } : { feedback: this.work().trim()}
      )
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.editor.set('');
          this.notice.set(submit ? '作业已提交，附件已固定。' : '反馈已保存。');
          this.load();
        },
        error: e => {
          this.busy.set(false);
          this.notice.set(e.error?.detail ?? '保存失败。');
        }
      });
  }

  status(s: string): string {
    return ({ PENDING: '待提交', SUBMITTED: '待教师反馈', COMPLETED: '已完成' } as Record<string, string>)[s] ?? s;
  }

  turnQuestion(delta: number): void {
    this.questionPage.update(p => p + delta);
    this.load();
  }

  answer(q: Question): void {
    if (this.busy() || !this.work().trim()) return;
    this.busy.set(true);
    this.http.post('/api/v1/learning/questions/' + q.questionId + ':answer', { answer: this.work().trim() }).subscribe({
      next: () => {
        this.busy.set(false);
        this.editor.set('');
        this.load();
      },
      error: e => {
        this.busy.set(false);
        this.notice.set(e.error?.detail ?? '回答失败。');
      }
    });
  }
}
