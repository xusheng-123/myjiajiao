import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { API } from '../../core/api/endpoints';

interface TeacherCandidate {
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

/**
 * 找老师 — 双入口并存（PRD 7.2）：「搜索/筛选」与「告诉平台我的需求」；
 * 预约走分步向导（PRD 7.3）：步骤 1/2 选时间 → 步骤 2/2 确认信息，
 * 每一步保留前文上下文；冲突时段标记失效并就地重选。
 */
@Component({
  selector: 'nur-match',
  standalone: true,
  imports: [DatePipe, RouterLink],
  templateUrl: './match.component.html',
  styleUrl: './match.component.css',
})
export class MatchComponent {
  private readonly http = inject(HttpClient);

  // ---------- 检索入口
  readonly subjectInput = signal('');
  readonly gradeBandInput = signal('');
  readonly maxPriceInput = signal('');
  readonly searched = signal(false);
  readonly loading = signal(false);
  readonly candidates = signal<TeacherCandidate[]>([]);

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
  readonly wizardStep = signal(1); // 1 选时间 / 2 确认信息
  readonly wizardSlots = signal<OpenSlot[]>([]);
  readonly selectedSlot = signal<OpenSlot | null>(null);
  readonly slotInvalid = signal<string | null>(null); // 冲突标记
  readonly submitting = signal(false);
  readonly wizardNotice = signal('');
  readonly wizardSuccess = signal(false);

  readonly canSearch = computed(() => this.subjectInput().trim().length > 0);

  readonly priceYuan = computed(() => this.maxPriceInput().trim());

  search(): void {
    this.loading.set(true);
    this.searched.set(true);
    const params = new URLSearchParams();
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
    this.subjectInput.set('');
    this.gradeBandInput.set('');
    this.maxPriceInput.set('');
    this.search();
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
  startWizard(teacher: TeacherCandidate): void {
    this.wizardTeacher.set(teacher);
    this.wizardStep.set(1);
    this.selectedSlot.set(null);
    this.slotInvalid.set(null);
    this.wizardNotice.set('');
    this.wizardSuccess.set(false);
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
    if (!teacher || !slot) {
      return;
    }
    this.submitting.set(true);
    this.http
      .post(API.booking.create, {
        teacherId: teacher.teacherId,
        slotId: slot.slotId,
        subject: teacher.subject,
        bookingType: 'TRIAL',
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.wizardSuccess.set(true);
          this.wizardNotice.set('试听已预约成功，可在首页看到迫近课程与课前准备清单。');
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
