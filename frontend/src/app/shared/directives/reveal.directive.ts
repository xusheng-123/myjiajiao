import { Directive, ElementRef, OnDestroy, OnInit, inject } from '@angular/core';

/**
 * 滚动显现：元素进入视口后单次显现（nur-reveal → is-visible）。
 * prefers-reduced-motion 环境下直接可见，不做位移动画。
 * 阶梯错落由模板上的 [style.--stagger-index] 控制延迟。
 *
 * 兜底链：IO 回调在 Chromium 系按帧派发，无头/嵌入式 WebView 帧停摆时
 * 回调永不到达，内容会被 opacity:0 永久困住；因此观察之外再用
 * 计时器 + 滚动/resize 事件做几何位置判定，保证任何环境最终可见。
 */
@Directive({
  selector: '[nurReveal]',
  standalone: true,
})
export class RevealDirective implements OnInit, OnDestroy {
  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef);
  private observer?: IntersectionObserver;
  private fallbackTimer?: number;
  private visible = false;
  private readonly onViewportChange = () => this.checkGeometry();

  ngOnInit(): void {
    const node = this.el.nativeElement;
    node.classList.add('nur-reveal');

    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduced || !('IntersectionObserver' in window)) {
      this.show();
      return;
    }

    this.observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            this.show();
            return;
          }
        }
      },
      { threshold: 0.12, rootMargin: '0px 0px -40px 0px' },
    );
    this.observer.observe(node);

    this.fallbackTimer = window.setTimeout(() => this.checkGeometry(), 350);
    window.addEventListener('scroll', this.onViewportChange, { passive: true });
    window.addEventListener('resize', this.onViewportChange, { passive: true });
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
    if (this.fallbackTimer !== undefined) {
      window.clearTimeout(this.fallbackTimer);
    }
    window.removeEventListener('scroll', this.onViewportChange);
    window.removeEventListener('resize', this.onViewportChange);
  }

  /** 与 IO 判定等价的纯几何检查：任一像素进入（视口 - 40px 底边距）即显现。 */
  private checkGeometry(): void {
    if (this.visible) {
      return;
    }
    const rect = this.el.nativeElement.getBoundingClientRect();
    const viewportH = window.innerHeight || document.documentElement.clientHeight;
    if (rect.height > 0 && rect.top < viewportH - 40 && rect.bottom > 0) {
      this.show();
    }
  }

  private show(): void {
    if (this.visible) {
      return;
    }
    this.visible = true;
    this.el.nativeElement.classList.add('is-visible');
    this.observer?.disconnect();
    if (this.fallbackTimer !== undefined) {
      window.clearTimeout(this.fallbackTimer);
      this.fallbackTimer = undefined;
    }
    window.removeEventListener('scroll', this.onViewportChange);
    window.removeEventListener('resize', this.onViewportChange);
  }
}
