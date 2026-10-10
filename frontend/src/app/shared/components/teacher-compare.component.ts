import { Component, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
export interface CompareTeacher {
  teacherId: string; displayName: string; school: string | null; highestDegree: string | null;
  yearsOfTeaching: number; subject: string; gradeBand: string | null; pricePerHourCents: number;
  verificationApproved: boolean; averageRating: number | null; reviewCount: number;
  nextOpenSlotAt: string | null; styleTags: string[];
}
@Component({ selector: 'nur-teacher-compare', standalone: true, imports: [DatePipe],
  template: `@if (teachers().length) { <section class="nur-card compare" aria-label="教师对比">
    <h2 class="nur-serif">教师对比（最多 3 位）</h2><p>资料与价格以当前检索结果为参考，预约与下单以服务端最新信息为准。</p>
    <div class="scroll" tabindex="0" role="region" aria-label="教师对比表，可横向滚动"><table><caption>当前选中的 {{ teachers().length }} 位教师</caption>
      <thead><tr><th scope="col">对比项</th>@for (t of teachers(); track t.teacherId) { <th scope="col">{{ t.displayName }} <button type="button" (click)="remove.emit(t.teacherId)" [attr.aria-label]="'移除对比 ' + t.displayName">移除</button></th> }</tr></thead>
      <tbody>
        <tr><th scope="row">课程与价格</th>@for (t of teachers(); track t.teacherId) { <td>{{ t.subject }} · {{ t.gradeBand || '学段不限' }}<br>¥{{ (t.pricePerHourCents/100).toFixed(2) }}/小时</td> }</tr>
        <tr><th scope="row">院校 / 学历</th>@for (t of teachers(); track t.teacherId) { <td>{{ t.school || '未填写' }} / {{ t.highestDegree || '未填写' }}</td> }</tr>
        <tr><th scope="row">教龄（教师填报）</th>@for (t of teachers(); track t.teacherId) { <td>{{ t.yearsOfTeaching }} 年</td> }</tr>
        <tr><th scope="row">认证</th>@for (t of teachers(); track t.teacherId) { <td>{{ t.verificationApproved ? '已认证' : '未认证' }}</td> }</tr>
        <tr><th scope="row">教学风格</th>@for (t of teachers(); track t.teacherId) { <td>{{ t.styleTags.join(' · ') || '未填写' }}</td> }</tr>
        <tr><th scope="row">评价证据</th>@for (t of teachers(); track t.teacherId) { <td>{{ t.averageRating === null ? '暂无评价' : t.averageRating + ' 分 · ' + t.reviewCount + ' 条评价' }}</td> }</tr>
        <tr><th scope="row">最近可约</th>@for (t of teachers(); track t.teacherId) { <td>{{ t.nextOpenSlotAt ? (t.nextOpenSlotAt | date:'M月d日 HH:mm') : '暂无可约时段' }}</td> }</tr>
      </tbody></table></div></section> }`,
  styles: [`.compare{padding:20px;margin:20px 0}.scroll{max-width:100%;overflow-x:auto}table{border-collapse:collapse;min-width:560px;width:100%}caption{text-align:left;padding:8px}th,td{padding:12px;border-bottom:1px solid var(--border-subtle,#ddd);text-align:left;vertical-align:top}th{font-weight:600}button{padding:6px;margin:6px;border:1px solid var(--border-subtle,#ddd);background:var(--bg-card,#fff);color:inherit;border-radius:6px}p{color:var(--text-secondary)}`],
})
export class TeacherCompareComponent {
  readonly teachers = input.required<CompareTeacher[]>();
  readonly remove = output<string>();
}
