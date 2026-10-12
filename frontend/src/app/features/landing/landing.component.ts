import { Component, OnInit, inject, signal } from '@angular/core';
import { TeachersApi } from '../../core/api/teachers-api';
import { TeacherSummary } from '../../shared/models/teacher.model';
import { Router, RouterLink } from '@angular/router';
import { SiteFooterComponent } from '../../layout/site-footer/site-footer.component';
import { SiteHeaderComponent } from '../../layout/site-header/site-header.component';
import { TeacherCardComponent } from '../../shared/components/teacher-card/teacher-card.component';
import { RevealDirective } from '../../shared/directives/reveal.directive';
import { RippleDirective } from '../../shared/directives/ripple.directive';
import { PRINCIPLES, PROCESS_STEPS, SERVICES, SUBJECTS, VOICES } from './landing-data';

/**
 * 首页（方向 D · 黛绿暖金，2026-10-11 重构）：
 * 摄影 Hero（月洞门雨后庭院，雾散入场）→ 办学理念 → 服务体系 → 精选导师
 * → 学科门类 → 学习流程 → 学员心声 → 品牌愿景 → 漏窗 CTA → Footer。
 * 区块底色严格 --bg-page / --bg-mist 交替；每屏一个主任务，克制留白。
 */
@Component({
  selector: 'nur-landing',
  standalone: true,
  imports: [
    SiteHeaderComponent,
    SiteFooterComponent,
    TeacherCardComponent,
    RevealDirective,
    RippleDirective,
    RouterLink,
  ],
  templateUrl: './landing.component.html',
  styleUrls: ['./landing.component.css', './landing-sections.css', './landing-sections-2.css'],
})
export class LandingComponent implements OnInit {
  private readonly router = inject(Router);
  private readonly teachersApi = inject(TeachersApi);
  readonly teachers = signal<TeacherSummary[]>([]);
  readonly teachersError = signal(false);
  readonly principles = PRINCIPLES;
  readonly services = SERVICES;
  readonly subjects = SUBJECTS;
  readonly steps = PROCESS_STEPS;
  readonly voices = VOICES;

  ngOnInit(): void {
    this.teachersApi.search({}).subscribe({
      next: rows => this.teachers.set([...new Map(rows.map(t => [t.teacherId, t])).values()].slice(0, 3)),
      error: () => this.teachersError.set(true),
    });
  }

  /** 全站唯一主 CTA：开启专属因材点拨 → 统一鉴权门户。 */
  beginNurture(): void {
    void this.router.navigate(['/auth']);
  }
}
