import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { HttpClient } from '@angular/common/http';
import { API } from '../../core/api/endpoints';
import { NoticeCenterComponent } from '../../shared/components/notice-center.component';
import { TeacherProfileEditorComponent } from '../../shared/components/teacher-profile-editor.component';

interface VerificationStatus {
  status: string;
  decisionNote: string | null;
  submittedAt: string | null;
  decidedAt: string | null;
  guidance: string;
}

/** G5 已选择的待上传材料（仅文件名/大小用于展示，不上传前不离开浏览器）。 */
interface PickedFile {
  file: File;
  name: string;
  sizeLabel: string;
}

/** G6 开课条目（服务端仅返回在售条目；active 字段如实展示）。 */
interface SubjectOffering {
  offeringId: string;
  subject: string;
  gradeBand: string | null;
  pricePerHourCents: number;
  active: boolean;
}

interface TodayLesson {
  bookingId: string;
  subject: string;
  startAt: string;
  endAt: string;
  status: string;
}

interface OpenDemand {
  demandId: string;
  subject: string;
  gradeBand: string | null;
  note: string | null;
  budgetMaxCents: number | null;
  preferredTime: string | null;
}

interface BookingRequest {
  bookingId: string;
  studentId: string;
  subject: string;
  type: string;
  status: string;
  createdAt: string;
}

interface Settlement {
  pendingNetCents: number;
  paidOutNetCents: number;
  entries: {
    entryId: string;
    bookingId: string;
    grossCents: number;
    platformFeeCents: number;
    netCents: number;
    status: string;
    settledAt: string | null;
    entryKind: 'EARNING' | 'REVERSAL';
    reversesEntryId: string | null;
  }[];
}

interface AvailabilitySlot {
  slotId: string;
  startAt: string;
  endAt: string;
  status: string;
}

/**
 * 导师工作台 — 资质认证状态流、档期日历、预约审批面板与课酬结算。
 */
@Component({
  selector: 'nur-teacher-dashboard',
  standalone: true,
  imports: [DatePipe, NoticeCenterComponent, RouterLink, TeacherProfileEditorComponent],
  templateUrl: './teacher-dashboard.component.html',
  styleUrl: './teacher-dashboard.component.css',
})
export class TeacherDashboardComponent implements OnInit {
  private readonly http = inject(HttpClient);
  readonly auth = inject(AuthService);

  readonly displayName = this.auth.displayName;
  readonly verification = signal<VerificationStatus | null>(null);
  readonly todayLessons = signal<TodayLesson[]>([]);
  readonly openDemands = signal<OpenDemand[]>([]);
  readonly bookingRequests = signal<BookingRequest[]>([]);
  readonly settlement = signal<Settlement | null>(null);
  readonly slots = signal<AvailabilitySlot[]>([]);
  readonly notice = signal('');
  readonly loaded = signal(false);

  // ---------- 资质材料上传（G5）----------
  // 服务端真实限制：单个文件 ≤20MB（multipart 硬限制）；服务端当前不限文件类型，
  // 前端只做大小与必填校验（体验优化），最终以服务端校验为准。
  private static readonly MAX_FILE_BYTES = 20 * 1024 * 1024;
  readonly idDocumentFile = signal<PickedFile | null>(null);
  readonly degreeCertFile = signal<PickedFile | null>(null);
  readonly verificationBusy = signal(false);
  readonly verificationMessage = signal<{ type: 'info' | 'error' | 'success'; text: string } | null>(null);
  /** 未提交或被驳回时可（重新）提交材料；审核中/已通过不再展示表单。 */
  readonly canSubmitVerification = computed(() =>
    this.idDocumentFile() !== null && this.degreeCertFile() !== null && !this.verificationBusy());
  /** 未提交或被驳回时可（重新）提交材料；审核中/已通过不再展示表单。状态加载完成前不展示，避免闪现。 */
  readonly showVerificationForm = computed(() => {
    if (!this.loaded()) {
      return false;
    }
    const status = this.verification()?.status;
    return status === undefined || status === 'NOT_SUBMITTED' || status === 'REJECTED';
  });

