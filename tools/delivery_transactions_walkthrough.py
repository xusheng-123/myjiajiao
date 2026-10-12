"""Exercise newly rebuilt dashboards through real UI writes on the delivery copy."""
import asyncio,datetime,json,pathlib,sys
from browser_support import Browser
from gap_fill_walkthrough import api
from learning_plan_walkthrough import register,PASSWORD
sys.stdout.reconfigure(encoding='utf-8')
ROOT=pathlib.Path(__file__).resolve().parents[1]
async def click(b,text,selector='button'):
    assert await b.js('(()=>{const n=s=>s.replace(/\\s+/g,"");const e=[...document.querySelectorAll('+json.dumps(selector)+')].find(e=>n(e.textContent)===n('+json.dumps(text)+'));if(!e||e.disabled)return false;e.click();return true;})()'),text
    await asyncio.sleep(.25)
async def main():
    s,t,p=register('STUDENT'),register('TEACHER'),register('PARENT')
    at=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8)))+datetime.timedelta(minutes=30)
    bookings=[]
    for i in range(2):
        start=at+datetime.timedelta(minutes=45*i)
        slot,_=api('POST','/scheduling/availability-slots',{'startAt':start.isoformat(),'endAt':(start+datetime.timedelta(minutes=30)).isoformat()},t['token'])
        booking,_=api('POST','/booking/bookings',{'teacherId':t['id'],'slotId':slot['slotId'],'subject':'交付履约数学','bookingType':'FORMAL'},s['token'])
        api('POST','/booking/booking-requests/'+booking['bookingId']+':decide?decision=approve',{},t['token']);bookings.append(booking)
    profile,_=api('GET','/student/me/profile',token=s['token'])
    async with Browser(ROOT/'.walk'/'delivery-transactions',port=9250) as b:
        await b.nav('/auth?role=STUDENT');await b.until('document.querySelector("#account")!==null','password login form')
        await b.fill('#account',s['phone']);await b.fill('#password',PASSWORD)
        assert await b.js('(()=>{const e=document.querySelector(".auth-form button[type=submit]");if(!e||e.disabled)return false;e.click();return true;})()')
        await b.until('location.pathname==="/student/dashboard"','actual UI login')
        await click(b,'展开全部功能');await b.until('document.querySelector("#profile-title")!==null','expanded dashboard')
        await click(b,'编辑资料');await b.fill('#edit-grade','高中');await b.fill('#edit-focus','交付验证：巩固函数基础')
        await click(b,'保存修改');await b.until('document.body.innerText.includes("巩固函数基础")','student profile saved')
        fresh,_=api('GET','/student/me/profile',token=s['token']);assert fresh['focusPreference']=='交付验证：巩固函数基础'
        await b.role('PARENT',p['phone'],PASSWORD,'/parent/dashboard')
        await b.fill('input[aria-label="学生编号"]',profile['studentNo']);await b.fill('input[aria-label="与学生关系"]','母亲');await click(b,'发起绑定申请')
        await b.until('document.body.innerText.includes("待学生确认")','parent binding pending')
        await b.role('STUDENT',s['phone'],PASSWORD,'/student/dashboard');await click(b,'展开全部功能')
        await b.until('[...document.querySelectorAll("button")].some(e=>e.textContent.includes("确认绑定"))','student confirmation control');await click(b,'确认绑定')
        children,_=api('GET','/parent/me/children',token=p['token']);assert len(children)==1 and children[0]['status']=='ACTIVE'
        await b.role('TEACHER',t['phone'],PASSWORD,'/teacher/dashboard')
        await b.until('document.querySelectorAll("nur-today-lessons-card .lesson-actions").length===2','today real lessons')
        await click(b,'课堂准备','nur-today-lessons-card .lesson-actions button')
        await b.until('document.querySelector("nur-today-lessons-card .classroom-panel")!==null || document.body.innerText.includes("生成课前任务清单")','classroom preparation')
        await click(b,'生成课前任务清单');await b.until('document.querySelectorAll("nur-today-lessons-card .task-checklist li").length===4','generated four real preparation tasks')
        tasks,_=api('GET','/classroom/bookings/'+bookings[0]['bookingId']+'/preparation-tasks',token=t['token']);assert len(tasks)==4
        await click(b,'收起面板');await click(b,'开始上课','nur-today-lessons-card .lesson-actions button')
        await b.until('[...document.querySelectorAll("nur-today-lessons-card button")].some(e=>e.textContent.trim()==="完成课程")','lesson started')
        assert api('GET','/booking/bookings/'+bookings[0]['bookingId'],token=t['token'])[0]['status']=='IN_PROGRESS'
        await click(b,'完成课程');await b.until('document.body.innerText.includes("课程已完成")','lesson complete')
        assert api('GET','/booking/bookings/'+bookings[0]['bookingId'],token=t['token'])[0]['status']=='COMPLETED'
        await click(b,'立即录入课堂记录');await b.until('document.querySelector("#booking-id-input")!==null','record context prefilled')
        assert await b.js('document.querySelector("#booking-id-input").value')==bookings[0]['bookingId']
        secret='交付验收教师内部备忘'
        for selector,value in [('#knowledge-input','函数图像'),('#performance-input','能够独立解释参数'),('#suggestion-input','下一节练习应用题'),('#note-input',secret)]:await b.fill(selector,value)
        await click(b,'保存并同步给学生');await b.until('document.querySelector("nur-lesson-records-card").innerText.includes("能够独立解释参数")','lesson record actually saved')
        records,_=api('GET','/learning/teacher/lesson-records',token=t['token']);record=next(x for x in records if x['bookingId']==bookings[0]['bookingId']);assert record['note']==secret
        await b.shot('teacher-real-completion-record-desktop')
        await b.role('STUDENT',s['phone'],PASSWORD,'/student/archive')
        await b.until('document.body.innerText.includes("能够独立解释参数")','student sees public lesson feedback')
        assert secret not in await b.js('document.body.innerText')
        archive,_=api('GET','/learning/archive',token=s['token']);assert not archive['lessonRecords'][0].get('note')
        await b.role('STUDENT',s['phone'],PASSWORD,'/student/dashboard');await click(b,'展开全部功能')
        # Cancel the remaining confirmed booking through both UI stages.
        await b.until('[...document.querySelectorAll("button")].some(e=>e.textContent.trim()==="取消预约")','owned cancel action');await click(b,'取消预约')
        await b.fill('#cancel-reason-input','交付隔离验收：调整学习安排');await click(b,'下一步：预览影响')
        await b.until('[...document.querySelectorAll("button")].some(e=>e.textContent.trim()==="确认取消预约")','preview confirmation')
        assert api('GET','/booking/bookings/'+bookings[1]['bookingId'],token=s['token'])[0]['status']=='CONFIRMED'
        await click(b,'确认取消预约');await b.until('document.querySelector("[role=dialog]")===null','cancellation finished')
        assert api('GET','/booking/bookings/'+bookings[1]['bookingId'],token=s['token'])[0]['status']=='CANCELLED'
        await b.viewport(375);await b.shot('student-cancelled-mobile375');await b.viewport(1280)
        await b.role('ADMIN','13800000000','Admin#2026','/admin/dashboard');await click(b,'平台配置')
        await b.until('document.querySelector("#config-val-input")!==null','admin config form');await b.fill('#config-val-input','0.15');await click(b,'提交更新参数 (PUT)')
        await b.until('document.querySelector("nur-admin-platform-config [role=status]")!==null','actual config update response')
        await click(b,'账户治理');await b.until('document.querySelector("#throttle-account")!==null','throttle management')
        await b.fill('#throttle-account',s['phone']);await click(b,'解除登录限频');await b.until('document.querySelector("nur-admin-accounts .status-banner")!==null','throttle reset response')
        await click(b,'审计日志');await b.until('document.querySelector("nur-admin-audit-logs")!==null','audit queue');await b.shot('admin-real-governance-desktop')
    print('PASS: UI password login/profile edit/guardian request+student confirmation/preparation/start/complete/record/cancel preview+execute/config/throttle/audit; actual persisted states checked',flush=True)
if __name__=='__main__':asyncio.run(main())
