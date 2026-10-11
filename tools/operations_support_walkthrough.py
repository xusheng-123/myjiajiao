"""Stage6 real browser queues, relisting, global search paging, private support evidence."""
import asyncio,json,pathlib,hashlib,uuid,sys
from browser_support import Browser
from gap_fill_walkthrough import api,login,upload
from learning_plan_walkthrough import register,PASSWORD
from learning_materials_walkthrough import file_input,PDF
sys.stdout.reconfigure(encoding='utf-8')
ROOT=pathlib.Path(__file__).resolve().parents[1]
async def main():
 s,t=register('STUDENT'),register('TEACHER');a,_,_=login('ADMIN','13800000000','Admin#2026')
 prefix='翻页验收'+uuid.uuid4().hex[:4]
 offering,_=api('POST','/teacher/me/subjects',{'subject':prefix,'gradeBand':'高中','pricePerHourCents':16800},t['token'])
 upload(t['token']);cases,_=api('GET','/admin/verification-cases?status=SUBMITTED',token=a);case=next(x for x in cases['content'] if x['teacherId']==t['id']);api('POST','/admin/verification-cases/'+case['caseId']+':decide',{'approve':True,'note':'本地浏览器核验'},a)
 for n in range(21):api('POST','/teacher/me/subjects',{'subject':prefix+str(n).zfill(2),'gradeBand':'高中','pricePerHourCents':30000-n*100},t['token'])
 async with Browser(ROOT/'.walk'/'operations-support',port=9236) as b:
  await b.role('TEACHER',t['phone'],PASSWORD,'/teacher/dashboard');await b.until('document.querySelector(".subject-list")!==null','teacher subjects ready')
  assert await b.js('(()=>{const row=[...document.querySelectorAll(".subject-list li")].find(x=>x.querySelector(".subject-name")?.textContent.trim()==='+json.dumps(prefix+' · 高中')+');row.querySelectorAll("button")[1].click();return true;})()')
  await b.click('确认下架');await b.until('document.querySelector(".subject-list").innerText.includes("重新上架")','inactive visible');await b.click('重新上架');await b.until('document.body.innerText.includes("课程已重新上架")','relisted')
  await b.js('document.querySelector("section[aria-labelledby=subjects-title]").scrollIntoView()');await b.viewport(375);await b.shot('teacher-relist-mobile375');await b.viewport(1280)
  await b.nav('/teachers');await b.fill('input[type=search]',prefix);await b.until('document.body.innerText.includes("共 22 个开课条目")','full result count');await b.click('下一页');await b.until('document.body.innerText.includes("第 2/2 页") && document.querySelectorAll("nur-teacher-card").length===2','public second page');await b.shot('search-second-page-desktop')
  await b.role('STUDENT',s['phone'],PASSWORD,'/student/dashboard');await b.click('客服与申诉','a');await b.until('document.querySelector("#support-title")!==null','support entry');await b.fill('#support-title','浏览器课程改期咨询');await b.fill('#support-description','希望调整下周课程，并提供相关证据');await b.click('提交工单');await b.until('document.querySelector("#support-detail")!==null','created support case')
  await file_input(b,'document.querySelector("#support-detail input[type=file]")','改期证据.pdf');await b.until('document.querySelector(".file")?.innerText.includes("改期证据.pdf")','private evidence uploaded')
  own,_=api('GET','/support/cases',token=s['token']);c=own['content'][0];files,_=api('GET','/support/cases/'+c['caseId']+'/evidence',token=s['token']);f=files[0]
  r=await b.js('(async()=>{const r=await fetch("/api/v1/support/cases/evidence/'+f['fileId']+'/download");return {status:r.status,bytes:[...new Uint8Array(await r.arrayBuffer())],cache:r.headers.get("Cache-Control"),disposition:r.headers.get("Content-Disposition")};})()')
  assert r['status']==200 and hashlib.sha256(bytes(r['bytes'])).hexdigest()==f['sha256'] and 'no-store' in r['cache'] and r['disposition'].startswith('attachment;')
  for n in range(20):api('POST','/support/cases',{'category':'OTHER','title':'分页记录'+str(n),'description':'仅用于隔离本地验收'},s['token'])
  await b.nav('/support');await b.until('document.body.innerText.includes("共 21 条")','support paging');await b.click('下一页');await b.until('document.body.innerText.includes("第 2/2 页")','support page2');await b.viewport(375);await b.shot('student-support-mobile375');await b.viewport(1280)
  await b.role('ADMIN','13800000000','Admin#2026','/admin/dashboard');await b.until('document.querySelector("nur-queue-pager")!==null','admin pager');assert await b.js('document.querySelector("section[aria-labelledby=account-title]").innerText.includes("共 ")');await b.shot('admin-queue-desktop');await b.viewport(375);await b.shot('admin-queue-mobile375');await b.viewport(1280)
  await b.click('客服与申诉','a');await b.until('document.querySelectorAll(".case").length>0','admin queue');await b.click('下一页');await b.until('document.querySelectorAll(".case").length===1','admin page2');assert await b.js('(()=>{const e=[...document.querySelectorAll("button.case")].find(x=>x.innerText.includes("浏览器课程改期咨询"));if(!e)return false;e.click();return true;})()');await b.until('document.querySelector("#support-reply")!==null','admin support detail');await b.fill('#support-reply','请先在课程表选择改期，教师将确认可用时间');await b.click('发送回复');await b.until('document.querySelector("#support-detail").innerText.includes("教师将确认可用时间")','admin response saved');await b.shot('admin-support-response-desktop')
  await b.role('STUDENT',s['phone'],PASSWORD,'/support');await b.until('document.querySelector("button.case")?.innerText.includes("浏览器课程改期咨询")','updated support case first');await b.js('document.querySelector("button.case").click()');await b.until('document.querySelector("#support-detail")?.innerText.includes("教师将确认可用时间")','student sees actual response');await b.fill('#support-status-note','已了解处理方式，确认关闭');await b.click('确认状态');await b.until('document.querySelector("#support-detail")?.innerText.includes("工单已关闭，沟通与证据保留")','user closure');assert await b.js('document.querySelector("#support-detail input[type=file]")===null'),'closed evidence frozen'
  await b.nav('/');await b.until('document.body.innerText.includes("非真实用户评价")','landing scene disclosure');assert not await b.js('document.body.innerText.includes("98.4%")'),'unsupported metric removed'
 print('PASS: real relisting/search/admin paging/support reply/private evidence/user closure; 6 desktop/mobile screenshots',flush=True)
if __name__=='__main__':asyncio.run(main())
