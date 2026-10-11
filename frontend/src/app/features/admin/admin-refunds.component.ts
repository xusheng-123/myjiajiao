import { Component, OnInit, inject, signal, output } from '@angular/core';
import { KeyValuePipe } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { API } from '../../core/api/endpoints';
import { QueuePagerComponent } from '../../shared/components/queue-pager.component';

export interface RefundCase {
  caseId: string;
  orderId: string;
  status: string;
  computedAmountCents: number;
  reason: string;
}

export interface ArbitrationPreview {
  cascadeImpact: Record<string, unknown>;
  confirmationToken: string;
  confirmationTokenExpiresAt: string;
  notice: string;
}

@Component({
  selector: 'nur-admin-refunds',
  standalone: true,
  imports: [KeyValuePipe, QueuePagerComponent],
  template: `
    <div class="queue-panel">
      <div class="panel-header">
        <div>
          <h3 class="panel-title nur-serif">差错退款仲裁 · 两阶段确认</h3>
          <p class="panel-desc">高后果资金仲裁机制：必须先执行「第一阶段影响评估」预计算级联资金流向，再凭安全凭证进行「第二阶段原子执行」。</p>
        </div>
      </div>

      @if (notice()) {
        <div class="status-banner" [class.status-error]="isError()" role="status">
          <span class="status-icon" aria-hidden="true">{{ isError() ? '⚠' : '✓' }}</span>
          <span>{{ notice() }}</span>
          <button type="button" class="btn-dismiss" (click)="notice.set('')" aria-label="关闭提示">×</button>
        </div>
      }

      <nur-queue-pager
        label="退款仲裁"
        [page]="page()"
        [pages]="pages()"
        [total]="total()"
        [busy]="busy()"
        [filter]="filter()"
        [options]="statusOptions"
        (turn)="turnQueue($event)"
        (filterChange)="filterQueue($event)"
        (reload)="loadQueue()"
      />

      @if (cases().length === 0) {
        <div class="empty-state">
          <p class="empty-text">当前筛选条件下暂无退款仲裁工单。</p>
        </div>
      } @else {
        <ul class="case-list" role="list">
          @for (c of cases(); track c.caseId) {
            <li class="case-card">
              <div class="case-main">
                <div class="case-meta">
                  <span class="order-ref">订单号 {{ c.orderId.slice(0, 8) }}</span>
                  <span class="amount-tag">¥{{ yuan(c.computedAmountCents) }}</span>
                  <span class="badge" [attr.data-status]="c.status">{{ formatStatus(c.status) }}</span>
                </div>
                <p class="case-reason"><strong>申请原因：</strong>{{ c.reason }}</p>

                @if (preview(); as p) {
                  @if (p.caseId === c.caseId) {
                    <div class="impact-panel" role="region" aria-label="级联影响评估结果">
                      <div class="impact-header">
                        <span class="impact-title">① 级联影响评估已就绪</span>
                        <span class="impact-expiry">凭证有效至 {{ p.data.confirmationTokenExpiresAt }}</span>
                      </div>
                      @if (p.data.notice) {
                        <p class="impact-notice">{{ p.data.notice }}</p>
                      }
                      <ul class="impact-list">
                        @for (kv of p.data.cascadeImpact | keyvalue; track kv.key) {
                          <li><span class="impact-key">{{ kv.key }}：</span><span class="impact-val">{{ kv.value }}</span></li>
                        }
                      </ul>
                    </div>
                  }
                }
              </div>

              @if (c.status === 'REQUESTED' || c.status === 'EVALUATED') {
                <div class="case-actions">
                  <button
                    type="button"
                    class="btn-outline-sm"
                    [disabled]="arbitrationBusy()"
                    (click)="arbitrationPreview(c.caseId)"
                  >
                    {{ arbitrationBusy() && previewTargetId() === c.caseId ? '评估中…' : '① 评估影响 (Preview)' }}
                  </button>
                  <button
                    type="button"
                    class="btn-primary-sm"
                    [disabled]="arbitrationBusy() || !hasValidPreview(c.caseId)"
                    (click)="askExecuteApprove(c)"
                  >
                    ② 执行退款入账
                  </button>
                  <button
                    type="button"
                    class="btn-danger-sm"
                    [disabled]="arbitrationBusy()"
                    (click)="askExecuteReject(c)"
                  >
                    驳回退款申请
                  </button>
                </div>
              }
            </li>
          }
        </ul>
      }

      @if (rejectTarget(); as target) {
        <div class="modal-backdrop" (click)="cancelModal()">
          <div class="modal-card" role="dialog" aria-modal="true" aria-labelledby="reject-refund-title" (click)="$event.stopPropagation()">
            <h4 id="reject-refund-title" class="modal-title nur-serif">驳回退款申请</h4>
            <p class="modal-desc">驳回说明将作为仲裁结论发送给申请人，字数限制 1–512 字，且落入不可篡改审计日志。</p>
            <div class="form-group">
              <label for="refund-reject-note">驳回理由 <span class="required">*</span></label>
              <textarea
                id="refund-reject-note"
                rows="3"
                maxlength="512"
                placeholder="请输入详细的仲裁驳回理由…"
                [value]="rejectNote()"
                (input)="rejectNote.set($any($event.target).value)"
              ></textarea>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn-outline-sm" (click)="cancelModal()">取消</button>
              <button
                type="button"
                class="btn-danger-sm"
                [disabled]="!rejectNote().trim() || arbitrationBusy()"
                (click)="confirmExecuteReject(target)"
              >
                {{ arbitrationBusy() ? '执行中…' : '确认驳回' }}
              </button>
            </div>
          </div>
        </div>
      }

      @if (approveTarget(); as target) {
        <div class="modal-backdrop" (click)="cancelModal()">
          <div class="modal-card" role="dialog" aria-modal="true" aria-labelledby="approve-refund-title" (click)="$event.stopPropagation()">
            <h4 id="approve-refund-title" class="modal-title nur-serif">确认执行退款仲裁</h4>
            <p class="modal-desc">
              退款金额 <strong>¥{{ yuan(target.computedAmountCents) }}</strong> 将原路返还至学员/家长家庭钱包并同步调减课酬预分配。此操作不可逆！
            </p>
            <div class="form-group">
              <label for="refund-approve-note">执行批注</label>
              <input
                id="refund-approve-note"
                type="text"
                maxlength="512"
                [value]="approveNote()"
                (input)="approveNote.set($any($event.target).value)"
              />
            </div>
            <div class="modal-footer">
              <button type="button" class="btn-outline-sm" (click)="cancelModal()">取消</button>
              <button
                type="button"
                class="btn-primary-sm"
                [disabled]="arbitrationBusy()"
                (click)="confirmExecuteApprove(target)"
              >
                {{ arbitrationBusy() ? '原子执行中…' : '确认执行入账' }}
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
    .case-list {
      margin: 0;
      padding: 0;
      list-style: none;
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
    }
    .case-card {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: var(--space-4);
      padding: var(--space-4) var(--space-5);
      background: var(--bg-card);
      border: 1px solid var(--line);
      border-radius: var(--radius-card);
      box-shadow: var(--shadow-subtle);
    }
    .case-main {
      flex: 1;
      min-width: 0;
    }
    .case-meta {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--space-3);
      margin-bottom: var(--space-2);
    }
    .order-ref {
      font-weight: 600;
      color: var(--text-title);
      font-size: 0.98rem;
    }
    .amount-tag {
      font-family: var(--font-serif);
      font-size: 1.15rem;
      font-weight: 600;
      color: var(--color-primary);
    }
    .badge {
      display: inline-flex;
      align-items: center;
      padding: 2px 10px;
      border-radius: var(--radius-pill);
      font-size: 0.8rem;
      font-weight: 500;
      background: var(--bg-page);
      color: var(--text-muted);
    }
    .badge[data-status="REQUESTED"] {
      background: var(--color-gold-bg);
      color: var(--color-gold-text);
    }
    .badge[data-status="EVALUATED"] {
      background: var(--color-primary-soft);
      color: var(--color-primary);
    }
    .badge[data-status="APPROVED"], .badge[data-status="EXECUTED"] {
      background: var(--color-success-bg);
      color: var(--color-success);
    }
    .badge[data-status="REJECTED"] {
      background: var(--color-danger-bg);
      color: var(--color-danger);
    }
    .case-reason {
      margin: 0 0 var(--space-2);
      font-size: 0.88rem;
      color: var(--text-main);
      line-height: 1.6;
    }
    .impact-panel {
      margin-top: var(--space-3);
      padding: var(--space-4);
      border-radius: var(--radius-control);
      background: var(--color-primary-soft);
      border: 1px solid rgba(30, 77, 62, 0.12);
    }
    .impact-header {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: center;
      gap: var(--space-2);
      margin-bottom: var(--space-2);
    }
    .impact-title {
      font-weight: 600;
      color: var(--color-primary);
      font-size: 0.9rem;
    }
    .impact-expiry {
      font-size: 0.8rem;
      color: var(--color-gold-text);
    }
    .impact-notice {
      margin: 0 0 var(--space-2);
      font-size: 0.85rem;
      color: var(--text-muted);
    }
    .impact-list {
      margin: 0;
      padding-left: var(--space-4);
      font-size: 0.85rem;
      color: var(--text-main);
      line-height: 1.7;
      word-break: break-all;
    }
    .impact-key {
      font-weight: 500;
      color: var(--color-primary);
    }
    .case-actions {
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
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
    .btn-danger-sm {
      min-height: 38px;
      padding: 0 var(--space-4);
      border: 1px solid var(--color-danger);
      border-radius: var(--radius-pill);
      background: transparent;
      color: var(--color-danger);
      font-size: 0.88rem;
      font-weight: 500;
      transition: all var(--duration-enter) var(--ease);
    }
    .btn-danger-sm:hover:not(:disabled) {
      background: var(--color-danger-bg);
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
      max-width: 480px;
      width: 100%;
      box-shadow: var(--shadow-hover);
    }
    .modal-title {
      margin: 0 0 var(--space-2);
      font-size: 1.25rem;
      color: var(--text-title);
    }
    .modal-desc {
      margin: 0 0 var(--space-4);
      font-size: 0.9rem;
      color: var(--text-muted);
      line-height: 1.6;
    }
    .form-group {
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
      margin-bottom: var(--space-5);
    }
    .form-group label {
      font-size: 0.88rem;
      font-weight: 500;
      color: var(--text-main);
    }
    .required {
      color: var(--color-danger);
    }
    .modal-footer {
      display: flex;
      justify-content: flex-end;
      gap: var(--space-3);
    }
    @media (max-width: 768px) {
      .case-card {
        flex-direction: column;
      }
      .case-actions {
        flex-direction: row;
        width: 100%;
      }
      .case-actions button {
        flex: 1;
      }
    }
  `]
})
export class AdminRefundsComponent implements OnInit {
  private readonly http = inject(HttpClient);

