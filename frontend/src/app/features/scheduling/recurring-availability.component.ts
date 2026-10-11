import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { CalendarExportComponent } from '../../shared/components/calendar-export.component';
interface Conflict {slotId:string;status:string;startAt:string;endAt:string;}
interface Occurrence {date:string;startAt:string|null;endAt:string|null;skipped:boolean;conflicts:Conflict[];}
interface Preview {occurrences:Occurrence[];publishCount:number;ready:boolean;}
interface Slot {slotId:string;startAt:string;endAt:string;status:string;}
interface Created {seriesId:string;timeZone:string;slots:Slot[];}
interface Series {seriesId:string;timeZone:string;startDate:string;startTime:string;weekCount:number;}
@Component({selector:'nur-recurring-availability',standalone:true,imports:[FormsModule,RouterLink,CalendarExportComponent],templateUrl:'./recurring-availability.component.html',styleUrl:'./recurring-availability.component.css'})
export class RecurringAvailabilityComponent implements OnInit {
  private http=inject(HttpClient);
  readonly date=signal(this.defaultDate());readonly time=signal('15:00');readonly zone=signal('Asia/Shanghai');readonly duration=signal(60);readonly weeks=signal(4);
  readonly skipped=signal<string[]>([]);readonly exceptions=signal<Record<string,string>>({});readonly preview=signal<Preview|null>(null);readonly valid=signal(false);readonly created=signal<Created|null>(null);readonly busy=signal(false);readonly notice=signal('');
  readonly series=signal<Series[]>([]);readonly page=signal(0);readonly pages=signal(0);
  ngOnInit():void{this.loadSeries();}
  loadSeries():void{this.http.get<{content:Series[];totalPages:number}>('/api/v1/scheduling/series',{params:{page:String(this.page()),size:'20'}}).subscribe({next:r=>{this.series.set(r.content);this.pages.set(r.totalPages);},error:()=>this.notice.set('已发布规则暂时无法加载。')});}
  turn(delta:number):void{this.page.update(p=>p+delta);this.loadSeries();}
  open(s:Series):void{this.busy.set(true);this.http.get<Created>('/api/v1/scheduling/series/'+s.seriesId).subscribe({next:r=>{this.created.set(r);this.busy.set(false);},error:e=>{this.busy.set(false);this.notice.set(e.error?.detail??'规则加载失败。');}});}
  private defaultDate():string{const d=new Date();d.setDate(d.getDate()+7);return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');}
  reset():void{this.preview.set(null);this.valid.set(false);this.skipped.set([]);this.exceptions.set({});this.notice.set('规则已调整，请重新预览。');}
  skip(date:string,skip:boolean):void{this.skipped.update(r=>skip?[...r.filter(d=>d!==date),date]:r.filter(d=>d!==date));if(skip)this.exceptions.update(r=>{const copy={...r};delete copy[date];return copy;});this.valid.set(false);this.notice.set('例外已调整，请重新预览后再发布。');}
  exception(date:string,time:string):void{this.exceptions.update(r=>{const copy={...r};if(time)copy[date]=time;else delete copy[date];return copy;});this.valid.set(false);this.notice.set('例外已调整，请重新预览后再发布。');}
  rule():unknown{return {startDate:this.date(),startTime:this.time(),timeZone:this.zone().trim(),durationMinutes:this.duration(),weekCount:this.weeks(),skipDates:this.skipped(),exceptions:Object.entries(this.exceptions()).map(([date,startTime])=>({date,startTime}))};}
  inspect():void{if(this.busy())return;this.busy.set(true);this.notice.set('');this.valid.set(false);this.http.post<Preview>('/api/v1/scheduling/series/preview',this.rule()).subscribe({next:r=>{this.preview.set(r);this.valid.set(true);this.busy.set(false);this.notice.set(r.ready?'预览通过，可确认发布。':'存在冲突，请跳过或调整例外后重新预览。');},error:e=>{this.busy.set(false);this.notice.set(e.error?.detail??'预览失败，请检查规则。');}});}
  publish():void{if(this.busy()||!this.valid()||!this.preview()?.ready)return;this.busy.set(true);this.http.post<Created>('/api/v1/scheduling/series',this.rule()).subscribe({next:r=>{this.created.set(r);this.busy.set(false);this.valid.set(false);this.preview.set(null);this.notice.set('重复档期已发布，学生可逐次预约。');this.page.set(0);this.loadSeries();},error:e=>{this.busy.set(false);this.valid.set(false);this.notice.set(e.error?.detail??'发布失败，请重新预览。');}});}
  block(slot:Slot):void{if(!window.confirm('下架这个空闲档期？已被预约的档期须通过课程取消流程处理。'))return;const series=this.created();if(!series)return;this.busy.set(true);this.http.delete('/api/v1/scheduling/availability-slots/'+slot.slotId).subscribe({next:()=>this.http.get<Created>('/api/v1/scheduling/series/'+series.seriesId).subscribe({next:r=>{this.created.set(r);this.busy.set(false);},error:()=>{this.busy.set(false);this.notice.set('已下架，请刷新工作台确认。');}}),error:e=>{this.busy.set(false);this.notice.set(e.error?.detail??'下架失败。');}});}
  display(at:string|null,zone=this.zone()):string{if(!at)return '';try{return new Intl.DateTimeFormat('zh-CN',{timeZone:zone,month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false,timeZoneName:'short'}).format(new Date(at));}catch{return at;}}
  status(s:string):string{return ({OPEN:'可预约',HELD:'待确认占用',BOOKED:'已预约',BLOCKED:'已下架'} as Record<string,string>)[s]??s;}
}
