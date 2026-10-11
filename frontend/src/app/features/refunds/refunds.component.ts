import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { API_BASE } from '../../core/api/endpoints';
import { AuthService } from '../../core/auth/auth.service';
import { ROLE_HOME } from '../../core/auth/auth.models';

interface Order {
  orderId: string;
  orderNo: string;
  amountCents: number;
  status: string;
}

interface Case {
  caseId: string;
  orderId: string;
  status: string;
  reason: string;
  computedAmountCents: number;
  decisionNote: string | null;
}

export interface TimelineNode {
  title: string;
  desc: string;
  status: 'done' | 'active' | 'success' | 'rejected' | 'pending';
}

const PRESET_REASONS = [
  '时间冲突，无法按计划上课',
  '学生学习计划调整',
  '与导师沟通协商一致退款',
  '不可抗力或个人原因',
];

/**
 * 退款与处理进度页 — 方向 D（黛绿暖金）
 * P1-3 重构：
 * - 申请退款改为两步模态弹层（理由录入 → 确认提交）
 * - 我的退款采用进度时间轴（Timeline）呈现状态流转与运营批注
 * - 完备的 loading/empty/error 反馈环
 */
@Component({
  selector: 'nur-refunds',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './refunds.component.html',
  styleUrl: './refunds.component.css',
})
export class RefundsComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  readonly home = computed(() => ROLE_HOME[this.auth.role()!] ?? '/');

  readonly orders = signal<Order[]>([]);
  readonly cases = signal<Case[]>([]);
  readonly selected = signal<Order | null>(null);
  readonly reason = signal('');
  readonly modalStep = signal<1 | 2>(1);
  readonly busy = signal(false);
  readonly loaded = signal(false);
  readonly notice = signal('');

  readonly orderPage = signal(0);
  readonly casePage = signal(0);
  readonly orderPages = signal(0);
  readonly casePages = signal(0);

  readonly presetReasons = PRESET_REASONS;

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.http
      .get<{ content: Order[]; totalPages: number }>(`${API_BASE}/order/me/refundable-orders`, {
        params: { page: this.orderPage(), size: 20 },
      })
      .subscribe({
        next: (p) => {
          this.orders.set(p.content ?? []);
          this.orderPages.set(p.totalPages ?? 0);
          this.loaded.set(true);
        },
        error: (e) => {
          this.loaded.set(true);
          this.notice.set(e.error?.detail ?? '订单加载失败，请重试。');
        },
      });

    this.http
      .get<{ content: Case[]; totalPages: number }>(`${API_BASE}/refund/me/cases`, {
        params: { page: this.casePage(), size: 20 },
      })
      .subscribe({
        next: (p) => {
          this.cases.set(p.content ?? []);
          this.casePages.set(p.totalPages ?? 0);
        },
        error: (e) => {
          this.notice.set(e.error?.detail ?? '退款进度加载失败，请重试。');
        },
      });
  }

  // ---------- 退款弹层操作 ----------
  openApplyModal(o: Order): void {
    this.selected.set(o);
    this.reason.set('');
    this.modalStep.set(1);
    this.notice.set('');
  }

  closeApplyModal(): void {
    this.selected.set(null);
    this.reason.set('');
    this.modalStep.set(1);
  }

  setPresetReason(text: string): void {
    this.reason.set(text);
  }

  toConfirmStep(): void {
    if (!this.reason().trim()) return;
    this.modalStep.set(2);
  }

  backToReasonStep(): void {
    this.modalStep.set(1);
  }

  submit(): void {
    const order = this.selected();
    const reason = this.reason().trim();
    if (!order || !reason || reason.length > 512 || this.busy()) return;

    this.busy.set(true);
    this.http.post<Case>(`${API_BASE}/refund/cases`, { orderId: order.orderId, reason }).subscribe({
      next: () => {
        this.busy.set(false);
        this.closeApplyModal();
        this.casePage.set(0);
        this.notice.set('退款申请已提交，等待运营审核；款项审核通过后将退回平台钱包。');
        this.refresh();
      },
      error: (e) => {
        this.busy.set(false);
        this.notice.set(e.error?.detail ?? '申请提交失败，填写内容已保留。');
      },
    });
  }

  move(which: 'orders' | 'cases', delta: number): void {
    if (which === 'orders') {
      this.orderPage.update((n) => n + delta);
    } else {
      this.casePage.update((n) => n + delta);
    }
    this.refresh();
  }

  // ---------- 时间轴节点计算 ----------
  getTimelineNodes(c: Case): TimelineNode[] {
    const isUnderReview = c.status === 'REQUESTED' || c.status === 'EVALUATED';
    const isApprovedOrExecuted = c.status === 'APPROVED' || c.status === 'EXECUTED';
    const isRejected = c.status === 'REJECTED';

    const node1: TimelineNode = {
      title: '提交退款申请',
      desc: '用户已提交退款理由并进入审核队列',
      status: 'done',
    };

    const node2: TimelineNode = {
      title: '平台运营审核',
      desc: isUnderReview
        ? '运营人员正在核对履约情况与退款理由'
        : '审核流转已完成',
      status: isUnderReview ? 'active' : 'done',
    };

    let node3: TimelineNode;
    if (isApprovedOrExecuted) {
      node3 = {
        title: '退款成功',
        desc: c.status === 'EXECUTED' ? '款项已全额退回付款人平台钱包' : '已审核批准，等待入账',
        status: 'success',
      };
    } else if (isRejected) {
      node3 = {
        title: '申请已驳回',
        desc: '运营审核未通过，请查看下方处理批注说明',
        status: 'rejected',
      };
    } else {
      node3 = {
        title: '退款结算',
        desc: '等待运营给出最终处理结论',
        status: 'pending',
      };
    }

    return [node1, node2, node3];
  }

  statusText(s: string): string {
    return (
      ({
        REQUESTED: '待审核',
        EVALUATED: '已评估，待处理',
        APPROVED: '已批准',
        EXECUTED: '已退款至钱包',
        REJECTED: '已驳回',
        PAID: '已支付',
        CLOSED: '已关闭',
        CANCELLED: '已取消',
        PENDING_PAYMENT: '待支付',
      } as Record<string, string>)[s] ?? s
    );
  }

  yuan(cents: number): string {
    return (cents / 100).toFixed(2);
  }
}
