import { Component, OnInit, inject, signal, output } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { API } from '../../core/api/endpoints';
import { QueuePagerComponent } from '../../shared/components/queue-pager.component';

export interface AccountView {
  accountId: string;
  role: string;
  displayName: string;
  phone: string;
  accountNo: string;
  status: string;
}

@Component({
  selector: 'nur-admin-accounts',
  standalone: true,
  imports: [QueuePagerComponent],
  template: `
    <div class="queue-panel">
      <!-- D2 登录限频误锁解封工具卡 -->
      <section class="throttle-card" aria-labelledby="throttle-title">
        <div class="throttle-header">
          <div class="icon-circle" aria-hidden="true">🔓</div>
          <div>
            <h4 id="throttle-title" class="throttle-title nur-serif">登录限频误锁解封</h4>
            <p class="throttle-desc">
              当学员、家长或导师因连续输错密码被系统触发频控锁定，或收到误锁申诉时，在此输入账号或 IP 可立即清空失败计数并解除临时封锁。动作落不可篡改审计流水。
            </p>
          </div>
        </div>

        <form class="throttle-form" (submit)="$event.preventDefault(); submitResetThrottle()">
          <div class="form-row">
            <div class="input-field">
              <label for="throttle-account">用户账号 / 手机号</label>
              <input
                id="throttle-account"
                type="text"
                placeholder="如 13800138000 或账号号"
                [value]="throttleAccount()"
                (input)="throttleAccount.set($any($event.target).value)"
              />
            </div>
            <div class="input-field">
              <label for="throttle-ip">来源 IP 地址（可选）</label>
              <input
                id="throttle-ip"
                type="text"
                placeholder="如 192.168.1.100"
                [value]="throttleIp()"
                (input)="throttleIp.set($any($event.target).value)"
              />
            </div>
            <div class="throttle-action">
              <button
                type="submit"
                class="btn-primary-sm"
                [disabled]="throttleBusy() || (!throttleAccount().trim() && !throttleIp().trim())"
              >
                {{ throttleBusy() ? '解封中…' : '解除登录限频' }}
              </button>
            </div>
          </div>
          <p class="field-hint">说明：账号与 IP 至少填入一项。若填写 IP，将同时重置该来源网络的所有频控封禁计数。</p>
        </form>

        @if (throttleMessage()) {
          <div class="status-banner" [class.status-error]="throttleIsError()" role="status">
            <span class="status-icon" aria-hidden="true">{{ throttleIsError() ? '⚠' : '✓' }}</span>
            <span>{{ throttleMessage() }}</span>
            <button type="button" class="btn-dismiss" (click)="throttleMessage.set('')" aria-label="关闭提示">×</button>
          </div>
        }
      </section>

      <!-- 账户列表与违规干预 -->
      <section class="accounts-section" aria-labelledby="accounts-list-title">
        <div class="panel-header">
          <div>
            <h3 id="accounts-list-title" class="panel-title nur-serif">全站账户管理与违规干预</h3>
            <p class="panel-desc">对违规账户实施临时锁定或解封处理，全部治理操作实时记录审计凭证。运营管理员账户受保护不可在此锁定。</p>
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
          label="账户"
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

        <div class="table-scroll" tabindex="0" role="region" aria-label="账户列表">
          <table class="data-table">
            <thead>
              <tr>
                <th scope="col">账户与编号</th>
                <th scope="col">角色身份</th>
                <th scope="col">联系手机</th>
                <th scope="col">账户状态</th>
                <th scope="col">违规治理操作</th>
              </tr>
            </thead>
            <tbody>
              @if (accounts().length === 0) {
                <tr>
                  <td colspan="5" class="empty-cell">当前筛选条件下暂无账户记录。</td>
                </tr>
              } @else {
                @for (a of accounts(); track a.accountId) {
                  <tr>
                    <td>
                      <span class="acc-name">{{ a.displayName }}</span>
                      <span class="acc-no">（{{ a.accountNo }}）</span>
                    </td>
                    <td>
                      <span class="role-tag" [attr.data-role]="a.role">{{ formatRole(a.role) }}</span>
                    </td>
                    <td>{{ a.phone || '—' }}</td>
                    <td>
                      <span class="badge" [attr.data-status]="a.status">{{ formatStatus(a.status) }}</span>
                    </td>
                    <td>
                      @if (a.role !== 'ADMIN') {
                        @if (a.status === 'ACTIVE') {
                          <button
                            type="button"
                            class="btn-danger-sm"
                            [disabled]="actionBusy() === a.accountId"
                            (click)="askLock(a, true)"
                          >
                            锁定账户
                          </button>
                        } @else if (a.status === 'LOCKED') {
                          <button
                            type="button"
                            class="btn-outline-sm"
                            [disabled]="actionBusy() === a.accountId"
                            (click)="askLock(a, false)"
                          >
                            恢复正常
                          </button>
                        } @else {
                          <span class="text-muted">已注销</span>
                        }
                      } @else {
                        <span class="text-muted" title="管理员账户不可在此锁定">受保护 (ADMIN)</span>
                      }
                    </td>
                  </tr>
                }
              }
            </tbody>
          </table>
        </div>
      </section>

      <!-- 锁定/解封二次确认弹层 -->
      @if (lockTarget(); as target) {
        <div class="modal-backdrop" (click)="cancelLockModal()">
          <div class="modal-card" role="dialog" aria-modal="true" aria-labelledby="lock-modal-title" (click)="$event.stopPropagation()">
            <h4 id="lock-modal-title" class="modal-title nur-serif">
              {{ isLocking() ? '锁定违规账户' : '解除账户锁定' }}
            </h4>
            <p class="modal-desc">
              {{ isLocking()
                ? '锁定后该账户（' + target.displayName + ' · ' + target.accountNo + '）将无法登录或发起交易，请明确录入处置理由以备审计查证。'
                : '确认解除对账户（' + target.displayName + ' · ' + target.accountNo + '）的锁定限制？'
              }}
            </p>
            <div class="form-group">
              <label for="lock-note">
                {{ isLocking() ? '处置理由（必填，入审计日志）' : '解封备注' }}
                @if (isLocking()) {
                  <span class="required">*</span>
                }
              </label>
              <input
                id="lock-note"
                type="text"
                maxlength="200"
                [placeholder]="isLocking() ? '请输入具体的违规干预原因…' : '请输入解除锁定说明…'"
                [value]="lockNote()"
                (input)="lockNote.set($any($event.target).value)"
              />
            </div>
            <div class="modal-footer">
              <button type="button" class="btn-outline-sm" (click)="cancelLockModal()">取消</button>
              <button
                type="button"
                [class]="isLocking() ? 'btn-danger-sm' : 'btn-primary-sm'"
                [disabled]="(isLocking() && !lockNote().trim()) || actionBusy() === target.accountId"
                (click)="confirmSetLock(target)"
              >
                {{ actionBusy() === target.accountId ? '提交中…' : (isLocking() ? '确认锁定' : '确认解封') }}
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
      gap: var(--space-6);
    }
    .throttle-card {
      padding: var(--space-5);
      background: var(--bg-card);
      border: 1px solid var(--line);
      border-radius: var(--radius-card);
      box-shadow: var(--shadow-subtle);
    }
    .throttle-header {
      display: flex;
      align-items: flex-start;
      gap: var(--space-3);
      margin-bottom: var(--space-4);
    }
    .icon-circle {
      width: 36px;
      height: 36px;
      display: grid;
      place-items: center;
      background: var(--color-gold-bg);
      border-radius: var(--radius-pill);
      font-size: 1.1rem;
      flex-shrink: 0;
    }
    .throttle-title {
      margin: 0;
      font-size: 1.15rem;
      color: var(--text-title);
    }
    .throttle-desc {
      margin: var(--space-1) 0 0;
      font-size: 0.88rem;
      color: var(--text-muted);
      line-height: 1.6;
    }
    .throttle-form {
      margin-top: var(--space-3);
    }
    .form-row {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-end;
      gap: var(--space-3);
    }
    .input-field {
      flex: 1;
      min-width: 200px;
      display: flex;
      flex-direction: column;
      gap: var(--space-1);
    }
    .input-field label {
      font-size: 0.82rem;
      font-weight: 500;
      color: var(--text-muted);
    }
    .input-field input {
      min-height: 40px;
    }
    .throttle-action {
      flex-shrink: 0;
    }
    .field-hint {
      margin: var(--space-2) 0 0;
      font-size: 0.8rem;
      color: var(--text-placeholder);
    }
    .accounts-section {
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
      margin-top: var(--space-3);
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
      min-width: 640px;
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
    .acc-name {
      font-weight: 600;
      color: var(--text-title);
    }
    .acc-no {
      font-size: 0.82rem;
      color: var(--text-muted);
    }
    .role-tag {
      display: inline-block;
      padding: 2px 8px;
      border-radius: var(--radius-pill);
      font-size: 0.78rem;
      background: var(--bg-page);
      color: var(--text-muted);
    }
    .role-tag[data-role="STUDENT"] { background: var(--color-primary-soft); color: var(--color-primary); }
    .role-tag[data-role="PARENT"] { background: var(--color-gold-bg); color: var(--color-gold-text); }
    .role-tag[data-role="TEACHER"] { background: var(--color-success-bg); color: var(--color-success); }
    .role-tag[data-role="ADMIN"] { background: var(--bg-mist); color: var(--text-title); font-weight: 600; }
    .badge {
      display: inline-block;
      padding: 2px 8px;
      border-radius: var(--radius-pill);
      font-size: 0.78rem;
    }
    .badge[data-status="ACTIVE"] { background: var(--color-success-bg); color: var(--color-success); }
    .badge[data-status="LOCKED"] { background: var(--color-danger-bg); color: var(--color-danger); }
    .badge[data-status="CLOSED"] { background: var(--bg-page); color: var(--text-muted); }
    .text-muted { color: var(--text-muted); font-size: 0.82rem; }
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
      min-height: 34px;
      padding: 0 var(--space-3);
      border: 1px solid var(--color-danger);
      border-radius: var(--radius-pill);
      background: transparent;
      color: var(--color-danger);
      font-size: 0.82rem;
      font-weight: 500;
      transition: all var(--duration-enter) var(--ease);
    }
    .btn-danger-sm:hover:not(:disabled) {
      background: var(--color-danger-bg);
    }
    .btn-outline-sm {
      min-height: 34px;
      padding: 0 var(--space-3);
      border: 1px solid var(--line);
      border-radius: var(--radius-pill);
      background: var(--bg-card);
      color: var(--text-main);
      font-size: 0.82rem;
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
    .required { color: var(--color-danger); }
    .modal-footer {
      display: flex;
      justify-content: flex-end;
      gap: var(--space-3);
    }
  `]
})
export class AdminAccountsComponent implements OnInit {
  private readonly http = inject(HttpClient);

