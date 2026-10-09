import { Component, OnInit, inject, signal } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';
import { HttpClient } from '@angular/common/http';
import { API } from '../../core/api/endpoints';
import { NoticeCenterComponent } from '../../shared/components/notice-center.component';

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

/**
 * 家长控制台 — 子女学情看板、家庭统一支付钱包与消费对账、进度透明追踪。
 */
@Component({
  selector: 'nur-parent-dashboard',
  standalone: true,
  imports: [NoticeCenterComponent],
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

  ngOnInit(): void {
    this.load();
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
