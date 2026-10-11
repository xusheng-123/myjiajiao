import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { HttpClient } from '@angular/common/http';
import { API } from '../../core/api/endpoints';
import { NoticeCenterComponent } from '../../shared/components/notice-center.component';
import { CalendarExportComponent } from '../../shared/components/calendar-export.component';

interface Binding {
  bindingId: string;
  studentId: string;
  studentNo: string | null;
  status: string;
  relation: string | null;
}

interface WalletOverview {
  balanceCents: number;
  currency: string;
  recentTransactions: {
    txnId: string;
    type: string;
    amountCents: number;
    balanceAfterCents: number;
    memo: string | null;
    createdAt: string;
  }[];
}

interface OrderableLesson {
  bookingId: string;
  subject: string;
  startAt: string | null;
  priceCents: number;
}

interface FamilyOrder {
  orderId: string;
  orderNo: string;
  bookingId: string;
  status: string;
  amountCents: number;
  paidAt: string | null;
}

/**
 * 家长控制台 — 子女学情看板、家庭统一支付钱包与消费对账、进度透明追踪。
 */
@Component({
  selector: 'nur-parent-dashboard',
  standalone: true,
  imports: [NoticeCenterComponent, DatePipe, RouterLink, CalendarExportComponent],
  templateUrl: './parent-dashboard.component.html',
  styleUrl: './parent-dashboard.component.css',
})
export class ParentDashboardComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  readonly displayName = this.auth.displayName;
  readonly children = signal<Binding[]>([]);
  readonly reportFor = signal<string | null>(null);          // 正在查看周报的孩子 studentId
  readonly reportData = signal<any | null>(null);
  readonly reportLoading = signal(false);
  readonly reportError = signal('');
  readonly wallet = signal<WalletOverview | null>(null);
  readonly loaded = signal(false);
  readonly notice = signal('');
  readonly studentNoInput = signal('');
  readonly relationInput = signal('');

  // ---------- 课程与订单（G7）：孩子 → 可下单课程 → 下单/支付，金额一律以服务端为准 ----------
  readonly activeChildren = computed(() => this.children().filter((c) => c.status === 'ACTIVE'));
  readonly selectedChild = signal<string>('');
  readonly orderable = signal<OrderableLesson[]>([]);
  readonly orderableLoading = signal(false);
  readonly orders = signal<FamilyOrder[]>([]);
  readonly ordersLoaded = signal(false);
  readonly orderBusy = signal(false);
  readonly orderMsg = signal<{ type: 'info' | 'success' | 'error'; text: string } | null>(null);
  /** 沙箱开关以服务端 /payment/config 为准：仅环境明确允许时展示沙箱演示支付。 */
  readonly sandboxEnabled = signal<boolean | null>(null);
  private readonly payingOrderId = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
    this.http.get<{ sandboxEnabled: boolean }>(API.payment.config).subscribe({
      next: (cfg) => this.sandboxEnabled.set(cfg?.sandboxEnabled === true),
      error: () => this.sandboxEnabled.set(false),
    });
    this.loadOrders();
  }

  load(): void {
    this.http.get<Binding[]>(API.parent.children).subscribe({
      next: (data) => {
        this.children.set(data);
        this.loaded.set(true);
      },
      error: () => this.loaded.set(true),
    });
    this.http.get<WalletOverview>(API.parent.wallet).subscribe({
      next: (data) => this.wallet.set(data),
    });
  }

  bindChild(): void {
    if (!this.studentNoInput().trim()) {
      this.notice.set('请先填写子女的学生编号，编号可在学生的个人档案页查看。');
      return;
    }
    this.http
      .post<Binding>(API.parent.children, {
        studentNo: this.studentNoInput().trim(),
        relation: this.relationInput() || '家长',
      })
      .subscribe({
        next: () => {
          this.notice.set('绑定申请已发出，待学生确认后生效。');
          this.studentNoInput.set('');
          this.load();
        },
        error: () => this.notice.set('绑定未成功，请核对编号后原地重试。'),
      });
  }

  topUp(amountCents: number): void {
    this.http.post<WalletOverview>(`${API.parent.wallet}/topup`, {
      amountCents,
      memo: '家庭钱包充值',
    }).subscribe({
      next: (data) => {
        this.wallet.set(data);
        this.notice.set('充值已到账。');
      },
    });
  }

  // ── 课程与订单（G7） ──

  loadOrders(): void {
    this.http
      .get<{ content: FamilyOrder[] }>(`${API.order.mine}?page=0&size=20`)
      .subscribe({
        next: (page) => {
          this.orders.set(page?.content ?? []);
          this.ordersLoaded.set(true);
        },
        error: () => this.ordersLoaded.set(true),
      });
  }

  /** 查看所选孩子的可下单课程（服务端校验监护关系与定价）。 */
  loadOrderable(): void {
    const studentId = this.selectedChild();
    if (!studentId) {
      this.orderMsg.set({ type: 'info', text: '请先选择孩子。' });
      return;
    }
    this.orderableLoading.set(true);
    this.orderMsg.set(null);
    this.http.get<OrderableLesson[]>(API.order.orderable(studentId)).subscribe({
      next: (list) => {
        this.orderable.set(list ?? []);
        this.orderableLoading.set(false);
      },
      error: (err) => {
        this.orderableLoading.set(false);
        this.orderable.set([]);
        const detail = err?.error?.detail ?? err?.error?.message ?? '可下单课程加载失败，请稍后重试。';
        this.orderMsg.set({ type: 'error', text: detail });
      },
    });
  }

  /** 为孩子下单：金额以服务端返回的定价快照为准。 */
  createOrder(bookingId: string): void {
    if (this.orderBusy()) {
      return;
    }
    this.orderBusy.set(true);
    this.orderMsg.set(null);
    this.http.post<FamilyOrder>(API.order.create, { bookingId }).subscribe({
      next: (order) => {
        this.orderBusy.set(false);
        this.orderMsg.set({
          type: 'success',
          text: `订单 ${order.orderNo} 已创建：¥${this.yuan(order.amountCents)}（待支付）。`,
        });
        this.loadOrderable();
        this.loadOrders();
      },
      error: (err) => {
        this.orderBusy.set(false);
        const detail = err?.error?.detail ?? err?.error?.message ?? '下单未成功，请稍后重试。';
        this.orderMsg.set({ type: 'error', text: detail });
        this.loadOrderable();
      },
    });
  }

  /** 支付待支付订单：仅沙箱环境开放演示支付；生产引导线下收款（模式 B）。 */
  payOrder(order: FamilyOrder): void {
    if (this.payingOrderId() || this.sandboxEnabled() !== true) {
      return;
    }
    this.payingOrderId.set(order.orderId);
    this.orderMsg.set(null);
    this.http
      .post<{ attemptId: string }>(API.payment.initiate, { orderId: order.orderId, channel: 'MOCK' })
      .subscribe({
        next: () => {
          // 沙箱网关模拟渠道回调：与真实回调同一条验签/防重入/联动链路
          this.http
            .post<{ result: string }>(API.payment.sandboxNotify, {
              orderId: order.orderId,
              channel: 'MOCK',
              success: true,
            })
            .subscribe({
              next: (res) => {
                this.payingOrderId.set(null);
                if (res?.result === 'SUCCESS') {
                  this.orderMsg.set({
                    type: 'success',
                    text: `沙箱演示支付完成：订单 ${order.orderNo} 已标记为已付（模拟渠道，非真实收款）。`,
                  });
                } else {
                  this.orderMsg.set({ type: 'info', text: '支付回调已受理，订单状态以服务端为准。' });
                }
                this.loadOrders();
                this.http.get<WalletOverview>(API.parent.wallet).subscribe({
                  next: (w) => this.wallet.set(w),
                });
              },
              error: (err) => {
                this.payingOrderId.set(null);
                const detail = err?.error?.detail ?? err?.error?.message
                  ?? '支付未完成：请确认家庭钱包余额充足后重试，或联系运营线下确认。';
                this.orderMsg.set({ type: 'error', text: detail });
                this.loadOrders();
              },
            });
        },
        error: (err) => {
          this.payingOrderId.set(null);
          const detail = err?.error?.detail ?? err?.error?.message ?? '支付发起失败，请稍后重试。';
          this.orderMsg.set({ type: 'error', text: detail });
        },
      });
  }

  isPaying(orderId: string): boolean {
    return this.payingOrderId() === orderId;
  }

  yuan(cents: number | undefined): string {
    return ((cents ?? 0) / 100).toFixed(2);
  }

  // ── 学习周报（模式：点开才拉取，切换孩子即换 ID 重新拉取）──
  toggleReport(child: Binding): void {
    if (child.status !== 'ACTIVE') {
      this.reportError.set('绑定生效后才能查看学习周报。');
      return;
    }
    if (this.reportFor() === child.studentId) {
      this.reportFor.set(null);
      this.reportData.set(null);
      return;
    }
    this.reportFor.set(child.studentId);
    this.reportData.set(null);
    this.reportError.set('');
    this.reportLoading.set(true);
    this.http.get<any>(API.learning.weeklyReport(child.studentId)).subscribe({
      next: (d) => { this.reportData.set(d); this.reportLoading.set(false); },
      error: (e) => {
        this.reportLoading.set(false);
        this.reportError.set(e?.error?.detail ?? '周报加载失败，请稍后重试。');
      },
    });
  }

  minutesText(mins: any): string {
    const n = Number(mins ?? 0);
    return n >= 60 ? (n / 60).toFixed(1) + ' 小时' : n + ' 分钟';
  }
}