  // ---------- 课程与定价管理（G6）----------
  // 下架 = DELETE /subjects/{id}：服务端语义为「停止销售」（active=false 标记，非删除记录），
  // 服务端无重新上架端点，故界面不提供该入口；调价/金额一律以服务端返回为准。
  readonly subjects = signal<SubjectOffering[]>([]);
  readonly subjectsLoaded = signal(false);
  readonly subjectsError = signal(false);
  readonly subjectsNotice = signal<{ type: 'info' | 'error' | 'success'; text: string } | null>(null);
  readonly newSubjectName = signal('');
  readonly newSubjectGradeBand = signal('');
  readonly newSubjectPriceYuan = signal('');
  readonly addSubjectBusy = signal(false);
  readonly repricingId = signal<string | null>(null);
  readonly repriceInputYuan = signal('');
  readonly repriceBusy = signal(false);
  readonly delistingId = signal<string | null>(null);
  readonly delistBusy = signal(false);

  // ---------- 改期（D11）：候选时段来自自己的开放档期 ----------
  readonly rescheduleBookingId = signal<string | null>(null);
  readonly rescheduleCandidates = signal<AvailabilitySlot[]>([]);
  readonly rescheduleBusy = signal(false);
  readonly rescheduleMessage = signal<{ type: 'info' | 'error' | 'success'; text: string } | null>(null);
  readonly pickedRescheduleSlot = signal<string | null>(null);

  // ---------- 课堂准备（P1-4）：登记外部会议链接 / 结课 / 考勤 ----------
  readonly classroomBookingId = signal<string | null>(null);
  readonly meetingUrlInput = signal('');
  readonly classroomBusy = signal(false);
  readonly classroomMessage = signal<{ type: 'info' | 'error' | 'success'; text: string } | null>(null);

  ngOnInit(): void {
    this.http.get<VerificationStatus>(API.teacher.verificationStatus).subscribe({
      next: (data) => {
        this.verification.set(data);
        this.loaded.set(true);
      },
      error: () => this.loaded.set(true),
    });
    this.http.get<TodayLesson[]>(API.teacher.todayLessons).subscribe({
      next: (data) => this.todayLessons.set(data ?? []),
    });
    this.http
      .get<{ content: OpenDemand[] }>(`${API.matching.openDemands}?page=0&size=5`)
      .subscribe({ next: (data) => this.openDemands.set(data.content ?? []) });
    this.refreshRequests();
    this.http.get<Settlement>(API.teacher.settlement).subscribe({ next: (d) => this.settlement.set(d) });
    this.refreshSlots();
    this.loadSubjects();
  }

  /** G6：加载我的开课条目（包含在售与已下架条目）。 */
  loadSubjects(): void {
    this.http.get<SubjectOffering[]>(API.teacher.subjects, {params:{includeInactive:true}}).subscribe({
      next: (data) => {
        this.subjects.set(data ?? []);
        this.subjectsLoaded.set(true);
        this.subjectsError.set(false);
      },
      error: () => {
        this.subjectsLoaded.set(true);
        this.subjectsError.set(true);
      },
    });
  }

  refreshRequests(): void {
    this.http.get<BookingRequest[]>(`${API.teacher.bookingRequests}?page=0&size=20`).subscribe({
      next: (data) => this.bookingRequests.set(data),
    });
  }

  refreshSlots(): void {
    const from = new Date();
    const to = new Date(Date.now() + 14 * 24 * 3600 * 1000);
    this.http
      .get<AvailabilitySlot[]>(`${API.teacher.availabilityCalendar}?from=${from.toISOString()}&to=${to.toISOString()}`)
      .subscribe({ next: (data) => this.slots.set(data) });
  }

  // ---------- 课程与定价管理（G6）方法 ----------

  onNewSubjectField(field: 'name' | 'gradeBand' | 'price', value: string): void {
    if (field === 'name') {
      this.newSubjectName.set(value);
    } else if (field === 'gradeBand') {
      this.newSubjectGradeBand.set(value);
    } else {
      this.newSubjectPriceYuan.set(value);
    }
  }

