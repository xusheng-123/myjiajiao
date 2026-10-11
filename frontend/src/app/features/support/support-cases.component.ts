import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { API_BASE } from '../../core/api/endpoints';
import { QueuePagerComponent } from '../../shared/components/queue-pager.component';
import { StorageQuotaComponent } from '../../shared/components/storage-quota.component';

interface Course {
  bookingId: string;
  subject: string;
  status: string;
  type: string;
  startAt: string;
  studentName: string;
  teacherName: string;
}

interface Case {
  caseId: string;
  ownerName: string;
  category: string;
  title: string;
  description: string;
  bookingId: string | null;
  course: Course | null;
  status: string;
  revision: number;
  createdAt: string;
  updatedAt: string;
}

interface Reply {
  replyId: string;
  authorName: string;
  authorRole: string;
  body: string;
  kind: string;
  createdAt: string;
}

interface Evidence {
  fileId: string;
  filename: string;
  sizeBytes: number;
  sha256: string;
  canDelete: boolean;
}

interface Page<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
}

const BASE = API_BASE + '/support/cases';

@Component({
  selector: 'nur-support-cases',
  standalone: true,
  imports: [FormsModule, DatePipe, RouterLink, QueuePagerComponent, StorageQuotaComponent],
  templateUrl: './support-cases.component.html',
  styleUrl: './support-cases.component.css'
})
export class SupportCasesComponent {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  readonly role = () => this.auth.role() ?? 'STUDENT';
  readonly admin = () => this.role() === 'ADMIN';

  readonly mobileView = signal<'list' | 'detail'>('list');
  readonly showCreate = signal<boolean>(false);

  readonly cases = signal<Case[]>([]);
  readonly page = signal(0);
  readonly pages = signal(0);
  readonly total = signal(0);
  readonly loading = signal(false);

  readonly selected = signal<Case | null>(null);
  readonly files = signal<Evidence[]>([]);
  readonly replies = signal<Reply[]>([]);

  readonly replyPage = signal(0);
  readonly replyPages = signal(0);
  readonly replyTotal = signal(0);
  readonly historyLoading = signal(false);

  readonly quotaRevision = signal(0);
  readonly busy = signal(false);
  readonly notice = signal('');
  private listVersion = 0;
  private detailVersion = 0;
  private historyVersion = 0;

  readonly courses = signal<Course[]>([]);
  readonly chosenCourse = signal<Course | null>(null);
  readonly coursePage = signal(0);
  readonly coursePages = signal(0);
  readonly courseTotal = signal(0);
  readonly courseLoading = signal(false);
  readonly courseError = signal('');
  private courseVersion = 0;
  courseFilter = '';

  readonly courseStatuses = [
    { value: 'REQUESTED', label: '待确认' },
    { value: 'CONFIRMED', label: '已确认' },
    { value: 'IN_PROGRESS', label: '进行中' },
    { value: 'COMPLETED', label: '已完成' },
    { value: 'CANCELLED', label: '已取消' }
  ];

  readonly categories = [
    { value: 'SCHEDULE', label: '预约与课程' },
    { value: 'PAYMENT', label: '订单与支付' },
    { value: 'LEARNING', label: '学习服务' },
    { value: 'ACCOUNT', label: '账户问题' },
    { value: 'OTHER', label: '其他' }
  ];

  readonly statuses = [
    { value: 'OPEN', label: '待处理' },
    { value: 'IN_PROGRESS', label: '处理中' },
    { value: 'WAITING_USER', label: '待用户补充' },
    { value: 'RESOLVED', label: '已解决' },
    { value: 'CLOSED', label: '已关闭' }
  ];

  category = 'SCHEDULE';
  title = '';
  description = '';
  filter = '';
  replyBody = '';
  statusNote = '';
  targetStatus = 'CLOSED';

  constructor() {
    this.load();
    if (!this.admin()) this.loadCourses();
  }

  openCreate(): void {
    this.showCreate.set(true);
    this.selected.set(null);
    this.mobileView.set('detail');
  }

  cancelCreate(): void {
    this.showCreate.set(false);
    if (this.cases().length) {
      this.open(this.cases()[0].caseId);
    } else {
      this.mobileView.set('list');
    }
  }

  backToList(): void {
    this.mobileView.set('list');
  }

  courseText(b: Course): string {
    return `${b.subject} · ${new Date(b.startAt).toLocaleString('zh-CN', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })} · ${b.studentName} / ${b.teacherName} · ${b.type === 'TRIAL' ? '试听' : '正式课'} · ${this.courseStatuses.find(x => x.value === b.status)?.label ?? b.status}`;
  }

