import { Component, Input, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
@Component({selector:'nur-calendar-export',standalone:true,template:`<button type="button" (click)="download()" [disabled]="busy()" style="min-height:44px;padding:8px 16px;border:1px solid var(--border-subtle);border-radius:16px;background:var(--bg-card);color:var(--text-main)">导出未来30天日历</button>@if(notice()){<p role="status">{{notice()}}</p>}`})
export class CalendarExportComponent {
  @Input() childProfileId='';private http=inject(HttpClient);readonly busy=signal(false);readonly notice=signal('');
  download():void{if(this.busy())return;this.busy.set(true);const params:Record<string,string>={};if(this.childProfileId)params['childProfileId']=this.childProfileId;
    this.http.get('/api/v1/booking/calendar.ics',{params,responseType:'blob'}).subscribe({next:blob=>{const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download='nurture-calendar.ics';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);this.busy.set(false);this.notice.set('日历文件已导出，可导入你使用的日历应用。');},error:()=>{this.busy.set(false);this.notice.set('导出失败，请检查当前绑定与权限后重试。');}});}
}
