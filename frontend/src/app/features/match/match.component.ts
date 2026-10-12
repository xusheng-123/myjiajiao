import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink, Router, ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { API } from '../../core/api/endpoints';
import { AuthService } from '../../core/auth/auth.service';
import { MatchStepperComponent } from './match-stepper.component';
import { MatchDemandStepComponent, DemandDraft, DemandView } from './match-demand-step.component';
import { MatchRecommendStepComponent, TeacherCandidate, TimeFilter } from './match-recommend-step.component';
import { MatchSlotsStepComponent, OpenSlot } from './match-slots-step.component';
import { MatchConfirmStepComponent } from './match-confirm-step.component';

/**
 * 找老师 — 步进式向导（方向 D · 黛绿暖金）
 * P0-1 重构：
 * 需求表单(1) → 推荐结果(2) → 档期选择(3) → 预约确认(4)
 * 四步 Stepper，支持进度指示、可回退、表单草稿持久保留，视觉样式完全自包含
 */
@Component({
  selector: 'nur-match',
  standalone: true,
  imports: [
    RouterLink,
    MatchStepperComponent,
    MatchDemandStepComponent,
    MatchRecommendStepComponent,
    MatchSlotsStepComponent,
    MatchConfirmStepComponent,
  ],
  templateUrl: './match.component.html',
  styleUrl: './match.component.css',
})
export class MatchComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  readonly auth = inject(AuthService);

  // ---------- 向导进度控制 ----------
  readonly step = signal<number>(1);
  readonly maxReachedStep = signal<number>(1);

  // ---------- 步骤一：需求表单草稿 ----------
  readonly demandDraft = signal<DemandDraft>({
    subject: '',
    gradeBand: '',
    budget: '',
    preferredTime: '',
    note: '',
  });
  readonly demandSuccess = signal(false);
  readonly demandPublishing = signal(false);
  readonly myDemands = signal<DemandView[]>([]);

  // ---------- 步骤二：候选导师列表与筛选 ----------
  readonly candidates = signal<TeacherCandidate[]>([]);
  readonly loading = signal(false);
  readonly searched = signal(false);
  readonly timeFilter = signal<TimeFilter>('any');
  readonly selectedTeacherId = signal<string | null>(null);

  readonly filteredCandidates = computed(() => {
    const filter = this.timeFilter();
    if (filter === 'any') {
      return this.candidates();
    }
    return this.candidates().filter((t) => withinWindow(t.nextOpenSlotAt, filter));
  });

  // 收藏与对比
  readonly favoriteIds = signal<Set<string>>(new Set());
  readonly favBusyIds = signal<Set<string>>(new Set());
  readonly favNotice = signal('');
  readonly compared = signal<TeacherCandidate[]>([]);
  readonly consultBusy = signal(false);

  // ---------- 步骤三：选定导师与档期 ----------
  readonly wizardTeacher = signal<TeacherCandidate | null>(null);
  readonly bookingType = signal<'TRIAL' | 'FORMAL'>('TRIAL');
  readonly trialAvailable = signal<boolean | null>(null);
  readonly trialReason = signal('');
  readonly wizardSlots = signal<OpenSlot[]>([]);
  readonly selectedSlot = signal<OpenSlot | null>(null);
  readonly slotInvalid = signal<string | null>(null);

  // ---------- 步骤四：预约提交反馈 ----------
  readonly submitting = signal(false);
  readonly wizardNotice = signal('');
  readonly wizardSuccess = signal(false);

  ngOnInit(): void {
    const queryTeacher = this.route.snapshot.queryParamMap.get('teacher');
    const querySubject = this.route.snapshot.queryParamMap.get('subject');

    if (queryTeacher) {
      this.selectedTeacherId.set(queryTeacher);
    }
    if (querySubject) {
      this.demandDraft.update((d) => ({ ...d, subject: querySubject }));
      this.search();
      this.step.set(2);
      this.maxReachedStep.set(2);
    }

    if (this.auth.isLoggedIn()) {
      this.loadFavoriteIds();
      this.loadMyDemands();
    }
  }

  // ---------- 步骤切换 ----------
  goToStep(targetStep: number): void {
    if (targetStep <= this.maxReachedStep()) {
      this.step.set(targetStep);
    }
  }

  /** 右栏「可约时间」事实行文案 */
  timeFilterLabel(): string {
    const f = this.timeFilter();
    return f === 'today' ? '今日可约' : f === '3d' ? '3 天内可约' : f === '7d' ? '7 天内可约' : '不限时段';
  }

  // ---------- 步骤一业务 ----------
  onProceedFromDemand(): void {
    this.search();
    this.step.set(2);
    this.maxReachedStep.update((m) => Math.max(m, 2));
  }

  onPublishDemand(): void {
    const draft = this.demandDraft();
    if (!draft.subject.trim()) return;

    this.demandPublishing.set(true);
    this.http
      .post<DemandView>(API.matching.demands, {
        subject: draft.subject.trim(),
        gradeBand: draft.gradeBand.trim() || null,
        note: draft.note.trim() || null,
        budgetMaxCents: draft.budget.trim() ? Math.round(Number(draft.budget) * 100) : null,
        preferredTime: draft.preferredTime.trim() || null,
      })
      .subscribe({
        next: () => {
          this.demandPublishing.set(false);
          this.demandSuccess.set(true);
          this.loadMyDemands();
        },
        error: () => {
          this.demandPublishing.set(false);
        },
      });
  }

  loadMyDemands(): void {
    this.http.get<{ content: DemandView[] }>(`${API.matching.demands}?page=0&size=5`).subscribe({
      next: (data) => this.myDemands.set(data.content ?? []),
    });
  }

  // ---------- 步骤二检索与交互 ----------
  search(): void {
    this.loading.set(true);
    this.searched.set(true);

    const draft = this.demandDraft();
    const params = new URLSearchParams();
    if (this.selectedTeacherId()) params.set('teacherId', this.selectedTeacherId()!);
    if (draft.subject.trim()) params.set('subject', draft.subject.trim());
    if (draft.gradeBand.trim()) params.set('gradeBand', draft.gradeBand.trim());
    if (draft.budget.trim()) {
      params.set('maxPricePerHourCents', String(Math.round(Number(draft.budget) * 100)));
    }

    this.http.get<TeacherCandidate[]>(`${API.matching.search}?${params.toString()}`).subscribe({
      next: (data) => {
        this.candidates.set(data ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.candidates.set([]);
        this.loading.set(false);
      },
    });
  }

  setTimeFilter(filter: TimeFilter): void {
    this.timeFilter.set(filter);
  }

  loadFavoriteIds(): void {
    this.http.get<{ teacherId: string }[]>(API.matching.favorites).subscribe({
      next: (list) => {
        this.favoriteIds.set(new Set((list ?? []).map((f) => f.teacherId)));
      },
    });
  }

  toggleFavorite(teacher: TeacherCandidate): void {
    if (!this.auth.isLoggedIn()) {
      this.favNotice.set('登录后即可收藏导师');
      return;
    }
    if (this.favBusyIds().has(teacher.teacherId)) return;

    this.favNotice.set('');
    this.favBusyIds.update((s) => new Set(s).add(teacher.teacherId));
    this.http
      .post<{ teacherId: string; favorited: boolean }>(API.matching.toggleFavorite(teacher.teacherId), {})
      .subscribe({
        next: (res) => {
          this.favBusyIds.update((s) => {
            const next = new Set(s);
            next.delete(teacher.teacherId);
            return next;
          });
          this.favoriteIds.update((s) => {
            const next = new Set(s);
            if (res.favorited) next.add(res.teacherId);
            else next.delete(res.teacherId);
            return next;
          });
          this.favNotice.set(res.favorited ? '已加入收藏' : '已取消收藏');
        },
        error: () => {
          this.favBusyIds.update((s) => {
            const next = new Set(s);
            next.delete(teacher.teacherId);
            return next;
          });
          this.favNotice.set('收藏操作未成功，请重试');
        },
      });
  }

  consult(t: TeacherCandidate): void {
    if (this.consultBusy()) return;
    this.consultBusy.set(true);
    this.http
      .post<{ conversationId: string }>(API.messaging.conversations, {
        teacherId: t.teacherId,
        subject: t.subject,
      })
      .subscribe({
        next: (c) => {
          this.consultBusy.set(false);
          void this.router.navigate(['/messages'], { queryParams: { conversation: c.conversationId } });
        },
        error: (e) => {
          this.consultBusy.set(false);
          this.favNotice.set(e.error?.detail ?? '暂时无法发起咨询，请重试。');
        },
      });
  }

  toggleCompare(t: TeacherCandidate): void {
    if (this.compared().some((c) => c.teacherId === t.teacherId)) {
      this.removeCompare(t.teacherId);
      return;
    }
    if (this.compared().length >= 3) {
      this.favNotice.set('最多对比 3 位导师，请先移除一位。');
      return;
    }
    this.compared.update((rows) => [...rows, t]);
    this.favNotice.set('');
  }

  removeCompare(id: string): void {
    this.compared.update((rows) => rows.filter((t) => t.teacherId !== id));
  }

  onSelectTeacher(payload: { teacher: TeacherCandidate; bookingType: 'TRIAL' | 'FORMAL' }): void {
    this.wizardTeacher.set(payload.teacher);
    this.bookingType.set(payload.bookingType);
    this.selectedSlot.set(null);
    this.slotInvalid.set(null);
    this.wizardNotice.set('');
    this.wizardSuccess.set(false);
    this.wizardSlots.set([]);

    this.trialAvailable.set(null);
    this.trialReason.set('正在核查免费试听名额…');
    if (payload.bookingType === 'TRIAL') {
      this.http
        .get<{ available: boolean; reason: string }>(
          `/api/v1/booking/trial-eligibility?teacherId=${payload.teacher.teacherId}`,
        )
        .subscribe({
          next: (r) => {
            if (this.wizardTeacher()?.teacherId === payload.teacher.teacherId) {
              this.trialAvailable.set(r.available);
              this.trialReason.set(r.reason);
            }
          },
          error: () => {
            this.trialReason.set('暂时无法核查名额，可直接改约正式课。');
          },
        });
    }

    this.http
      .get<OpenSlot[]>(API.scheduling.openSlots(payload.teacher.teacherId))
      .subscribe({
        next: (slots) => this.wizardSlots.set(slots ?? []),
      });

    this.step.set(3);
    this.maxReachedStep.update((m) => Math.max(m, 3));
  }

  // ---------- 步骤三档期业务 ----------
  onPickSlot(slot: OpenSlot): void {
    this.selectedSlot.set(slot);
    this.slotInvalid.set(null);
  }

  onSwitchBookingType(type: 'TRIAL' | 'FORMAL'): void {
    this.bookingType.set(type);
  }

  onToConfirmStep(): void {
    if (this.bookingType() === 'TRIAL' && this.trialAvailable() !== true) return;
    if (!this.selectedSlot()) return;

    this.step.set(4);
    this.maxReachedStep.update((m) => Math.max(m, 4));
  }

  // ---------- 步骤四预约提交 ----------
  submitBooking(): void {
    const teacher = this.wizardTeacher();
    const slot = this.selectedSlot();
    if (!teacher || !slot || this.submitting()) return;
    if (this.bookingType() === 'TRIAL' && this.trialAvailable() !== true) return;

    this.submitting.set(true);
    this.http
      .post(API.booking.create, {
        teacherId: teacher.teacherId,
        slotId: slot.slotId,
        subject: teacher.subject,
        bookingType: this.bookingType(),
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.wizardSuccess.set(true);
          this.wizardNotice.set(
            this.bookingType() === 'TRIAL'
              ? '免费试听预约成功！课程已排入你的课程表，无需付款。'
              : '正式课申请已成功提交！等待导师确认后，即可在课程表发起付款。',
          );
        },
        error: (err: { error?: { detail?: string } }) => {
          this.submitting.set(false);
          // 档期冲突：标记失效并回到步骤三重选
          this.slotInvalid.set(err.error?.detail ?? '所选时段刚被他人抢先预约，已失效，请重新选择。');
          this.step.set(3);
          this.http
            .get<OpenSlot[]>(API.scheduling.openSlots(teacher.teacherId))
            .subscribe({
              next: (data) => this.wizardSlots.set(data ?? []),
            });
        },
      });
  }

  onResetWizard(): void {
    this.step.set(1);
    this.maxReachedStep.set(1);
    this.wizardTeacher.set(null);
    this.selectedSlot.set(null);
    this.wizardSuccess.set(false);
  }
}

/** 时间过滤辅助函数 */
function withinWindow(iso: string | null, filter: TimeFilter): boolean {
  if (!iso) return false;
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return false;
  const now = Date.now();
  if (t < now) return false;
  if (filter === 'today') {
    const endOfDay = new Date();
    endOfDay.setHours(23, 59, 59, 999);
    return t <= endOfDay.getTime();
  }
  const horizon = filter === '3d' ? 72 : 168;
  return t <= now + horizon * 3600 * 1000;
}
