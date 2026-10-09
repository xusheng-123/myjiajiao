import { Directive, ElementRef, OnDestroy, OnInit, inject } from '@angular/core';

/**
 * 滚动显现：元素进入视口后单次显现（nur-reveal → is-visible）。
 * prefers-reduced-motion 环境下直接可见，不做位移动画。
 * 阶梯错落由模板上的 [style.--stagger-index] 控制延迟。
 */
@Directive({
  selector: '[nurReveal]',
  standalone: true,
})
export class RevealDirective implements OnInit, OnDestroy {
  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef);
  private observer?: IntersectionObserver;

  ngOnInit(): void {
    const node = this.el.nativeElement;
    node.classList.add('nur-reveal');

    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduced || !('IntersectionObserver' in window)) {
      node.classList.add('is-visible');
      return;
    }

    this.observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            node.classList.add('is-visible');
            this.observer?.disconnect();
          }
        }
      },
      { threshold: 0.12, rootMargin: '0px 0px -40px 0px' },
    );
    this.observer.observe(node);
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }
}
