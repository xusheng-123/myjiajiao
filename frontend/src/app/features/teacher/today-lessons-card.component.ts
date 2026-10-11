import { Component, OnInit, inject, signal, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { API } from '../../core/api/endpoints';

export interface TodayLesson {
  bookingId: string;
  subject: string;
  startAt: string;
  endAt: string;
  status: string;
}

export interface AvailabilitySlot {
  slotId: string;
  startAt: string;
  endAt: string;
  status: string;
}

export interface PreparationTask {
  taskId: string;
  title: string;
  sortIndex: number;
  completed: boolean;
}

export interface CancelPreviewResult {
  cascadeImpact: Record<string, unknown>;
  confirmationToken: string;
  confirmationTokenExpiresAt: string;
  notice: string;
}

@Component({
  selector: 'nur-today-lessons-card',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './today-lessons-card.component.html',
  styleUrl: './today-lessons-card.component.css',
})
export class TodayLessonsCardComponent implements OnInit {
  private readonly http = inject(HttpClient);

  readonly recordLesson = output<{ bookingId: string; subject: string }>();

  readonly todayLessons = signal<TodayLesson[]>([]);
  readonly loading = signal(false);
  readonly slots = signal<AvailabilitySlot[]>([]);

  // 状态流转操作反馈
  readonly actionNotice = signal<{ type: 'info' | 'error' | 'success'; text: string } | null>(null);

  // A2 状态操作
  readonly actionBusyId = signal<string | null>(null);
  readonly completedLessonHint = signal<{ bookingId: string; subject: string } | null>(null);

  // A1 两阶段取消预约
  readonly cancelBookingId = signal<string | null>(null);
  readonly cancelReason = signal('');
  readonly cancelPreviewData = signal<CancelPreviewResult | null>(null);
  readonly cancelBusy = signal(false);
  readonly cancelMessage = signal<{ type: 'info' | 'error' | 'success'; text: string } | null>(null);

  // 改期（D11）
  readonly rescheduleBookingId = signal<string | null>(null);
  readonly rescheduleCandidates = signal<AvailabilitySlot[]>([]);
  readonly rescheduleBusy = signal(false);
  readonly rescheduleMessage = signal<{ type: 'info' | 'error' | 'success'; text: string } | null>(null);
  readonly pickedRescheduleSlot = signal<string | null>(null);

  // 课堂准备（P1-4 & B6）
  readonly classroomBookingId = signal<string | null>(null);
  readonly meetingUrlInput = signal('');
  readonly classroomBusy = signal(false);
  readonly classroomMessage = signal<{ type: 'info' | 'error' | 'success'; text: string } | null>(null);
  readonly prepTasks = signal<PreparationTask[]>([]);
  readonly prepTasksLoading = signal(false);

  ngOnInit(): void {
    this.refreshLessons();
    this.refreshSlots();
  }

  refreshLessons(): void {
    this.loading.set(true);
    this.http.get<TodayLesson[]>(API.teacher.todayLessons).subscribe({
      next: (data) => {
        this.todayLessons.set(data ?? []);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  refreshSlots(): void {
    const from = new Date();
    const to = new Date(Date.now() + 14 * 24 * 3600 * 1000);
    this.http
      .get<AvailabilitySlot[]>(`${API.teacher.availabilityCalendar}?from=${from.toISOString()}&to=${to.toISOString()}`)
      .subscribe({ next: (data) => this.slots.set(data ?? []) });
  }

  // ---------- A2: 开始上课 & 完成课程 ----------

  startLesson(lesson: TodayLesson): void {
    if (this.actionBusyId()) return;
    this.actionBusyId.set(lesson.bookingId);
    this.actionNotice.set(null);

    this.http.post(API.booking.start(lesson.bookingId), {}).subscribe({
      next: () => {
        this.actionBusyId.set(null);
        this.actionNotice.set({
          type: 'success',
          text: `「${lesson.subject}」已开始上课，状态更新为进行中。`,
        });
        this.refreshLessons();
      },
      error: (err) => {
        this.actionBusyId.set(null);
        this.actionNotice.set({
          type: 'error',
          text: err?.error?.detail ?? '开始上课失败，请检查课程状态。',
        });
      },
    });
  }

  completeLesson(lesson: TodayLesson): void {
    if (this.actionBusyId()) return;
    this.actionBusyId.set(lesson.bookingId);
    this.actionNotice.set(null);

    this.http.post(API.booking.complete(lesson.bookingId), {}).subscribe({
      next: () => {
        this.actionBusyId.set(null);
        this.actionNotice.set({
          type: 'success',
          text: `「${lesson.subject}」已结课完成！请及时录入课堂记录以沉淀学习档案。`,
        });
        this.completedLessonHint.set({ bookingId: lesson.bookingId, subject: lesson.subject });
        this.refreshLessons();
      },
      error: (err) => {
        this.actionBusyId.set(null);
        this.actionNotice.set({
          type: 'error',
          text: err?.error?.detail ?? '完成课程操作失败，请重试。',
        });
      },
    });
  }

  triggerRecordLesson(bookingId: string, subject: string): void {
    this.recordLesson.emit({ bookingId, subject });
    this.completedLessonHint.set(null);
  }

  // ---------- A1: 导师侧两阶段取消预约 ----------

  startCancel(lesson: TodayLesson): void {
    this.cancelBookingId.set(lesson.bookingId);
    this.cancelReason.set('');
    this.cancelPreviewData.set(null);
    this.cancelMessage.set(null);
  }

  closeCancel(): void {
    this.cancelBookingId.set(null);
    this.cancelReason.set('');
    this.cancelPreviewData.set(null);
    this.cancelMessage.set(null);
  }

  onCancelReasonInput(val: string): void {
    this.cancelReason.set(val);
  }

  requestCancelPreview(): void {
    const bookingId = this.cancelBookingId();
    const reason = this.cancelReason().trim();
    if (!bookingId || !reason || this.cancelBusy()) return;

    this.cancelBusy.set(true);
    this.cancelMessage.set(null);

    this.http.post<CancelPreviewResult>(API.booking.cancelPreview(bookingId), { reason }).subscribe({
      next: (res) => {
        this.cancelBusy.set(false);
        this.cancelPreviewData.set(res);
      },
      error: (err) => {
        this.cancelBusy.set(false);
        this.cancelMessage.set({
          type: 'error',
          text: err?.error?.detail ?? '取消预览失败，请检查预约状态。',
        });
      },
    });
  }

  confirmCancelExecute(): void {
    const bookingId = this.cancelBookingId();
    const reason = this.cancelReason().trim();
    const preview = this.cancelPreviewData();
    if (!bookingId || !reason || !preview?.confirmationToken || this.cancelBusy()) return;

    this.cancelBusy.set(true);
    this.http
      .post(API.booking.cancelExecute(bookingId), {
        reason,
        confirmationToken: preview.confirmationToken,
      })
      .subscribe({
        next: () => {
          this.cancelBusy.set(false);
          this.actionNotice.set({
            type: 'info',
            text: '预约已取消，时段已按规则释放。',
          });
          this.closeCancel();
          this.refreshLessons();
          this.refreshSlots();
        },
        error: (err) => {
          this.cancelBusy.set(false);
          this.cancelMessage.set({
            type: 'error',
            text: err?.error?.detail ?? '取消执行失败，凭据可能已失效，请重新预览。',
          });
        },
      });
  }

  // ---------- 改期（D11） ----------

  startReschedule(lesson: TodayLesson): void {
    this.rescheduleBookingId.set(lesson.bookingId);
    this.rescheduleMessage.set(null);
    this.pickedRescheduleSlot.set(null);
    this.refreshSlots();
    const candidates = this.slots().filter((s) => s.status === 'OPEN');
    this.rescheduleCandidates.set(candidates);
    if (candidates.length === 0) {
      this.rescheduleMessage.set({ type: 'info', text: '目前没有其他开放档期，可先在「档期日历」发布新时段。' });
    }
  }

  pickRescheduleSlot(slotId: string): void {
    this.pickedRescheduleSlot.set(slotId);
    this.rescheduleMessage.set(null);
  }

  confirmReschedule(): void {
    const bookingId = this.rescheduleBookingId();
    const newSlotId = this.pickedRescheduleSlot();
    if (!bookingId || !newSlotId || this.rescheduleBusy()) return;

    this.rescheduleBusy.set(true);
    this.http.post(API.booking.reschedule(bookingId), { newSlotId }).subscribe({
      next: () => {
        this.rescheduleMessage.set({ type: 'success', text: '改期成功，课程安排已更新。' });
        this.rescheduleBookingId.set(null);
        this.pickedRescheduleSlot.set(null);
        this.rescheduleBusy.set(false);
        this.refreshSlots();
        this.refreshLessons();
      },
      error: (err) => {
        this.rescheduleBusy.set(false);
        const detail = err?.error?.detail ?? '改期未成功，所选时段可能刚被占用，请重新选择。';
        this.rescheduleMessage.set({ type: 'error', text: detail });
      },
    });
  }

  cancelReschedule(): void {
    this.rescheduleBookingId.set(null);
    this.pickedRescheduleSlot.set(null);
    this.rescheduleMessage.set(null);
  }

  // ---------- 课堂准备（P1-4 & B6） ----------

  startClassroom(lesson: TodayLesson): void {
    this.classroomBookingId.set(lesson.bookingId);
    this.meetingUrlInput.set('');
    this.classroomMessage.set(null);
    this.loadPrepTasks(lesson.bookingId);
  }

  cancelClassroom(): void {
    this.classroomBookingId.set(null);
    this.meetingUrlInput.set('');
    this.classroomMessage.set(null);
    this.prepTasks.set([]);
  }

  onMeetingUrlInput(value: string): void {
    this.meetingUrlInput.set(value);
  }

  registerMeetingUrl(): void {
    const bookingId = this.classroomBookingId();
    const url = this.meetingUrlInput().trim();
    if (!bookingId || this.classroomBusy()) return;

    if (!/^https?:\/\//.test(url)) {
      this.classroomMessage.set({ type: 'error', text: '请粘贴以 http(s):// 开头的完整会议链接。' });
      return;
    }
    this.classroomBusy.set(true);
    this.http.post(API.classroom.ensureSession(bookingId), { meetingUrl: url }).subscribe({
      next: () => {
        this.classroomBusy.set(false);
        this.classroomMessage.set({ type: 'success', text: '会议链接已登记，学生端「进入课堂」入口已就绪。' });
      },
      error: (err) => {
        this.classroomBusy.set(false);
        this.classroomMessage.set({ type: 'error', text: err?.error?.detail ?? '登记未成功，请稍后重试。' });
      },
    });
  }

  joinClassroom(bookingId: string): void {
    this.http.post<{ meetingUrl?: string; provider?: string }>(API.classroom.join(bookingId), {}).subscribe({
      next: (res) => {
        if (res.meetingUrl) {
          window.open(res.meetingUrl, '_blank', 'noopener,noreferrer');
        } else {
          this.classroomMessage.set({ type: 'info', text: '课堂入口凭证已就绪。' });
        }
      },
      error: (err) => {
        this.classroomMessage.set({
          type: 'error',
          text: err?.error?.detail ?? '课堂尚未准备就绪，请先登记会议链接。',
        });
      },
    });
  }

  endLesson(): void {
    const bookingId = this.classroomBookingId();
    if (!bookingId || this.classroomBusy()) return;

    this.classroomBusy.set(true);
    this.http.post(API.classroom.endSession(bookingId), {}).subscribe({
      next: () => {
        this.classroomBusy.set(false);
        this.classroomMessage.set({ type: 'success', text: '课程已结束，请标记学生考勤。' });
        this.refreshLessons();
      },
      error: (err) => {
        this.classroomBusy.set(false);
        this.classroomMessage.set({ type: 'error', text: err?.error?.detail ?? '结课未成功，请稍后重试。' });
      },
    });
  }

  markAttendance(status: 'PRESENT' | 'LATE' | 'ABSENT'): void {
    const bookingId = this.classroomBookingId();
    if (!bookingId || this.classroomBusy()) return;

    this.classroomBusy.set(true);
    this.http.post(API.classroom.attendance(bookingId), { attendanceStatus: status }).subscribe({
      next: () => {
        this.classroomBusy.set(false);
        this.classroomMessage.set({ type: 'success', text: '考勤已记录。' });
      },
      error: (err) => {
        this.classroomBusy.set(false);
        this.classroomMessage.set({ type: 'error', text: err?.error?.detail ?? '考勤记录未成功，请稍后重试。' });
      },
    });
  }

  // ---------- B6: 课前任务生成与查看 ----------

  loadPrepTasks(bookingId: string): void {
    this.prepTasksLoading.set(true);
    this.http.get<PreparationTask[]>(API.classroom.preparationTasks(bookingId)).subscribe({
      next: (tasks) => {
        this.prepTasks.set(tasks ?? []);
        this.prepTasksLoading.set(false);
      },
      error: () => this.prepTasksLoading.set(false),
    });
  }

  generatePrepTasks(): void {
    const bookingId = this.classroomBookingId();
    if (!bookingId || this.prepTasksLoading()) return;

    this.prepTasksLoading.set(true);
    this.http.post<PreparationTask[]>(API.classroom.preparationTasks(bookingId), {}).subscribe({
      next: (tasks) => {
        this.prepTasks.set(tasks ?? []);
        this.prepTasksLoading.set(false);
        this.classroomMessage.set({ type: 'success', text: '课前任务清单已生成，学生端可逐项打卡准备！' });
      },
      error: (err) => {
        this.prepTasksLoading.set(false);
        this.classroomMessage.set({ type: 'error', text: err?.error?.detail ?? '生成课前任务清单失败。' });
      },
    });
  }
}