  /** 新增开课条目：服务端 D9 幂等（同科目+学段重复创建返回既有条目）。 */
  addSubject(): void {
    const subject = this.newSubjectName().trim();
    const gradeBand = this.newSubjectGradeBand().trim();
    const price = parseYuanToCents(this.newSubjectPriceYuan());
    if (this.addSubjectBusy()) {
      return;
    }
    if (!subject || !gradeBand) {
      this.subjectsNotice.set({ type: 'error', text: '请填写科目与学段。' });
      return;
    }
    if (price === null || price <= 0) {
      this.subjectsNotice.set({ type: 'error', text: '课时定价需为正数（元/小时，最多两位小数）。' });
      return;
    }
    const knownIds = new Set(this.subjects().map((s) => s.offeringId));
    this.addSubjectBusy.set(true);
    this.subjectsNotice.set(null);
    this.http
      .post<SubjectOffering>(API.teacher.subjects, { subject, gradeBand, pricePerHourCents: price })
      .subscribe({
        next: (o) => {
          this.addSubjectBusy.set(false);
          // 重复添加时服务端返回既有条目（D9），如实提示而非谎称新建
          const existed = knownIds.has(o.offeringId);
          this.subjectsNotice.set({
            type: existed ? 'info' : 'success',
            text: existed
              ? `该科目与学段已有在售条目（当前 ¥${this.yuan(o.pricePerHourCents)}/小时），未重复创建；如需改价请用「调价」。`
              : `已新增「${o.subject} · ${o.gradeBand}」，学生在检索中即可看到。`,
          });
          if (!existed) {
            this.subjects.update((list) => [...list, o]);
          }
          this.newSubjectName.set('');
          this.newSubjectGradeBand.set('');
          this.newSubjectPriceYuan.set('');
        },
        error: (err) => {
          this.addSubjectBusy.set(false);
          this.subjectsNotice.set({ type: 'error', text: err?.error?.detail ?? '新增未成功，请稍后重试。' });
        },
      });
  }

  startReprice(offering: SubjectOffering): void {
    this.repricingId.set(offering.offeringId);
    this.repriceInputYuan.set(this.yuan(offering.pricePerHourCents));
    this.subjectsNotice.set(null);
  }

  cancelReprice(): void {
    this.repricingId.set(null);
    this.repriceInputYuan.set('');
  }

  onRepriceInput(value: string): void {
    this.repriceInputYuan.set(value);
  }

  /** 调价：原价仅供确认，新价以服务端校验与返回为准；已生成订单金额不受影响。 */
  confirmReprice(offering: SubjectOffering): void {
    if (this.repriceBusy()) {
      return;
    }
    const cents = parseYuanToCents(this.repriceInputYuan());
    if (cents === null || cents <= 0) {
      this.subjectsNotice.set({ type: 'error', text: '新价格需为正数（元/小时，最多两位小数）。' });
      return;
    }
    this.repriceBusy.set(true);
    this.http
      .put<SubjectOffering>(API.teacher.pricing(offering.offeringId), { pricePerHourCents: cents })
      .subscribe({
        next: (updated) => {
          this.repriceBusy.set(false);
          this.repricingId.set(null);
          this.repriceInputYuan.set('');
          // 直接采用服务端返回价刷新列表，不在前端臆造
          this.subjects.update((list) => list.map((s) => (s.offeringId === updated.offeringId ? updated : s)));
          this.subjectsNotice.set({
            type: 'success',
            text: `「${updated.subject} · ${updated.gradeBand}」已调至 ¥${this.yuan(updated.pricePerHourCents)}/小时。仅影响之后的新订单，已生成订单金额不变。`,
          });
        },
        error: (err) => {
          this.repriceBusy.set(false);
          this.subjectsNotice.set({ type: 'error', text: err?.error?.detail ?? '调价未成功，请稍后重试。' });
        },
      });
  }

  readonly relistBusy=signal(false);
  relist(offering:SubjectOffering):void {
    if(this.relistBusy())return;
    this.relistBusy.set(true);
    this.http.post<SubjectOffering>(API.teacher.subjects+'/'+offering.offeringId+':activate',{}).subscribe({
      next: updated => {this.relistBusy.set(false);this.subjects.update(list=>list.map(o=>o.offeringId===updated.offeringId?updated:o));this.subjectsNotice.set({type:'success',text:'课程已重新上架，公开检索恢复展示。'});},
      error: e => {this.relistBusy.set(false);this.subjectsNotice.set({type:'error',text:e.error?.detail??'重新上架失败，请稍后重试。'});},
    });
  }

  askDelist(offering: SubjectOffering): void {
    this.delistingId.set(offering.offeringId);
    this.subjectsNotice.set(null);
  }

