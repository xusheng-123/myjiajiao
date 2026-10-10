import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { API, API_BASE } from '../../core/api/endpoints';
import { AuthService } from '../../core/auth/auth.service';
import { ROLE_HOME } from '../../core/auth/auth.models';
interface Order { orderId: string; orderNo: string; amountCents: number; status: string; }
interface Case { caseId: string; orderId: string; status: string; reason: string; computedAmountCents: number; decisionNote: string | null; }
@Component({ selector: 'nur-refunds', standalone: true, imports: [RouterLink], templateUrl: './refunds.component.html', styleUrl: './refunds.component.css' })
export class RefundsComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  readonly home = computed(() => ROLE_HOME[this.auth.role()!]);
  readonly orders = signal<Order[]>([]);
  readonly cases = signal<Case[]>([]);
  readonly selected = signal<Order | null>(null);
  readonly reason = signal('');
  readonly busy = signal(false);
  readonly loaded = signal(false);
  readonly notice = signal('');
  readonly orderPage = signal(0);
  readonly casePage = signal(0);
  readonly orderPages = signal(0);
  readonly casePages = signal(0);
  ngOnInit(): void { this.refresh(); }
  refresh(): void {
    this.http.get<{ content: Order[]; totalPages: number }>(`${API_BASE}/order/me/refundable-orders`, { params: { page: this.orderPage(), size: 20 } }).subscribe({
      next: p => { this.orders.set(p.content); this.orderPages.set(p.totalPages); this.loaded.set(true); },
      error: e => { this.loaded.set(true); this.notice.set(e.error?.detail ?? '订单加载失败，请重试。'); },
    });
    this.http.get<{ content: Case[]; totalPages: number }>(`${API_BASE}/refund/me/cases`, { params: { page: this.casePage(), size: 20 } }).subscribe({
      next: p => { this.cases.set(p.content); this.casePages.set(p.totalPages); },
      error: e => this.notice.set(e.error?.detail ?? '退款进度加载失败，请重试。'),
    });
  }
  select(o: Order): void { this.selected.set(o); this.reason.set(''); this.notice.set(''); }
  submit(): void {
    const order = this.selected(), reason = this.reason().trim();
    if (!order || !reason || reason.length > 512 || this.busy()) return;
    this.busy.set(true);
    this.http.post<Case>(`${API_BASE}/refund/cases`, { orderId: order.orderId, reason }).subscribe({
      next: () => { this.busy.set(false); this.selected.set(null); this.casePage.set(0); this.notice.set('申请已提交，运营审核后会更新处理结果。'); this.refresh(); },
      error: e => { this.busy.set(false); this.notice.set(e.error?.detail ?? '申请提交失败，填写内容已保留。'); },
    });
  }
  move(which: 'orders' | 'cases', delta: number): void {
    if (which === 'orders') this.orderPage.update(n => n + delta); else this.casePage.update(n => n + delta);
    this.refresh();
  }
  status(s: string): string { return ({ REQUESTED: '待审核', EVALUATED: '已评估，待处理', APPROVED: '已批准', EXECUTED: '已退回付款人钱包', REJECTED: '已驳回', PAID: '已支付', CLOSED: '已关闭', CANCELLED: '已取消', PENDING_PAYMENT: '待支付' } as Record<string, string>)[s] ?? s; }
  yuan(cents: number): string { return (cents / 100).toFixed(2); }
}
