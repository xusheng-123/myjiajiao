import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe, KeyValuePipe } from '@angular/common';
import { AuthService } from '../../core/auth/auth.service';
import { HttpClient, HttpParams } from '@angular/common/http';
import { API, API_BASE } from '../../core/api/endpoints';
import { QueuePagerComponent } from '../../shared/components/queue-pager.component';
import { NoticeCenterComponent } from '../../shared/components/notice-center.component';

interface VerificationCase {
  caseId: string;
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
  caseId: string;
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
  auditId: string;
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
  imports: [DatePipe, KeyValuePipe, NoticeCenterComponent, QueuePagerComponent, RouterLink],
  templateUrl: './admin-dashboard.component.html',
  styleUrl: './admin-dashboard.component.css',
})
export class AdminDashboardComponent implements OnInit {
  readonly materialBusy = signal(false);
  readonly arbitrationBusy = signal(false);

  downloadMaterial(caseId: string, kind: 'identity' | 'degree'): void {
    if (this.materialBusy()) return;
    this.materialBusy.set(true);
    this.http.get(`${API_BASE}/admin/verification-cases/${caseId}/materials/${kind}`, { responseType: 'blob', observe: 'response' }).subscribe({
      next: response => {
        this.materialBusy.set(false);
        if (!response.body) { this.notice.set('材料内容为空，请联系导师补交。'); return; }
        const url = URL.createObjectURL(response.body), a = document.createElement('a');
        a.href = url;
        a.download = response.headers.get('Content-Disposition')?.match(/filename="?([^";]+)"?/)?.[1] ?? `${kind}-${caseId}.bin`;
        a.click(); setTimeout(() => URL.revokeObjectURL(url), 10000);
        this.notice.set('材料已下载，请核对后完成审核。'); this.loadAuditLogs();
      }, error: () => { this.materialBusy.set(false); this.notice.set('材料下载失败，请确认权限和材料是否仍可读取。'); },
    });
  }
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

  readonly queues = {
    accounts: {page: signal(0), pages: signal(0), total: signal(0), busy: signal(false), filter: signal(''), version: 0},
    verifications: {page: signal(0), pages: signal(0), total: signal(0), busy: signal(false), filter: signal(''), version: 0},
    refunds: {page: signal(0), pages: signal(0), total: signal(0), busy: signal(false), filter: signal(''), version: 0},
    audit: {page: signal(0), pages: signal(0), total: signal(0), busy: signal(false), filter: signal(''), version: 0},
    orders: {page: signal(0), pages: signal(0), total: signal(0), busy: signal(false), filter: signal(''), version: 0},
  };
  readonly statusOptions = {
    accounts: [{value:'ACTIVE',label:'正常'},{value:'LOCKED',label:'已锁定'},{value:'CLOSED',label:'已注销'}],
    verifications: [{value:'SUBMITTED',label:'待审核'},{value:'UNDER_REVIEW',label:'审核中'},{value:'APPROVED',label:'已通过'},{value:'REJECTED',label:'已驳回'}],
    refunds: [{value:'REQUESTED',label:'待评估'},{value:'EVALUATED',label:'已评估'},{value:'APPROVED',label:'已批准'},{value:'REJECTED',label:'已驳回'},{value:'EXECUTED',label:'已执行'}],
  };
  loadQueue(key: keyof typeof this.queues): void {
    const q=this.queues[key], version=++q.version;
    const url={accounts:API.admin.accounts,verifications:API.admin.verificationCases,refunds:API.admin.refundCases,audit:API.admin.auditLogs,orders:API.adminOrders.pending}[key];
    let params=new HttpParams().set('page',q.page()).set('size',20);
    if(q.filter().trim()) params=params.set(key==='audit'?'actionType':'status',q.filter().trim());
    q.busy.set(true);
    this.http.get<{content:any[];totalPages:number;totalElements:number}>(url,{params}).subscribe({
      next: d => {
        if(version!==q.version)return;
        if(q.page()>0 && d.content.length===0){q.page.set(Math.max(0,d.totalPages-1));this.loadQueue(key);return;}
        q.pages.set(d.totalPages);q.total.set(d.totalElements);q.busy.set(false);
        ({accounts:this.accounts,verifications:this.verificationCases,refunds:this.refundCases,audit:this.auditLogs,orders:this.pendingOrders}[key]).set(d.content);
      },
      error: () => {if(version!==q.version)return;q.busy.set(false);this.notice.set('列表读取失败，保留当前数据，请点击对应列表的刷新重试。');},
    });
  }
  turnQueue(key:keyof typeof this.queues,delta:number):void {
    const q=this.queues[key],target=q.page()+delta;
    if(q.busy() || target<0 || target>=q.pages())return;
    q.page.set(target);this.loadQueue(key);
  }
  filterQueue(key:keyof typeof this.queues,value:string):void {this.queues[key].filter.set(value);this.queues[key].page.set(0);this.loadQueue(key);}
  loadAccounts(): void {this.loadQueue('accounts');}

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

  loadVerifications(): void {this.loadQueue('verifications');}
  loadRefunds(): void {this.loadQueue('refunds');}
  loadAuditLogs(): void {this.loadQueue('audit');}

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
    if (this.arbitrationBusy()) return;
    this.arbitrationBusy.set(true);
    this.http.post<ArbitrationPreview>(API.admin.arbitrationPreview(caseId), {}).subscribe({
      next: (data) => { this.arbitrationBusy.set(false); this.preview.set({ caseId, data }); },
      error: e => { this.arbitrationBusy.set(false); this.notice.set(e.error?.detail ?? '影响评估未成功，请稍后重试。'); },
    });
  }

  /** 两阶段仲裁第二阶段：Execute 终态执行。 */
  arbitrationExecute(caseId: string, approve: boolean): void {
    if (this.arbitrationBusy()) return;
    const current = this.preview();
    if (approve && (!current || current.caseId !== caseId)) {
      this.notice.set('请先完成第一阶段 Preview 评估。');
      return;
    }
    const decisionNote = approve ? '仲裁通过，退款原路入账' : window.prompt('请输入驳回原因（最多 512 字，将向申请人展示）：')?.trim();
    if (!decisionNote || decisionNote.length > 512) { this.notice.set('请输入 1–512 字的处理说明。'); return; }
    this.arbitrationBusy.set(true);
    this.http
      .post(API.admin.arbitrationExecute(caseId), {
        approve,
        decisionNote,
        confirmationToken: approve ? current!.data.confirmationToken : undefined,
      })
      .subscribe({
        next: () => {
          this.arbitrationBusy.set(false);
          this.notice.set(approve ? '退款已执行并入账家庭钱包。' : '已驳回该退款申请。');
          this.preview.set(null);
          this.loadRefunds();
        },
        error: e => { this.arbitrationBusy.set(false); this.notice.set(e.error?.detail ?? '执行未成功：请重新评估影响后重试。'); },
      });
  }

  yuan(cents: number | undefined): string {
    return ((cents ?? 0) / 100).toFixed(2);
  }

  // ── 订单与收款（模式 B）──
  loadPendingOrders(): void {this.loadQueue('orders');}

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
