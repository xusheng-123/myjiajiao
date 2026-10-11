import { Component, input, output, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API } from '../../core/api/endpoints';

export interface PresetConfig {
  key: string;
  name: string;
  unit: string;
  desc: string;
  defaultVal: string;
}

export interface ConfigUpdateRecord {
  key: string;
  value: string;
  updatedAt: Date;
}

@Component({
  selector: 'nur-admin-platform-config',
  standalone: true,
  template: `
    <div class="config-panel">
      <div class="panel-header">
        <div>
          <h3 class="panel-title nur-serif">平台全局参数治理（D1 契约）</h3>
          <p class="panel-desc">
            平台运行核心规则参数维护。修改将即时影响资金抽成、试听体验与撮合策略，写操作自动注入强幂等凭证并落入不可篡改审计日志。
          </p>
        </div>
      </div>

      <!-- 实时已知参数看板 -->
      <section class="current-card" aria-labelledby="current-fee-title">
        <div class="card-inner">
          <div class="gold-indicator" aria-hidden="true">✦</div>
          <div class="current-info">
            <span class="info-kicker">全景大盘实时已知项</span>
            <h4 id="current-fee-title" class="current-label">平台课酬抽成比例 (settlement.platform-fee-rate)</h4>
            <p class="current-note">
              数据源自概览实时聚合：当前平台课酬抽成系数为
              <strong class="highlight-val">{{ currentFeeRate() || '0.15' }}</strong>
              （即 {{ ((+currentFeeRate() || 0.15) * 100).toFixed(0) }}%）。
            </p>
          </div>
        </div>
        <p class="factual-note">
          架构说明：后端提供 <code>PUT /api/v1/admin/configs/&#123;key&#125;</code> 原生更新契约；因后端未提供全量参数列表端点，当前已知值从大盘聚合获取，其余项提交后由后端权威回显。
        </p>
      </section>

      <!-- 按键更新参数表单 -->
      <section class="form-card" aria-labelledby="update-form-title">
        <h4 id="update-form-title" class="form-title nur-serif">按键更新全局参数</h4>

        @if (updateMessage()) {
          <div class="status-banner" [class.status-error]="isError()" role="status">
            <span class="status-icon" aria-hidden="true">{{ isError() ? '⚠' : '✓' }}</span>
            <span>{{ updateMessage() }}</span>
            <button type="button" class="btn-dismiss" (click)="updateMessage.set('')" aria-label="关闭提示">×</button>
          </div>
        }

        <form class="config-form" (submit)="$event.preventDefault(); submitUpdate()">
          <div class="form-grid">
            <div class="field-col">
              <label for="config-key-select">参数键名 (Config Key) <span class="required">*</span></label>
              <select
                id="config-key-select"
                [value]="selectedKey()"
                (change)="onKeySelect($any($event.target).value)"
              >
                @for (p of presets; track p.key) {
                  <option [value]="p.key">{{ p.name }}（{{ p.key }}）</option>
                }
                <option value="__CUSTOM__">＋ 手动输入其他参数键名…</option>
              </select>
            </div>

            @if (selectedKey() === '__CUSTOM__') {
              <div class="field-col">
                <label for="custom-key-input">自定义键名 <span class="required">*</span></label>
                <input
                  id="custom-key-input"
                  type="text"
                  placeholder="如 system.feature-toggle"
                  [value]="customKey()"
                  (input)="customKey.set($any($event.target).value)"
                />
              </div>
            }

            <div class="field-col">
              <label for="config-val-input">
                新参数值 (Value) <span class="required">*</span>
                @if (activePreset(); as pr) {
                  <span class="unit-hint">（单位/说明：{{ pr.unit }}）</span>
                }
              </label>
              <input
                id="config-val-input"
                type="text"
                [placeholder]="activePreset()?.desc || '请输入新参数值…'"
                [value]="configValue()"
                (input)="configValue.set($any($event.target).value)"
              />
            </div>
          </div>

          @if (activePreset(); as pr) {
            <p class="preset-help">
              <strong>参数定义：</strong>{{ pr.desc }} · 参考默认值：<code>{{ pr.defaultVal }}</code>
            </p>
          }

          <div class="form-actions">
            <button
              type="submit"
              class="btn-primary"
              [disabled]="busy() || !effectiveKey() || !configValue().trim()"
            >
              {{ busy() ? '提交更新中…' : '提交更新参数 (PUT)' }}
            </button>
          </div>
        </form>
      </section>

      <!-- 预置核心参数清单与快捷填入 -->
      <section class="presets-card" aria-labelledby="presets-title">
        <h4 id="presets-title" class="presets-title nur-serif">后端预置核心参数速查</h4>
        <div class="preset-grid">
          @for (p of presets; track p.key) {
            <div class="preset-item" [class.preset-active]="effectiveKey() === p.key">
              <div class="preset-meta">
                <span class="preset-name">{{ p.name }}</span>
                <code class="preset-key">{{ p.key }}</code>
              </div>
              <p class="preset-desc">{{ p.desc }}（单位：{{ p.unit }}）</p>
              <div class="preset-footer">
                <span class="preset-default">默认：{{ p.defaultVal }}</span>
                <button
                  type="button"
                  class="btn-pick"
                  (click)="pickPreset(p)"
                >
                  填入表单 ↗
                </button>
              </div>
            </div>
          }
        </div>
      </section>

      <!-- 本次会话生效回显历史 -->
      @if (recentUpdates().length > 0) {
        <section class="echo-card" aria-labelledby="echo-title">
          <h4 id="echo-title" class="echo-title nur-serif">本次会话已生效参数回显记录</h4>
          <ul class="echo-list">
            @for (rec of recentUpdates(); track rec.key + rec.updatedAt.getTime()) {
              <li class="echo-item">
                <span class="echo-badge">已生效</span>
                <code class="echo-key">{{ rec.key }}</code>
                <span class="echo-val">→ <strong>{{ rec.value }}</strong></span>
                <span class="echo-time">{{ formatTime(rec.updatedAt) }}</span>
              </li>
            }
          </ul>
        </section>
      }
    </div>
  `,
  styles: [`
    .config-panel {
      display: flex;
      flex-direction: column;
      gap: var(--space-5);
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
    .current-card {
      padding: var(--space-5);
      background: var(--bg-card);
      border: 1px solid var(--line);
      border-radius: var(--radius-card);
      box-shadow: var(--shadow-subtle);
    }
    .card-inner {
      display: flex;
      align-items: flex-start;
      gap: var(--space-3);
    }
    .gold-indicator {
      width: 32px;
      height: 32px;
      display: grid;
      place-items: center;
      background: var(--color-gold-bg);
      color: var(--color-gold-text);
      border-radius: var(--radius-pill);
      font-size: 1rem;
      flex-shrink: 0;
    }
    .info-kicker {
      font-size: 0.78rem;
      color: var(--color-gold-text);
      text-transform: uppercase;
      letter-spacing: 0.08em;
      font-weight: 600;
    }
    .current-label {
      margin: var(--space-1) 0;
      font-size: 1.05rem;
      color: var(--text-title);
    }
    .current-note {
      margin: 0;
      font-size: 0.88rem;
      color: var(--text-muted);
      line-height: 1.6;
    }
    .highlight-val {
      font-family: var(--font-serif);
      font-size: 1.15rem;
      color: var(--color-primary);
    }
    .factual-note {
      margin: var(--space-4) 0 0;
      padding-top: var(--space-3);
      border-top: 1px dashed var(--line);
      font-size: 0.8rem;
      color: var(--text-placeholder);
      line-height: 1.5;
    }
    .form-card {
      padding: var(--space-5);
      background: var(--bg-card);
      border: 1px solid var(--line);
      border-radius: var(--radius-card);
      box-shadow: var(--shadow-subtle);
    }
    .form-title {
      margin: 0 0 var(--space-4);
      font-size: 1.15rem;
      color: var(--text-title);
    }
    .config-form {
      display: flex;
      flex-direction: column;
      gap: var(--space-4);
    }
    .form-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: var(--space-4);
    }
    .field-col {
      display: flex;
      flex-direction: column;
      gap: var(--space-1);
    }
    .field-col label {
      font-size: 0.85rem;
      font-weight: 500;
      color: var(--text-main);
    }
    .unit-hint {
      font-size: 0.8rem;
      color: var(--text-muted);
      font-weight: normal;
    }
    .required { color: var(--color-danger); }
    .preset-help {
      margin: 0;
      font-size: 0.82rem;
      color: var(--text-muted);
      background: var(--bg-page);
      padding: var(--space-2) var(--space-3);
      border-radius: 8px;
    }
    .form-actions {
      display: flex;
      justify-content: flex-end;
    }
    .btn-primary {
      min-height: 42px;
      padding: 0 var(--space-6);
      border: none;
      border-radius: var(--radius-pill);
      background: var(--color-primary);
      color: var(--bg-card);
      font-size: 0.92rem;
      font-weight: 500;
      box-shadow: 0 4px 14px rgba(30, 77, 62, 0.2);
      transition: all var(--duration-enter) var(--ease);
    }
    .btn-primary:hover:not(:disabled) {
      background: var(--color-primary-hover);
      transform: translateY(-1px);
    }
    button:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
    .status-banner {
      display: flex;
      align-items: center;
      gap: var(--space-2);
      padding: var(--space-3) var(--space-4);
      margin-bottom: var(--space-4);
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
    .presets-card {
      padding: var(--space-5);
      background: var(--bg-card);
      border: 1px solid var(--line);
      border-radius: var(--radius-card);
      box-shadow: var(--shadow-subtle);
    }
    .presets-title {
      margin: 0 0 var(--space-4);
      font-size: 1.15rem;
      color: var(--text-title);
    }
    .preset-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
      gap: var(--space-3);
    }
    .preset-item {
      padding: var(--space-3) var(--space-4);
      background: var(--bg-page);
      border: 1px solid var(--line);
      border-radius: var(--radius-control);
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
      transition: border-color var(--duration-enter) var(--ease);
    }
    .preset-item.preset-active {
      border-color: var(--color-primary);
      background: var(--color-primary-soft);
    }
    .preset-meta {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .preset-name {
      font-weight: 600;
      font-size: 0.9rem;
      color: var(--text-title);
    }
    .preset-key {
      font-family: monospace;
      font-size: 0.78rem;
      color: var(--text-muted);
    }
    .preset-desc {
      margin: 0;
      font-size: 0.82rem;
      color: var(--text-muted);
      line-height: 1.5;
    }
    .preset-footer {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: auto;
      padding-top: var(--space-2);
      border-top: 1px solid rgba(30, 58, 49, 0.08);
    }
    .preset-default {
      font-size: 0.78rem;
      color: var(--text-placeholder);
    }
    .btn-pick {
      min-height: 28px;
      padding: 0 var(--space-3);
      border: 1px solid var(--line);
      border-radius: var(--radius-pill);
      background: var(--bg-card);
      color: var(--color-primary);
      font-size: 0.78rem;
      font-weight: 500;
      transition: all var(--duration-enter) var(--ease);
    }
    .btn-pick:hover {
      background: var(--color-primary);
      color: var(--bg-card);
    }
    .echo-card {
      padding: var(--space-5);
      background: var(--bg-card);
      border: 1px solid var(--line);
      border-radius: var(--radius-card);
      box-shadow: var(--shadow-subtle);
    }
    .echo-title {
      margin: 0 0 var(--space-3);
      font-size: 1.05rem;
      color: var(--text-title);
    }
    .echo-list {
      margin: 0;
      padding: 0;
      list-style: none;
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
    }
    .echo-item {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--space-2);
      padding: var(--space-2) var(--space-3);
      background: var(--bg-page);
      border-radius: 8px;
      font-size: 0.85rem;
    }
    .echo-badge {
      display: inline-block;
      padding: 1px 8px;
      border-radius: var(--radius-pill);
      background: var(--color-success-bg);
      color: var(--color-success);
      font-size: 0.75rem;
      font-weight: 500;
    }
    .echo-key {
      font-family: monospace;
      color: var(--text-title);
      font-weight: 600;
    }
    .echo-val {
      color: var(--color-primary);
    }
    .echo-time {
      margin-left: auto;
      font-size: 0.78rem;
      color: var(--text-muted);
    }
    @media (max-width: 768px) {
      .preset-grid {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class AdminPlatformConfigComponent {
  private readonly http = inject(HttpClient);

  readonly currentFeeRate = input<string>('0.15');
  readonly configUpdated = output<{ key: string; value: string }>();

  readonly presets: PresetConfig[] = [
    {
      key: 'settlement.platform-fee-rate',
      name: '平台课酬抽成比例',
      unit: '数值 0~1（如 0.15 表示 15%）',
      desc: '平台对每笔已完成课时佣金的课酬抽成基准比例',
      defaultVal: '0.15',
    },
    {
      key: 'booking.trial-price-cents',
      name: '试听课默认定价',
      unit: '分（分币制，0 为完全免费）',
      desc: '首次试听课程的默认下单基准价格',
      defaultVal: '0',
    },
    {
      key: 'booking.cancel-deadline-hours',
      name: '开课前自主取消截止门槛',
      unit: '小时（如 2 表示开课前 2 小时）',
      desc: '学员/家长在正式开课前可无责自助取消的最小提前时长',
      defaultVal: '2',
    },
    {
      key: 'matching.max-result-size',
      name: '找老师单次推荐上限',
      unit: '条数（正整数）',
      desc: '智能撮合与推荐算法单批次向用户呈现的最多导师卡片数',
      defaultVal: '20',
    },
    {
      key: 'messaging.max-message-length',
      name: '站内消息正文长度上限',
      unit: '字符数',
      desc: '即时沟通与咨询单条聊天消息的最大字数限制',
      defaultVal: '2000',
    },
  ];

  readonly selectedKey = signal<string>('settlement.platform-fee-rate');
  readonly customKey = signal<string>('');
  readonly configValue = signal<string>('');
  readonly busy = signal<boolean>(false);
  readonly updateMessage = signal<string>('');
  readonly isError = signal<boolean>(false);
  readonly recentUpdates = signal<ConfigUpdateRecord[]>([]);

  effectiveKey(): string {
    return this.selectedKey() === '__CUSTOM__' ? this.customKey().trim() : this.selectedKey();
  }

  activePreset(): PresetConfig | undefined {
    return this.presets.find((p) => p.key === this.effectiveKey());
  }

  onKeySelect(val: string): void {
    this.selectedKey.set(val);
    const pr = this.presets.find((p) => p.key === val);
    if (pr) {
      this.configValue.set(pr.defaultVal);
    }
  }

  pickPreset(p: PresetConfig): void {
    this.selectedKey.set(p.key);
    this.configValue.set(p.defaultVal);
  }

  submitUpdate(): void {
    const key = this.effectiveKey();
    const value = this.configValue().trim();
    if (!key) {
      this.isError.set(true);
      this.updateMessage.set('请选择或输入要更新的参数键名。');
      return;
    }
    if (!value) {
      this.isError.set(true);
      this.updateMessage.set('请输入新的参数值。');
      return;
    }
    if (this.busy()) return;
    this.busy.set(true);

    this.http.put<{ key: string; value: string }>(
      API.admin.updateConfig(key),
      { value }
    ).subscribe({
      next: (res) => {
        this.busy.set(false);
        this.isError.set(false);
        this.updateMessage.set(`参数已成功更新并生效：${res.key} = ${res.value}`);
        this.recentUpdates.update((list) => [
          { key: res.key, value: res.value, updatedAt: new Date() },
          ...list.slice(0, 9),
        ]);
        this.configUpdated.emit({ key: res.key, value: res.value });
      },
      error: (e) => {
        this.busy.set(false);
        this.isError.set(true);
        this.updateMessage.set(e?.error?.detail ?? '参数更新失败，请确认键名是否存在或格式是否正确。');
      },
    });
  }

  formatTime(d: Date): string {
    const pad = (n: number) => (n < 10 ? '0' + n : n);
    return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
  }
}
