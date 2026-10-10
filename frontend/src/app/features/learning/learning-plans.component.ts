import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../core/auth/auth.service';

interface Milestone { milestoneId: string; title: string; goal: string; targetDate: string | null; completed: boolean; }
interface Task { taskId: string; title: string; status: string; submission: string | null; feedback: string | null; canSubmit: boolean; canFeedback: boolean; }
interface Lesson { bookingId: string; subject: string; status: string; startAt: string | null; }
interface Plan { planId: string; studentId: string; subject: string; title: string; longTermGoal: string; status: string; revision: number; milestones: Milestone[]; tasks: Task[]; milestonesCompleted: number; tasksCompleted: number; lessonsCompleted: number; }
interface Change { changeId: string; action: string; actorName: string; details: string; at: string; }
interface Page<T> { content: T[]; totalPages: number; totalElements: number; }

@Component({ selector: 'nur-learning-plans', standalone: true, imports: [DatePipe, FormsModule, RouterLink],
  templateUrl: './learning-plans.component.html', styleUrl: './learning-plans.component.css' })
export class LearningPlansComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private loadVersion = 0;
  readonly auth = inject(AuthService);
  readonly studentId = signal(''); readonly childProfileId = signal('');
  readonly students = signal<{ studentId: string; displayName: string }[]>([]);
  readonly canEdit = computed(() => this.auth.role() === 'STUDENT' || this.auth.role() === 'TEACHER');
  readonly home = computed(() => '/' + this.auth.role()?.toLowerCase() + '/dashboard');
  readonly plans = signal<Plan[]>([]); readonly page = signal(0); readonly totalPages = signal(0);
  readonly loading = signal(false); readonly busy = signal(false); readonly notice = signal('');
  readonly editing = signal<Plan | null>(null);
  readonly draftSubject = signal(''); readonly draftTitle = signal(''); readonly draftGoal = signal('');
  readonly expanded = signal(''); readonly options = signal<Task[]>([]); readonly selectedTask = signal('');
  readonly lessons = signal<Lesson[]>([]); readonly selectedLesson = signal('');
  readonly taskTitle = signal(''); readonly taskDescription = signal(''); readonly taskDeadline = signal('');
  readonly taskEditor = signal(''); readonly taskDraft = signal('');
  readonly milestoneEditing = signal<Milestone | null>(null);
  readonly milestoneTitle = signal(''); readonly milestoneGoal = signal(''); readonly milestoneDate = signal('');
  readonly historyFor = signal(''); readonly history = signal<Change[]>([]);
  readonly historyPage = signal(0); readonly historyTotal = signal(0); readonly historyLoading = signal(false);
  ngOnInit(): void {
    this.studentId.set(this.route.snapshot.queryParamMap.get('studentId') ?? '');
    this.childProfileId.set(this.route.snapshot.queryParamMap.get('childProfileId') ?? '');
    if (this.auth.role() === 'TEACHER') this.http.get<{ studentId: string; displayName: string }[]>('/api/v1/learning/plans/students').subscribe({
      next: rows => { this.students.set(rows); if (!this.studentId() && rows.length) this.studentId.set(rows[0].studentId); if (this.studentId()) this.load(); },
      error: e => this.notice.set(e.error?.detail ?? '暂时无法加载学生列表。'),
    }); else this.load();
  }
  selectStudent(id: string): void { this.studentId.set(id); this.page.set(0); this.expanded.set(''); this.historyFor.set(''); this.resetDraft(); this.load(); }
  load(): void {
    const version = ++this.loadVersion; this.loading.set(true); this.notice.set('');
    const params: Record<string, string> = { page: String(this.page()), size: '20' };
    if (this.studentId()) params['studentId'] = this.studentId();
    if (this.childProfileId()) params['childProfileId'] = this.childProfileId();
    this.http.get<Page<Plan>>('/api/v1/learning/plans', { params }).subscribe({
      next: r => { if (version !== this.loadVersion) return; this.plans.set(r.content); this.totalPages.set(r.totalPages); this.loading.set(false); },
      error: e => { if (version !== this.loadVersion) return; this.plans.set([]); this.loading.set(false); this.notice.set(e.error?.detail ?? '暂时无法加载计划，请重试。'); },
    });
  }
  turnPage(delta: number): void { this.page.update(p => p + delta); this.load(); }
  edit(p: Plan): void { this.editing.set(p); this.draftSubject.set(p.subject); this.draftTitle.set(p.title); this.draftGoal.set(p.longTermGoal); }
  resetDraft(): void { this.editing.set(null); this.draftSubject.set(''); this.draftTitle.set(''); this.draftGoal.set(''); }
  savePlan(): void {
    const p = this.editing(); const body: Record<string, unknown> = { subject: this.draftSubject().trim(), title: this.draftTitle().trim(), longTermGoal: this.draftGoal().trim() };
    if (p) { body['revision'] = p.revision; body['status'] = p.status; } else if (this.studentId()) body['studentId'] = this.studentId();
    this.mutate(p ? 'PUT' : 'POST', '/api/v1/learning/plans' + (p ? '/' + p.planId : ''), body, () => { this.resetDraft(); this.page.set(0); this.load(); });
  }
  manage(p: Plan): void {
    this.expanded.set(this.expanded() === p.planId ? '' : p.planId); this.resetMilestone(); this.options.set([]); this.selectedTask.set('');
    this.lessons.set([]); this.selectedLesson.set(''); this.taskTitle.set(''); this.taskDescription.set(''); this.taskDeadline.set('');
    if (this.expanded()) this.http.get<Task[]>('/api/v1/learning/plans/' + p.planId + '/eligible-tasks').subscribe({
      next: rows => { if (this.expanded() === p.planId) this.options.set(rows); }, error: e => this.notice.set(e.error?.detail ?? '无法加载任务。'),
    });
    if (this.expanded() && this.auth.role() === 'TEACHER') this.http.get<Lesson[]>('/api/v1/learning/plans/' + p.planId + '/eligible-lessons').subscribe({
      next: rows => { if (this.expanded() === p.planId) { this.lessons.set(rows); this.selectedLesson.set(rows[0]?.bookingId ?? ''); } },
      error: e => this.notice.set(e.error?.detail ?? '无法加载可布置任务的课程。'),
    });
  }
  resetMilestone(): void { this.milestoneEditing.set(null); this.milestoneTitle.set(''); this.milestoneGoal.set(''); this.milestoneDate.set(''); }
  editMilestone(p: Plan, m: Milestone): void {
    if (this.expanded() !== p.planId) this.manage(p);
    this.milestoneEditing.set(m); this.milestoneTitle.set(m.title); this.milestoneGoal.set(m.goal); this.milestoneDate.set(m.targetDate ?? '');
  }
  saveMilestone(p: Plan): void {
    const m = this.milestoneEditing();
    this.mutate(m ? 'PUT' : 'POST', '/api/v1/learning/plans/' + p.planId + '/milestones' + (m ? '/' + m.milestoneId : ''),
      { revision: p.revision, title: this.milestoneTitle().trim(), goal: this.milestoneGoal().trim(), targetDate: this.milestoneDate() || null, completed: m?.completed ?? false }, () => this.resetMilestone());
  }
  toggleMilestone(p: Plan, m: Milestone): void { this.mutate('PUT', '/api/v1/learning/plans/' + p.planId + '/milestones/' + m.milestoneId,
    { revision: p.revision, title: m.title, goal: m.goal, targetDate: m.targetDate, completed: !m.completed }); }
  linked(p: Plan, id: string): boolean { return p.tasks.some(t => t.taskId === id); }
  link(p: Plan): void { if (this.selectedTask()) this.mutate('POST', '/api/v1/learning/plans/' + p.planId + '/tasks', { taskId: this.selectedTask(), revision: p.revision }); }
  unlink(p: Plan, taskId: string): void { this.mutate('DELETE', '/api/v1/learning/plans/' + p.planId + '/tasks/' + taskId + '?revision=' + p.revision); }
  createTask(p: Plan): void {
    this.mutate('POST', '/api/v1/learning/plans/' + p.planId + '/tasks/new', { revision: p.revision, bookingId: this.selectedLesson(),
      title: this.taskTitle().trim(), description: this.taskDescription().trim(), deadline: this.taskDeadline() ? new Date(this.taskDeadline()).toISOString() : null },
      () => { this.taskTitle.set(''); this.taskDescription.set(''); this.taskDeadline.set(''); });
  }
  openTask(t: Task): void { this.taskEditor.set(t.taskId); this.taskDraft.set(''); }
  saveTask(p: Plan, t: Task): void {
    if (this.busy() || !this.taskDraft().trim()) return;
    const submit = t.status === 'PENDING' && t.canSubmit;
    const feedback = t.status === 'SUBMITTED' && t.canFeedback;
    if (!submit && !feedback) return;
    this.busy.set(true); this.notice.set('');
    this.http.post('/api/v1/learning/tasks/' + t.taskId + (submit ? ':submit' : ':feedback'),
      submit ? { content: this.taskDraft().trim() } : { feedback: this.taskDraft().trim() }).subscribe({
      next: () => {
        this.taskEditor.set(''); this.taskDraft.set('');
        this.http.get<Plan>('/api/v1/learning/plans/' + p.planId).subscribe({
          next: fresh => { this.plans.update(rows => rows.map(old => old.planId === fresh.planId ? fresh : old)); this.busy.set(false); this.notice.set(submit ? '作业已提交，等待教师反馈。' : '反馈已保存，任务已完成。'); },
          error: () => { this.busy.set(false); this.notice.set('内容已保存，请刷新计划查看最新进度。'); },
        });
      }, error: e => { this.busy.set(false); this.notice.set(e.error?.detail ?? '未能保存作业，请刷新后重试。'); },
    });
  }
  archive(p: Plan): void { this.mutate('PUT', '/api/v1/learning/plans/' + p.planId,
    { revision: p.revision, subject: p.subject, title: p.title, longTermGoal: p.longTermGoal, status: p.status === 'ACTIVE' ? 'ARCHIVED' : 'ACTIVE' }); }
  showHistory(p: Plan, page = 0): void {
    this.historyFor.set(p.planId); this.historyPage.set(page); this.historyLoading.set(true); this.history.set([]);
    this.http.get<Page<Change>>('/api/v1/learning/plans/' + p.planId + '/history', { params: { page, size: 20 } }).subscribe({
      next: r => { if (this.historyFor() !== p.planId || this.historyPage() !== page) return; this.history.set(r.content); this.historyTotal.set(r.totalPages); this.historyLoading.set(false); },
      error: e => { this.historyLoading.set(false); this.notice.set(e.error?.detail ?? '无法加载修改记录。'); },
    });
  }
  taskStatus(status: string): string { return ({ PENDING: '待提交', SUBMITTED: '待教师反馈', COMPLETED: '已完成' } as Record<string, string>)[status] ?? status; }
  actionLabel(action: string): string { return ({ CREATE: '建立计划', UPDATE: '调整计划', MILESTONE_CREATE: '新增阶段目标', MILESTONE_UPDATE: '调整阶段目标', TASK_LINK: '关联任务', TASK_UNLINK: '取消关联', TASK_CREATE: '发布课后任务' } as Record<string, string>)[action] ?? '计划调整'; }
  private mutate(method: string, url: string, body?: unknown, done?: () => void): void {
    if (this.busy() || !this.canEdit()) return;
    this.busy.set(true); this.notice.set('');
    this.http.request<Plan>(method, url, { body }).subscribe({
      next: p => { this.busy.set(false); this.plans.update(rows => rows.map(old => old.planId === p.planId ? p : old)); this.notice.set('已保存。'); if (this.historyFor() === p.planId) this.showHistory(p); done?.(); },
      error: e => { this.busy.set(false); this.notice.set(e.error?.detail ?? '未能保存，请刷新后重试。'); },
    });
  }
}
