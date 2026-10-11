import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { HttpClient } from '@angular/common/http';
import { API } from '../../core/api/endpoints';
import { NoticeCenterComponent } from '../../shared/components/notice-center.component';
import { CalendarExportComponent } from '../../shared/components/calendar-export.component';
interface TaskView {
  taskId: string;
  title: string;
  completed: boolean;
}

interface FocusOverview {
  nextLesson: {
    bookingId: string;
    subject: string;
    teacherName: string;
    teacherId: string | null;
    startAt: string;
    status: string;
  } | null;
  preparationTasks: TaskView[];
  encouragement: string;
}

interface PendingBinding {
  bindingId: string;
  parentDisplayName: string;
  relation: string;
  createdAt: string | null;
}

interface MyOrder {
  orderId: string;
  orderNo: string;
  bookingId: string;
  status: string;
  amountCents: number;
  paidAt: string | null;
}

/**
 * 学生控制台 — ADHD 友好硬性规范：
 * 每个视口只突出一个核心主任务（迫近课程 + 进入课堂）；
 * 任务切块 3~4 个原子微任务，支持无序勾选与跳过；
 * 进度提示使用正向反馈文案，严禁惩罚性倒计时与羞辱性文案。
 */
@Component({
  selector: 'nur-student-dashboard',
  standalone: true,
  imports: [DatePipe, RouterLink, NoticeCenterComponent, CalendarExportComponent],
  templateUrl: './student-dashboard.component.html',
  styleUrl: './student-dashboard.component.css',
})
export class StudentDashboardComponent implements OnInit {
  private readonly http = inject(HttpClient);
  readonly auth = inject(AuthService);

  readonly displayName = this.auth.displayName;
  readonly focusMode = signal(true); // 专注模式默认开启：收起历史图表、积分与推荐卡片
  readonly overview = signal<FocusOverview | null>(null);
  readonly loaded = signal(false);
  readonly asking = signal(false);

  readonly completedCount = computed(() =>
    this.overview()?.preparationTasks.filter((t) => t.completed).length ?? 0);

  readonly totalCount = computed(() => this.overview()?.preparationTasks.length ?? 0);

  readonly progressText = computed(() => `${this.completedCount()}/${this.totalCount()} 已完成`);

  /** 我的课程表（PRD 1.2 课程与课后反馈：课程表视图）。 */
  readonly schedule = signal<{ bookingId: string; subject: string; status: string; type: string; startAt: string | null; teacherId: string | null }[]>([]);

  private readonly upcomingLessons = signal<{ bookingId: string; subject: string; status: string; type: string; startAt: string | null; teacherId: string | null }[]>([]);
  private readonly historyLessons = signal<{ bookingId: string; subject: string; status: string; type: string; startAt: string | null; teacherId: string | null }[]>([]);

  // ---------- 改期（D11 前端入口） ----------
  readonly rescheduleBookingId = signal<string | null>(null);
  readonly openSlots = signal<{ slotId: string; startAt: string }[]>([]);
  readonly slotsLoading = signal(false);
  readonly rescheduleBusy = signal(false);
  readonly rescheduleMessage = signal<{ type: 'info' | 'error' | 'success'; text: string } | null>(null);
  readonly pickedSlotId = signal<string | null>(null);

  // ---------- 课程评价（G4 前端入口） ----------
  readonly reviewBookingId = signal<string | null>(null);
  readonly reviewRating = signal(5);
  readonly reviewComment = signal('');
  readonly reviewTags = signal<string[]>([]);
  readonly reviewBusy = signal(false);
  readonly reviewMsg = signal<{ type: 'info' | 'success' | 'error'; text: string } | null>(null);
  readonly reviewedBookings = signal<Set<string>>(new Set());

  // ---------- 进入课堂（G8） ----------
  readonly classroomBusy = signal(false);
  readonly classroomMsg = signal<{ type: 'info' | 'error'; text: string } | null>(null);
  readonly classroomLink = signal<string | null>(null);

  // ---------- 监护绑定申请（G3） ----------
  readonly pendingBindings = signal<PendingBinding[]>([]);
  readonly bindingBusy = signal(false);
  readonly bindingMsg = signal<{ type: 'info' | 'success' | 'error'; text: string } | null>(null);

  // ---------- 课程下单（G7）：金额与状态一律以服务端为准 ----------
  private readonly myOrders = signal<Map<string, MyOrder>>(new Map());
  readonly orderBusy = signal(false);
  readonly orderMsg = signal<{ type: 'info' | 'success' | 'error'; text: string } | null>(null);

  constructor() {
    this.load();
  }