  chosenOffPage(): Course | null {
    const b = this.chosenCourse();
    return b && !this.courses().some(x => x.bookingId === b.bookingId) ? b : null;
  }

  chooseCourse(id: string): void {
    this.chosenCourse.set(this.courses().find(x => x.bookingId === id) ?? (this.chosenCourse()?.bookingId === id ? this.chosenCourse() : null));
  }

  loadCourses(): void {
    if (this.admin()) return;
    const v = ++this.courseVersion;
    this.courseLoading.set(true);
    this.courseError.set('');
    let params = new HttpParams().set('page', this.coursePage()).set('size', 20);
    if (this.courseFilter) params = params.set('status', this.courseFilter);

    this.http.get<Page<Course>>(BASE + '/course-options', { params }).subscribe({
      next: d => {
        if (v !== this.courseVersion) return;
        if (this.coursePage() > 0 && !d.content.length) {
          this.coursePage.set(Math.max(0, d.totalPages - 1));
          this.loadCourses();
          return;
        }
        this.courses.set(d.content);
        this.coursePages.set(d.totalPages);
        this.courseTotal.set(d.totalElements);
        this.courseLoading.set(false);
      },
      error: e => {
        if (v !== this.courseVersion) return;
        this.courseLoading.set(false);
        this.courseError.set(e.error?.detail ?? '课程暂未读取，请刷新后重试。');
      }
    });
  }

  turnCourses(delta: number): void {
    if (this.courseLoading() || this.coursePage() + delta < 0 || this.coursePage() + delta >= this.coursePages()) return;
    this.coursePage.update(x => x + delta);
    this.loadCourses();
  }

  courseFilterChanged(value: string): void {
    this.courseFilter = value;
    this.coursePage.set(0);
    this.loadCourses();
  }

  label(value: string): string {
    return [...this.statuses, ...this.categories].find(x => x.value === value)?.label ?? value;
  }

  transitions(c: Case) {
    return this.admin()
      ? this.statuses.filter(x => x.value !== c.status && x.value !== 'OPEN')
      : this.statuses.filter(x => x.value === 'CLOSED' || (c.status === 'RESOLVED' && x.value === 'OPEN'));
  }

  private failure(e: any): void {
    this.busy.set(false);
    this.notice.set(e.error?.detail ?? '操作未完成，请重试。');
  }

  load(): void {
    const v = ++this.listVersion;
    this.loading.set(true);
    let params = new HttpParams().set('page', this.page()).set('size', 20);
    if (this.filter) params = params.set('status', this.filter);

    this.http.get<Page<Case>>(BASE, { params }).subscribe({
      next: d => {
        if (v !== this.listVersion) return;
        if (this.page() > 0 && !d.content.length) {
          this.page.set(Math.max(0, d.totalPages - 1));
          this.load();
          return;
        }
        this.cases.set(d.content);
        this.pages.set(d.totalPages);
        this.total.set(d.totalElements);
        this.loading.set(false);

        // Desktop auto-selection when nothing is selected
        if (typeof window !== 'undefined' && window.innerWidth >= 768 && !this.selected() && !this.showCreate() && d.content.length) {
          this.open(d.content[0].caseId);
        }
      },
      error: e => {
        if (v !== this.listVersion) return;
        this.loading.set(false);
        this.failure(e);
      }
    });
  }

  turn(delta: number): void {
    if (this.loading() || this.page() + delta < 0 || this.page() + delta >= this.pages()) return;
    this.page.update(x => x + delta);
    this.load();
  }

  filterChanged(value: string): void {
    this.filter = value;
    this.page.set(0);
    this.load();
  }

  open(id: string): void {
    const v = ++this.detailVersion;
    ++this.historyVersion;
    this.showCreate.set(false);
    this.mobileView.set('detail');
    this.selected.set(null);
    this.files.set([]);
    this.replies.set([]);
    this.replyPage.set(0);

    this.http.get<Case>(BASE + '/' + id).subscribe({
      next: c => {
        if (v !== this.detailVersion) return;
        this.selected.set(c);
        this.targetStatus = this.transitions(c)[0]?.value ?? 'CLOSED';
        this.history();
        this.loadFiles();
      },
      error: e => {
        if (v === this.detailVersion) this.failure(e);
      }
    });
  }

  history(): void {
    const c = this.selected();
    if (!c) return;
    const v = ++this.historyVersion;
    this.historyLoading.set(true);

    this.http.get<Page<Reply>>(BASE + '/' + c.caseId + '/replies', { params: { page: this.replyPage(), size: 20 } }).subscribe({
      next: d => {
        if (v !== this.historyVersion || this.selected()?.caseId !== c.caseId) return;
        this.replies.set([...d.content].reverse());
        this.replyPages.set(d.totalPages);
        this.replyTotal.set(d.totalElements);
        this.historyLoading.set(false);
      },
      error: e => {
        if (v !== this.historyVersion) return;
        this.historyLoading.set(false);
        this.failure(e);
      }
    });
  }

