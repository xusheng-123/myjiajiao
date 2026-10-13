"""Production-build UI acceptance: teacher pricing and support closure, isolated DB only."""
import asyncio, hashlib, json, pathlib, sys, uuid
from browser_support import Browser
from gap_fill_walkthrough import api, login, upload
from learning_plan_walkthrough import register, PASSWORD
from learning_materials_walkthrough import file_input
from delivery_transactions_walkthrough import click
sys.stdout.reconfigure(encoding='utf-8')
ROOT=pathlib.Path(__file__).resolve().parents[1]
async def main():
    student,teacher=register('STUDENT'),register('TEACHER')
    admin,_,_=login('ADMIN','13800000000','Admin#2026')
    prefix='交付运营'+uuid.uuid4().hex[:6]
    offering,_=api('POST','/teacher/me/subjects',{'subject':prefix,'gradeBand':'高中','pricePerHourCents':16800},teacher['token'])
    upload(teacher['token'])
    pending,_=api('GET','/admin/verification-cases?status=SUBMITTED&size=100',token=admin)
    case=next(c for c in pending['content'] if c['teacherId']==teacher['id'])
    api('POST','/admin/verification-cases/'+case['caseId']+':decide',{'approve':True,'note':'隔离交付验收'},admin)
    async with Browser(ROOT/'.walk'/'delivery-operations',port=9253) as b:
        await b.role('TEACHER',teacher['phone'],PASSWORD,'/teacher/dashboard')
        await b.until('document.querySelector(".subject-item")!==null','real offering')
        await click(b,'调价','.subject-actions button')
        await b.fill('input[aria-label="新价格（元/小时）"]','188.50');await click(b,'确认调价')
        await b.until('document.querySelector(".subject-price").innerText.includes("188.50")','repriced')
        await click(b,'下架','.subject-actions button');await click(b,'确认下架（停止销售）')
        await b.until('document.querySelector(".subject-item--inactive")!==null','delisted')
        offers,_=api('GET','/teacher/me/subjects?includeInactive=true',token=teacher['token'])
        row=next(x for x in offers if x['offeringId']==offering['offeringId'])
        assert row['pricePerHourCents']==18850 and row['active'] is False
        await click(b,'重新上架');await b.until('document.querySelector(".subject-item--inactive")===null','relisted')
        offers,_=api('GET','/teacher/me/subjects?includeInactive=true',token=teacher['token']);assert next(x for x in offers if x['offeringId']==offering['offeringId'])['active'] is True
        await b.viewport(375);await b.shot('teacher-reprice-relist-mobile375');await b.viewport(1280)
        title=prefix+'课程咨询'
        await b.role('STUDENT',student['phone'],PASSWORD,'/support');await click(b,'提交新工单')
        await b.fill('#support-title',title);await b.fill('#support-description','申请了解改期规则，并留存私有凭证。');await click(b,'提交工单')
        await b.until('document.querySelector("#support-reply")!==null','created case detail')
        await file_input(b,'document.querySelector("#support-detail input[type=file]")','交付凭证.pdf')
        await b.until('document.querySelector("#support-detail").innerText.includes("交付凭证.pdf")','private evidence uploaded')
        cases,_=api('GET','/support/cases',token=student['token']);case=next(c for c in cases['content'] if c['title']==title)
        files,_=api('GET','/support/cases/'+case['caseId']+'/evidence',token=student['token']);f=files[0]
        result=await b.js('(async()=>{const r=await fetch("/api/v1/support/cases/evidence/'+f['fileId']+'/download");return {status:r.status,bytes:[...new Uint8Array(await r.arrayBuffer())],cache:r.headers.get("Cache-Control")};})()')
        assert result['status']==200 and hashlib.sha256(bytes(result['bytes'])).hexdigest()==f['sha256'] and 'no-store' in result['cache']
        await b.role('ADMIN','13800000000','Admin#2026','/support')
        await b.until('[...document.querySelectorAll(".case-item")].some(e=>e.innerText.includes('+json.dumps(title)+'))','admin real queue')
        await b.js('[...document.querySelectorAll(".case-item")].find(e=>e.innerText.includes('+json.dumps(title)+')).click()')
        await b.until('document.querySelector("#support-reply")!==null','admin case opened')
        await b.fill('#support-reply','请在课程表发起改期，由授课教师确认。');await click(b,'发送回复')
        await b.until('document.querySelector("#support-detail").innerText.includes("由授课教师确认")','admin reply saved');await b.shot('admin-support-reply-desktop')
        await b.role('STUDENT',student['phone'],PASSWORD,'/support')
        await b.until('document.querySelector("#support-detail").innerText.includes("由授课教师确认")','owner sees reply')
        await b.fill('#support-status-note','已理解规则，确认办结');await click(b,'确认变更状态')
        await b.until('document.querySelector(".closed-notice-card")!==null','case closed')
        assert await b.js('document.querySelector("#support-detail input[type=file]")===null && document.querySelector("#support-reply")===null')
        fresh,_=api('GET','/support/cases/'+case['caseId'],token=student['token']);assert fresh['status']=='CLOSED'
        await b.viewport(375);await b.shot('student-closed-support-mobile375')
    print('PASS: actual UI reprice/delist/relist, private evidence/hash/no-store, admin reply/owner sees response/user closure; persisted states checked',flush=True)
if __name__=='__main__':asyncio.run(main())
