import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { API_BASE } from '../../core/api/endpoints';
import { QueuePagerComponent } from '../../shared/components/queue-pager.component';
interface Case {caseId:string;ownerName:string;category:string;title:string;description:string;status:string;revision:number;createdAt:string;updatedAt:string;}
interface Reply {replyId:string;authorName:string;authorRole:string;body:string;kind:string;createdAt:string;}
interface Evidence {fileId:string;filename:string;sizeBytes:number;sha256:string;canDelete:boolean;}
interface Page<T>{content:T[];totalPages:number;totalElements:number;}
const BASE=API_BASE+'/support/cases';
@Component({selector:'nur-support-cases',standalone:true,imports:[FormsModule,DatePipe,RouterLink,QueuePagerComponent],
 template:`<main class="shell"><a [routerLink]="'/' + role().toLowerCase() + '/dashboard'">返回工作台</a>
 <h1 class="nur-serif">客服与申诉</h1><p>描述预约、支付或学习问题，与运营沟通并保留处理记录。</p>
 @if(notice()){<p class="notice" role="status">{{notice()}}</p>}
 @if(!admin()){
 <section class="nur-card"><h2>提交问题</h2><form (ngSubmit)="create()">
 <label>问题类别<select name="category" [(ngModel)]="category">@for(o of categories;track o.value){<option [value]="o.value">{{o.label}}</option>}</select></label>
 <label>标题<input id="support-title" name="title" [(ngModel)]="title" maxlength="120" required placeholder="简要说明遇到的问题" /></label>
 <label>问题描述<textarea id="support-description" name="description" [(ngModel)]="description" maxlength="4000" required rows="4" placeholder="可填写课程日期、订单号与期望的处理方式，请勿填写密码或支付密钥"></textarea></label>
 <button class="nur-btn nur-btn--primary nur-btn--sm" [disabled]="busy() || !title.trim() || !description.trim()">提交工单</button>
 </form></section>}
 <section class="nur-card"><h2>{{admin()?'运营处理队列':'我的工单'}}</h2>
 <nur-queue-pager label="工单" [page]="page()" [pages]="pages()" [total]="total()" [busy]="loading()" [filter]="filter" [options]="statuses" (filterChange)="filterChanged($event)" (turn)="turn($event)" (reload)="load()" />
 @if(!loading() && !cases().length){<p>当前筛选没有工单。</p>}
 @for(c of cases();track c.caseId){<button class="case" type="button" (click)="open(c.caseId)" [attr.aria-pressed]="selected()?.caseId===c.caseId"><strong>{{c.title}}</strong><span>{{label(c.status)}} · {{c.ownerName}} · {{c.updatedAt | date:'MM-dd HH:mm'}}</span></button>}
 </section>
 @if(selected();as c){<section class="nur-card" id="support-detail"><h2>{{c.title}}</h2><p>{{label(c.category)}} · {{label(c.status)}} · {{c.createdAt | date:'yyyy-MM-dd HH:mm'}}</p><p class="body">{{c.description}}</p>
 <h3>私有证据</h3><p class="hint">PDF、PNG、JPEG，每份不超过10MB，每个工单最多保留10份；个人客服证据保留容量100MB。仅本人及运营可读取，移除后保留文件与容量供审计。</p>
 @for(f of files();track f.fileId){<div class="file"><span>{{f.filename}} · {{(f.sizeBytes/1024).toFixed(1)}} KB</span><button type="button" (click)="download(f)" [disabled]="busy()">下载证据</button>@if(f.canDelete){<button type="button" (click)="remove(f)" [disabled]="busy()">移除</button>}</div>}
 @if(c.status!=='CLOSED'){<label>补充证据<input type="file" accept=".pdf,.png,.jpg,.jpeg" (change)="upload($event)" [disabled]="busy()" /></label>}
 <h3>沟通与处理记录</h3>
 <nur-queue-pager label="沟通记录" [page]="replyPage()" [pages]="replyPages()" [total]="replyTotal()" [busy]="historyLoading()" (turn)="turnHistory($event)" (reload)="history()" />
 <p class="hint">第1页显示最新记录，翻页可查看更早的沟通。</p>
 @for(r of replies();track r.replyId){<article><p>{{r.authorRole==='ADMIN'?'运营':r.authorName}} · {{r.createdAt | date:'MM-dd HH:mm'}}{{r.kind==='STATUS'?' · 状态调整':''}}</p><p class="body">{{r.body}}</p></article>}
 @if(c.status!=='CLOSED'){
 <form (ngSubmit)="reply()"><label>补充说明<textarea id="support-reply" name="reply" [(ngModel)]="replyBody" maxlength="4000" required rows="3"></textarea></label><button class="nur-btn nur-btn--primary nur-btn--sm" [disabled]="busy() || !replyBody.trim()">发送回复</button></form>
 <form (ngSubmit)="changeStatus()"><h3>{{admin()?'更新处理状态':'确认处理结果'}}</h3>
 <label>状态<select name="status" [(ngModel)]="targetStatus">@for(o of transitions(c);track o.value){<option [value]="o.value">{{o.label}}</option>}</select></label>
 <label>处理说明<textarea id="support-status-note" name="note" [(ngModel)]="statusNote" maxlength="2000" required rows="2"></textarea></label>
 <p class="hint">关闭后无法继续回复或补传证据。涉及退款时请使用退款申请及仲裁流程，工单状态不会自动变更资金或订单。</p>
 <button class="nur-btn nur-btn--ghost nur-btn--sm" [disabled]="busy() || !statusNote.trim()">确认状态</button></form>
 } @else {<p>工单已关闭，沟通与证据保留。后续问题请提交新工单。</p>}
 </section>}
 </main>`,
 styles:[`.shell{max-width:980px;margin:32px auto;padding:0 18px;color:#263d33}.nur-card{padding:24px;margin:20px 0}form{display:grid;gap:12px}label{display:grid;gap:6px}input,textarea,select{font:inherit;box-sizing:border-box;width:100%;padding:10px;border:1px solid #cad9cf;border-radius:9px;background:#fff}textarea{resize:vertical}.notice{background:#edf5ef;padding:12px;border-radius:10px}.case{display:flex;justify-content:space-between;flex-wrap:wrap;gap:8px;text-align:left;width:100%;margin:8px 0;padding:14px;border:1px solid #dce6df;border-radius:10px;background:#fff;font:inherit;color:inherit}.case span,.hint{font-size:.85rem;color:#567065}.case[aria-pressed=true]{border-color:#478866}.body{white-space:pre-wrap;overflow-wrap:anywhere}.file{display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin:10px 0}.file span{overflow-wrap:anywhere}article{border-left:3px solid #bfd6c5;padding:8px 14px;margin:12px 0}button{cursor:pointer}button:disabled{cursor:default;opacity:.5}h2{font-size:1.3rem}h3{font-size:1.05rem;margin-top:24px}@media(max-width:500px){.nur-card{padding:16px}}`]
})
export class SupportCasesComponent{
 private http=inject(HttpClient);private auth=inject(AuthService);
 readonly role=()=>this.auth.role()??'STUDENT';readonly admin=()=>this.role()==='ADMIN';
 readonly cases=signal<Case[]>([]);readonly page=signal(0);readonly pages=signal(0);readonly total=signal(0);readonly loading=signal(false);
 readonly selected=signal<Case|null>(null);readonly files=signal<Evidence[]>([]);readonly replies=signal<Reply[]>([]);
 readonly replyPage=signal(0);readonly replyPages=signal(0);readonly replyTotal=signal(0);readonly historyLoading=signal(false);
 readonly busy=signal(false);readonly notice=signal('');private listVersion=0;private detailVersion=0;private historyVersion=0;
 readonly categories=[{value:'SCHEDULE',label:'预约与课程'},{value:'PAYMENT',label:'订单与支付'},{value:'LEARNING',label:'学习服务'},{value:'ACCOUNT',label:'账户问题'},{value:'OTHER',label:'其他'}];
 readonly statuses=[{value:'OPEN',label:'待处理'},{value:'IN_PROGRESS',label:'处理中'},{value:'WAITING_USER',label:'待用户补充'},{value:'RESOLVED',label:'已解决'},{value:'CLOSED',label:'已关闭'}];
 category='SCHEDULE';title='';description='';filter='';replyBody='';statusNote='';targetStatus='CLOSED';
 constructor(){this.load();}
 label(value:string):string{return [...this.statuses,...this.categories].find(x=>x.value===value)?.label??value;}
 transitions(c:Case){return this.admin()?this.statuses.filter(x=>x.value!==c.status && x.value!=='OPEN'):this.statuses.filter(x=>x.value==='CLOSED' || c.status==='RESOLVED' && x.value==='OPEN');}
 private failure(e:any):void{this.busy.set(false);this.notice.set(e.error?.detail??'操作未完成，请重试。');}
 load():void{const v=++this.listVersion;this.loading.set(true);let params=new HttpParams().set('page',this.page()).set('size',20);if(this.filter)params=params.set('status',this.filter);
 this.http.get<Page<Case>>(BASE,{params}).subscribe({next:d=>{if(v!==this.listVersion)return;if(this.page()>0 && !d.content.length){this.page.set(Math.max(0,d.totalPages-1));this.load();return;}this.cases.set(d.content);this.pages.set(d.totalPages);this.total.set(d.totalElements);this.loading.set(false);},error:e=>{if(v!==this.listVersion)return;this.loading.set(false);this.failure(e);}});}
 turn(delta:number):void{if(this.loading() || this.page()+delta<0 || this.page()+delta>=this.pages())return;this.page.update(x=>x+delta);this.load();}
 filterChanged(value:string):void{this.filter=value;this.page.set(0);this.load();}
 open(id:string):void{const v=++this.detailVersion;++this.historyVersion;this.selected.set(null);this.files.set([]);this.replies.set([]);this.replyPage.set(0);
 this.http.get<Case>(BASE+'/'+id).subscribe({next:c=>{if(v!==this.detailVersion)return;this.selected.set(c);this.targetStatus=this.transitions(c)[0]?.value??'CLOSED';this.history();this.loadFiles();},error:e=>{if(v===this.detailVersion)this.failure(e);}});}
 history():void{const c=this.selected();if(!c)return;const v=++this.historyVersion;this.historyLoading.set(true);
 this.http.get<Page<Reply>>(BASE+'/'+c.caseId+'/replies',{params:{page:this.replyPage(),size:20}}).subscribe({next:d=>{if(v!==this.historyVersion || this.selected()?.caseId!==c.caseId)return;this.replies.set([...d.content].reverse());this.replyPages.set(d.totalPages);this.replyTotal.set(d.totalElements);this.historyLoading.set(false);},error:e=>{if(v!==this.historyVersion)return;this.historyLoading.set(false);this.failure(e);}});}
 turnHistory(delta:number):void{if(this.historyLoading() || this.replyPage()+delta<0 || this.replyPage()+delta>=this.replyPages())return;this.replyPage.update(x=>x+delta);this.history();}
 private loadFiles():void{const c=this.selected();if(!c)return;this.http.get<Evidence[]>(BASE+'/'+c.caseId+'/evidence').subscribe({next:f=>{if(this.selected()?.caseId===c.caseId)this.files.set(f);},error:e=>this.failure(e)});}
 private refreshed(c:Case,message:string):void{this.busy.set(false);this.notice.set(message);if(this.selected()?.caseId===c.caseId){this.selected.set(c);this.replyPage.set(0);this.history();this.loadFiles();}this.load();}
 create():void{if(this.busy() || !this.title.trim() || !this.description.trim())return;this.busy.set(true);this.http.post<Case>(BASE,{category:this.category,title:this.title,description:this.description}).subscribe({next:c=>{this.busy.set(false);this.title='';this.description='';this.notice.set('工单已提交，可补充证据并等待运营回复。');this.page.set(0);this.filter='';this.load();this.open(c.caseId);},error:e=>this.failure(e)});}
 reply():void{const c=this.selected();if(!c || this.busy() || !this.replyBody.trim())return;this.busy.set(true);this.http.post<Case>(BASE+'/'+c.caseId+'/replies',{body:this.replyBody}).subscribe({next:x=>{this.replyBody='';this.refreshed(x,'回复已保存。');},error:e=>this.failure(e)});}
 changeStatus():void{const c=this.selected();if(!c || this.busy() || !this.statusNote.trim())return;this.busy.set(true);this.http.post<Case>(BASE+'/'+c.caseId+'/status',{status:this.targetStatus,revision:c.revision,note:this.statusNote}).subscribe({next:x=>{this.statusNote='';this.refreshed(x,'状态已更新并留存处理记录。');},error:e=>{this.failure(e);if(e.status===409)this.open(c.caseId);}});}
 upload(event:Event):void{const el=event.target as HTMLInputElement,f=el.files?.[0],c=this.selected();if(!f || !c || this.busy())return;if(f.size>10485760){this.notice.set('单份证据不能超过10MB。');el.value='';return;}const data=new FormData();data.append('file',f);this.busy.set(true);
 this.http.post(BASE+'/'+c.caseId+'/evidence',data).subscribe({next:()=>{this.busy.set(false);el.value='';this.notice.set('私有证据已保存。');this.open(c.caseId);this.load();},error:e=>{el.value='';this.failure(e);}});}
 download(f:Evidence):void{if(this.busy())return;this.busy.set(true);this.http.get(BASE+'/evidence/'+f.fileId+'/download',{responseType:'blob'}).subscribe({next:blob=>{this.busy.set(false);const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=f.filename;a.click();setTimeout(()=>URL.revokeObjectURL(url),10000);},error:e=>this.failure(e)});}
 remove(f:Evidence):void{const c=this.selected();if(!c || this.busy())return;this.busy.set(true);this.http.delete(BASE+'/evidence/'+f.fileId).subscribe({next:()=>{this.busy.set(false);this.notice.set('证据已从沟通页面移除，审计保留容量未清理。');this.open(c.caseId);this.load();},error:e=>this.failure(e)});}
}
