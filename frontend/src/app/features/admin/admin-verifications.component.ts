import { Component, OnInit, inject, signal, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { API, API_BASE } from '../../core/api/endpoints';
import { QueuePagerComponent } from '../../shared/components/queue-pager.component';

export interface VerificationCase {
  caseId: string;
  teacherId: string;
  status: string;
  decisionNote: string | null;
  submittedAt: string;
}

@Component({
  selector: 'nur-admin-verifications',
  standalone: true,
  imports: [DatePipe, QueuePagerComponent],
  template: `
    <div class="queue-panel">
      <div class="panel-header">
        <div>
          <h3 class="panel-title nur-serif">教师资质审核流转</h3>
          <p class="panel-desc">严格审查导师身份与学历资质证明，审核决定实时通知导师并记录不可篡改审计流水。</p>
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
        label="资质审核"
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
          <p class="empty-text">当前筛选条件下暂无资质审核案例。</p>
        </div>
      } @else {
        <ul class="case-list" role="list">
          @for (c of cases(); track c.caseId) {
            <li class="case-card">
              <div class="case-main">
                <div class="case-meta">
                  <span class="case-teacher">导师 {{ c.teacherId.slice(0, 8) }}</span>
                  <span class="badge" [attr.data-status]="c.status">{{ formatStatus(c.status) }}</span>
                  <span class="case-time">提交于 {{ c.submittedAt | date:'yyyy-MM-dd HH:mm' }}</span>
                </div>
                @if (c.decisionNote) {
                  <p class="decision-note">审核批注：{{ c.decisionNote }}</p>
                }
                <div class="material-actions">
                  <span class="action-label">受控凭证：</span>
                  <button
                    type="button"
                    class="btn-outline-sm"
                    (click)="downloadMaterial(c.caseId, 'identity')"
                    [disabled]="materialBusy() === c.caseId + '-identity'"
                  >
                    {{ materialBusy() === c.caseId + '-identity' ? '下载中…' : '下载身份证明' }}
                  </button>
                  <button
                    type="button"
                    class="btn-outline-sm"
                    (click)="downloadMaterial(c.caseId, 'degree')"
                    [disabled]="materialBusy() === c.caseId + '-degree'"
                  >
                    {{ materialBusy() === c.caseId + '-degree' ? '下载中…' : '下载学历证明' }}
                  </button>
                </div>
              </div>

              @if (c.status === 'SUBMITTED' || c.status === 'UNDER_REVIEW') {
                <div class="case-actions">
                  <button
                    type="button"
                    class="btn-primary-sm"
                    [disabled]="actionBusy() === c.caseId"
                    (click)="askApprove(c)"
                  >
                    通过审核
                  </button>
                  <button
                    type="button"
                    class="btn-danger-sm"
                    [disabled]="actionBusy() === c.caseId"
                    (click)="askReject(c)"
                  >
                    驳回材料
                  </button>
                </div>
              }
            </li>
          }
        </ul>
      }

      @if (rejectTarget(); as target) {
        <div class="modal-backdrop" (click)="cancelModal()">
          <div class="modal-card" role="dialog" aria-modal="true" aria-labelledby="reject-title" (click)="$event.stopPropagation()">
            <h4 id="reject-title" class="modal-title nur-serif">驳回资质审核申请</h4>
            <p class="modal-desc">驳回原因将直接向导师公示，请详细说明需要补充或修改的材料规范（如：证件模糊、学历证书编号不全等）。</p>
            <div class="form-group">
              <label for="reject-note">驳回原因 <span class="required">*</span></label>
              <textarea
                id="reject-note"
                rows="3"
                maxlength="500"
                placeholder="请输入明确的驳回原因…"
                [value]="rejectNote()"
                (input)="rejectNote.set($any($event.target).value)"
              ></textarea>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn-outline-sm" (click)="cancelModal()">取消</button>
              <button
                type="button"
                class="btn-danger-sm"
                [disabled]="!rejectNote().trim() || actionBusy() === target.caseId"
                (click)="confirmReject(target)"
              >
                {{ actionBusy() === target.caseId ? '提交中…' : '确认驳回' }}
              </button>
            </div>
          </div>
        </div>
      }

      @if (approveTarget(); as target) {
        <div class="modal-backdrop" (click)="cancelModal()">
          <div class="modal-card" role="dialog" aria-modal="true" aria-labelledby="approve-title" (click)="$event.stopPropagation()">
            <h4 id="approve-title" class="modal-title nur-serif">通过导师资质审核</h4>
            <p class="modal-desc">导师 {{ target.teacherId.slice(0, 8) }} 将获得认证教师标识并可正式发布教学档期。确认核验材料齐全？</p>
            <div class="form-group">
              <label for="approve-note">核验备注</label>
              <input
                id="approve-note"
                type="text"
                maxlength="200"
                [value]="approveNote()"
                (input)="approveNote.set($any($event.target).value)"
              />
            </div>
            <div class="modal-footer">
              <button type="button" class="btn-outline-sm" (click)="cancelModal()">取消</button>
              <button
                type="button"
                class="btn-primary-sm"
                [disabled]="actionBusy() === target.caseId"
                (click)="confirmApprove(target)"
              >
                {{ actionBusy() === target.caseId ? '处理中…' : '确认通过' }}
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
      transition: border-color var(--duration-enter) var(--ease);
    }
    .case-card:hover {
      border-color: rgba(30, 77, 62, 0.28);
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
    .case-teacher {
      font-weight: 600;
      color: var(--text-title);
      font-size: 0.98rem;
    }
    .case-time {
      font-size: 0.85rem;
      color: var(--text-muted);
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
    .badge[data-status="SUBMITTED"] {
      background: var(--color-gold-bg);
      color: var(--color-gold-text);
    }
    .badge[data-status="UNDER_REVIEW"] {
      background: var(--color-primary-soft);
      color: var(--color-primary);
    }
    .badge[data-status="APPROVED"] {
      background: var(--color-success-bg);
      color: var(--color-success);
    }
    .badge[data-status="REJECTED"] {
      background: var(--color-danger-bg);
      color: var(--color-danger);
    }
    .decision-note {
      margin: 0 0 var(--space-2);
      font-size: 0.88rem;
      color: var(--text-muted);
      line-height: 1.6;
      background: var(--bg-page);
      padding: var(--space-2) var(--space-3);
      border-radius: 8px;
    }
    .material-actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--space-2);
      margin-top: var(--space-2);
    }
    .action-label {
      font-size: 0.85rem;
      color: var(--text-muted);
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
      opacity: 0.55;
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
export class AdminVerificationsComponent implements OnInit {
  private readonly http = inject(HttpClient);

  readonly auditChanged = output<void>();

  readonly cases = signal<VerificationCase[]>([]);
  readonly page = signal(0);
  readonly pages = signal(0);
  readonly total = signal(0);
  readonly busy = signal(false);
  readonly filter = signal('');
  private version = 0;

  readonly notice = signal('');
  readonly isError = signal(false);
  readonly materialBusy = signal<string | null>(null);
  readonly actionBusy = signal<string | null>(null);

  readonly rejectTarget = signal<VerificationCase | null>(null);
  readonly rejectNote = signal('');
  readonly approveTarget = signal<VerificationCase | null>(null);
  readonly approveNote = signal('材料齐全，核验通过');

  readonly statusOptions = [
    { value: 'SUBMITTED', label: '待审核' },
    { value: 'UNDER_REVIEW', label: '审核中' },
    { value: 'APPROVED', label: '已通过' },
    { value: 'REJECTED', label: '已驳回' },
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
    this.http.get<{ content: VerificationCase[]; totalPages: number; totalElements: number }>(
      API.admin.verificationCases,
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
        this.notice.set('资质审核列表读取失败，请点击刷新重试。');
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

  downloadMaterial(caseId: string, kind: 'identity' | 'degree'): void {
    const key = `${caseId}-${kind}`;
    if (this.materialBusy()) return;
    this.materialBusy.set(key);
    this.http.get(`${API_BASE}/admin/verification-cases/${caseId}/materials/${kind}`, {
      responseType: 'blob',
      observe: 'response',
    }).subscribe({
      next: (response) => {
        this.materialBusy.set(null);
        if (!response.body) {
          this.isError.set(true);
          this.notice.set('材料内容为空，请联系导师补交。');
          return;
        }
        const url = URL.createObjectURL(response.body);
        const a = document.createElement('a');
        a.href = url;
        a.download = response.headers.get('Content-Disposition')?.match(/filename="?([^";]+)"?/)?.[1] ?? `${kind}-${caseId}.bin`;
        a.click();
        setTimeout(() => URL.revokeObjectURL(url), 10000);
        this.isError.set(false);
        this.notice.set('材料已下载，请核对后完成审核。');
        this.auditChanged.emit();
      },
      error: () => {
        this.materialBusy.set(null);
        this.isError.set(true);
        this.notice.set('材料下载失败，请确认权限和材料是否仍可读取。');
      },
    });
  }

  askApprove(c: VerificationCase): void {
    this.approveNote.set('材料齐全，核验通过');
    this.approveTarget.set(c);
  }

  confirmApprove(c: VerificationCase): void {
    if (this.actionBusy()) return;
    this.actionBusy.set(c.caseId);
    this.http.post(API.admin.decideVerification(c.caseId), {
      approve: true,
      note: this.approveNote().trim() || '材料齐全，核验通过',
    }).subscribe({
      next: () => {
        this.actionBusy.set(null);
        this.approveTarget.set(null);
        this.isError.set(false);
        this.notice.set('审核结果已流转并通过，通知已下发。');
        this.loadQueue();
        this.auditChanged.emit();
      },
      error: (e) => {
        this.actionBusy.set(null);
        this.isError.set(true);
        this.notice.set(e?.error?.detail ?? '审核通过操作失败，请稍后重试。');
      },
    });
  }

  askReject(c: VerificationCase): void {
    this.rejectNote.set('');
    this.rejectTarget.set(c);
  }

  confirmReject(c: VerificationCase): void {
    const note = this.rejectNote().trim();
    if (!note) return;
    if (this.actionBusy()) return;
    this.actionBusy.set(c.caseId);
    this.http.post(API.admin.decideVerification(c.caseId), {
      approve: false,
      note,
    }).subscribe({
      next: () => {
        this.actionBusy.set(null);
        this.rejectTarget.set(null);
        this.isError.set(false);
        this.notice.set('审核已驳回，驳回原因已下发通知导师。');
        this.loadQueue();
        this.auditChanged.emit();
      },
      error: (e) => {
        this.actionBusy.set(null);
        this.isError.set(true);
        this.notice.set(e?.error?.detail ?? '审核驳回操作失败，请稍后重试。');
      },
    });
  }

  cancelModal(): void {
    this.rejectTarget.set(null);
    this.approveTarget.set(null);
  }
}
