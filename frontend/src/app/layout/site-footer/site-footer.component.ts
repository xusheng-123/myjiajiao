import { Component } from '@angular/core';

/** 全站页脚 — 深墨绿收尾，只放真实可用的锚点与联络信息。 */
@Component({
  selector: 'nur-site-footer',
  standalone: true,
  templateUrl: './site-footer.component.html',
  styleUrl: './site-footer.component.css',
})
export class SiteFooterComponent {
  readonly year = new Date().getFullYear();

  readonly anchors = [
    { label: '精选导师', fragment: 'teachers' },
    { label: '服务体系', fragment: 'services' },
    { label: '学习流程', fragment: 'process' },
    { label: '办学理念', fragment: 'philosophy' },
    { label: '学员心声', fragment: 'voices' },
  ] as const;
}
