"""Stage4 actual UI: mistake -> review task -> attachments -> feedback -> guardian."""
import asyncio, datetime, json, pathlib, hashlib, sys
from browser_support import Browser
from gap_fill_walkthrough import api
from learning_plan_walkthrough import register, PASSWORD
sys.stdout.reconfigure(encoding='utf-8')
ROOT=pathlib.Path(__file__).resolve().parents[1]
PDF='%PDF-1.4\nStage4 browser acceptance file\n%%EOF'
async def file_input(browser,selector,filename):
    expression='''(()=>{const e=SELECTOR;if(!e||e.disabled)return false;const d=new DataTransfer();d.items.add(new File([CONTENT],NAME,{type:'application/pdf'}));e.files=d.files;e.dispatchEvent(new Event('change',{bubbles:true}));return true;})()'''
    assert await browser.js(expression.replace('SELECTOR',selector).replace('CONTENT',json.dumps(PDF)).replace('NAME',json.dumps(filename))),filename
async def main():
    student,teacher,parent=register('STUDENT'),register('TEACHER'),register('PARENT')
    at=(datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8)))+datetime.timedelta(days=15)).replace(hour=16,minute=0,second=0,microsecond=0)
    slot,_=api('POST','/scheduling/availability-slots',{'startAt':at.isoformat(),'endAt':(at+datetime.timedelta(hours=1)).isoformat()},teacher['token'])
    booking,_=api('POST','/booking/bookings',{'teacherId':teacher['id'],'slotId':slot['slotId'],'subject':'数学','bookingType':'FORMAL'},student['token'])
    api('POST','/booking/booking-requests/'+booking['bookingId']+':decide?decision=approve',{},teacher['token'])
    profile,_=api('GET','/student/me/profile',token=student['token']);api('POST','/parent/me/children',{'studentNo':profile['studentNo'],'relation':'母亲'},parent['token']);api('POST','/parent/me/children:confirm',{},student['token'])
    async with Browser(ROOT/'.walk'/'learning-materials',port=9234) as b:
        await b.role('STUDENT',student['phone'],PASSWORD,'/student/growth');await b.click('错题本','a');await b.until('document.querySelector("#mistake-title")!==null','mistake entry')
        for key,value in [('subject','数学'),('title','一次函数移项错误'),('question','求y=2x+3的横轴截距'),('reason','移项时忘记变号'),('tags','一次函数,移项')]:await b.fill('#mistake-'+key,value)
        await b.click('记录错题');await b.until('document.querySelector("article")!==null','mistake persisted');await b.shot('student-mistake-desktop')
        await b.role('TEACHER',teacher['phone'],PASSWORD,'/teacher/dashboard');await b.click('学生错题本','a');await b.until('document.querySelector("article")!==null','teacher sees own student note');await b.click('布置复习任务');await b.until('document.querySelector("select[name=lesson] option")!==null','real lesson options');await b.click('发布复习任务');await b.until('document.querySelector("article").innerText.includes("复习任务：待提交")','atomic review task');await b.shot('teacher-review-task-desktop')
        await b.nav('/learning/materials');await b.until('document.querySelector("input[type=file]")!==null','teacher materials ready');await file_input(b,'document.querySelector("input[type=file]")','函数讲义.pdf');await b.until('document.body.innerText.includes("函数讲义.pdf")','course file uploaded')
        await file_input(b,'document.querySelector("nur-learning-attachments input[type=file]")','复习提示.pdf');await b.until('document.querySelector("nur-learning-attachments").innerText.includes("复习提示.pdf")','task material uploaded');await b.shot('teacher-files-desktop')
        await b.role('STUDENT',student['phone'],PASSWORD,'/learning/materials');await b.until('document.querySelector("nur-learning-attachments input[type=file]")!==null','student attachment controls');await file_input(b,'document.querySelector("nur-learning-attachments input[type=file]")','我的解答.pdf');await b.until('document.querySelector("nur-learning-attachments").innerText.includes("我的解答.pdf")','student submission file')
        await b.click('填写作业');await b.fill('#material-task-work','横轴截距为-1.5，移项后符号变为负号');await b.click('确认提交作业');await b.until('document.querySelector("article").innerText.includes("待教师反馈")','submit locks attachments');assert await b.js('document.querySelector("nur-learning-attachments input[type=file]")===null'),'submitted attachments frozen'
        await b.viewport(375);await b.shot('student-file-work-mobile375');await b.viewport(1280)
        files,_=api('GET','/learning/files',token=student['token']);f=next(x for x in files['content'] if x['filename']=='我的解答.pdf')
        downloaded=await b.js('(async()=>{const r=await fetch("/api/v1/learning/files/'+f['fileId']+'/download");const bytes=new Uint8Array(await r.arrayBuffer());return {status:r.status,content:[...bytes],cache:r.headers.get("Cache-Control"),disposition:r.headers.get("Content-Disposition")};})()')
        assert downloaded['status']==200 and hashlib.sha256(bytes(downloaded['content'])).hexdigest()==f['sha256'] and 'no-store' in downloaded['cache'] and downloaded['disposition'].startswith('attachment;'),'actual browser download bytes and headers'
        await b.role('TEACHER',teacher['phone'],PASSWORD,'/learning/materials');await b.until('document.querySelector("article").innerText.includes("负号")','teacher sees submission');await b.click('填写反馈');await b.fill('#material-task-work','移项符号已修正，请继续复习');await b.click('保存反馈并完成');await b.until('document.querySelector("article").innerText.includes("已完成")','teacher feedback completes task')
        await b.role('STUDENT',student['phone'],PASSWORD,'/learning/mistakes');await b.until('document.querySelector("article").innerText.includes("复习任务：已完成")','real linked task completion');await b.click('修改错题');assert await b.js('(()=>{const e=document.querySelector("select[name=status]");e.value="MASTERED";e.dispatchEvent(new Event("change",{bubbles:true}));return true;})()');await b.click('保存错题');await b.until('document.querySelector("article").innerText.includes("已掌握")','student confirms mastery');await b.viewport(375);await b.shot('student-reviewed-mistake-mobile375')
        await b.role('PARENT',parent['phone'],PASSWORD,'/parent/dashboard');await b.click('教学资料与作业','a');await b.until('document.querySelector("article")!==null','guardian child files');assert await b.js('document.querySelector("input[type=file]")===null && ![...document.querySelectorAll("button")].some(e=>["填写作业","填写反馈","移除"].includes(e.textContent.trim()))'),'guardian read only';await b.shot('guardian-files-mobile375')
        print('PASS: mistakes, atomic review task, real upload/download, frozen submission, feedback and guardian read-only',flush=True)
if __name__=='__main__':asyncio.run(main())
