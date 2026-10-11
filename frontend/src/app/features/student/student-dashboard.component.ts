import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../core/auth/auth.service';
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

export interface BookingItem {
  bookingId: string;
  studentId: string;
  teacherId: string;
  slotId: string;
  subject: string;
  type: string;
  status: string;
  priceCents: number;
  cancelReason: string | null;
  createdAt: string;
}

export interface BookingPage {
  content: BookingItem[];
  totalPages: number;
  totalElements: number;
  number: number;
}

export interface CancelPreviewResult {
  cascadeImpact: {
    bookingId?: string;
    cancelReason?: string;
    willReleaseSlot?: boolean;
    refundAmountCents?: number;
    refundDescription?: string;
    affectedLessons?: number;
    orderStatus?: string;
  };
  confirmationToken: string;
  confirmationTokenExpiresAt: string;
  notice: string;
}

export interface StudentProfileData {
  accountId: string;
  studentNo: string;
  gradeLevel: string | null;
  learningStyle: string | null;
  focusPreference: string | null;
  preferredSubjects: string[];
}

export interface LearningProfileData {
  studentId?: string;
  goal: string | null;
  stage: string | null;
  targetSubjects: string | null;
}

@Component({
  selector: 'nur-student-dashboard',
  standalone: true,
  imports: [
    DatePipe,
    RouterLink,
    FormsModule,
    NoticeCenterComponent,
    CalendarExportComponent,
  ],
  templateUrl: './student-dashboard.component.html',
  styleUrls: ['./student-dashboard.component.css', './student-dashboard-bookings.css'],
})
export class StudentDashboardComponent implements OnInit {
  private readonly http = inject(HttpClient);
  readonly auth = inject(AuthService);

  readonly displayName = this.auth.displayName;
  readonly focusMode = signal(true); // 专注模式默认开启：收起历史图表、次要卡片
  readonly overview = signal<FocusOverview | null>(null);
  readonly loaded = signal(false);
  readonly asking = signal(false);

  readonly completedCount = computed(() =>
    this.overview()?.preparationTasks.filter((t) => t.completed).length ?? 0
  );
  readonly totalCount = computed(() => this.overview()?.preparationTasks.length ?? 0);
  readonly progressText = computed(() => `${this.completedCount()}/${this.totalCount()} 已完成`);

  /** 我的课程表视图 */
  readonly schedule = signal<{
    bookingId: string;
    subject: string;
    status: string;
    type: string;
    startAt: string | null;
    teacherId: string | null;
  }[]>([]);
  private readonly upcomingLessons = signal<{
    bookingId: string;
    subject: string;
    status: string;
    type: string;
    startAt: string | null;
    teacherId: string | null;
  }[]>([]);
  private readonly historyLessons = signal<{
    bookingId: string;
    subject: string;
    status: string;
    type: string;
    startAt: string | null;
    teacherId: string | null;
  }[]>([]);

  // ---------- A3 缺口补齐：我的预约列表 ----------
  readonly myBookings = signal<BookingItem[]>([]);
  readonly bookingsPage = signal(0);
  readonly bookingsTotalPages = signal(0);
  readonly bookingsTotalElements = signal(0);
  readonly bookingsLoading = signal(false);
  readonly bookingsFilter = signal<'ALL' | 'REQUESTED' | 'CONFIRMED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'>('ALL');

  readonly filteredBookings = computed(() => {
    const list = this.myBookings();
    const filter = this.bookingsFilter();
    if (filter === 'ALL') return list;
    return list.filter((b) => b.status === filter);
  });

  // ---------- A1 缺口补齐：两阶段取消预约 ----------
  readonly cancelBookingId = signal<string | null>(null);
  readonly cancelReason = signal('');
  readonly cancelStage = signal<'FORM' | 'PREVIEW'>('FORM');
  readonly cancelPreviewData = signal<CancelPreviewResult | null>(null);
  readonly cancelBusy = signal(false);
  readonly cancelMsg = signal<{ type: 'info' | 'success' | 'error'; text: string } | null>(null);

