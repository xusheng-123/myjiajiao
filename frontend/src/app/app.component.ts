import { Component, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';

/**
 * 应用根组件 — 路由出口 + 页面切换淡入过渡。
 * 公开页（首页/登录门户/教师列表）共用同一竹林暗色基调，淡入使页面衔接更顺滑；
 * 每次路由成功切换交替使用两组同形 keyframe 以重放动画，prefers-reduced-motion 下自动关闭。
 */
@Component({
  selector: 'nur-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `
    <a class="skip-link" href="#main-content">跳转到主要内容</a>
    <main id="main-content">
      <div class="route-fade" [style.animation-name]="fadeName()">
        <router-outlet />
      </div>
    </main>
  `,
  styles: [
    `
      .route-fade {
        min-height: 100vh;
      }

      @keyframes routeInA {
        from {
          opacity: 0;
          transform: translateY(10px);
        }
        to {
          opacity: 1;
          transform: none;
        }
      }

      @keyframes routeInB {
        from {
          opacity: 0;
          transform: translateY(10px);
        }
        to {
          opacity: 1;
          transform: none;
        }
      }

      @media (prefers-reduced-motion: reduce) {
        .route-fade {
          animation: none !important;
        }
      }
    `,
  ],
})
export class AppComponent {
  private readonly router = inject(Router);
  private readonly navigationCount = signal(0);

  /** 每次导航交替返回动画名，驱动 CSS 动画重放。 */
  readonly fadeName = computed(() => (this.navigationCount() % 2 === 0 ? 'routeInA' : 'routeInB'));

  constructor() {
    this.router.events
      .pipe(
        filter((e) => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        this.navigationCount.update((n) => n + 1);
      });
  }
}
