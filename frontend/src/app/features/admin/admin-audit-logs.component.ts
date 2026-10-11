import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { API } from '../../core/api/endpoints';
import { QueuePagerComponent } from '../../shared/components/queue-pager.component';

export interface AuditLog {
  auditId: string;
  callerId: string | null;
  callerRole: string | null;
  occurredAt: string;
  actionType: string;
  executionStatus: string;
  payloadHash: string | null;
}

@Component({
  selector: 'nur-admin-audit-logs',
  standalone: true,
  imports: [DatePipe, QueuePagerComponent],
  template: `
    <div class="queue-panel">
      <div class="panel-header">
        <div>
          <h3 class="panel-title nur-serif">全站审计日志流水检视</h3>
          <p class="panel-desc">底座核心安全机制：由数据库触发器保障物理不可篡改性，全量记录关键写操作、载荷摘要与调用者角色。</p>
        </div>
      </div>

      @if (notice()) {
        <div class="status-banner status-error" role="status">
          <span class="status-icon" aria-hidden="true">⚠</span>
          <span>{{ notice() }}</span>
          <button type="button" class="btn-dismiss" (click)="notice.set('')" aria-label="关闭提示">×</button>
        </div>
      }

      <div class="filter-bar">
        <label for="audit-filter-input" class="filter-label">按动作类型检索：</label>
        <div class="search-input-wrap">
          <input
            id="audit-filter-input"
            type="text"
            maxlength="128"
            placeholder="如 admin:account-lock、OFFERING_RELIST…"
            [value]="filter()"
            (change)="filterQueue($any($event.target).value)"
          />
          @if (filter()) {
            <button type="button" class="btn-clear" (click)="filterQueue('')" aria-label="清空筛选">×</button>
          }
        </div>
      </div>

      <nur-queue-pager
        label="审计日志"
        [page]="page()"
        [pages]="pages()"
        [total]="total()"
        [busy]="busy()"
        [filter]="filter()"
        (turn)="turnQueue($event)"
        (filterChange)="filterQueue($event)"
        (reload)="loadQueue()"
      />

      <div class="table-scroll" tabindex="0" role="region" aria-label="审计日志列表">
        <table class="data-table">
          <thead>
            <tr>
              <th scope="col">记录时间</th>
              <th scope="col">动作类型</th>
              <th scope="col">调用者角色</th>
              <th scope="col">执行状态</th>
              <th scope="col">载荷摘要哈希</th>
            </tr>
          </thead>
          <tbody>
            @if (logs().length === 0) {
              <tr>
                <td colspan="5" class="empty-cell">当前筛选条件下暂无审计流水。</td>
              </tr>
            } @else {
              @for (log of logs(); track log.auditId) {
                <tr>
                  <td class="cell-time">{{ log.occurredAt | date:'yyyy-MM-dd HH:mm:ss' }}</td>
                  <td><code class="action-code">{{ log.actionType }}</code></td>
                  <td><span class="role-chip">{{ log.callerRole || '—' }}</span></td>
                  <td>
                    <span class="status-chip" [attr.data-status]="log.executionStatus">
                      {{ log.executionStatus }}
                    </span>
                  </td>
                  <td class="cell-hash">
                    <span class="hash-text" [title]="log.payloadHash || '无载荷哈希'">
                      {{ log.payloadHash ? log.payloadHash.slice(0, 16) + '…' : '—' }}
                    </span>
                  </td>
                </tr>
              }
            }
          </tbody>
        </table>
      </div>

      <div class="security-footer">
        <span class="shield-icon" aria-hidden="true">🛡</span>
        <p class="security-hint">安全硬护栏：全站审计日志由底层数据库级防篡改触发器严格保护，任何 UPDATE 或 DELETE 操作均会被物理拒绝并引发系统告警。</p>
      </div>
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
    .filter-bar {
      display: flex;
      align-items: center;
      gap: var(--space-3);
      padding: var(--space-3) var(--space-4);
      background: var(--bg-card);
      border: 1px solid var(--line);
      border-radius: var(--radius-control);
    }
    .filter-label {
      font-size: 0.88rem;
      color: var(--text-muted);
      white-space: nowrap;
    }
    .search-input-wrap {
      position: relative;
      flex: 1;
      max-width: 360px;
    }
    .search-input-wrap input {
      width: 100%;
      min-height: 38px;
      padding-right: var(--space-6);
    }
    .btn-clear {
      position: absolute;
      right: 8px;
      top: 50%;
      transform: translateY(-50%);
      background: none;
      border: none;
      color: var(--text-muted);
      font-size: 1.1rem;
      min-height: auto;
      padding: 2px 6px;
    }
    .table-scroll {
      overflow-x: auto;
      background: var(--bg-card);
      border: 1px solid var(--line);
      border-radius: var(--radius-card);
      box-shadow: var(--shadow-subtle);
    }
    .data-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.88rem;
      min-width: 680px;
    }
    .data-table th,
    .data-table td {
      padding: var(--space-3) var(--space-4);
      border-bottom: 1px solid var(--line);
      text-align: left;
    }
    .data-table th {
      background: var(--bg-page);
      color: var(--text-muted);
      font-weight: 500;
      font-size: 0.82rem;
    }
    .data-table tr:last-child td {
      border-bottom: none;
    }
    .empty-cell {
      text-align: center;
      color: var(--text-muted);
      padding: var(--space-6) !important;
    }
    .cell-time {
      white-space: nowrap;
      color: var(--text-muted);
      font-size: 0.82rem;
    }
    .action-code {
      font-family: monospace;
      font-size: 0.85rem;
      color: var(--color-primary);
      background: var(--color-primary-soft);
      padding: 2px 6px;
      border-radius: 4px;
    }
    .role-chip {
      font-size: 0.82rem;
      color: var(--text-main);
    }
    .status-chip {
      display: inline-block;
      padding: 2px 8px;
      border-radius: var(--radius-pill);
      font-size: 0.78rem;
      font-weight: 500;
      background: var(--bg-page);
      color: var(--text-muted);
    }
    .status-chip[data-status="SUCCESS"] {
      background: var(--color-success-bg);
      color: var(--color-success);
    }
    .status-chip[data-status="FAILURE"] {
      background: var(--color-danger-bg);
      color: var(--color-danger);
    }
    .cell-hash {
      font-family: monospace;
      font-size: 0.8rem;
      color: var(--text-muted);
    }
    .hash-text {
      cursor: help;
    }
    .security-footer {
      display: flex;
      align-items: center;
      gap: var(--space-2);
      padding: var(--space-3) var(--space-4);
      border-radius: var(--radius-control);
      background: var(--bg-mist);
    }
    .shield-icon {
      font-size: 1.1rem;
      color: var(--color-primary);
      flex-shrink: 0;
    }
    .security-hint {
      margin: 0;
      font-size: 0.82rem;
      color: var(--text-muted);
      line-height: 1.6;
    }
  `]
})
export class AdminAuditLogsComponent implements OnInit {
  private readonly http = inject(HttpClient);

  readonly logs = signal<AuditLog[]>([]);
  readonly page = signal(0);
  readonly pages = signal(0);
  readonly total = signal(0);
  readonly busy = signal(false);
  readonly filter = signal('');
  private version = 0;

  readonly notice = signal('');

  ngOnInit(): void {
    this.loadQueue();
  }

  loadQueue(): void {
    const v = ++this.version;
    let params = new HttpParams().set('page', this.page()).set('size', 20);
    if (this.filter().trim()) {
      params = params.set('actionType', this.filter().trim());
    }
    this.busy.set(true);
    this.http.get<{ content: AuditLog[]; totalPages: number; totalElements: number }>(
      API.admin.auditLogs,
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
        this.logs.set(d.content);
        this.busy.set(false);
      },
      error: () => {
        if (v !== this.version) return;
        this.busy.set(false);
        this.notice.set('审计日志读取失败，请点击刷新重试。');
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
}
