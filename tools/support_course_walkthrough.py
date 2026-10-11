"""Real three-role course selector and persisted support/admin detail acceptance."""
import asyncio, datetime, json, pathlib, sys
from browser_support import Browser
from gap_fill_walkthrough import api
from learning_plan_walkthrough import register, PASSWORD
sys.stdout.reconfigure(encoding='utf-8')
ROOT=pathlib.Path(__file__).resolve().parents[1]
async def main():
    student,teacher,parent=register('STUDENT'),register('TEACHER'),register('PARENT')
    at=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8)))+datetime.timedelta(days=18)
    ids=[]
    for i in range(21):
        start=at+datetime.timedelta(days=i)
        slot,_=api('POST','/scheduling/availability-slots',{'startAt':start.isoformat(),'endAt':(start+datetime.timedelta(hours=1)).isoformat()},teacher['token'])
        course,_=api('POST','/booking/bookings',{'teacherId':teacher['id'],'slotId':slot['slotId'],'subject':'关联课程验收数学','bookingType':'FORMAL'},student['token']);ids.append(course['bookingId'])
    profile,_=api('GET','/student/me/profile',token=student['token'])
    api('POST','/parent/me/children',{'studentNo':profile['studentNo'],'relation':'母亲'},parent['token']);api('POST','/parent/me/children:confirm',{},student['token'])
    cases=[]
    async with Browser(ROOT/'.walk'/'support-course',port=9240) as b:
        for role,user in [('STUDENT',student),('TEACHER',teacher),('PARENT',parent)]:
            await b.role(role,user['phone'],PASSWORD,'/support')
            await b.until('document.querySelectorAll("#support-course option").length===21','twenty scoped courses on page one')
            chosen=await b.js('(()=>{const e=document.querySelector("#support-course");e.value=e.options[1].value;e.dispatchEvent(new Event("change",{bubbles:true}));return e.value;})()')
            assert chosen in ids
            await b.js('[...document.querySelectorAll("#support-course-picker button")].find(e=>e.textContent.trim()==="下一页").click()')
            await b.until('document.querySelector("#support-course-picker").innerText.includes("第 2/2 页")','course page two')
            assert await b.js('document.querySelector("#support-course").value')==chosen,'selected course retained across paging'
            await b.fill('#support-title',role+'课程关联验收');await b.fill('#support-description','通过下拉选择关联课程，提交后核对实际保存记录。');await b.click('提交工单')
            await b.until('document.querySelector("#support-linked-course")!==null','associated detail returned')
            assert chosen in await b.js('document.querySelector("#support-linked-course").innerText')
            data,_=api('GET','/support/cases',token=user['token']);case=data['content'][0];assert case['bookingId']==chosen and case['course']['bookingId']==chosen;cases.append(case)
            if role=='PARENT':await b.viewport(375)
            await b.shot(role.lower()+'-course-persisted')
        await b.viewport(1280);await b.role('ADMIN','13800000000','Admin#2026','/support')
        await b.until('document.querySelectorAll(".case").length>=3','admin real queue');await b.js('[...document.querySelectorAll(".case")].find(e=>e.querySelector("strong").textContent==="PARENT课程关联验收").click()')
        await b.until('document.querySelector("#support-linked-course")!==null','admin linked metadata')
        assert cases[-1]['bookingId'] in await b.js('document.querySelector("#support-linked-course").innerText')
        await b.shot('admin-linked-course')
    print('PASS: scoped course paging, retained choice, three-role creation, stored booking IDs, admin detail, mobile 375px',flush=True)
if __name__=='__main__':asyncio.run(main())