  readonly auditChanged = output<void>();

  readonly cases = signal<RefundCase[]>([]);
  readonly page = signal(0);
  readonly pages = signal(0);
  readonly total = signal(0);
  readonly busy = signal(false);
  readonly filter = signal('');
  private version = 0;

  readonly notice = signal('');
  readonly isError = signal(false);
  readonly arbitrationBusy = signal(false);
  readonly previewTargetId = signal<string | null>(null);
  readonly preview = signal<{ caseId: string; data: ArbitrationPreview } | null>(null);

  readonly rejectTarget = signal<RefundCase | null>(null);
  readonly rejectNote = signal('');
  readonly approveTarget = signal<RefundCase | null>(null);
  readonly approveNote = signal('仲裁通过，退款原路入账');

  readonly statusOptions = [
    { value: 'REQUESTED', label: '待评估' },
    { value: 'EVALUATED', label: '已评估' },
    { value: 'APPROVED', label: '已批准' },
    { value: 'REJECTED', label: '已驳回' },
    { value: 'EXECUTED', label: '已执行' },
  ];

  ngOnInit(): void {
    this.loadQueue();
  }

  loadQueue(): void {
    const v = ++this.version;
    let params = new HttpParams().set('page', this.page()).set('size', 20);
    if (this.filter().trim()) {
      params = params.set('status', this.filter().trim());
    }
    this.busy.set(true);
    this.http.get<{ content: RefundCase[]; totalPages: number; totalElements: number }>(
      API.admin.refundCases,
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
        this.cases.set(d.content);
        this.busy.set(false);
      },
      error: () => {
        if (v !== this.version) return;
        this.busy.set(false);
        this.isError.set(true);
        this.notice.set('退款案例列表读取失败，请点击刷新重试。');
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

  formatStatus(status: string): string {
    const opt = this.statusOptions.find((o) => o.value === status);
    return opt ? opt.label : status;
  }

  yuan(cents: number | undefined): string {
    return ((cents ?? 0) / 100).toFixed(2);
  }

  hasValidPreview(caseId: string): boolean {
    const p = this.preview();
    return p !== null && p.caseId === caseId && !!p.data?.confirmationToken;
  }

  arbitrationPreview(caseId: string): void {
    if (this.arbitrationBusy()) return;
    this.arbitrationBusy.set(true);
    this.previewTargetId.set(caseId);
    this.http.post<ArbitrationPreview>(API.admin.arbitrationPreview(caseId), {}).subscribe({
      next: (data) => {
        this.arbitrationBusy.set(false);
        this.previewTargetId.set(null);
        this.preview.set({ caseId, data });
        this.isError.set(false);
        this.notice.set('第一阶段评估完成，已获得短效确认凭证。请核对级联影响后点击「执行退款入账」。');
      },
      error: (e) => {
        this.arbitrationBusy.set(false);
        this.previewTargetId.set(null);
        this.isError.set(true);
        this.notice.set(e?.error?.detail ?? '影响评估未成功，请稍后重试。');
      },
    });
  }

  askExecuteApprove(c: RefundCase): void {
    this.approveNote.set('仲裁通过，退款原路入账');
    this.approveTarget.set(c);
  }

  confirmExecuteApprove(c: RefundCase): void {
    const current = this.preview();
    if (!current || current.caseId !== c.caseId) {
      this.isError.set(true);
      this.notice.set('请先完成第一阶段 Preview 评估。');
      return;
    }
    if (this.arbitrationBusy()) return;
    this.arbitrationBusy.set(true);
    this.http.post(API.admin.arbitrationExecute(c.caseId), {
      approve: true,
      decisionNote: this.approveNote().trim() || '仲裁通过，退款原路入账',
      confirmationToken: current.data.confirmationToken,
    }).subscribe({
      next: () => {
        this.arbitrationBusy.set(false);
        this.approveTarget.set(null);
        this.preview.set(null);
        this.isError.set(false);
        this.notice.set('退款已执行并入账家庭钱包，操作已落审计日志。');
        this.loadQueue();
        this.auditChanged.emit();
      },
      error: (e) => {
        this.arbitrationBusy.set(false);
        this.isError.set(true);
        this.notice.set(e?.error?.detail ?? '执行未成功：凭证可能已过期，请重新评估影响后重试。');
      },
    });
  }

  askExecuteReject(c: RefundCase): void {
    this.rejectNote.set('');
    this.rejectTarget.set(c);
  }

  confirmExecuteReject(c: RefundCase): void {
    const note = this.rejectNote().trim();
    if (!note || note.length > 512) {
      this.isError.set(true);
      this.notice.set('请输入 1–512 字的驳回说明。');
      return;
    }
    if (this.arbitrationBusy()) return;
    this.arbitrationBusy.set(true);
    this.http.post(API.admin.arbitrationExecute(c.caseId), {
      approve: false,
      decisionNote: note,
    }).subscribe({
      next: () => {
        this.arbitrationBusy.set(false);
        this.rejectTarget.set(null);
        this.preview.set(null);
        this.isError.set(false);
        this.notice.set('已驳回该退款申请并记录审计。');
        this.loadQueue();
        this.auditChanged.emit();
      },
      error: (e) => {
        this.arbitrationBusy.set(false);
        this.isError.set(true);
        this.notice.set(e?.error?.detail ?? '驳回未成功，请稍后重试。');
      },
    });
  }

  cancelModal(): void {
    this.rejectTarget.set(null);
    this.approveTarget.set(null);
  }
}
