import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API } from '../../core/api/endpoints';

export interface SubjectOffering {
  offeringId: string;
  subject: string;
  gradeBand: string | null;
  pricePerHourCents: number;
  active: boolean;
}

@Component({
  selector: 'nur-subjects-pricing-card',
  standalone: true,
  imports: [],
  templateUrl: './subjects-pricing-card.component.html',
  styleUrl: './subjects-pricing-card.component.css',
})
export class SubjectsPricingCardComponent implements OnInit {
  private readonly http = inject(HttpClient);

  readonly subjects = signal<SubjectOffering[]>([]);
  readonly loaded = signal(false);
  readonly error = signal(false);
  readonly notice = signal<{ type: 'info' | 'error' | 'success'; text: string } | null>(null);

  // 新增开课条目
  readonly newSubjectName = signal('');
  readonly newSubjectGradeBand = signal('');
  readonly newSubjectPriceYuan = signal('');
  readonly addBusy = signal(false);

  // 调价
  readonly repricingId = signal<string | null>(null);
  readonly repriceInputYuan = signal('');
  readonly repriceBusy = signal(false);

  // 下架确认
  readonly delistingId = signal<string | null>(null);
  readonly delistBusy = signal(false);

  // 重新上架
  readonly relistBusyId = signal<string | null>(null);

  ngOnInit(): void {
    this.loadSubjects();
  }

  loadSubjects(): void {
    this.http.get<SubjectOffering[]>(API.teacher.subjects, { params: { includeInactive: true } }).subscribe({
      next: (data) => {
        this.subjects.set(data ?? []);
        this.loaded.set(true);
        this.error.set(false);
      },
      error: () => {
        this.loaded.set(true);
        this.error.set(true);
      },
    });
  }

  onNewSubjectField(field: 'name' | 'gradeBand' | 'price', value: string): void {
    if (field === 'name') {
      this.newSubjectName.set(value);
    } else if (field === 'gradeBand') {
      this.newSubjectGradeBand.set(value);
    } else {
      this.newSubjectPriceYuan.set(value);
    }
  }

  addSubject(): void {
    const subject = this.newSubjectName().trim();
    const gradeBand = this.newSubjectGradeBand().trim();
    const price = parseYuanToCents(this.newSubjectPriceYuan());

    if (this.addBusy()) return;

    if (!subject || !gradeBand) {
      this.notice.set({ type: 'error', text: '请填写科目与学段。' });
      return;
    }
    if (price === null || price <= 0) {
      this.notice.set({ type: 'error', text: '课时定价需为正数（元/小时，最多两位小数）。' });
      return;
    }

    const knownIds = new Set(this.subjects().map((s) => s.offeringId));
    this.addBusy.set(true);
    this.notice.set(null);

    this.http
      .post<SubjectOffering>(API.teacher.subjects, { subject, gradeBand, pricePerHourCents: price })
      .subscribe({
        next: (o) => {
          this.addBusy.set(false);
          const existed = knownIds.has(o.offeringId);
          this.notice.set({
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
          this.addBusy.set(false);
          this.notice.set({ type: 'error', text: err?.error?.detail ?? '新增未成功，请稍后重试。' });
        },
      });
  }

  startReprice(offering: SubjectOffering): void {
    this.repricingId.set(offering.offeringId);
    this.repriceInputYuan.set(this.yuan(offering.pricePerHourCents));
    this.notice.set(null);
  }

  cancelReprice(): void {
    this.repricingId.set(null);
    this.repriceInputYuan.set('');
  }

  onRepriceInput(value: string): void {
    this.repriceInputYuan.set(value);
  }

  confirmReprice(offering: SubjectOffering): void {
    if (this.repriceBusy()) return;
    const cents = parseYuanToCents(this.repriceInputYuan());
    if (cents === null || cents <= 0) {
      this.notice.set({ type: 'error', text: '新价格需为正数（元/小时，最多两位小数）。' });
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
          this.subjects.update((list) => list.map((s) => (s.offeringId === updated.offeringId ? updated : s)));
          this.notice.set({
            type: 'success',
            text: `「${updated.subject} · ${updated.gradeBand}」已调至 ¥${this.yuan(updated.pricePerHourCents)}/小时。仅影响之后的新订单，已生成订单金额不变。`,
          });
        },
        error: (err) => {
          this.repriceBusy.set(false);
          this.notice.set({ type: 'error', text: err?.error?.detail ?? '调价未成功，请稍后重试。' });
        },
      });
  }

  relist(offering: SubjectOffering): void {
    if (this.relistBusyId()) return;
    this.relistBusyId.set(offering.offeringId);
    this.http.post<SubjectOffering>(API.teacher.subjects + '/' + offering.offeringId + ':activate', {}).subscribe({
      next: (updated) => {
        this.relistBusyId.set(null);
        this.subjects.update((list) => list.map((o) => (o.offeringId === updated.offeringId ? updated : o)));
        this.notice.set({ type: 'success', text: '课程已重新上架，公开检索恢复展示。' });
      },
      error: (e) => {
        this.relistBusyId.set(null);
        this.notice.set({ type: 'error', text: e?.error?.detail ?? '重新上架失败，请稍后重试。' });
      },
    });
  }

  askDelist(offering: SubjectOffering): void {
    this.delistingId.set(offering.offeringId);
    this.notice.set(null);
  }

  cancelDelist(): void {
    this.delistingId.set(null);
  }

  confirmDelist(offering: SubjectOffering): void {
    if (this.delistBusy()) return;
    this.delistBusy.set(true);
    this.http.delete(API.teacher.subjectRemove(offering.offeringId)).subscribe({
      next: () => {
        this.delistBusy.set(false);
        this.delistingId.set(null);
        this.loadSubjects();
        this.notice.set({
          type: 'success',
          text: `「${offering.subject} · ${offering.gradeBand}」已下架（停止销售）：学生无法再检索到或预约该条目；已生成订单不受影响。`,
        });
      },
      error: (err) => {
        this.delistBusy.set(false);
        this.notice.set({ type: 'error', text: err?.error?.detail ?? '下架未成功，请稍后重试。' });
      },
    });
  }

  yuan(cents: number): string {
    return (cents / 100).toFixed(2);
  }
}

function parseYuanToCents(input: string): number | null {
  const trimmed = input.trim();
  if (!/^\d+(\.\d{1,2})?$/.test(trimmed)) {
    return null;
  }
  return Math.round(parseFloat(trimmed) * 100);
}
