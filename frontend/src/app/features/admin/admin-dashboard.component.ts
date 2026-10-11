import { Component, OnInit, inject, signal, computed, ViewChild, ElementRef } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../core/auth/auth.service';
import { API } from '../../core/api/endpoints';
import { NoticeCenterComponent } from '../../shared/components/notice-center.component';
import { AdminVerificationsComponent } from './admin-verifications.component';
import { AdminRefundsComponent } from './admin-refunds.component';
import { AdminAccountsComponent } from './admin-accounts.component';
import { AdminAuditLogsComponent } from './admin-audit-logs.component';
import { AdminOrdersComponent } from './admin-orders.component';
import { AdminPlatformConfigComponent } from './admin-platform-config.component';

export type AdminTabId = 'verifications' | 'refunds' | 'accounts' | 'audit' | 'orders' | 'configs';

export interface AdminTabDef {
  id: AdminTabId;
  label: string;
  badge?: () => number | string | null;
}

/**
 * 运营治理控制台（方向 D · 黛绿暖金）
 * 顶部全景指标大盘 + 五大治理队列与平台配置 Tab 切换。
 */
@Component({
  selector: 'nur-admin-dashboard',
  standalone: true,
  imports: [
    RouterLink,
    NoticeCenterComponent,
    AdminVerificationsComponent,
    AdminRefundsComponent,
    AdminAccountsComponent,
    AdminAuditLogsComponent,
    AdminOrdersComponent,
    AdminPlatformConfigComponent,
  ],
  templateUrl: './admin-dashboard.component.html',
  styleUrl: './admin-dashboard.component.css',
})
export class AdminDashboardComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  @ViewChild('auditLogsComp') auditLogsComp?: AdminAuditLogsComponent;

  readonly displayName = this.auth.displayName;
  readonly overview = signal<Record<string, number | string> | null>(null);
  readonly activeTab = signal<AdminTabId>('verifications');
  readonly globalNotice = signal('');

  readonly feeRateString = computed(() => {
    const o = this.overview();
    return o ? String(o['platformFeeRate'] ?? '0.15') : '0.15';
  });

  readonly pendingVerificationsCount = computed(() => {
    const o = this.overview();
    if (!o) return 0;
    const v = Number(o['pendingVerifications']);
    return isNaN(v) ? 0 : v;
  });

  readonly tabs: AdminTabDef[] = [
    {
      id: 'verifications',
      label: '资质审核',
      badge: () => (this.pendingVerificationsCount() > 0 ? this.pendingVerificationsCount() : null),
    },
    { id: 'refunds', label: '退款仲裁' },
    { id: 'accounts', label: '账户治理' },
    { id: 'orders', label: '线下收款' },
    { id: 'audit', label: '审计日志' },
    { id: 'configs', label: '平台配置' },
  ];

  ngOnInit(): void {
    this.loadOverview();
  }

  loadOverview(): void {
    this.http.get<Record<string, number | string>>(API.admin.overview).subscribe({
      next: (d) => this.overview.set(d),
      error: () => this.globalNotice.set('平台全景概览数据拉取未成功，请稍后刷新重试。'),
    });
  }

  setTab(tabId: AdminTabId): void {
    this.activeTab.set(tabId);
  }

  onTabKeydown(event: KeyboardEvent, currentIndex: number): void {
    let nextIndex = -1;
    if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
      event.preventDefault();
      nextIndex = (currentIndex + 1) % this.tabs.length;
    } else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
      event.preventDefault();
      nextIndex = (currentIndex - 1 + this.tabs.length) % this.tabs.length;
    } else if (event.key === 'Home') {
      event.preventDefault();
      nextIndex = 0;
    } else if (event.key === 'End') {
      event.preventDefault();
      nextIndex = this.tabs.length - 1;
    }

    if (nextIndex >= 0) {
      const nextTab = this.tabs[nextIndex];
      this.setTab(nextTab.id);
      const tabBtn = document.getElementById(`tab-${nextTab.id}`);
      tabBtn?.focus();
    }
  }

  onConfigUpdated(ev: { key: string; value: string }): void {
    if (ev.key === 'settlement.platform-fee-rate') {
      this.overview.update((prev) => (prev ? { ...prev, platformFeeRate: ev.value } : prev));
    }
    this.loadOverview();
  }

  onAuditChanged(): void {
    if (this.activeTab() === 'audit' && this.auditLogsComp) {
      this.auditLogsComp.loadQueue();
    }
  }
}