  readonly auditChanged = output<void>();

  // D2 限流解锁状态
  readonly throttleAccount = signal('');
  readonly throttleIp = signal('');
  readonly throttleBusy = signal(false);
  readonly throttleMessage = signal('');
  readonly throttleIsError = signal(false);

  // 账户队列状态
  readonly accounts = signal<AccountView[]>([]);
  readonly page = signal(0);
  readonly pages = signal(0);
  readonly total = signal(0);
  readonly busy = signal(false);
  readonly filter = signal('');
  private version = 0;

  readonly notice = signal('');
  readonly isError = signal(false);
  readonly actionBusy = signal<string | null>(null);

  readonly lockTarget = signal<AccountView | null>(null);
  readonly isLocking = signal(true);
  readonly lockNote = signal('');

  readonly statusOptions = [
    { value: 'ACTIVE', label: '正常' },
    { value: 'LOCKED', label: '已锁定' },
    { value: 'CLOSED', label: '已注销' },
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
    this.http.get<{ content: AccountView[]; totalPages: number; totalElements: number }>(
      API.admin.accounts,
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
        this.accounts.set(d.content);
        this.busy.set(false);
      },
      error: () => {
        if (v !== this.version) return;
        this.busy.set(false);
        this.isError.set(true);
        this.notice.set('账户列表读取失败，请点击刷新重试。');
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

  formatRole(role: string): string {
    const map: Record<string, string> = {
      STUDENT: '学生',
      PARENT: '家长',
      TEACHER: '导师',
      ADMIN: '运营管理',
    };
    return map[role] ?? role;
  }

  formatStatus(status: string): string {
    const opt = this.statusOptions.find((o) => o.value === status);
    return opt ? opt.label : status;
  }

  // D2 登录限流解锁
  submitResetThrottle(): void {
    const account = this.throttleAccount().trim();
    const ip = this.throttleIp().trim();
    if (!account && !ip) {
      this.throttleIsError.set(true);
      this.throttleMessage.set('请至少提供用户账号或来源 IP 之一。');
      return;
    }
    if (this.throttleBusy()) return;
    this.throttleBusy.set(true);
    this.http.post<{ reset: boolean; account: string; ip: string }>(
      API.admin.resetLoginThrottle,
      { account: account || undefined, ip: ip || undefined }
    ).subscribe({
      next: (res) => {
        this.throttleBusy.set(false);
        this.throttleIsError.set(false);
        const details = [
          res.account ? `账号「${res.account}」` : null,
          res.ip ? `IP「${res.ip}」` : null,
        ].filter(Boolean).join(' 与 ');
        this.throttleMessage.set(`已成功重置 ${details} 的登录频控计数，限制已解除。`);
        this.throttleAccount.set('');
        this.throttleIp.set('');
        this.auditChanged.emit();
      },
      error: (e) => {
        this.throttleBusy.set(false);
        this.throttleIsError.set(true);
        this.throttleMessage.set(e?.error?.detail ?? '解除登录限频失败，请检查输入或稍后重试。');
      },
    });
  }

  askLock(a: AccountView, lock: boolean): void {
    this.isLocking.set(lock);
    this.lockNote.set(lock ? '' : '解除锁定');
    this.lockTarget.set(a);
  }

  confirmSetLock(a: AccountView): void {
    const note = this.lockNote().trim();
    if (this.isLocking() && !note) {
      this.isError.set(true);
      this.notice.set('锁定违规账户必须提供处置理由以备审计。');
      return;
    }
    if (this.actionBusy()) return;
    this.actionBusy.set(a.accountId);
    const url = this.isLocking()
      ? API.admin.lockAccount(a.accountId)
      : API.admin.unlockAccount(a.accountId);

    this.http.post(url, { note: note || '解除锁定' }).subscribe({
      next: () => {
        this.actionBusy.set(null);
        this.lockTarget.set(null);
        this.isError.set(false);
        this.notice.set(this.isLocking() ? '账户已锁定并记入不可篡改审计日志。' : '账户已恢复正常状态。');
        this.loadQueue();
        this.auditChanged.emit();
      },
      error: (e) => {
        this.actionBusy.set(null);
        this.isError.set(true);
        this.notice.set(e?.error?.detail ?? '账户治理操作失败，请稍后重试。');
      },
    });
  }

  cancelLockModal(): void {
    this.lockTarget.set(null);
  }
}
