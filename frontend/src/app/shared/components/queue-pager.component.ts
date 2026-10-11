import { Component, input, output } from '@angular/core';

@Component({
  selector: 'nur-queue-pager', standalone: true,
  template: `<nav class="pager" [attr.aria-label]="label() + '分页'">
    @if (options().length) {
      <label>{{ label() }}状态 <select [value]="filter()" (change)="filterChange.emit($any($event.target).value)">
        <option value="">全部状态</option>
        @for (o of options(); track o.value) { <option [value]="o.value">{{ o.label }}</option> }
      </select></label>
    }
    <span aria-live="polite">共 {{ total() }} 条 · 第 {{ pages() ? page() + 1 : 0 }}/{{ pages() }} 页{{ busy() ? ' · 读取中…' : '' }}</span>
    <button type="button" [disabled]="busy() || page() === 0" (click)="turn.emit(-1)">上一页</button>
    <button type="button" [disabled]="busy() || page()+1 >= pages()" (click)="turn.emit(1)">下一页</button>
    <button type="button" [disabled]="busy()" (click)="reload.emit()">刷新</button>
  </nav>`,
  styles: [`.pager{display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin:14px 0;font-size:.85rem;color:var(--nur-ink,#33443b)}button,select{font:inherit;padding:6px 10px;border:1px solid #cbd8cf;border-radius:8px;background:#fff;color:inherit}button:disabled{opacity:.45}label{display:flex;gap:6px;align-items:center}`],
})
export class QueuePagerComponent {
  readonly label=input('列表'); readonly page=input(0); readonly pages=input(0); readonly total=input(0);
  readonly busy=input(false); readonly filter=input(''); readonly options=input<{value:string;label:string}[]>([]);
  readonly turn=output<number>(); readonly filterChange=output<string>(); readonly reload=output<void>();
}
