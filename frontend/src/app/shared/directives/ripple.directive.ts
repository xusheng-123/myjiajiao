import { Directive, ElementRef, HostListener, inject } from '@angular/core';

/**
 * 方向 D · 苔青涟漪：主操作按钮点击时的方位反馈（380ms，仅 transform/opacity）。
 * 用法：`<button class="nur-btn nur-btn--primary" nurRipple>…</button>`
 * prefers-reduced-motion 时静默跳过；生成的 span 动画结束即移除，不累积 DOM。
 */
@Directive({ selector: '[nurRipple]', standalone: true })
export class RippleDirective {
  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly reduceMotion =
    typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  @HostListener('click', ['$event'])
  onClick(event: MouseEvent): void {
    if (this.reduceMotion) {
      return;
    }
    const host = this.el.nativeElement;
    host.classList.add('nur-ripple-host');
    const rect = host.getBoundingClientRect();
    const d = Math.max(rect.width, rect.height);
    const span = document.createElement('span');
    span.className = 'nur-ripple';
    span.style.width = span.style.height = `${d}px`;
    span.style.left = `${event.clientX - rect.left - d / 2}px`;
    span.style.top = `${event.clientY - rect.top - d / 2}px`;
    span.addEventListener('animationend', () => span.remove(), { once: true });
    host.appendChild(span);
  }
}