  ngOnInit(): void {
    this.http
      .get<{ bookingId: string; subject: string; status: string; type: string; startAt: string | null; teacherId: string | null }[]>(API.student.upcomingLessons)
      .subscribe({ next: (data) => { this.upcomingLessons.set(data ?? []); this.refreshSchedule(); } });
    // 历史课程（已完成/已结课）：课程表「评价」入口的数据源
    this.http
      .get<{ bookingId: string; subject: string; status: string; type: string; startAt: string | null; teacherId: string | null }[]>(API.student.lessonHistory)
      .subscribe({ next: (data) => { this.historyLessons.set(data ?? []); this.refreshSchedule(); } });
    // 我已发出的评价：刷新后仍能正确显示「✓ 已评价」，避免对同一课程重复提交
    this.http
      .get<{ bookingId: string; direction: string }[]>(API.review.given)
      .subscribe({ next: (data) => {
        const mine = (data ?? []).filter((r) => r.direction === 'STUDENT_TO_TEACHER').map((r) => r.bookingId);
        this.reviewedBookings.set(new Set(mine));
      } });
    // 监护绑定申请（G3）
    this.loadPendingBindings();
    // Course ownership includes orders paid by an active guardian.
    this.loadMyOrders();
  }

  loadMyOrders(): void {
    this.http
      .get<{ content: MyOrder[] }>(`${API.order.related}?page=0&size=50`)
      .subscribe({
        next: (page) => {
          const map = new Map<string, MyOrder>();
          for (const o of page?.content ?? []) {
            map.set(o.bookingId, o);
          }
          this.myOrders.set(map);
        },
        error: () => this.myOrders.set(new Map()),
      });
  }

  orderOf(bookingId: string): MyOrder | undefined {
    return this.myOrders().get(bookingId);
  }

  yuan(cents: number | undefined): string {
    return ((cents ?? 0) / 100).toFixed(2);
  }

  loadPendingBindings(): void {
    this.http.get<PendingBinding[]>(API.student.pendingBindings).subscribe({
      next: (data) => this.pendingBindings.set(data ?? []),
      error: () => this.pendingBindings.set([]),
    });
  }

  /** 确认监护绑定：结果以服务端响应为准，成功后重新拉取待确认列表。 */
  confirmBinding(): void {
    if (this.bindingBusy()) {
      return;
    }
    this.bindingBusy.set(true);
    this.bindingMsg.set(null);
    this.http.post<{ status: string }>(API.student.confirmBinding, {}).subscribe({
      next: () => {
        this.bindingBusy.set(false);
        this.bindingMsg.set({ type: 'success', text: '已确认绑定：对方现在可以为你的课程代付下单，并查看你的学习周报。' });
        this.loadPendingBindings();
      },
      error: (err) => {
        this.bindingBusy.set(false);
        const detail = err?.error?.detail ?? err?.error?.message ?? '确认未成功，请稍后重试。';
        this.bindingMsg.set({ type: 'error', text: detail });
      },
    });
  }

  private refreshSchedule(): void {
    const merged = [...this.upcomingLessons(), ...this.historyLessons()]
      .sort((a, b) => (b.startAt ?? '').localeCompare(a.startAt ?? ''));
    this.schedule.set(merged);
  }

  load(): void {
    this.http.get<FocusOverview>(API.student.focusOverview).subscribe({
      next: (data) => {
        this.overview.set(data);
        this.loaded.set(true);
      },
      error: () => {
        this.loaded.set(true);
        // 网络异常就地容错：展示温和提示与原地重试，不弹模态框
      },
    });
  }

  toggleFocusMode(): void {
    this.focusMode.update((v) => !v);
  }

  /** 无序勾选微任务（Task Chunking）。 */
  toggleTask(task: TaskView): void {
    this.http
      .post<{ completed: boolean }>(`/api/v1/classroom/preparation-tasks/${task.taskId}:complete`, {})
      .subscribe({
        next: (res) => {
          task.completed = res.completed;
        },
      });
  }

  // ---------- 改期流（D11） ----------
  startReschedule(lesson: { bookingId: string; teacherId: string | null }): void {
    if (!lesson.teacherId) {
      this.rescheduleMessage.set({ type: 'error', text: '该课程暂无可用的改期信息，请联系导师。' });
      return;
    }
    this.rescheduleBookingId.set(lesson.bookingId);
    this.rescheduleMessage.set(null);
    this.pickedSlotId.set(null);
    this.slotsLoading.set(true);
    this.http
      .get<{ slotId: string; startAt: string }[]>(API.scheduling.openSlots(lesson.teacherId))
      .subscribe({
        next: (slots) => {
          this.openSlots.set(slots ?? []);
          this.slotsLoading.set(false);
          if (!slots?.length) {
            this.rescheduleMessage.set({ type: 'info', text: '导师当前没有可约的开放时间，稍后再来看看或与导师沟通。' });
          }
        },
        error: () => {
          this.slotsLoading.set(false);
          this.rescheduleMessage.set({ type: 'error', text: '可约时间获取失败，请稍后重试。' });
        },
      });
  }

