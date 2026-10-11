import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../core/auth/auth.service';
import { QueuePagerComponent } from '../../shared/components/queue-pager.component';
interface Question {prompt:string;choices:string[];correctIndex:number|null;}
interface Attempt {answers:number[];correctCount:number;totalCount:number;teacherComment:string|null;commentRevision:number;submittedAt:string;}
interface Assessment {assessmentId:string;bookingId:string;studentId:string;subject:string;title:string;scope:string;status:string;revision:number;questions:Question[];attempt:Attempt|null;}
interface Page<T>{content:T[];totalPages:number;totalElements:number;}
interface Score{assessmentId:string;title:string;subject:string;correctCount:number;totalCount:number;submittedAt:string;teacherComment:string|null;}
interface Report{tasksTotal:number;tasksCompleted:number;lessonsCompleted:number;scores:Page<Score>;classroomNotes:Record<string,string>[];notice:string;}
const BASE='/api/v1/learning/assessments';
@Component({selector:'nur-assessments',standalone:true,imports:[FormsModule,DatePipe,RouterLink,QueuePagerComponent],
 template:`<main class="shell"><a [routerLink]="'/' + auth.role()?.toLowerCase() + '/dashboard'">返回工作台</a><h1 class="nur-serif">阶段小测与学习报告</h1>
 <p>题目由授课教师录入，客观题按实际作答评分。阶段性小结，非全面能力诊断。</p>@if(notice()){<p class="notice" role="status">{{notice()}}</p>}
 @if(auth.role()==='TEACHER'){<label>学生<select [ngModel]="student" (ngModelChange)="selectStudent($event)">@for(s of students();track s.studentId){<option [value]="s.studentId">{{s.displayName}}</option>}</select></label>}
 @if(auth.role()==='PARENT'){<label>孩子<select [ngModel]="child" (ngModelChange)="selectChild($event)">@for(c of children();track c.studentId){<option [value]="c.studentId">孩子 {{c.studentNo ?? '已绑定'}}</option>}</select></label>}
 @if(auth.role()==='TEACHER' && student){<section class="nur-card"><h2>{{editing?'编辑小测草稿':'录入阶段小测'}}</h2><form (ngSubmit)="saveDraft()">
 <label>关联课程<select name="lesson" [(ngModel)]="bookingId" [disabled]="!!editing">@for(l of lessons();track l.bookingId){<option [value]="l.bookingId">{{l.subject}} · {{l.startAt | date:'MM-dd HH:mm'}}</option>}</select></label>
 <label>科目<input id="assessment-subject" name="subject" [(ngModel)]="subject" maxlength="60" required [disabled]="!!editing" /></label>
 <label>标题<input id="assessment-title" name="title" [(ngModel)]="title" maxlength="120" required /></label>
 <label>范围说明<textarea id="assessment-scope" name="scope" [(ngModel)]="scope" maxlength="1000" required rows="2"></textarea></label>
 @for(q of questions;track $index;let n=$index){<fieldset><legend>第{{n+1}}题</legend><label>题干<textarea [id]="'assessment-question-'+n" [name]="'prompt-'+n" [(ngModel)]="q.prompt" maxlength="1000" required rows="2"></textarea></label>
 @for(option of q.choices;track $index;let j=$index){<label>选项{{j+1}}<input [id]="'assessment-choice-'+n+'-'+j" [name]="'choice-'+n+'-'+j" [(ngModel)]="q.choices[j]" maxlength="300" required /></label>}
 <label>正确答案<select [name]="'correct-'+n" [(ngModel)]="q.correctIndex">@for(option of q.choices;track $index;let j=$index){<option [ngValue]="j">选项{{j+1}}</option>}</select></label><button type="button" (click)="removeQuestion(n)" [disabled]="questions.length===1">移除此题</button></fieldset>}
 <div class="actions"><button type="button" (click)="addQuestion()" [disabled]="questions.length>=20">增加题目（最多20题）</button><button class="nur-btn nur-btn--primary nur-btn--sm" [disabled]="busy() || !bookingId">保存草稿</button>@if(editing){<button type="button" (click)="clearDraft()">取消编辑</button>}</div></form></section>}
 <section class="nur-card"><h2>小测列表</h2><nur-queue-pager label="小测" [page]="page()" [pages]="pages()" [total]="total()" [busy]="loading()" (turn)="turn($event)" (reload)="load()" />
 @if(!loading() && !quizzes().length){<p>暂无可查看的小测。草稿仅创建教师可见。</p>}
 @for(q of quizzes();track q.assessmentId){<article><h3>{{q.title}} · {{status(q.status)}}</h3><p>{{q.subject}} · {{q.scope}}</p>
 @if(auth.role()==='TEACHER' && q.status==='DRAFT'){<div class="actions"><button type="button" (click)="edit(q)">编辑草稿</button><button type="button" (click)="action(q,'publish')" [disabled]="busy()">发布小测</button></div><p class="hint">发布后题目与答案固定，不可修改；学生仅可提交一次。</p>}
 @if(auth.role()==='TEACHER' && q.status==='PUBLISHED'){<button type="button" (click)="action(q,'archive')" [disabled]="busy()">归档小测</button>}
 @for(item of q.questions;track $index;let n=$index){<fieldset><legend>第{{n+1}}题 · {{item.prompt}}</legend>
 @for(option of item.choices;track $index;let j=$index){<label class="answer">@if(auth.role()==='STUDENT' && q.status==='PUBLISHED' && !q.attempt){<input type="radio" [name]="q.assessmentId+'-'+n" [checked]="answers[q.assessmentId]?.[n]===j" (change)="setAnswer(q,n,j)" />}<span>{{j+1}}. {{option}}</span></label>}
 @if(item.correctIndex!==null){<p class="hint">正确答案：选项{{item.correctIndex+1}}@if(q.attempt){ · 作答：选项{{q.attempt.answers[n]+1}}}</p>}</fieldset>}
 @if(auth.role()==='STUDENT' && q.status==='PUBLISHED' && !q.attempt){<button type="button" class="nur-btn nur-btn--primary nur-btn--sm" [disabled]="busy() || !complete(q)" (click)="submit(q)">提交作答并评分</button>}
 @if(q.attempt;as a){<p class="score">本次答对 {{a.correctCount}}/{{a.totalCount}} 题 · {{a.submittedAt | date:'MM-dd HH:mm'}}</p><p>{{a.teacherComment || '教师尚未补充批注。'}}</p>@if(auth.role()==='TEACHER'){<label>教师批注<textarea [id]="'assessment-comment-'+q.assessmentId" maxlength="4000" [value]="comments[q.assessmentId]??a.teacherComment??''" (input)="comments[q.assessmentId]=$any($event.target).value" rows="2"></textarea></label><button type="button" (click)="comment(q)" [disabled]="busy()">保存批注</button>}}</article>}
 </section>
 @if(report();as r){<section class="nur-card"><h2>真实学习证据</h2><p>{{r.notice}}</p><p>已完成任务 {{r.tasksCompleted}}/{{r.tasksTotal}} · 已完成课程 {{r.lessonsCompleted}} 节{{auth.role()==='TEACHER'?'（仅你的授课与任务）':''}}</p>
 <h3>小测成绩记录</h3><nur-queue-pager label="成绩" [page]="scorePage()" [pages]="r.scores.totalPages" [total]="r.scores.totalElements" (turn)="turnScores($event)" (reload)="loadReport()" />
 @for(s of r.scores.content;track s.assessmentId){<div class="trend"><span>{{s.title}} · {{s.subject}} · {{s.submittedAt | date:'MM-dd'}}</span><progress [value]="s.correctCount" [max]="s.totalCount"></progress><strong>答对{{s.correctCount}}/{{s.totalCount}}</strong></div>}
 @if(!r.scores.totalElements){<p>暂无已提交的小测成绩。</p>}
 <h3>最近课堂记录要点（最多10条）</h3>@for(n of r.classroomNotes;track $index){<article><p>{{n['subject']}} · {{n['knowledgePoints']}}</p><p>{{n['performance']}}</p><p>下次建议：{{n['nextSuggestion']}}</p></article>}
 </section>}
 </main>`,
 styles:[`.shell{max-width:950px;margin:30px auto;padding:0 18px;color:#263e33}.nur-card{padding:24px;margin:20px 0}form,label{display:grid;gap:8px}form{gap:14px}input,select,textarea{font:inherit;width:100%;box-sizing:border-box;padding:9px;border:1px solid #ceded2;border-radius:8px;background:#fff}fieldset{border:1px solid #dae5dd;border-radius:10px;padding:14px;margin:12px 0;min-width:0}legend{overflow-wrap:anywhere}article{padding:16px 0;border-bottom:1px solid #e4eae5;overflow-wrap:anywhere}.answer{display:flex;align-items:center;gap:8px;margin:8px 0}.answer input{width:auto}.actions,.trend{display:flex;gap:10px;flex-wrap:wrap;align-items:center}.trend{margin:12px 0}.trend span{flex:1;min-width:180px}.hint{font-size:.85rem;color:#617669}.score{font-weight:600}.notice{padding:12px;background:#eff6ef;border-radius:9px}button{font:inherit;border:1px solid #cbdace;border-radius:8px;background:#fff;padding:7px 12px;color:inherit;cursor:pointer}button:disabled{opacity:.5}@media(max-width:500px){.nur-card{padding:16px}}`]
})
export class AssessmentsComponent {
 private http=inject(HttpClient);readonly auth=inject(AuthService);private route=inject(ActivatedRoute);private version=0;
 readonly quizzes=signal<Assessment[]>([]);readonly report=signal<Report|null>(null);readonly students=signal<{studentId:string;displayName:string}[]>([]);readonly children=signal<{studentId:string;studentNo:string|null}[]>([]);readonly lessons=signal<{bookingId:string;subject:string;startAt:string}[]>([]);
 readonly page=signal(0);readonly pages=signal(0);readonly total=signal(0);readonly scorePage=signal(0);readonly busy=signal(false);readonly loading=signal(false);readonly notice=signal('');
 student='';child='';bookingId='';subject='数学';title='';scope='';editing='';draftRevision=0;questions:Question[]=[{prompt:'',choices:['','','',''],correctIndex:0}];answers:Partial<Record<string,number[]>>={};comments:Partial<Record<string,string>>={};
 constructor(){this.student=this.route.snapshot.queryParamMap.get('studentId')??'';this.child=this.route.snapshot.queryParamMap.get('childProfileId')??'';
 if(this.auth.role()==='TEACHER')this.http.get<{studentId:string;displayName:string}[]>('/api/v1/learning/plans/students').subscribe({next:s=>{this.students.set(s);this.student=this.student||s[0]?.studentId||'';if(this.student)this.load();},error:e=>this.fail(e)});
 else if(this.auth.role()==='PARENT')this.http.get<any[]>('/api/v1/parent/me/children').subscribe({next:c=>{const active=c.filter(x=>x.status==='ACTIVE');this.children.set(active);this.child=this.child||active[0]?.studentId||'';if(this.child)this.load();},error:e=>this.fail(e)});
 else this.load();}
 private fail(e:any):void{this.busy.set(false);this.loading.set(false);this.notice.set(e.error?.detail??'操作失败，请刷新后重试。');}
 params():Record<string,string|number>{const p:Record<string,string|number>={};if(this.student)p['studentId']=this.student;if(this.child)p['childProfileId']=this.child;return p;}
 selectStudent(id:string):void{this.student=id;this.page.set(0);this.scorePage.set(0);this.clearDraft();this.answers={};this.comments={};this.quizzes.set([]);this.report.set(null);this.load();}
 selectChild(id:string):void{this.child=id;this.page.set(0);this.scorePage.set(0);this.quizzes.set([]);this.report.set(null);this.load();}
 load():void{const v=++this.version;this.loading.set(true);this.http.get<Page<Assessment>>(BASE,{params:{...this.params(),page:this.page(),size:20}}).subscribe({next:r=>{if(v!==this.version)return;this.quizzes.set(r.content);this.pages.set(r.totalPages);this.total.set(r.totalElements);this.loading.set(false);},error:e=>{if(v===this.version)this.fail(e);}});this.loadReport();if(this.auth.role()==='TEACHER')this.http.get<any[]>('/api/v1/learning/files/eligible-lessons',{params:{studentId:this.student}}).subscribe({next:l=>{if(v!==this.version)return;this.lessons.set(l);if(!this.editing)this.bookingId=l[0]?.bookingId??'';},error:e=>{if(v===this.version)this.fail(e);}});}
 private reportVersion=0;
 loadReport():void{const v=++this.reportVersion;this.http.get<Report>(BASE+'/report',{params:{...this.params(),page:this.scorePage(),size:20}}).subscribe({next:r=>{if(v===this.reportVersion)this.report.set(r);},error:e=>{if(v===this.reportVersion)this.fail(e);}});}
 turn(n:number):void{if(this.loading() || this.page()+n<0 || this.page()+n>=this.pages())return;this.page.update(p=>p+n);this.load();}
 turnScores(n:number):void{if(this.scorePage()+n<0 || this.scorePage()+n>=(this.report()?.scores.totalPages??0))return;this.scorePage.update(p=>p+n);this.loadReport();}
 status(s:string):string{return ({DRAFT:'教师草稿',PUBLISHED:'已发布',ARCHIVED:'已归档'} as Record<string,string>)[s]??s;}
 addQuestion():void{if(this.questions.length<20)this.questions.push({prompt:'',choices:['','','',''],correctIndex:0});}
 removeQuestion(n:number):void{if(this.questions.length>1)this.questions.splice(n,1);}
 clearDraft():void{this.editing='';this.title='';this.scope='';this.questions=[{prompt:'',choices:['','','',''],correctIndex:0}];this.bookingId=this.lessons()[0]?.bookingId??'';}
 edit(q:Assessment):void{this.editing=q.assessmentId;this.draftRevision=q.revision;this.bookingId=q.bookingId;this.subject=q.subject;this.title=q.title;this.scope=q.scope;this.questions=q.questions.map(x=>({...x,choices:[...x.choices]}));}
 saveDraft():void{if(this.busy())return;this.busy.set(true);const body={bookingId:this.bookingId,subject:this.subject,title:this.title,scope:this.scope,questions:this.questions,revision:this.draftRevision};const req=this.editing?this.http.put(BASE+'/'+this.editing,body):this.http.post(BASE,body);req.subscribe({next:()=>{this.busy.set(false);this.clearDraft();this.notice.set('草稿已保存，发布前可继续编辑题目。');this.page.set(0);this.load();},error:e=>this.fail(e)});}
 action(q:Assessment,action:string):void{if(this.busy())return;this.busy.set(true);this.http.post(BASE+'/'+q.assessmentId+'/'+action,{revision:q.revision}).subscribe({next:()=>{this.busy.set(false);this.notice.set(action==='publish'?'小测已发布，题目与答案已固定。':'小测已归档，既有成绩保留。');this.load();},error:e=>this.fail(e)});}
 setAnswer(q:Assessment,n:number,j:number):void{(this.answers[q.assessmentId]??=[])[n]=j;}
 complete(q:Assessment):boolean{return q.questions.every((_,n)=>Number.isInteger(this.answers[q.assessmentId]?.[n]));}
 submit(q:Assessment):void{if(this.busy() || !this.complete(q))return;this.busy.set(true);this.http.post(BASE+'/'+q.assessmentId+'/submit',{answers:this.answers[q.assessmentId]}).subscribe({next:()=>{this.busy.set(false);this.notice.set('作答已提交，以下为本次题目范围的真实得分。');this.load();},error:e=>this.fail(e)});}
 comment(q:Assessment):void{const comment=(this.comments[q.assessmentId]??q.attempt?.teacherComment??'').trim();if(this.busy() || !comment || !q.attempt)return;this.busy.set(true);this.http.post(BASE+'/'+q.assessmentId+'/comment',{revision:q.attempt.commentRevision,comment}).subscribe({next:()=>{this.busy.set(false);this.notice.set('教师批注已保存。');this.load();},error:e=>this.fail(e)});}
}
