import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, KeyValuePipe } from '@angular/common';
import { AuthService } from '../../core/auth/auth.service';
import { HttpClient, HttpParams } from '@angular/common/http';
import { API } from '../../core/api/endpoints';
import { NoticeCenterComponent } from '../../shared/components/notice-center.component';

interface VerificationCase {
  id: string;
  teacherId: string;
  status: string;
  decisionNote: string | null;
  submittedAt: string;
}

interface AccountView {
  accountId: string;
  role: string;
  displayName: string;
  phone: string;
  accountNo: string;
  status: string;
}

interface RefundCase {
  id: string;
  orderId: string;
  status: string;
  computedAmountCents: number;
  reason: string;
}

interface ArbitrationPreview {
  cascadeImpact: Record<string, unknown>;
  confirmationToken: string;
  confirmationTokenExpiresAt: string;
  notice: string;
}

interface AuditLog {
  id: string;
  callerId: string | null;
  callerRole: string | null;
  occurredAt: string;
  actionType: string;
  executionStatus: string;
  payloadHash: string | null;
}

/**
 * 运营治理控制台 — 全景数据、资质审核状态机、差错退款两阶段仲裁、审计日志检视。
 */
@Component({
  selector: 'nur-admin-dashboard',
  standalone: true,
  imports: [DatePipe, KeyValuePipe, NoticeCenterComponent],
  templateUrl: './admin-dashboard.component.html',
  styleUrl: './admin-dashboard.component.css',
})
export class AdminDashboardComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  readonly displayName = this.auth.displayName;
  readonly overview = signal<Record<string, number | string> | null>(null);
  readonly verificationCases = signal<VerificationCase[]>([]);
  readonly refundCases = signal<RefundCase[]>([]);
  readonly accounts = signal<AccountView[]>([]);
  readonly auditLogs = signal<AuditLog[]>([]);
  readonly preview = signal<{ caseId: string; data: ArbitrationPreview } | null>(null);
  readonly notice = signal('');

  readonly pendingOrders = signal<any[]>([]);
  readonly orderMsg = signal<{ type: 'info' | 'success' | 'error'; text: string } | null>(null);
  readonly confirmTarget = signal<any>(null);
  readonly orderBusy = signal(false);

  ngOnInit(): void {
    this.http.get<Record<string, number | string>>(API.admin.overview)
      .subscribe({ next: (d) => this.overview.set(d) });
    this.loadVerifications();
    this.loadRefunds();
    this.loadAccounts();
    this.loadAuditLogs();
    this.loadPendingOrders();
  }

  loadAccounts(): void {
    this.http.get<{ content: AccountView[] }>(`${API.admin.accounts}?page=0&size=20`)
      .subscribe({ next: (d) => this.accounts.set(d.content ?? []) });
  }

  setLock(account: AccountView, lock: boolean): void {
    const note = lock ? window.prompt('锁定原因（将写入审计日志）：') ?? '' : '解除锁定';
    if (lock && !note) {
      return;
    }
    this.http.post(lock ? API.admin.lockAccount(account.accountId) : API.admin.unlockAccount(account.accountId), { note })
      .subscribe({
        next: () => {
          this.notice.set(lock ? '账户已锁定并记录审计。' : '账户已解锁。');
          this.loadAccounts();
        },
        error: () => this.notice.set('操作未成功，请稍后重试。'),
      });
  }

  loadVerifications(): void {
    this.http.get<{ content: VerificationCase[] }>(`${API.admin.verificationCases}?page=0&size=20`)
      .subscribe({ next: (d) => this.verificationCases.set(d.content) });
  }

  loadRefunds(): void {
    this.http.get<{ content: RefundCase[] }>(`${API.admin.refundCases}?page=0&size=20`)
      .subscribe({ next: (d) => this.refundCases.set(d.content) });
  }

  loadAuditLogs(): void {
    this.http.get<{ content: AuditLog[] }>(`${API.admin.auditLogs}?page=0&size=20`)
      .subscribe({ next: (d) => this.auditLogs.set(d.content) });
  }

  decideVerification(caseId: string, approve: boolean): void {
    const note = approve ? '材料齐全，核验通过' : window.prompt('驳回必须附上明确原因（对导师可见）：') ?? '';
    if (!approve && !note) {
      return;
    }
    this.http.post(API.admin.decideVerification(caseId), { approve, note }).subscribe({
      next: () => {
        this.notice.set('审核结果已流转并通知导师。');
        this.loadVerifications();
      },
      error: () => this.notice.set('审核操作未成功，请稍后重试。'),
    });
  }

  /** 两阶段仲裁第一阶段：Preview 预计算级联影响。 */
  arbitrationPreview(caseId: string): void {
    this.http.post<ArbitrationPreview>(API.admin.arbitrationPreview(caseId), {}).subscribe({
      next: (data) => this.preview.set({ caseId, data }),
      error: () => this.notice.set('影响评估未成功，请稍后重试。'),
    });
  }

  /** 两阶段仲裁第二阶段：Execute 终态执行。 */
  arbitrationExecute(caseId: string, approve: boolean): void {
    const current = this.preview();
    if (!current || current.caseId !== caseId) {
      this.notice.set('请先完成第一阶段 Preview 评估。');
      return;
    }
    this.http
      .post(API.admin.arbitrationExecute(caseId), {
        approve,
        decisionNote: approve ? '仲裁通过，退款原路入账' : '驳回：证据不足',
        confirmationToken: current.data.confirmationToken,
      })
      .subscribe({
        next: () => {
          this.notice.set(approve ? '退款已执行并入账家庭钱包。' : '已驳回该退款申请。');
          this.preview.set(null);
          this.loadRefunds();
        },
        error: () => this.notice.set('执行未成功：凭证可能已过期，请重新 Preview。'),
      });
  }

  yuan(cents: number | undefined): string {
    return ((cents ?? 0) / 100).toFixed(2);
  }

  // ── 订单与收款（模式 B）──
  loadPendingOrders(): void {
    this.http.get<any>(`${API.adminOrders.pending}?page=0&size=20`).subscribe({
      next: (d) => this.pendingOrders.set(d?.content ?? []),
      error: () => this.orderMsg.set({ type: 'error', text: '待支付订单加载失败。' }),
    });
  }

  askConfirm(o: any): void {
    this.confirmTarget.set(o);
    this.orderMsg.set(null);
  }

  cancelConfirm(): void {
    this.confirmTarget.set(null);
  }

  confirmOffline(o: any): void {
    if (this.orderBusy()) return;
    this.orderBusy.set(true);
    this.http.post(API.adminOrders.confirmOffline(o.orderId), {}).subscribe({
      next: (r: any) => {
        this.orderBusy.set(false);
        this.confirmTarget.set(null);
        this.orderMsg.set({ type: 'success',
          text: r?.idempotent ? '该订单此前已确认，未重复入账。' : '已确认收款，订单转为已支付。' });
        this.loadPendingOrders();
      },
      error: (e) => {
        this.orderBusy.set(false);
        this.orderMsg.set({ type: 'error', text: e?.error?.detail ?? '确认未成功，请稍后重试。' });
      },
    });
  }
}
