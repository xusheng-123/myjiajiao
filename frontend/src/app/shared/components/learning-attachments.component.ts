import { Component, Input, OnChanges, inject, signal, output } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface LearningFile { fileId: string; filename: string; sizeBytes: number; category: string; canDelete: boolean; }
@Component({ selector: 'nur-learning-attachments', standalone: true,
  template: `<section class="attachments" aria-label="学习附件"><h4>附件</h4>
    @if (notice()) { <p role="status">{{ notice() }}</p> }
    @if (ready()) {
      @if (canUpload()) { <label>上传附件（单个≤20MB）<input type="file" accept=".pdf,.png,.jpg,.jpeg,.docx,.xlsx,.pptx" [disabled]="busy()" (change)="choose($event)"></label>
        <p>PDF、PNG、JPEG或无宏的Office文档；提交作业后不能补传或移除作业附件。</p> }
      @if (!rows().length) { <p>暂无附件。</p> }
      @for (f of rows(); track f.fileId) { <div class="file-row"><span>{{ f.filename }} · {{ size(f.sizeBytes) }}</span>
        <button type="button" (click)="download(f)" [disabled]="busy()">下载</button>
        @if (f.canDelete && (canUpload() || f.category !== 'TASK_SUBMIT')) { <button type="button" (click)="remove(f)" [disabled]="busy()">移除</button> }
      </div> }
      @if (totalPages()>1) { <div class="file-row"><button type="button" (click)="turn(-1)" [disabled]="busy() || page()===0">上一页附件</button><span>{{ page()+1 }}/{{ totalPages() }}</span><button type="button" (click)="turn(1)" [disabled]="busy() || page()+1>=totalPages()">下一页附件</button></div> }
    }
  </section>`, styles: [`.attachments{margin-top:1rem;border-top:1px solid var(--border-subtle);padding-top:.5rem;overflow-wrap:anywhere}h4{margin:.5rem 0}p{font-size:.85rem;color:var(--text-muted)}.file-row{display:flex;align-items:center;gap:.5rem;flex-wrap:wrap;margin:.5rem 0}button{min-height:44px;padding:.35rem .8rem;border:1px solid var(--border-subtle);border-radius:8px;background:var(--bg-card);color:var(--text-main)}input{max-width:100%;display:block;margin:.5rem 0}button:disabled{opacity:.5}`] })
export class LearningAttachmentsComponent implements OnChanges {
  readonly changed=output<void>();
  @Input() taskId = ''; @Input() questionId = ''; @Input() status = '';
  private http = inject(HttpClient); private version = 0; private target = ''; private category = '';
  readonly rows = signal<LearningFile[]>([]); readonly ready = signal(false); readonly busy = signal(false);
  readonly canUpload = signal(false); readonly notice = signal(''); readonly page = signal(0); readonly totalPages = signal(0);
  ngOnChanges(): void {
    const v = ++this.version; this.ready.set(false); this.canUpload.set(false); this.rows.set([]); this.page.set(0);
    const params = this.contextParams();
    this.http.get<{ studentId: string; category: string; canUpload: boolean }>('/api/v1/learning/files/context', { params }).subscribe({
      next: c => { if(v !== this.version) return; this.target=c.studentId; this.category=c.category; this.canUpload.set(c.canUpload); this.ready.set(true); this.load(); },
      error: e => { if(v === this.version) this.notice.set(e.status===403 ? '附件由此课程的学生、教师和有效监护家长查看。' : e.error?.detail ?? '附件暂时不可用。'); },
    });
  }
  private contextParams(): Record<string,string> { return this.taskId ? {taskId:this.taskId} : {questionId:this.questionId}; }
  private load(): void {
    const v=this.version; this.http.get<{content:LearningFile[];totalPages:number}>('/api/v1/learning/files',{params:{...this.contextParams(),studentId:this.target,page:String(this.page()),size:'20'}}).subscribe({
      next:r=>{if(v!==this.version)return;this.rows.set(r.content);this.totalPages.set(r.totalPages);},error:e=>this.notice.set(e.error?.detail??'附件加载失败。'),
    });
  }
  turn(delta:number):void{this.page.update(p=>p+delta);this.load();}
  size(bytes:number):string{return bytes<1048576?(bytes/1024).toFixed(1)+' KB':(bytes/1048576).toFixed(1)+' MB';}
  choose(event:Event):void{
    const input=event.target as HTMLInputElement;const file=input.files?.[0];if(!file)return;
    if(file.size===0||file.size>20971520){this.notice.set('文件不能为空且不得超过20MB。');input.value='';return;}
    const form=new FormData();form.append('category',this.category);form.append('contextId',this.taskId||this.questionId);form.append('file',file);
    this.busy.set(true);this.http.post('/api/v1/learning/files',form).subscribe({next:()=>{this.busy.set(false);input.value='';this.notice.set('附件已保存。');this.changed.emit();this.load();},error:e=>{this.busy.set(false);input.value='';this.notice.set(e.error?.detail??'上传失败，请重试。');}});
  }
  download(f:LearningFile):void{this.busy.set(true);this.http.get('/api/v1/learning/files/'+f.fileId+'/download',{responseType:'blob'}).subscribe({next:blob=>{const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=f.filename;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);this.busy.set(false);},error:()=>{this.busy.set(false);this.notice.set('文件暂时无法下载，请刷新权限后重试。');}});}
  remove(f:LearningFile):void{if(!window.confirm('移除此附件？上传文件将保留用于审计，仍计入个人容量。'))return;this.busy.set(true);this.http.delete('/api/v1/learning/files/'+f.fileId).subscribe({next:()=>{this.busy.set(false);this.changed.emit();this.load();},error:e=>{this.busy.set(false);this.notice.set(e.error?.detail??'移除失败。');}});}
}
