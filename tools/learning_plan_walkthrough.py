"""Stage 3: actual student/teacher/guardian browser journey on isolated localhost API."""
import asyncio, datetime, json, pathlib, secrets, sys, uuid
from gap_fill_walkthrough import api, account_id
from browser_support import Browser
sys.stdout.reconfigure(encoding='utf-8')
ROOT=pathlib.Path(__file__).resolve().parents[1]
PASSWORD='PlanWalk#2026'
def register(role):
    phone='15'+f'{secrets.randbelow(1000000000):09d}'
    body={'displayName':'计划验收'+{'STUDENT':'学生','TEACHER':'老师','PARENT':'家长'}[role],'phone':phone,'password':PASSWORD}
    if role=='TEACHER': body.update(school='验收大学',highestDegree='硕士',yearsOfTeaching=3)
    data,_=api('POST','/identity/auth/register:'+role.lower(),body)
    return {'phone':phone,'token':data['accessToken'],'id':account_id(data['accessToken'])}

async def main():
    student,teacher,parent=register('STUDENT'),register('TEACHER'),register('PARENT')
    at=(datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8)))+datetime.timedelta(days=12)).replace(hour=15,minute=0,second=0,microsecond=0)
    slot,_=api('POST','/scheduling/availability-slots',{'startAt':at.isoformat(),'endAt':(at+datetime.timedelta(hours=1)).isoformat()},teacher['token'])
    booking,_=api('POST','/booking/bookings',{'teacherId':teacher['id'],'slotId':slot['slotId'],'subject':'数学','bookingType':'FORMAL'},student['token'])
    api('POST','/booking/booking-requests/'+booking['bookingId']+':decide?decision=approve',{},teacher['token'])
    profile,_=api('GET','/student/me/profile',token=student['token'])
    api('POST','/parent/me/children',{'studentNo':profile['studentNo'],'relation':'母亲'},parent['token'])
    api('POST','/parent/me/children:confirm',{},student['token'])
    async with Browser(ROOT/'.walk'/'learning-plans') as b:
        await b.role('STUDENT',student['phone'],PASSWORD,'/student/growth')
        await b.click('学习计划与阶段目标','a')
        await b.until('document.querySelector("#plan-title")!==null','student plan entry')
        await b.fill('#plan-subject','数学'); await b.fill('#plan-title','阶段3数学计划'); await b.fill('#plan-goal','建立完整的函数解题步骤')
        await b.click('建立计划'); await b.until('document.querySelector(".plan-card")!==null','student creates plan')
        plans,_=api('GET','/learning/plans',token=student['token']); plan=plans['content'][0]; pid=plan['planId']
        await b.shot('student-created-desktop')
        await b.role('TEACHER',teacher['phone'],PASSWORD,'/teacher/dashboard'); await b.click('学生学习计划','a')
        await b.until('document.querySelector(".plan-card")!==null','teacher sees own student')
        await b.click('调整计划'); await b.fill('#plan-title','阶段3教师协同计划'); await b.fill('#plan-goal','先解释斜率，再独立推导表达式'); await b.click('保存调整')
        await b.until('document.querySelector(".plan-card h2").textContent.includes("教师协同")','teacher adjustment saved')
        await b.click('阶段目标与任务'); await b.fill('#milestone-title','理解一次函数'); await b.fill('#milestone-goal','独立说明表达式和图像的关系'); await b.click('添加阶段目标')
        await b.until('document.querySelector(".milestone")!==null','milestone saved')
        await b.until('document.querySelector("select[name=taskLesson] option")!==null','actual owned lesson choices')
        await b.fill('#new-task-title','画出函数图像'); await b.fill('#new-task-description','解释每一个参数的含义'); await b.click('发布并关联任务')
        await b.until('document.querySelector(".linked-task")!==null','teacher task creation and linking')
        await b.shot('teacher-goals-task-desktop')
        await b.role('STUDENT',student['phone'],PASSWORD,'/learning/plans'); await b.until('document.querySelector(".linked-task")!==null','student sees actual task')
        await b.click('提交作业'); await b.fill('#task-work','图像穿过原点，斜率为2，已写出推导过程'); await b.click('确认提交')
        await b.until('document.querySelector(".linked-task").innerText.includes("待教师反馈")','student work submitted')
        assert await b.js('document.querySelector(".progress").innerText.includes("关联任务 0/1")'),'submitted must not count as completed'
        await b.viewport(375); await b.shot('student-submitted-mobile375'); await b.viewport(1280)
        await b.role('TEACHER',teacher['phone'],PASSWORD,'/learning/plans?studentId='+student['id']); await b.until('document.querySelector(".linked-task")!==null','teacher actual submission visible')
        assert await b.js('document.querySelector(".linked-task").innerText.includes("斜率为2")')
        await b.click('给出反馈'); await b.fill('#task-work','推导准确，下次练习不经过原点的图像'); await b.click('保存反馈并完成任务')
        await b.until('document.querySelector(".progress").innerText.includes("关联任务 1/1")','feedback changes real completion')
        await b.click('确认达成'); await b.until('document.querySelector(".progress").innerText.includes("阶段目标 1/1")','goal confirmed')
        await b.click('修改记录'); await b.until('document.querySelector(".history").innerText.includes("发布课后任务")','append-only history')
        await b.shot('teacher-feedback-history-desktop')
        api('POST','/booking/bookings/'+booking['bookingId']+':start',{},teacher['token']); api('POST','/booking/bookings/'+booking['bookingId']+':complete',{},teacher['token'])
        await b.role('PARENT',parent['phone'],PASSWORD,'/parent/dashboard'); await b.click('学习计划','a')
        await b.until('document.querySelector(".plan-card")!==null','active guardian follows child profile ID')
        assert await b.js('document.querySelector(".progress").innerText.includes("关联任务 1/1") && document.querySelector(".progress").innerText.includes("已完成课程 1")')
        assert await b.js('document.querySelector(".plan-editor")===null && ![...document.querySelectorAll("button")].some(e=>e.textContent.trim()==="调整计划")'),'guardian read-only UI'
        await b.viewport(375); await b.shot('guardian-real-progress-mobile375'); await b.click('修改记录')
        await b.until('document.querySelector(".history").innerText.includes("计划验收老师")','guardian history visible')
        await b.shot('guardian-history-mobile375')
        print('PASS: student create/work, teacher goals/atomic task/feedback, real progress, active guardian read-only and adjustment history',flush=True)

if __name__=='__main__': asyncio.run(main())
