import { Component, input } from '@angular/core';

/**
 * 「雨后竹林·月洞门」纯装饰场景层 — 登录门户与营销首页共用同一画稿，保证视觉同源。
 * 各元素按需组合：冰裂花窗纹 / 月洞门拱环 / 春夜喜雨雨丝 / 写实楠竹框景 / 青石阶水面光线。
 * 默认铺满最近的定位父级（absolute inset:0）；宿主加 class="scene--fixed" 则固定于视口
 * （长页面滚动时楠竹如穿行竹林）。尊重 prefers-reduced-motion：动效由使用方页面统一关闭，
 * 本组件动画仅渐变/位移，无闪烁。
 */
@Component({
  selector: 'nur-garden-scene',
  standalone: true,
  templateUrl: './garden-scene.component.html',
  styleUrl: './garden-scene.component.css',
})
export class GardenSceneComponent {
  /** 冰裂花窗暗纹 */
  readonly lattice = input(false);
  /** 月洞门空间拱环（置于段落中心背后） */
  readonly moonGate = input(false);
  /** 斜织雨丝 */
  readonly rain = input(false);
  /** 底部青石阶与水面光线 */
  readonly plinth = input(false);
  /** 两侧写实楠竹框景 */
  readonly bamboo = input(true);
  /** 渲染分层模式：full=竹竿竹叶一体（默认）；stems=仅竹竿竹枝（叶组隐藏）；
   *  leaves=仅竹叶（独立前景 SVG，overflow 可见、叶尖完整，供使用方置于最高层）。 */
  readonly mode = input<'full' | 'stems' | 'leaves'>('full');
  /** 雨后水面微涟漪：极淡圆环偶尔扩散（默认关闭，仅首屏水岸场景启用）。 */
  readonly ripples = input(false);
}