  // ---------- B2 缺口补齐：学生个人资料卡 ----------
  readonly studentProfile = signal<StudentProfileData | null>(null);
  readonly profileLoading = signal(false);
  readonly profileSaving = signal(false);
  readonly profileEditing = signal(false);
  readonly profileMsg = signal<{ type: 'success' | 'error'; text: string } | null>(null);
  readonly copiedStudentNo = signal(false);
  // 表单字段
  readonly editGradeLevel = signal('');
  readonly editLearningStyle = signal('');
  readonly editFocusPreference = signal('');
  readonly editPreferredSubjects = signal<string[]>([]);

  // ---------- B1 缺口补齐：学习目标画像卡 ----------
  readonly learningProfile = signal<LearningProfileData | null>(null);
  readonly learningProfileLoading = signal(false);
  readonly learningProfileSaving = signal(false);
  readonly learningProfileEditing = signal(false);
  readonly learningProfileMsg = signal<{ type: 'success' | 'error'; text: string } | null>(null);
  // 表单字段
  readonly editGoal = signal('');
  readonly editStage = signal('');
  readonly editTargetSubjects = signal('');

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

  // ---------- 课程下单（G7） ----------
  private readonly myOrders = signal<Map<string, MyOrder>>(new Map());
  readonly orderBusy = signal(false);
  readonly orderMsg = signal<{ type: 'info' | 'success' | 'error'; text: string } | null>(null);

  // ---------- 历史学生付款钱包（只读） ----------
  readonly legacyWallet = signal<{
    present: boolean;
    balanceCents: number | null;
    currency: string | null;
    transactions: {
      content: {
        txnId: string;
        type: string;
        amountCents: number;
        balanceAfterCents: number;
        memo: string;
        createdAt: string;
      }[];
      totalPages: number;
      totalElements: number;
    };
  } | null>(null);
  readonly legacyPage = signal(0);
  readonly legacyLoading = signal(false);
  readonly legacyError = signal(false);

  constructor() {
    this.load();
  }

  ngOnInit(): void {
    this.loadLegacyWallet();
    this.loadScheduleData();
    this.loadPendingBindings();
    this.loadMyOrders();
    this.loadMyBookings(0);
    this.loadStudentProfile();
    this.loadLearningProfile();
  }

