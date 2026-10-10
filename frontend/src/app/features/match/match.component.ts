import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink, Router, ActivatedRoute } from '@angular/router';
import { TeacherCompareComponent, CompareTeacher } from '../../shared/components/teacher-compare.component';
import { HttpClient } from '@angular/common/http';
import { API } from '../../core/api/endpoints';
import { AuthService } from '../../core/auth/auth.service';

interface TeacherCandidate extends CompareTeacher {
  teacherId: string;
  subject: string;
  gradeBand: string | null;
  pricePerHourCents: number;
  verificationApproved: boolean;
  averageRating: number | null;
  reviewCount: number;
  nextOpenSlotAt: string | null;
  reasons: string[];
}

/** G10 收藏列表条目：服务端返回 teacherId/displayName/favoritedAt，检索可见的教师再做真实数据富集。 */
interface FavoriteItem {
  teacherId: string;
  displayName: string;
  favoritedAt: string;
  subject: string | null;
  gradeBand: string | null;
  pricePerHourCents: number | null;
  averageRating: number | null;
  nextOpenSlotAt: string | null;
  retrievable: boolean;
}

interface OpenSlot {
  slotId: string;
  teacherId: string;
  startAt: string;
  endAt: string;
  status: string;
}

interface DemandView {
  demandId: string;
  subject: string;
  status: string;
}

type EntryMode = 'search' | 'demand' | 'favorites';
type TimeFilter = 'any' | 'today' | '3d' | '7d';

/**
 * 找老师 — 双入口并存（PRD 7.2）：「搜索/筛选」与「告诉平台我的需求」；
 * 预约走分步向导（PRD 7.3）：步骤 1/2 选时间 → 步骤 2/2 确认信息，
 * 每一步保留前文上下文；冲突时段标记失效并就地重选。
 * G10：收藏 toggle（服务端权威状态）+ 最近可约时间前端过滤。
 */
