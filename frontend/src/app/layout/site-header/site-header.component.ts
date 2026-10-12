import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ROLE_HOME, ROLE_LABELS } from '../../core/auth/auth.models';

interface NavItem {
  label: string;
  /** 站内路由（如 /teachers），优先于锚点 */
  link?: string;
  /** 首页区块锚点 */
  fragment?: string;
}

/** 站点导航：找老师走独立列表页，其余为首页区块锚点 */
const NAV_ITEMS: NavItem[] = [
  { label: '找老师', link: '/teachers' },
  { label: '服务体系', fragment: 'services' },
  { label: '学习流程', fragment: 'process' },
  { label: '办学理念', fragment: 'philosophy' },
  { label: '学员心声', fragment: 'voices' },
];

/**
 * 营销站吸顶导航。桌面端横排锚点 + 登录入口；
 * 移动端汉堡菜单（aria-expanded / Escape 关闭 / 点击链接后收起）。
 */
@Component({
  selector: 'nur-site-header',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './site-header.component.html',
  styleUrl: './site-header.component.css',
  host: {
    '(window:scroll)': 'onWindowScroll()',
    '(document:keydown.escape)': 'closeMenu()',
  },
})
export class SiteHeaderComponent {
  readonly auth = inject(AuthService);
  readonly navItems = NAV_ITEMS;
  readonly scrolled = signal(false);
  readonly menuOpen = signal(false);
  readonly demoBannerVisible = signal(true);

  readonly roleHome = computed(() => {
    const r = this.auth.role();
    return r ? ROLE_HOME[r] : '/auth';
  });

  readonly roleLabel = computed(() => {
    const r = this.auth.role();
    return r ? ROLE_LABELS[r] : '';
  });

  onWindowScroll(): void {
    this.scrolled.set(window.scrollY > 40); /* 方向 D 规格：40px 阈值 */
  }

  toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  closeMenu(): void {
    this.menuOpen.set(false);
  }

  closeBanner(): void {
    this.demoBannerVisible.set(false);
  }

  logout(): void {
    this.auth.logout();
  }
}