  loadScheduleData(): void {
    this.http
      .get<{ bookingId: string; subject: string; status: string; type: string; startAt: string | null; teacherId: string | null }[]>(
        API.student.upcomingLessons
      )
      .subscribe({
        next: (data) => {
          this.upcomingLessons.set(data ?? []);
          this.refreshSchedule();
        },
      });

    this.http
      .get<{ bookingId: string; subject: string; status: string; type: string; startAt: string | null; teacherId: string | null }[]>(
        API.student.lessonHistory
      )
      .subscribe({
        next: (data) => {
          this.historyLessons.set(data ?? []);
          this.refreshSchedule();
        },
      });

    this.http
      .get<{ bookingId: string; direction: string }[]>(API.review.given)
      .subscribe({
        next: (data) => {
          const mine = (data ?? [])
            .filter((r) => r.direction === 'STUDENT_TO_TEACHER')
            .map((r) => r.bookingId);
          this.reviewedBookings.set(new Set(mine));
        },
      });
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

  confirmBinding(): void {
    if (this.bindingBusy()) return;
    this.bindingBusy.set(true);
    this.bindingMsg.set(null);
    this.http.post<{ status: string }>(API.student.confirmBinding, {}).subscribe({
      next: () => {
        this.bindingBusy.set(false);
        this.bindingMsg.set({
          type: 'success',
          text: '已确认绑定：对方现在可以为你的课程代付下单，并查看你的学习周报。',
        });
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
    const merged = [...this.upcomingLessons(), ...this.historyLessons()].sort((a, b) =>
      (b.startAt ?? '').localeCompare(a.startAt ?? '')
    );
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
      },
    });
  }

  toggleFocusMode(): void {
    this.focusMode.update((v) => !v);
  }

  toggleTask(task: TaskView): void {
    this.http
      .post<{ completed: boolean }>(`/api/v1/classroom/preparation-tasks/${task.taskId}:complete`, {})
      .subscribe({
        next: (res) => {
          task.completed = res.completed;
        },
      });
  }

  // ==========================================
  // A3 缺口补齐：我的预约列表实现
  // ==========================================
  loadMyBookings(page = 0): void {
    this.bookingsLoading.set(true);
    this.http
      .get<BookingPage>(`${API.booking.mine}?page=${page}&size=10`)
      .subscribe({
        next: (res) => {
          this.myBookings.set(res?.content ?? []);
          this.bookingsPage.set(res?.number ?? page);
          this.bookingsTotalPages.set(res?.totalPages ?? 0);
          this.bookingsTotalElements.set(res?.totalElements ?? 0);
          this.bookingsLoading.set(false);
        },
        error: () => {
          this.myBookings.set([]);
          this.bookingsLoading.set(false);
        },
      });
  }

  turnBookingsPage(delta: number): void {
    const next = this.bookingsPage() + delta;
    if (next < 0 || next >= this.bookingsTotalPages() || this.bookingsLoading()) return;
    this.loadMyBookings(next);
  }

  // ==========================================
  // A1 缺口补齐：两阶段取消预约
  // ==========================================
  openCancel(bookingId: string): void {
    this.cancelBookingId.set(bookingId);
    this.cancelReason.set('');
    this.cancelStage.set('FORM');
    this.cancelPreviewData.set(null);
    this.cancelMsg.set(null);
  }

  dismissCancel(): void {
    this.cancelBookingId.set(null);
    this.cancelReason.set('');
    this.cancelStage.set('FORM');
    this.cancelPreviewData.set(null);
    this.cancelMsg.set(null);
    this.cancelBusy.set(false);
  }

  previewCancel(): void {
    const bId = this.cancelBookingId();
    const reason = this.cancelReason().trim();
    if (!bId || !reason || this.cancelBusy()) return;

    this.cancelBusy.set(true);
    this.cancelMsg.set(null);

    this.http
      .post<CancelPreviewResult>(API.booking.cancelPreview(bId), { reason })
      .subscribe({
        next: (preview) => {
          this.cancelBusy.set(false);
          this.cancelPreviewData.set(preview);
          this.cancelStage.set('PREVIEW');
        },
        error: (err) => {
          this.cancelBusy.set(false);
          const detail = err?.error?.detail ?? err?.error?.message ?? '取消预览失败，请稍后重试。';
          this.cancelMsg.set({ type: 'error', text: detail });
        },
      });
  }

  executeCancel(): void {
    const bId = this.cancelBookingId();
    const preview = this.cancelPreviewData();
    const reason = this.cancelReason().trim();
    if (!bId || !preview?.confirmationToken || this.cancelBusy()) return;

    this.cancelBusy.set(true);
    this.cancelMsg.set(null);

    this.http
      .post(API.booking.cancelExecute(bId), {
        reason,
        confirmationToken: preview.confirmationToken,
      })
      .subscribe({
        next: () => {
          this.cancelBusy.set(false);
          this.cancelMsg.set({ type: 'success', text: '预约已成功取消，档期已释放。' });
          setTimeout(() => {
            this.dismissCancel();
            this.loadMyBookings(this.bookingsPage());
            this.loadScheduleData();
            this.load();
          }, 1500);
        },
        error: (err) => {
          this.cancelBusy.set(false);
          const detail = err?.error?.detail ?? err?.error?.message ?? '确认取消未成功，请稍后重试。';
          this.cancelMsg.set({ type: 'error', text: detail });
        },
      });
  }

  // ==========================================
  // B2 缺口补齐：学生个人资料卡
  // ==========================================
  loadStudentProfile(): void {
    this.profileLoading.set(true);
    this.http.get<StudentProfileData>(API.student.profile).subscribe({
      next: (data) => {
        this.studentProfile.set(data);
        this.profileLoading.set(false);
      },
      error: () => this.profileLoading.set(false),
    });
  }

  startEditProfile(): void {
    const p = this.studentProfile();
    this.editGradeLevel.set(p?.gradeLevel ?? '');
    this.editLearningStyle.set(p?.learningStyle ?? '');
    this.editFocusPreference.set(p?.focusPreference ?? '');
    this.editPreferredSubjects.set(p?.preferredSubjects ? [...p.preferredSubjects] : []);
    this.profileEditing.set(true);
    this.profileMsg.set(null);
  }

  cancelEditProfile(): void {
    this.profileEditing.set(false);
    this.profileMsg.set(null);
  }

  togglePreferredSubject(sub: string): void {
    const cur = this.editPreferredSubjects();
    if (cur.includes(sub)) {
      this.editPreferredSubjects.set(cur.filter((s) => s !== sub));
    } else {
      this.editPreferredSubjects.set([...cur, sub]);
    }
  }

  saveStudentProfile(): void {
    if (this.profileSaving()) return;
    this.profileSaving.set(true);
    this.profileMsg.set(null);

    this.http
      .put<StudentProfileData>(API.student.profile, {
        gradeLevel: this.editGradeLevel().trim() || null,
        learningStyle: this.editLearningStyle().trim() || null,
        focusPreference: this.editFocusPreference().trim() || null,
        preferredSubjects: this.editPreferredSubjects(),
      })
      .subscribe({
        next: (updated) => {
          this.studentProfile.set(updated);
          this.profileSaving.set(false);
          this.profileEditing.set(false);
          this.profileMsg.set({ type: 'success', text: '个人资料与学习偏好已更新。' });
        },
        error: (err) => {
          this.profileSaving.set(false);
          const detail = err?.error?.detail ?? err?.error?.message ?? '保存失败，请稍后重试。';
          this.profileMsg.set({ type: 'error', text: detail });
        },
      });
  }

  copyStudentNo(no: string): void {
    if (!no) return;
    navigator.clipboard?.writeText(no);
    this.copiedStudentNo.set(true);
    setTimeout(() => this.copiedStudentNo.set(false), 2000);
  }

  // ==========================================
  // B1 缺口补齐：学习目标画像卡
  // ==========================================
  loadLearningProfile(): void {
    this.learningProfileLoading.set(true);
    this.http.get<LearningProfileData>(API.learning.profile).subscribe({
      next: (data) => {
        this.learningProfile.set(data);
        this.learningProfileLoading.set(false);
      },
      error: () => this.learningProfileLoading.set(false),
    });
  }

  startEditLearningProfile(): void {
    const lp = this.learningProfile();
    this.editGoal.set(lp?.goal ?? '');
    this.editStage.set(lp?.stage ?? '');
    this.editTargetSubjects.set(lp?.targetSubjects ?? '');
    this.learningProfileEditing.set(true);
    this.learningProfileMsg.set(null);
  }

  cancelEditLearningProfile(): void {
    this.learningProfileEditing.set(false);
    this.learningProfileMsg.set(null);
  }

  saveLearningProfile(): void {
    if (this.learningProfileSaving()) return;
    this.learningProfileSaving.set(true);
    this.learningProfileMsg.set(null);

    this.http
      .put<LearningProfileData>(API.learning.profile, {
        goal: this.editGoal().trim() || null,
        stage: this.editStage().trim() || null,
        targetSubjects: this.editTargetSubjects().trim() || null,
      })
      .subscribe({
        next: (updated) => {
          this.learningProfile.set(updated);
          this.learningProfileSaving.set(false);
          this.learningProfileEditing.set(false);
          this.learningProfileMsg.set({ type: 'success', text: '学习目标与阶段画像已保存。' });
        },
        error: (err) => {
          this.learningProfileSaving.set(false);
          const detail = err?.error?.detail ?? err?.error?.message ?? '保存失败，请稍后重试。';
          this.learningProfileMsg.set({ type: 'error', text: detail });
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
            this.rescheduleMessage.set({
              type: 'info',
              text: '导师当前没有可约的开放时间，稍后再来看看或与导师沟通。',
            });
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
    this.http.post(API.booking.reschedule(bookingId), { newSlotId }).subscribe({
      next: () => {
        this.rescheduleMessage.set({ type: 'success', text: '改期成功，课程表已更新。' });
        this.rescheduleBookingId.set(null);
        this.pickedSlotId.set(null);
        this.loadScheduleData();
        this.loadMyBookings(this.bookingsPage());
        this.load();
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
    if (!lesson || !question.trim()) return;
    this.asking.set(true);
    this.http
      .post(API.student.questions, { teacherId: lesson.teacherId, question: question.trim() })
      .subscribe({
        next: () => this.asking.set(false),
        error: () => this.asking.set(false),
      });
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

  toggleReviewTag(tag: string): void {
    this.reviewTags.update((tags) =>
      tags.includes(tag) ? tags.filter((t) => t !== tag) : tags.length >= 3 ? tags : [...tags, tag]
    );
  }

  submitReview(bookingId: string): void {
    if (this.reviewBusy()) return;
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

  enterClassroom(): void {
    const lesson = this.overview()?.nextLesson;
    if (!lesson || this.classroomBusy()) return;
    this.classroomBusy.set(true);
    this.classroomMsg.set(null);
    this.classroomLink.set(null);
    this.http
      .post<{ meetingUrl: string; provider: string; sessionStatus: string }>(
        API.classroom.join(lesson.bookingId),
        {}
      )
      .subscribe({
        next: (res) => {
          this.classroomBusy.set(false);
          const url = res?.meetingUrl ?? '';
          if (!url) {
            this.classroomMsg.set({
              type: 'info',
              text: '课堂尚未就绪：导师还未录入会议链接，可与导师确认后再来。',
            });
            return;
          }
          if (!/^https?:\/\//i.test(url)) {
            this.classroomMsg.set({
              type: 'error',
              text: '会议链接格式异常，已阻止打开，请联系导师重新录入。',
            });
            return;
          }
          const win = window.open(url, '_blank', 'noopener');
          if (!win) {
            this.classroomLink.set(url);
            this.classroomMsg.set({
              type: 'info',
              text: '浏览器拦截了新窗口，请点击下方链接进入课堂。',
            });
          }
        },
        error: (err) => {
          this.classroomBusy.set(false);
          const detail = err?.error?.detail ?? err?.error?.message ?? '进入课堂失败，请稍后重试。';
          this.classroomMsg.set({ type: 'error', text: detail });
        },
      });
  }

  loadLegacyWallet(page = this.legacyPage()): void {
    this.legacyLoading.set(true);
    this.legacyError.set(false);
    this.http
      .get<any>('/api/v1/student/me/legacy-wallet', { params: { page, size: 20 } })
      .subscribe({
        next: (w) => {
          this.legacyWallet.set(w);
          this.legacyPage.set(page);
          this.legacyLoading.set(false);
        },
        error: () => {
          this.legacyLoading.set(false);
          this.legacyError.set(true);
        },
      });
  }

  turnLegacyWallet(delta: number): void {
    if (
      this.legacyLoading() ||
      this.legacyPage() + delta < 0 ||
      this.legacyPage() + delta >= (this.legacyWallet()?.transactions.totalPages ?? 0)
    ) {
      return;
    }
    this.loadLegacyWallet(this.legacyPage() + delta);
  }
}