@Component({
  selector: 'nur-match',
  standalone: true,
  imports: [DatePipe, RouterLink, TeacherCompareComponent],
  templateUrl: './match.component.html',
  styleUrl: './match.component.css',
})
export class MatchComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  readonly compared = signal<TeacherCandidate[]>([]);
  readonly selectedTeacherId = signal<string | null>(null);
  isCompared(id: string): boolean { return this.compared().some(t => t.teacherId === id); }
  toggleCompare(t: TeacherCandidate): void {
    if (this.compared().some(c => c.teacherId === t.teacherId)) { this.removeCompare(t.teacherId); return; }
    if (this.compared().length >= 3) { this.favNotice.set('最多对比 3 位老师，请先移除一位。'); return; }
    this.compared.update(rows => [...rows, t]); this.favNotice.set('');
  }
  removeCompare(id: string): void { this.compared.update(rows => rows.filter(t => t.teacherId !== id)); }
  readonly consultBusy = signal(false);

  consult(t: TeacherCandidate): void {
    if (this.consultBusy()) return;
    this.consultBusy.set(true);
    this.http.post<{ conversationId: string }>(API.messaging.conversations, { teacherId: t.teacherId, subject: t.subject }).subscribe({
      next: c => { this.consultBusy.set(false); void this.router.navigate(['/messages'], { queryParams: { conversation: c.conversationId } }); },
      error: e => { this.consultBusy.set(false); this.favNotice.set(e.error?.detail ?? '暂时无法发起咨询，请重试。'); },
    });
  }
  readonly auth = inject(AuthService);

  // ---------- 检索入口
  readonly subjectInput = signal('');
  readonly gradeBandInput = signal('');
  readonly maxPriceInput = signal('');
  readonly searched = signal(false);
  readonly loading = signal(false);
  readonly candidates = signal<TeacherCandidate[]>([]);

  // ---------- 时间筛选（G10）：仅过滤真实检索结果，null/无效时间在启用筛选时不计入
  readonly timeFilter = signal<TimeFilter>('any');
  readonly filteredCandidates = computed(() => {
    const filter = this.timeFilter();
    if (filter === 'any') {
      return this.candidates();
    }
    return this.candidates().filter((t) => withinWindow(t.nextOpenSlotAt, filter));
  });

  // ---------- 收藏（G10）：状态以服务端为准（toggle 响应 / 收藏列表），不落 localStorage
  readonly favoriteIds = signal<Set<string>>(new Set());
  readonly favBusyIds = signal<Set<string>>(new Set());
  readonly favNotice = signal('');
  readonly favoritesList = signal<FavoriteItem[]>([]);
  readonly favLoading = signal(false);
  readonly favError = signal(false);

  // ---------- 需求发布入口（双入口之二）
  readonly demandMode = signal(false);
  readonly demandSubject = signal('');
  readonly demandGrade = signal('');
  readonly demandNote = signal('');
  readonly demandBudget = signal('');
  readonly demandTime = signal('');
  readonly demandDone = signal(false);
  readonly myDemands = signal<DemandView[]>([]);

  // ---------- 预约分步向导
  readonly wizardTeacher = signal<TeacherCandidate | null>(null);
  readonly bookingType = signal<'TRIAL' | 'FORMAL'>('TRIAL');
  readonly trialAvailable = signal<boolean | null>(null);
  readonly trialReason = signal('');
  readonly wizardStep = signal(1); // 1 选时间 / 2 确认信息
  readonly wizardSlots = signal<OpenSlot[]>([]);
  readonly selectedSlot = signal<OpenSlot | null>(null);
  readonly slotInvalid = signal<string | null>(null); // 冲突标记
  readonly submitting = signal(false);
  readonly wizardNotice = signal('');
  readonly wizardSuccess = signal(false);

  readonly entryMode = signal<EntryMode>('search');
  readonly canSearch = computed(() => this.subjectInput().trim().length > 0);

  readonly priceYuan = computed(() => this.maxPriceInput().trim());

  ngOnInit(): void {
    this.selectedTeacherId.set(this.route.snapshot.queryParamMap.get('teacher'));
    const subject = this.route.snapshot.queryParamMap.get('subject');
    if (subject) { this.subjectInput.set(subject); this.search(); }
    if (this.auth.isLoggedIn()) {
      this.loadFavoriteIds();
    }
  }

  search(): void {
    this.loading.set(true);
    this.searched.set(true);
    const params = new URLSearchParams();
    if (this.selectedTeacherId()) params.set('teacherId', this.selectedTeacherId()!);
    if (this.subjectInput().trim()) {
      params.set('subject', this.subjectInput().trim());
    }
    if (this.gradeBandInput().trim()) {
      params.set('gradeBand', this.gradeBandInput().trim());
    }
    if (this.maxPriceInput().trim()) {
      params.set('maxPricePerHourCents', String(Math.round(Number(this.maxPriceInput()) * 100)));
    }
    this.http.get<TeacherCandidate[]>(`${API.matching.search}?${params.toString()}`).subscribe({
      next: (data) => {
        this.candidates.set(data ?? []);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  clearFilters(): void {
    this.selectedTeacherId.set(null);
    this.subjectInput.set('');
    this.gradeBandInput.set('');
    this.maxPriceInput.set('');
    this.search();
  }

  // ---------------- 时间筛选（G10） ----------------

  setTimeFilter(filter: TimeFilter): void {
    // 仅切换过滤窗口，不触发重复检索，不影响科目/学段/价格等既有条件
    this.timeFilter.set(filter);
  }

  // ---------------- 收藏（G10） ----------------

  /** 收藏状态来源：服务端收藏列表（页面加载时恢复真实状态）。 */
  loadFavoriteIds(): void {
    this.http.get<{ teacherId: string }[]>(API.matching.favorites).subscribe({
      next: (list) => {
        this.favoriteIds.set(new Set((list ?? []).map((f) => f.teacherId)));
      },
      error: () => {
        // 读不到收藏状态就不展示任何已收藏标记，等待下次进入页面重试
      },
    });
  }

  isFavorite(teacherId: string): boolean {
    return this.favoriteIds().has(teacherId);
  }

  /** 收藏 / 取消收藏：以服务端响应的 favorited 为准更新本地集合；失败不翻转图标。 */
  toggleFavorite(teacher: TeacherCandidate): void {
    if (!this.auth.isLoggedIn()) {
      // 未登录：只做登录引导，不发未经认证的写请求
      this.favNotice.set('登录后即可收藏老师');
      return;
    }
    if (this.favBusyIds().has(teacher.teacherId)) {
      return; // 进行中，防连点错乱
    }
    this.favNotice.set('');
    this.favBusyIds.update((set) => new Set(set).add(teacher.teacherId));
    this.http
      .post<{ teacherId: string; favorited: boolean }>(API.matching.toggleFavorite(teacher.teacherId), {})
      .subscribe({
        next: (res) => {
          this.favBusyIds.update((set) => {
            const next = new Set(set);
            next.delete(teacher.teacherId);
            return next;
          });
          this.favoriteIds.update((set) => {
            const next = new Set(set);
            if (res.favorited) {
              next.add(res.teacherId);
            } else {
              next.delete(res.teacherId);
            }
            return next;
          });
          this.favNotice.set(res.favorited ? '已收藏，可在「我的收藏」中找到' : '已取消收藏');
        },
        error: (err: { error?: { detail?: string } }) => {
          this.favBusyIds.update((set) => {
            const next = new Set(set);
            next.delete(teacher.teacherId);
            return next;
          });
          // 失败：状态保持操作前的服务端真实状态，仅提示原因
          this.favNotice.set(err.error?.detail ?? '收藏未成功，请稍后重试');
        },
      });
  }

  /** 打开「我的收藏」面板：服务端列表 + 无条件检索结果富集（不伪造教师数据）。 */
  openFavorites(): void {
    if (!this.auth.isLoggedIn()) {
      this.favNotice.set('登录后即可查看我的收藏');
      return;
    }
    this.favLoading.set(true);
    this.favError.set(false);
    this.http
      .get<{ teacherId: string; displayName: string; favoritedAt: string }[]>(API.matching.favorites)
      .subscribe({
        next: (list) => {
          this.favoriteIds.set(new Set((list ?? []).map((f) => f.teacherId)));
          if ((list ?? []).length === 0) {
            this.favoritesList.set([]);
            this.favLoading.set(false);
            return;
          }
          // 富集数据来自当前真实检索池（无筛选条件）；缺席者如实标注「当前未在检索结果中」
          this.http.get<TeacherCandidate[]>(`${API.matching.search}?`).subscribe({
            next: (pool) => {
              const byTeacher = new Map<string, TeacherCandidate>();
              for (const t of pool ?? []) {
                if (!byTeacher.has(t.teacherId)) {
                  byTeacher.set(t.teacherId, t);
                }
              }
              this.favoritesList.set(
                (list ?? []).map((f) => {
                  const t = byTeacher.get(f.teacherId);
                  return {
                    teacherId: f.teacherId,
                    displayName: f.displayName,
                    favoritedAt: f.favoritedAt,
                    subject: t?.subject ?? null,
                    gradeBand: t?.gradeBand ?? null,
                    pricePerHourCents: t?.pricePerHourCents ?? null,
                    averageRating: t?.averageRating ?? null,
                    nextOpenSlotAt: t?.nextOpenSlotAt ?? null,
                    retrievable: t !== undefined,
                  };
                }),
              );
              this.favLoading.set(false);
            },
            error: () => {
              // 检索池读取失败：仍展示服务端收藏骨架（仅姓名），不伪造其余字段
              this.favoritesList.set(
                (list ?? []).map((f) => ({
                  teacherId: f.teacherId,
                  displayName: f.displayName,
                  favoritedAt: f.favoritedAt,
                  subject: null,
                  gradeBand: null,
                  pricePerHourCents: null,
                  averageRating: null,
                  nextOpenSlotAt: null,
                  retrievable: false,
                })),
              );
              this.favLoading.set(false);
            },
          });
        },
        error: () => {
          this.favLoading.set(false);
          this.favError.set(true);
        },
      });
  }

  /** 从收藏列表取消收藏：以服务端返回为准，成功后刷新列表。 */
  unfavorite(item: FavoriteItem): void {
    if (this.favBusyIds().has(item.teacherId)) {
      return;
    }
    this.favBusyIds.update((set) => new Set(set).add(item.teacherId));
    this.http
      .post<{ teacherId: string; favorited: boolean }>(API.matching.toggleFavorite(item.teacherId), {})
      .subscribe({
        next: (res) => {
          this.favBusyIds.update((set) => {
            const next = new Set(set);
            next.delete(item.teacherId);
            return next;
          });
          if (!res.favorited) {
            this.favoriteIds.update((set) => {
              const next = new Set(set);
              next.delete(res.teacherId);
              return next;
            });
            this.favoritesList.update((list) => list.filter((f) => f.teacherId !== res.teacherId));
          }
        },
        error: () => {
          this.favBusyIds.update((set) => {
            const next = new Set(set);
            next.delete(item.teacherId);
            return next;
          });
          this.favNotice.set('取消收藏未成功，请稍后重试');
        },
      });
  }

  showEntry(mode: EntryMode): void {
    this.entryMode.set(mode);
    this.favNotice.set('');
    if (mode === 'demand') {
      this.toggleDemandMode();
    } else {
      this.demandMode.set(false);
    }
    if (mode === 'favorites') {
      this.openFavorites();
    }
  }

  // ---------------- 需求发布 ----------------
  toggleDemandMode(): void {
    this.demandMode.update((v) => !v);
    this.demandDone.set(false);
    if (this.demandMode() && this.myDemands().length === 0) {
      this.loadMyDemands();
    }
  }

  loadMyDemands(): void {
    this.http.get<{ content: DemandView[] }>(`${API.matching.demands}?page=0&size=5`).subscribe({
      next: (data) => this.myDemands.set(data.content ?? []),
    });
  }

  submitDemand(): void {
    if (!this.demandSubject().trim()) {
      return;
    }
    this.http
      .post<DemandView>(API.matching.demands, {
        subject: this.demandSubject().trim(),
        gradeBand: this.demandGrade().trim() || null,
        note: this.demandNote().trim() || null,
        budgetMaxCents: this.demandBudget().trim() ? Math.round(Number(this.demandBudget()) * 100) : null,
        preferredTime: this.demandTime().trim() || null,
      })
      .subscribe({
        next: () => {
          this.demandDone.set(true);
          this.demandSubject.set('');
          this.demandGrade.set('');
          this.demandNote.set('');
          this.demandBudget.set('');
          this.demandTime.set('');
          this.loadMyDemands();
        },
      });
  }

  // ---------------- 预约向导 ----------------
  startWizard(teacher: TeacherCandidate, type: 'TRIAL' | 'FORMAL' = 'TRIAL'): void {
    this.bookingType.set(type);
    this.wizardTeacher.set(teacher);
    this.wizardStep.set(1);
    this.selectedSlot.set(null);
    this.slotInvalid.set(null);
    this.wizardNotice.set('');
    this.wizardSuccess.set(false);
    this.wizardSlots.set([]);
    this.trialAvailable.set(null);
    this.trialReason.set('正在核查试听名额…');
    if (type === 'TRIAL') {
      this.http.get<{ available: boolean; reason: string }>(`/api/v1/booking/trial-eligibility?teacherId=${teacher.teacherId}`).subscribe({
        next: r => { if (this.wizardTeacher()?.teacherId === teacher.teacherId && this.bookingType() === 'TRIAL') {
          this.trialAvailable.set(r.available); this.trialReason.set(r.reason);
        } },
        error: () => this.trialReason.set('暂时无法核查名额，请关闭后重试。'),
      });
    }
    this.http.get<OpenSlot[]>(API.scheduling.openSlots(teacher.teacherId)).subscribe({
      next: (data) => this.wizardSlots.set(data ?? []),
    });
  }

  closeWizard(): void {
    this.wizardTeacher.set(null);
  }

  pickSlot(slot: OpenSlot): void {
    this.selectedSlot.set(slot);
    this.slotInvalid.set(null);
  }

  /** 步骤 1 → 2：保留已选上下文。 */
  toConfirmStep(): void {
    if (this.bookingType() === 'TRIAL' && this.trialAvailable() !== true) return;
    if (!this.selectedSlot()) {
      this.wizardNotice.set('请先选择一个可约时间。');
      return;
    }
    this.wizardNotice.set('');
    this.wizardStep.set(2);
  }

  backToSlots(): void {
    // 保留科目/时间选择上下文，不无理由清空
    this.wizardStep.set(1);
  }

  submitBooking(): void {
    const teacher = this.wizardTeacher();
    const slot = this.selectedSlot();
    if (!teacher || !slot || this.submitting() || (this.bookingType() === 'TRIAL' && this.trialAvailable() !== true)) {
      return;
    }
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
          this.wizardNotice.set(this.bookingType() === 'TRIAL'
            ? '免费试听已预约成功，可在课程表查看。'
            : '正式课申请已提交，等待教师确认；确认后可在课程表下单，由家长完成付款。');
        },
        error: (err: { status?: number; error?: { detail?: string } }) => {
          this.submitting.set(false);
          // 时间冲突：标记该时段失效并要求重选（PRD 7.3）
          this.slotInvalid.set(
            err.error?.detail ?? '该时段刚被他人预约，已标记失效，请重新选择。'
          );
          this.wizardStep.set(1);
          this.http.get<OpenSlot[]>(API.scheduling.openSlots(teacher.teacherId)).subscribe({
            next: (data) => this.wizardSlots.set(data ?? []),
          });
        },
      });
  }

  yuan(cents: number): string {
    return (cents / 100).toFixed(0);
  }
}

/**
 * G10 时间窗口（全部按用户浏览器本地时区计算）：
 * - today：[当前时刻, 当地今日 23:59:59.999]——已过去的今日时段不可约，不计入；
 * - 3d / 7d：[当前时刻, 当前时刻 + 72h / 168h]；
 * - null / 无效日期在启用筛选时不计入；未启用筛选时原样保留（不影响原始结果）。
 */
function withinWindow(iso: string | null, filter: TimeFilter): boolean {
  if (!iso) {
    return false;
  }
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) {
    return false;
  }
  const now = Date.now();
  if (t < now) {
    return false; // 过去时间不可约（缓存残留的 OPEN 档期不得承诺可用）
  }
  if (filter === 'today') {
    const endOfDay = new Date();
    endOfDay.setHours(23, 59, 59, 999);
    return t <= endOfDay.getTime();
  }
  const horizon = filter === '3d' ? 72 : 168;
  return t <= now + horizon * 3600 * 1000;
}
