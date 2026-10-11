import { Component, OnInit, inject, signal, output } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { API } from '../../core/api/endpoints';
import { QueuePagerComponent } from '../../shared/components/queue-pager.component';

export interface PendingPaymentOrder {
  orderId: string;
  orderNo: string;
  subject: string;
  amountCents: number;
  payerName: string;
  studentName: string;
  teacherName: string;
}

@Component({
  selector: 'nur-admin-orders',
  standalone: true,
  imports: [QueuePagerComponent],
  template: `
    <div class="queue-panel">
      <div class="panel-header">
        <div>
          <h3 class="panel-title nur-serif">线下待支付订单核验（支付模式 B）</h3>
          <p class="panel-desc">线下对公转账或收款码凭证核销：运营人工核验款项实际到账后落账，订单状态转为已支付，课酬进入结算链路。</p>
        </div>
      </div>

      @if (message()) {
        <div class="status-banner" [class.status-error]="isError()" role="status">
          <span class="status-icon" aria-hidden="true">{{ isError() ? '⚠' : '✓' }}</span>
          <span>{{ message() }}</span>
          <button type="button" class="btn-dismiss" (click)="message.set('')" aria-label="关闭提示">×</button>
        </div>
      }

      <nur-queue-pager
        label="待支付订单"
        [page]="page()"
        [pages]="pages()"
        [total]="total()"
        [busy]="busy()"
        [filter]="filter()"
        (turn)="turnQueue($event)"
        (filterChange)="filterQueue($event)"
        (reload)="loadQueue()"
      />

      @if (orders().length === 0) {
        <div class="empty-state">
          <p class="empty-text">当前没有待确认线下收款的订单。</p>
        </div>
      } @else {
        <ul class="order-list" role="list">
          @for (o of orders(); track o.orderId) {
            <li class="order-card">
              <div class="order-main">
                <div class="order-meta">
                  <span class="order-no">{{ o.orderNo }}</span>
                  <span class="subject-tag">{{ o.subject }}</span>
                  <span class="amount-tag">¥{{ yuan(o.amountCents) }}</span>
                </div>
                <div class="payer-info">
                  <span class="info-label">交易关联：</span>
                  <span class="info-val">付款人「{{ o.payerName }}」 → 学员「{{ o.studentName }}」（授课导师：{{ o.teacherName }}）</span>
                </div>
              </div>

              <div class="order-actions">
                <button
                  type="button"
                  class="btn-primary-sm"
                  [disabled]="orderBusy()"
                  (click)="askConfirm(o)"
                >
                  确认收款
                </button>
              </div>
            </li>
          }
        </ul>
      }

      @if (confirmTarget(); as o) {
        <div class="modal-backdrop" (click)="cancelConfirm()">
          <div class="modal-card" role="alertdialog" aria-modal="true" aria-labelledby="confirm-order-title" (click)="$event.stopPropagation()">
            <h4 id="confirm-order-title" class="modal-title nur-serif">核验线下实际到账</h4>
            <div class="confirm-box">
              <p class="confirm-row"><strong>订单单号：</strong>{{ o.orderNo }}</p>
              <p class="confirm-row"><strong>课程科目：</strong>{{ o.subject }}</p>
              <p class="confirm-row"><strong>核验金额：</strong><span class="highlight-amt">¥{{ yuan(o.amountCents) }}</span></p>
              <p class="confirm-row"><strong>付款人：</strong>{{ o.payerName }}（学员：{{ o.studentName }} / 导师：{{ o.teacherName }}）</p>
            </div>
            <p class="warning-text">
              ⚠ 严正提示：仅当银行转账流水或聚合收款码已<strong>实际到账</strong>后方可确认。确认后订单将立即生效转为「PAID」，资金自动进入课酬待结算池，此操作不可逆！
            </p>
            <div class="modal-footer">
              <button type="button" class="btn-outline-sm" (click)="cancelConfirm()">取消</button>
              <button
                type="button"
                class="btn-primary-sm"
                [disabled]="orderBusy()"
                (click)="executeConfirmOffline(o)"
              >
                {{ orderBusy() ? '确认中…' : '确认已实际到账' }}
              </button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .queue-panel {
      display: flex;
      flex-direction: column;
      gap: var(--space-4);
    }
    .panel-header {
      padding-bottom: var(--space-2);
      border-bottom: 1px solid var(--line);
    }
    .panel-title {
      margin: 0;
      font-size: 1.25rem;
      color: var(--text-title);
      font-weight: 600;
    }
    .panel-desc {
      margin: var(--space-1) 0 0;
      font-size: 0.9rem;
      color: var(--text-muted);
      line-height: 1.6;
    }
    .status-banner {
      display: flex;
      align-items: center;
      gap: var(--space-2);
      padding: var(--space-3) var(--space-4);
      border-radius: var(--radius-control);
      background: var(--color-success-bg);
      color: var(--color-success);
      font-size: 0.9rem;
    }
    .status-error {
      background: var(--color-danger-bg);
      color: var(--color-danger);
    }
    .btn-dismiss {
      margin-left: auto;
      background: none;
      border: none;
      font-size: 1.2rem;
      line-height: 1;
      color: inherit;
      padding: 0 var(--space-1);
      min-height: auto;
    }
    .empty-state {
      padding: var(--space-7) var(--space-4);
      text-align: center;
      background: var(--bg-card);
      border: 1px dashed var(--line);
      border-radius: var(--radius-card);
    }
    .empty-text {
      margin: 0;
      color: var(--text-muted);
      font-size: 0.92rem;
    }
    .order-list {
      margin: 0;
      padding: 0;
      list-style: none;
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
    }
    .order-card {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: var(--space-4);
      padding: var(--space-4) var(--space-5);
      background: var(--bg-card);
      border: 1px solid var(--line);
      border-radius: var(--radius-card);
      box-shadow: var(--shadow-subtle);
    }
    .order-main {
      flex: 1;
      min-width: 0;
    }
    .order-meta {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--space-3);
      margin-bottom: var(--space-2);
    }
    .order-no {
      font-family: monospace;
      font-size: 0.95rem;
      font-weight: 600;
      color: var(--text-title);
    }
    .subject-tag {
      padding: 2px 10px;
      border-radius: var(--radius-pill);
      font-size: 0.8rem;
      background: var(--color-primary-soft);
      color: var(--color-primary);
    }
    .amount-tag {
      font-family: var(--font-serif);
      font-size: 1.15rem;
      font-weight: 600;
      color: var(--color-primary);
    }
    .payer-info {
      font-size: 0.85rem;
      color: var(--text-muted);
    }
    .info-val {
      color: var(--text-main);
    }
    .order-actions {
      flex-shrink: 0;
    }
    .btn-primary-sm {
      min-height: 38px;
      padding: 0 var(--space-4);
      border: none;
      border-radius: var(--radius-pill);
      background: var(--color-primary);
      color: var(--bg-card);
      font-size: 0.88rem;
      font-weight: 500;
      box-shadow: 0 4px 12px rgba(30, 77, 62, 0.16);
      transition: background-color var(--duration-enter) var(--ease), transform var(--duration-enter) var(--ease);
    }
    .btn-primary-sm:hover:not(:disabled) {
      background: var(--color-primary-hover);
      transform: translateY(-1px);
    }
    .btn-outline-sm {
      min-height: 36px;
      padding: 0 var(--space-3);
      border: 1px solid var(--line);
      border-radius: var(--radius-pill);
      background: var(--bg-card);
      color: var(--text-main);
      font-size: 0.85rem;
      transition: all var(--duration-enter) var(--ease);
    }
    .btn-outline-sm:hover:not(:disabled) {
      border-color: var(--color-primary);
      color: var(--color-primary);
    }
    button:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
    .modal-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(26, 42, 35, 0.45);
      backdrop-filter: blur(4px);
      display: grid;
      place-items: center;
      z-index: 1000;
      padding: var(--space-4);
    }
    .modal-card {
      background: var(--bg-card);
      border: 1px solid var(--line);
      border-radius: var(--radius-card);
      padding: var(--space-6);
      max-width: 500px;
      width: 100%;
      box-shadow: var(--shadow-hover);
    }
    .modal-title {
      margin: 0 0 var(--space-3);
      font-size: 1.25rem;
      color: var(--text-title);
    }
    .confirm-box {
      background: var(--bg-page);
      padding: var(--space-4);
      border-radius: var(--radius-control);
      margin-bottom: var(--space-4);
    }
    .confirm-row {
      margin: 0 0 var(--space-1);
      font-size: 0.88rem;
      color: var(--text-main);
    }
    .confirm-row:last-child {
      margin-bottom: 0;
    }
    .highlight-amt {
      font-family: var(--font-serif);
      font-size: 1.15rem;
      font-weight: 600;
      color: var(--color-primary);
    }
    .warning-text {
      margin: 0 0 var(--space-5);
      font-size: 0.85rem;
      color: var(--color-gold-text);
      background: var(--color-gold-bg);
      padding: var(--space-3);
      border-radius: var(--radius-control);
      line-height: 1.6;
    }
    .modal-footer {
      display: flex;
      justify-content: flex-end;
      gap: var(--space-3);
    }
    @media (max-width: 768px) {
      .order-card {
        flex-direction: column;
        align-items: stretch;
      }
      .order-actions button {
        width: 100%;
      }
    }
  `]
})
export class AdminOrdersComponent implements OnInit {
  private readonly http = inject(HttpClient);

