import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API } from '../../core/api/endpoints';
interface Profile { school: string | null; highestDegree: string | null; yearsOfTeaching: number; intro: string | null; styleTags: string[]; }
@Component({ selector: 'nur-teacher-profile-editor', standalone: true,
  template: `<section class="nur-card profile"><h2 class="nur-serif">公开导师资料</h2>
    @if (profile(); as p) { <p>{{ p.school || '院校未填写' }} · {{ p.highestDegree || '学历未填写' }} · {{ p.yearsOfTeaching }} 年教龄（本人填报）</p><p>{{ p.styleTags.join(' · ') || '暂无风格标签' }}</p> }
    @if (notice()) { <p role="status">{{ notice() }}</p> }
    @if (!editing()) { <button type="button" (click)="edit()" [disabled]="!profile()">完善公开资料</button> }
    @if (editing() && draft(); as d) { <form (submit)="$event.preventDefault(); save()">
      <label for="profile-school">院校</label><input id="profile-school" maxlength="128" required [value]="d.school ?? ''" (input)="field('school',$any($event.target).value)" [disabled]="busy()">
      <label for="profile-degree">最高学历</label><input id="profile-degree" maxlength="32" required [value]="d.highestDegree ?? ''" (input)="field('highestDegree',$any($event.target).value)" [disabled]="busy()">
      <label for="profile-years">教学经验（年）</label><input id="profile-years" type="number" min="0" max="80" step="1" required [value]="d.yearsOfTeaching" (input)="field('yearsOfTeaching',+$any($event.target).value)" [disabled]="busy()">
      <label for="profile-intro">个人介绍（最多 1024 字）</label><textarea id="profile-intro" rows="4" maxlength="1024" [value]="d.intro ?? ''" (input)="field('intro',$any($event.target).value)" [disabled]="busy()"></textarea>
      <label for="profile-tags">教学风格（逗号分隔，最多 12 个标签，每个最多 32 字）</label><input id="profile-tags" maxlength="395" [value]="tags()" (input)="tags.set($any($event.target).value)" [disabled]="busy()">
      <p>这些文字会用于公开师资资料。请填写真实教学信息，避免留下个人联系方式。</p>
      <button type="submit" [disabled]="busy()">{{ busy() ? '保存中…' : '保存资料' }}</button> <button type="button" (click)="editing.set(false)" [disabled]="busy()">返回</button>
    </form> }
  </section>`,
  styles: [`.profile{padding:24px;margin:20px 0}label{display:block;margin:14px 0 5px}input,textarea{width:100%;box-sizing:border-box;padding:10px;border:1px solid var(--border-subtle,#ddd);border-radius:8px;background:var(--bg-card,#fff);color:inherit}button{padding:10px 16px;border:1px solid var(--border-subtle,#ddd);border-radius:8px;background:var(--bg-card,#fff);color:inherit}p{overflow-wrap:anywhere;line-height:1.7}@media (min-width:1024px){.profile{padding:24px 32px}.profile form{max-width:920px}}`],
})
export class TeacherProfileEditorComponent implements OnInit {
  private readonly http=inject(HttpClient);
  readonly profile=signal<Profile|null>(null);
  readonly draft=signal<Profile|null>(null);
  readonly tags=signal('');
  readonly notice=signal('');
  readonly editing=signal(false);
  readonly busy=signal(false);
  ngOnInit(): void { this.http.get<Profile>(API.teacher.profile).subscribe({next:p=>this.profile.set(p),error:()=>this.notice.set('资料加载失败，请刷新页面重试。')}); }
  edit(): void { const p=this.profile(); if(!p)return; this.draft.set({...p}); this.tags.set(p.styleTags.join('，')); this.editing.set(true); this.notice.set(''); }
  field(key: 'school'|'highestDegree'|'yearsOfTeaching'|'intro', value:string|number): void { this.draft.update(d=>d?{...d,[key]:value}:d); }
  save(): void {
    const p=this.draft(); if(!p || this.busy())return;
    const styleTags=[...new Set(this.tags().split(/[,，]/).map(t=>t.trim()).filter(Boolean))];
    if(styleTags.length>12 || styleTags.some(t=>t.length>32)) { this.notice.set('请检查风格标签的数量与长度。'); return; }
    this.busy.set(true);
    this.http.put<Profile>(API.teacher.profile,{...p,styleTags}).subscribe({next:updated=>{this.busy.set(false);this.profile.set(updated);this.editing.set(false);this.notice.set('公开资料已保存。');},error:e=>{this.busy.set(false);this.notice.set(e.error?.detail??'保存失败，填写内容已保留。');}});
  }
}
