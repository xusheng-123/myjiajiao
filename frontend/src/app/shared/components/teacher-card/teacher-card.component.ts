import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TeacherSummary } from '../../../shared/models/teacher.model';

/**
 * 教师卡片 — 规范十七：姓名/学校/学历/学科/经验/价格/可预约时间/认证状态/教学风格/评价。
 * 纯展示组件，首页精选导师与后续教师列表页共用。
 */
@Component({
  selector: 'nur-teacher-card',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink],
  templateUrl: './teacher-card.component.html',
  styleUrl: './teacher-card.component.css',
})
export class TeacherCardComponent {
  readonly teacher = input.required<TeacherSummary>();

  readonly initial = computed(() => this.teacher().displayName.slice(0, 1));

  readonly styleTags = computed(() => this.teacher().styleTags.slice(0, 3));

  readonly priceYuan = computed(() => this.teacher().pricePerHourCents / 100);
}