  cancelDelist(): void {
    this.delistingId.set(null);
  }

  /** 下架（停止销售）：二次确认后调用 DELETE；服务端为标记语义，不删除记录，保留重新上架入口。 */
  confirmDelist(offering: SubjectOffering): void {
    if (this.delistBusy()) {
      return;
    }
    this.delistBusy.set(true);
    this.http.delete(API.teacher.subjectRemove(offering.offeringId)).subscribe({
      next: () => {
        this.delistBusy.set(false);
        this.delistingId.set(null);
        this.loadSubjects(); // 以服务端列表为准：在售条目中不再出现
        this.subjectsNotice.set({
          type: 'success',
          text: `「${offering.subject} · ${offering.gradeBand}」已下架（停止销售）：学生无法再检索到或预约该条目；已生成订单不受影响。`,
        });
      },
      error: (err) => {
        this.delistBusy.set(false);
        this.subjectsNotice.set({ type: 'error', text: err?.error?.detail ?? '下架未成功，请稍后重试。' });
      },
    });
  }

  // ---------- 资质材料上传（G5）----------

  /** 选择材料文件：前端只做大小与必填校验（体验优化），最终以服务端校验为准。 */
  onVerificationFilePick(part: 'idDocument' | 'degreeCert', event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    if (!file) {
      return;
    }
    if (file.size > TeacherDashboardComponent.MAX_FILE_BYTES) {
      this.verificationMessage.set({ type: 'error', text: `「${file.name}」超过 20MB 限制，请压缩后重新选择。` });
      input.value = '';
      return;
    }
    this.verificationMessage.set(null);
    const picked: PickedFile = { file, name: file.name, sizeLabel: formatBytes(file.size) };
    if (part === 'idDocument') {
      this.idDocumentFile.set(picked);
    } else {
      this.degreeCertFile.set(picked);
    }
    input.value = '';
  }

  removeVerificationFile(part: 'idDocument' | 'degreeCert'): void {
    if (part === 'idDocument') {
      this.idDocumentFile.set(null);
    } else {
      this.degreeCertFile.set(null);
    }
  }

  /** 提交资质材料：multipart 双必填；成功后直接采用服务端返回的最新审核状态，不在前端臆造。 */
  submitVerification(): void {
    const idDoc = this.idDocumentFile();
    const degree = this.degreeCertFile();
    if (!idDoc || !degree || this.verificationBusy()) {
      return;
    }
    const form = new FormData();
    form.append('idDocument', idDoc.file, idDoc.name);
    form.append('degreeCert', degree.file, degree.name);
    this.verificationBusy.set(true);
    this.verificationMessage.set(null);
    this.http.post<VerificationStatus>(API.teacher.verificationSubmit, form).subscribe({
      next: (v) => {
        this.verification.set(v);
        this.idDocumentFile.set(null);
        this.degreeCertFile.set(null);
        this.verificationBusy.set(false);
        this.verificationMessage.set({ type: 'success', text: '材料已提交，以下为服务端返回的最新审核状态。' });
      },
      error: (err) => {
        this.verificationBusy.set(false);
        const detail = err?.error?.detail;
        this.verificationMessage.set({
          type: 'error',
          text: detail ?? '提交未成功，请确认两个材料文件都已选择后重试；多次失败请联系运营。',
        });
      },
    });
  }

  /** 发布可用时段：快捷模板按钮（次日/后天固定时段）。 */
  publishSlot(dayOffset: number, hour: number): void {
    const start = new Date();
    start.setDate(start.getDate() + dayOffset);
    start.setHours(hour, 0, 0, 0);
    const end = new Date(start.getTime() + 3600 * 1000);
    this.http
      .post(API.teacher.availabilityPublish, { startAt: start.toISOString(), endAt: end.toISOString() })
      .subscribe({
        next: () => {
          this.notice.set('可用时段已发布。');
          this.refreshSlots();
        },
        error: () => this.notice.set('发布未成功：时段可能与现有档期重叠，请调整时间。'),
      });
  }

