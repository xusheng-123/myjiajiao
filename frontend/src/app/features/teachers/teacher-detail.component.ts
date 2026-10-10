import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { API, API_BASE } from '../../core/api/endpoints';
import { AuthService } from '../../core/auth/auth.service';
import { Role } from '../../core/auth/auth.models';
interface Detail { teacherId: string; displayName: string; school: string | null; highestDegree: string | null; yearsOfTeaching: number; intro: string | null; styleTags: string[]; averageRating: number | null; reviewCount: number; offerings: { subject: string; gradeBand: string; pricePerHourCents: number }[]; openSlots: { slotId: string; startAt: string; endAt: string }[]; }
@Component({ selector: 'nur-teacher-detail', standalone: true, imports: [RouterLink, DatePipe],
  template: `<main class="detail"><a routerLink="/teachers">← 返回师资列表</a>
    @if (notice()) { <p role="status">{{ notice() }}</p> }
    @if (detail(); as t) {
      <section class="nur-card"><h1 class="nur-serif">{{ t.displayName }} <small>已认证</small></h1>
        <p>{{ t.school || '院校未填写' }} · {{ t.highestDegree || '学历未填写' }} · {{ t.yearsOfTeaching }} 年教学经验（教师填报）</p>
        <p>{{ t.intro || '老师正在准备个人介绍。' }}</p><p>{{ t.styleTags.join(' · ') || '暂无教学风格标签' }}</p>
        <p>{{ t.averageRating === null ? '暂无学员评价' : '学员评分 ' + t.averageRating + ' · ' + t.reviewCount + ' 条评价' }}</p>
        @if (auth.isLoggedIn() && auth.role() !== Role.TEACHER) { <button type="button" (click)="consult()" [disabled]="busy()">{{ busy() ? '正在开启…' : '课前咨询' }}</button> }
        @else if (!auth.isLoggedIn()) { <a routerLink="/auth">登录后咨询与预约</a> }
      </section>
      <section class="nur-card"><h2 class="nur-serif">在售课程</h2><ul>@for (o of t.offerings; track o.subject + o.gradeBand) { <li>{{ o.subject }} · {{ o.gradeBand }} · ¥{{ yuan(o.pricePerHourCents) }}/小时
        @if (auth.role() === Role.STUDENT) { <a routerLink="/student/match" [queryParams]="{subject: o.subject, teacher: t.teacherId}">选择时间并预约</a> }
      </li> }</ul></section>
      <section class="nur-card"><h2 class="nur-serif">可约时间</h2>@if (!t.openSlots.length) { <p>近期暂无可约时段，可先咨询老师。</p> }<ul>@for (s of t.openSlots; track s.slotId) { <li>{{ s.startAt | date:'M月d日 HH:mm' }} — {{ s.endAt | date:'HH:mm' }}</li> }</ul><p>可约时间以提交预约时的最新结果为准。</p></section>
    } @else if (!notice()) { <p role="status">正在读取导师资料…</p> }
  </main>`,
  styles: [`.detail{max-width:960px;margin:32px auto;padding:20px;color:var(--text-main)}.nur-card{padding:24px;margin-top:20px}p{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.7}small{font-size:1rem}button{padding:12px 18px;border:1px solid var(--border-subtle,#ddd);background:var(--bg-card,#fff);color:inherit;border-radius:10px}li{margin:15px 0}li a{margin-left:16px}`],
})
export class TeacherDetailComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly auth = inject(AuthService);
  readonly Role = Role;
  readonly detail = signal<Detail | null>(null);
  readonly notice = signal('');
  readonly busy = signal(false);
  ngOnInit(): void {
    this.http.get<Detail>(`${API_BASE}/matching/public/teachers/${this.route.snapshot.paramMap.get('teacherId')}`).subscribe({
      next: t => this.detail.set(t), error: e => this.notice.set(e.error?.detail ?? '导师资料读取失败，请返回列表重试。'),
    });
  }
  consult(): void {
    const t = this.detail(); if (!t || this.busy()) return;
    this.busy.set(true);
    this.http.post<{ conversationId: string }>(API.messaging.conversations, { teacherId: t.teacherId, subject: t.offerings[0]?.subject ?? '课程咨询' }).subscribe({
      next: c => { this.busy.set(false); void this.router.navigate(['/messages'], { queryParams: { conversation: c.conversationId } }); },
      error: e => { this.busy.set(false); this.notice.set(e.error?.detail ?? '咨询开启失败，请重试。'); },
    });
  }
  yuan(cents: number): string { return (cents/100).toFixed(2); }
}
