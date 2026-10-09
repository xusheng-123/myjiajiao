import { Component, ElementRef, inject, NgZone, OnDestroy, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { SiteFooterComponent } from '../../layout/site-footer/site-footer.component';
import { SiteHeaderComponent } from '../../layout/site-header/site-header.component';
import { TeacherCardComponent } from '../../shared/components/teacher-card/teacher-card.component';
import { GardenSceneComponent } from '../../shared/components/garden-scene/garden-scene.component';
import { RevealDirective } from '../../shared/directives/reveal.directive';
import {
  FEATURED_TEACHERS,
  PRINCIPLES,
  PROCESS_STEPS,
  SERVICES,
  SUBJECTS,
  VOICES,
} from './landing-data';

/**
 * 首页（规范二十六）：
 * Navbar → Hero → Brand Philosophy → Service Features → Teacher Showcase
 * → Subject Categories → Learning Process → Testimonials → Brand Vision → CTA → Footer。
 * 每屏一个主任务，克制留白，不堆卡片。
 */
@Component({
  selector: 'nur-landing',
  standalone: true,
  imports: [SiteHeaderComponent, SiteFooterComponent, TeacherCardComponent, GardenSceneComponent, RevealDirective, RouterLink],
  templateUrl: './landing.component.html',
  styleUrl: './landing.component.css',
})
export class LandingComponent implements OnInit, OnDestroy {

  private readonly zone = inject(NgZone);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private removeParallax?: () => void;

  ngOnInit(): void {
    this.setupBreathingParallax();
  }

  ngOnDestroy(): void {
    this.removeParallax?.();
  }

  /**
   * 雨后呼吸感·鼠标视差：仅桌面精确指针且未开启「减少动效」时启用。
   * 监听在 Angular 区域外 + requestAnimationFrame 节流，写入 CSS 变量，
   * 不触发任何变更检测；位移量 2–5px，仅作用于雾层/竹竿/竹叶三个环境层。
   */
  private setupBreathingParallax(): void {
    const fine = window.matchMedia('(hover: hover) and (pointer: fine)');
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)');
    if (!fine.matches || reduced.matches) {
      return;
    }
    this.zone.runOutsideAngular(() => {
      let frame = 0;
      let lastX = 0;
      let lastY = 0;
      const onMove = (event: PointerEvent) => {
        lastX = event.clientX;
        lastY = event.clientY;
        if (frame) {
          return;
        }
        frame = requestAnimationFrame(() => {
          frame = 0;
          const nx = (lastX / window.innerWidth) * 2 - 1;
          const ny = (lastY / window.innerHeight) * 2 - 1;
          this.host.nativeElement.style.setProperty('--px', nx.toFixed(3));
          this.host.nativeElement.style.setProperty('--py', ny.toFixed(3));
        });
      };
      window.addEventListener('pointermove', onMove, { passive: true });
      this.removeParallax = () => window.removeEventListener('pointermove', onMove);
    });
  }

  private readonly router = inject(Router);

  readonly teachers = FEATURED_TEACHERS;
  readonly principles = PRINCIPLES;
  readonly services = SERVICES;
  readonly subjects = SUBJECTS;
  readonly steps = PROCESS_STEPS;
  readonly voices = VOICES;

  /** 全站唯一主 CTA：开启专属因材点拨 → 统一鉴权门户。 */
  beginNurture(): void {
    void this.router.navigate(['/auth']);
  }
}