  turnHistory(delta: number): void {
    if (this.historyLoading() || this.replyPage() + delta < 0 || this.replyPage() + delta >= this.replyPages()) return;
    this.replyPage.update(x => x + delta);
    this.history();
  }

  private loadFiles(): void {
    this.quotaRevision.update(x => x + 1);
    const c = this.selected();
    if (!c) return;
    this.http.get<Evidence[]>(BASE + '/' + c.caseId + '/evidence').subscribe({
      next: f => {
        if (this.selected()?.caseId === c.caseId) this.files.set(f);
      },
      error: e => this.failure(e)
    });
  }

  private refreshed(c: Case, message: string): void {
    this.busy.set(false);
    this.notice.set(message);
    if (this.selected()?.caseId === c.caseId) {
      this.selected.set(c);
      this.replyPage.set(0);
      this.history();
      this.loadFiles();
    }
    this.load();
  }

  create(): void {
    if (this.busy() || !this.title.trim() || !this.description.trim()) return;
    this.busy.set(true);
    this.http
      .post<Case>(BASE, {
        category: this.category,
        title: this.title,
        description: this.description,
        bookingId: this.chosenCourse()?.bookingId ?? null
      })
      .subscribe({
        next: c => {
          this.busy.set(false);
          this.title = '';
          this.description = '';
          this.chosenCourse.set(null);
          this.showCreate.set(false);
          this.notice.set('工单已提交，可补充证据并等待运营回复。');
          this.page.set(0);
          this.filter = '';
          this.load();
          this.open(c.caseId);
        },
        error: e => {
          this.failure(e);
          if (e.status === 403) {
            this.chosenCourse.set(null);
            this.loadCourses();
          }
        }
      });
  }

  reply(): void {
    const c = this.selected();
    if (!c || this.busy() || !this.replyBody.trim()) return;
    this.busy.set(true);
    this.http.post<Case>(BASE + '/' + c.caseId + '/replies', { body: this.replyBody }).subscribe({
      next: x => {
        this.replyBody = '';
        this.refreshed(x, '回复已保存。');
      },
      error: e => this.failure(e)
    });
  }

  changeStatus(): void {
    const c = this.selected();
    if (!c || this.busy() || !this.statusNote.trim()) return;
    this.busy.set(true);
    this.http.post<Case>(BASE + '/' + c.caseId + '/status', { status: this.targetStatus, revision: c.revision, note: this.statusNote }).subscribe({
      next: x => {
        this.statusNote = '';
        this.refreshed(x, '状态已更新并留存处理记录。');
      },
      error: e => {
        this.failure(e);
        if (e.status === 409) this.open(c.caseId);
      }
    });
  }

  upload(event: Event): void {
    const el = event.target as HTMLInputElement,
      f = el.files?.[0],
      c = this.selected();
    if (!f || !c || this.busy()) return;
    if (f.size > 10485760) {
      this.notice.set('单份证据不能超过10MB。');
      el.value = '';
      return;
    }
    const data = new FormData();
    data.append('file', f);
    this.busy.set(true);

    this.http.post(BASE + '/' + c.caseId + '/evidence', data).subscribe({
      next: () => {
        this.busy.set(false);
        el.value = '';
        this.notice.set('私有证据已保存。');
        this.open(c.caseId);
        this.load();
      },
      error: e => {
        el.value = '';
        this.failure(e);
      }
    });
  }

  download(f: Evidence): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.http.get(BASE + '/evidence/' + f.fileId + '/download', { responseType: 'blob' }).subscribe({
      next: blob => {
        this.busy.set(false);
        const url = URL.createObjectURL(blob),
          a = document.createElement('a');
        a.href = url;
        a.download = f.filename;
        a.click();
        setTimeout(() => URL.revokeObjectURL(url), 10000);
      },
      error: e => this.failure(e)
    });
  }

  remove(f: Evidence): void {
    const c = this.selected();
    if (!c || this.busy()) return;
    this.busy.set(true);
    this.http.delete(BASE + '/evidence/' + f.fileId).subscribe({
      next: () => {
        this.busy.set(false);
        this.notice.set('证据已从沟通页面移除，审计保留容量未清理。');
        this.open(c.caseId);
        this.load();
      },
      error: e => this.failure(e)
    });
  }
}