  pickSlot(slotId: string): void {
    this.pickedSlotId.set(slotId);
    this.rescheduleMessage.set(null);
  }

  confirmReschedule(): void {
    const bookingId = this.rescheduleBookingId();
    const newSlotId = this.pickedSlotId();
    if (!bookingId || !newSlotId || this.rescheduleBusy()) {
      return;
    }
    this.rescheduleBusy.set(true);
    this.http
      .post(API.booking.reschedule(bookingId), { newSlotId })
      .subscribe({
        next: () => {
          this.rescheduleMessage.set({ type: 'success', text: '改期成功，课程表已更新。' });
          this.rescheduleBookingId.set(null);
          this.pickedSlotId.set(null);
          this.ngOnInit();          // 课程表同步刷新
          this.load();              // 专注概览同步刷新
        },
        error: (err) => {
          this.rescheduleBusy.set(false);
          const detail = err?.error?.detail ?? '改期未成功，所选时间可能刚被占用，请重新选择时间。';
          this.rescheduleMessage.set({ type: 'error', text: detail });
        },
      });
  }

  cancelReschedule(): void {
    this.rescheduleBookingId.set(null);
    this.pickedSlotId.set(null);
    this.rescheduleMessage.set(null);
  }

  askTeacher(question: string): void {
    const lesson = this.overview()?.nextLesson;
    if (!lesson || !question.trim()) {
      return;
    }
    this.asking.set(true);
    this.http
      .post(API.student.questions, { teacherId: lesson.teacherId, question: question.trim() })
      .subscribe({ next: () => this.asking.set(false), error: () => this.asking.set(false) });
  }

  retry(): void {
    this.load();
  }

  // ---------- 课程评价流（G4） ----------
  openReview(bookingId: string): void {
    this.reviewBookingId.set(bookingId);
    this.reviewRating.set(5);
    this.reviewComment.set('');
    this.reviewTags.set([]);
    this.reviewMsg.set(null);
  }

  cancelReview(): void {
    this.reviewBookingId.set(null);
    this.reviewMsg.set(null);
  }

  /** 标签最多选 3 个，再点已选项为取消。 */
  toggleReviewTag(tag: string): void {
    this.reviewTags.update((tags) =>
      tags.includes(tag) ? tags.filter((t) => t !== tag) : tags.length >= 3 ? tags : [...tags, tag]);
  }

  submitReview(bookingId: string): void {
    if (this.reviewBusy()) {
      return;
    }
    this.reviewBusy.set(true);
    this.reviewMsg.set(null);
    const comment = this.reviewComment().trim();
    this.http
      .post(API.review.submit, {
        bookingId,
        rating: this.reviewRating(),
        tags: this.reviewTags(),
        comment: comment ? comment : null,
      })
      .subscribe({
        next: () => {
          this.reviewBusy.set(false);
          this.reviewedBookings.update((set) => new Set(set).add(bookingId));
          this.reviewBookingId.set(null);
        },
        error: (err) => {
          this.reviewBusy.set(false);
          const detail = err?.error?.detail ?? err?.error?.message ?? '评价提交未成功，请稍后重试。';
          this.reviewMsg.set({ type: 'error', text: detail });
        },
      });
  }

  /** 进入课堂：取回会议链接，校验协议白名单后新窗口打开；弹窗被拦截时降级为可点击链接。 */
  enterClassroom(): void {
    const lesson = this.overview()?.nextLesson;
    if (!lesson || this.classroomBusy()) {
      return;
    }
    this.classroomBusy.set(true);
    this.classroomMsg.set(null);
    this.classroomLink.set(null);
    this.http
      .post<{ meetingUrl: string; provider: string; sessionStatus: string }>(API.classroom.join(lesson.bookingId), {})
      .subscribe({
        next: (res) => {
          this.classroomBusy.set(false);
          const url = res?.meetingUrl ?? '';
          if (!url) {
            this.classroomMsg.set({ type: 'info', text: '课堂尚未就绪：导师还未录入会议链接，可与导师确认后再来。' });
            return;
          }
          if (!/^https?:\/\//i.test(url)) {
            this.classroomMsg.set({ type: 'error', text: '会议链接格式异常，已阻止打开，请联系导师重新录入。' });
            return;
          }
          const win = window.open(url, '_blank', 'noopener');
          if (!win) {
            this.classroomLink.set(url);
            this.classroomMsg.set({ type: 'info', text: '浏览器拦截了新窗口，请点击下方链接进入课堂。' });
          }
        },
        error: (err) => {
          this.classroomBusy.set(false);
          const detail = err?.error?.detail ?? err?.error?.message ?? '进入课堂失败，请稍后重试。';
          this.classroomMsg.set({ type: 'error', text: detail });
        },
      });
  }
}