  readonly auditChanged = output<void>();

  readonly orders = signal<PendingPaymentOrder[]>([]);
  readonly page = signal(0);
  readonly pages = signal(0);
  readonly total = signal(0);
  readonly busy = signal(false);
  readonly filter = signal('');
  private version = 0;

  readonly message = signal('');
  readonly isError = signal(false);
  readonly orderBusy = signal(false);
  readonly confirmTarget = signal<PendingPaymentOrder | null>(null);

  ngOnInit(): void {
    this.loadQueue();
  }

  loadQueue(): void {
    const v = ++this.version;
    const params = new HttpParams().set('page', this.page()).set('size', 10);
    this.busy.set(true);
    this.http.get<{ content: PendingPaymentOrder[]; totalPages: number; totalElements: number }>(
      API.adminOrders.pending,
      { params }
    ).subscribe({
      next: (d) => {
        if (v !== this.version) return;
        if (this.page() > 0 && d.content.length === 0) {
          this.page.set(Math.max(0, d.totalPages - 1));
          this.loadQueue();
          return;
        }
        this.pages.set(d.totalPages);
        this.total.set(d.totalElements);
        this.orders.set(d.content);
        this.busy.set(false);
      },
      error: () => {
        if (v !== this.version) return;
        this.busy.set(false);
        this.isError.set(true);
        this.message.set('待支付订单列表读取失败，请点击刷新重试。');
      },
    });
  }