  /** 改期（D11）：导师把自己的已确认课程改到另一个开放档期。 */
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
    if (!bookingId || !newSlotId || this.rescheduleBusy()) {
      return;
    }
    this.rescheduleBusy.set(true);
    this.http
      .post(API.booking.reschedule(bookingId), { newSlotId })
      .subscribe({
        next: () => {
          this.rescheduleMessage.set({ type: 'success', text: '改期成功，课程安排已更新。' });
          this.rescheduleBookingId.set(null);
          this.pickedRescheduleSlot.set(null);
          this.rescheduleBusy.set(false);
          this.refreshSlots();
          this.http.get<TodayLesson[]>(API.teacher.todayLessons).subscribe({
            next: (data) => this.todayLessons.set(data ?? []),
          });
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

  // ---------- 课堂准备（P1-4）----------

  startClassroom(lesson: TodayLesson): void {
    this.classroomBookingId.set(lesson.bookingId);
    this.meetingUrlInput.set('');
    this.classroomMessage.set(null);
  }

  cancelClassroom(): void {
    this.classroomBookingId.set(null);
    this.meetingUrlInput.set('');
    this.classroomMessage.set(null);
  }

  onMeetingUrlInput(value: string): void {
    this.meetingUrlInput.set(value);
  }

  /** 登记外部会议链接（腾讯会议/飞书等），形成学生端「进入课堂」入口。 */
  registerMeetingUrl(): void {
    const bookingId = this.classroomBookingId();
    const url = this.meetingUrlInput().trim();
    if (!bookingId || this.classroomBusy()) {
      return;
    }
    if (!/^https?:\/\//.test(url)) {
      this.classroomMessage.set({ type: 'error', text: '请粘贴以 http(s):// 开头的完整会议链接。' });
      return;
    }
    this.classroomBusy.set(true);
    this.http
      .post(API.classroom.ensureSession(bookingId), { meetingUrl: url })
      .subscribe({
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

  /** 结课：结束后才可标记考勤。 */
  endLesson(): void {
    const bookingId = this.classroomBookingId();
    if (!bookingId || this.classroomBusy()) {
      return;
    }
    this.classroomBusy.set(true);
    this.http
      .post(API.classroom.endSession(bookingId), {})
      .subscribe({
        next: () => {
          this.classroomBusy.set(false);
          this.classroomMessage.set({ type: 'success', text: '课程已结束，请标记学生考勤。' });
          this.refreshTodayLessons();
        },
        error: (err) => {
          this.classroomBusy.set(false);
          this.classroomMessage.set({ type: 'error', text: err?.error?.detail ?? '结课未成功，请稍后重试。' });
        },
      });
  }

  /** 标记考勤：PRESENT 正常 / LATE 迟到 / ABSENT 缺席。 */
  markAttendance(status: 'PRESENT' | 'LATE' | 'ABSENT'): void {
    const bookingId = this.classroomBookingId();
    if (!bookingId || this.classroomBusy()) {
      return;
    }
    this.classroomBusy.set(true);
    this.http
      .post(API.classroom.attendance(bookingId), { attendanceStatus: status })
      .subscribe({
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

  private refreshTodayLessons(): void {
    this.http.get<TodayLesson[]>(API.teacher.todayLessons).subscribe({
      next: (data) => this.todayLessons.set(data ?? []),
    });
  }

  decide(bookingId: string, decision: 'approve' | 'reject'): void {
    this.http
      .post(API.teacher.decideBooking(bookingId) + `?decision=${decision}`, {})
      .subscribe({
        next: () => {
          this.notice.set(decision === 'approve' ? '已确认该预约，时段已锁定。' : '已驳回，档期已释放。');
          this.refreshRequests();
        },
        error: () => this.notice.set('操作未成功，请稍后原地重试。'),
      });
  }

  yuan(cents: number): string {
    return (cents / 100).toFixed(2);
  }
}

function formatBytes(size: number): string {
  if (size >= 1024 * 1024) {
    return `${(size / (1024 * 1024)).toFixed(1)} MB`;
  }
  if (size >= 1024) {
    return `${Math.round(size / 1024)} KB`;
  }
  return `${size} B`;
}

/** 元字符串 → 分（整数）；非法/负数/超两位小数返回 null。金额换算仅用于提交表单，订单金额一律由服务端决定。 */
function parseYuanToCents(input: string): number | null {
  const trimmed = input.trim();
  if (!/^\d+(\.\d{1,2})?$/.test(trimmed)) {
    return null;
  }
  return Math.round(parseFloat(trimmed) * 100);
}
