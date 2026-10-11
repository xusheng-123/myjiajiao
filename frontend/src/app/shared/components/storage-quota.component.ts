import { Component, effect, inject, input, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
interface Quota {storedBytes:number;limitBytes:number;maxFileBytes:number;remainingBytes:number;removedRetainedBytes:number;nearLimit:boolean;retentionNotice:string;}
@Component({selector:'nur-storage-quota',standalone:true,template:`<aside class="quota" aria-label="个人文件保留容量">
 @if(value();as q){<p>个人保留容量：{{size(q.storedBytes)}} / {{size(q.limitBytes)}} · 剩余 {{size(q.remainingBytes)}}</p><p>已移除但保留：{{size(q.removedRetainedBytes)}} · 单份上限 {{size(q.maxFileBytes)}}</p><progress [value]="q.storedBytes" [max]="q.limitBytes" aria-label="已用容量"></progress>
 @if(q.nearLimit){<p role="status">{{q.remainingBytes===0?'容量已满':'容量已使用80%以上'}}，请先核查文件大小或联系运营。移除文件不会释放保留容量。</p>}<p>{{q.retentionNotice}}</p>}
 @if(error()){<p role="alert">{{error()}}</p>}<button type="button" (click)="load()" [disabled]="loading()">{{loading()?'读取容量…':'刷新容量'}}</button></aside>`,
 styles:[`.quota{font-size:.85rem;color:#567065;overflow-wrap:anywhere;margin:14px 0}p{margin:6px 0}progress{width:100%;max-width:320px;accent-color:#478866}button{font:inherit;border:1px solid #cad9cf;border-radius:8px;padding:6px 10px;background:#fff;color:#263d33}`]})
export class StorageQuotaComponent {
 readonly endpoint=input.required<string>();readonly revision=input(0);readonly value=signal<Quota|null>(null);readonly error=signal('');readonly loading=signal(false);
 private http=inject(HttpClient);private version=0;
 constructor(){effect(()=>{this.endpoint();this.revision();this.load();});}
 size(bytes:number):string{return bytes<1048576?(bytes/1024).toFixed(1)+' KB':(bytes/1048576).toFixed(1)+' MB';}
 load():void{const version=++this.version;this.loading.set(true);this.error.set('');this.http.get<Quota>(this.endpoint()).subscribe({next:q=>{if(version!==this.version)return;this.value.set(q);this.loading.set(false);},error:()=>{if(version!==this.version)return;this.value.set(null);this.loading.set(false);this.error.set('容量未读取，请刷新后再判断可用容量。');}});}
}