  turnQueue(delta: number): void {
    const target = this.page() + delta;
    if (this.busy() || target < 0 || target >= this.pages()) return;
    this.page.set(target);
    this.loadQueue();
  }

  filterQueue(val: string): void {
    this.filter.set(val);
    this.page.set(0);
    this.loadQueue();
  }

  yuan(cents: number | undefined): string {
    return ((cents ?? 0) / 100).toFixed(2);
  }

  askConfirm(o: PendingPaymentOrder): void {
    this.confirmTarget.set(o);
  }

  cancelConfirm(): void {
    this.confirmTarget.set(null);
  }

  executeConfirmOffline(o: PendingPaymentOrder): void {
    if (this.orderBusy()) return;
    this.orderBusy.set(true);
    this.http.post<{ idempotent?: boolean }>(
      API.adminOrders.confirmOffline(o.orderId),
      {}
    ).subscribe({
      next: (r) => {
        this.orderBusy.set(false);
        this.confirmTarget.set(null);
        this.isError.set(false);
        this.message.set(r?.idempotent ? '该订单此前已确认，未重复入账。' : '已确认实际收款，订单状态已流转为已支付。');
        this.loadQueue();
        this.auditChanged.emit();
      },
      error: (e) => {
        this.orderBusy.set(false);
        this.isError.set(true);
        this.message.set(e?.error?.detail ?? '确认收款未成功，请稍后重试。');
      },
    });
  }
}
