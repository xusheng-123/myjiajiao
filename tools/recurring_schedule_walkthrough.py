"""Stage5 actual teacher conflict/exception UI and three-role ICS export."""
import asyncio,datetime,json,pathlib,sys
from browser_support import Browser
from gap_fill_walkthrough import api
from learning_plan_walkthrough import register,PASSWORD
sys.stdout.reconfigure(encoding='utf-8')
ROOT=pathlib.Path(__file__).resolve().parents[1]
async def ics(b,query=''):
    result=await b.js('(async()=>{const r=await fetch("/api/v1/booking/calendar.ics'+query+'");return {status:r.status,text:await r.text(),cache:r.headers.get("Cache-Control"),type:r.headers.get("Content-Type")};})()')
    assert result['status']==200 and result['text'].endswith('END:VCALENDAR\r\n') and 'no-store' in result['cache'] and 'text/calendar' in result['type']
    return result['text']
async def main():
    student,teacher,parent=register('STUDENT'),register('TEACHER'),register('PARENT')
    date=datetime.date.today()+datetime.timedelta(days=14)
    at=datetime.datetime.combine(date,datetime.time(15),datetime.timezone(datetime.timedelta(hours=8)))
    api('POST','/scheduling/availability-slots',{'startAt':at.isoformat(),'endAt':(at+datetime.timedelta(hours=1)).isoformat()},teacher['token'])
    async with Browser(ROOT/'.walk'/'recurring-schedule',port=9235) as b:
        await b.role('TEACHER',teacher['phone'],PASSWORD,'/teacher/dashboard');await b.click('重复档期与日历','a');await b.until('document.querySelector("#series-date")!==null','schedule entry')
        await b.fill('#series-date',date.isoformat());await b.fill('#series-weeks','3');await b.click('预览逐次冲突');await b.until('document.querySelector("#series-preview")!==null','preview actual conflict')
        assert await b.js('document.querySelector("#series-preview").innerText.includes("有冲突") && [...document.querySelectorAll("button")].find(e=>e.textContent.trim()==="确认发布 3 个档期").disabled'),'conflict blocks publish'
        await b.shot('teacher-conflict-desktop')
        assert await b.js('(()=>{const e=document.querySelector("#series-preview input[type=checkbox]");e.checked=true;e.dispatchEvent(new Event("change",{bubbles:true}));return true;})()')
        assert await b.js('(()=>{const e=[...document.querySelectorAll("#series-preview .milestone")][1].querySelector("input[type=time]");e.value="17:00";e.dispatchEvent(new Event("change",{bubbles:true}));return true;})()')
        await b.click('重新预览');await b.until('document.body.innerText.includes("预览通过")','skip and time exception');await b.viewport(375);await b.shot('teacher-exceptions-mobile375');await b.viewport(1280)
        await b.click('确认发布 2 个档期');await b.until('document.querySelector("#series-created")!==null','finite series publication');await b.shot('teacher-published-desktop')
        groups,_=api('GET','/scheduling/series',token=teacher['token']);assert groups['totalElements']==1
        series,_=api('GET','/scheduling/series/'+groups['content'][0]['seriesId'],token=teacher['token']);assert len(series['slots'])==2 and datetime.datetime.fromisoformat(series['slots'][0]['startAt']).astimezone(datetime.timezone(datetime.timedelta(hours=8))).hour==17
        await b.nav('/scheduling/series');await b.until('[...document.querySelectorAll("button")].some(e=>e.textContent.trim()==="查看这组档期")','series survives reload');await b.click('查看这组档期');await b.until('document.querySelector("#series-created")!==null','reload group management');await b.viewport(375);await b.shot('teacher-reloaded-mobile375')
        await b.click('导出未来30天日历');await b.until('document.body.innerText.includes("日历文件已导出")','teacher calendar download UI');assert (await ics(b)).count('BEGIN:VEVENT')==3
        booking,_=api('POST','/booking/bookings',{'teacherId':teacher['id'],'slotId':series['slots'][0]['slotId'],'subject':'数学','bookingType':'FORMAL'},student['token']);api('POST','/booking/booking-requests/'+booking['bookingId']+':decide?decision=approve',{},teacher['token'])
        profile,_=api('GET','/student/me/profile',token=student['token']);api('POST','/parent/me/children',{'studentNo':profile['studentNo'],'relation':'母亲'},parent['token']);api('POST','/parent/me/children:confirm',{},student['token'])
        await b.role('STUDENT',student['phone'],PASSWORD,'/student/dashboard');await b.click('导出未来30天日历');await b.until('document.body.innerText.includes("日历文件已导出")','student export UI');text=await ics(b);assert text.count('BEGIN:VEVENT')==1 and booking['bookingId'] in text and 'STATUS:CONFIRMED' in text;await b.shot('student-calendar-mobile375')
        await b.role('PARENT',parent['phone'],PASSWORD,'/parent/dashboard');await b.until('[...document.querySelectorAll("button")].some(e=>e.textContent.trim()==="导出未来30天日历")','guardian child calendar entry');await b.click('导出未来30天日历');await b.until('document.body.innerText.includes("日历文件已导出")','guardian export UI');children,_=api('GET','/parent/me/children',token=parent['token']);text=await ics(b,'?childProfileId='+children[0]['studentId']);assert text.count('BEGIN:VEVENT')==1 and booking['bookingId'] in text;await b.shot('guardian-calendar-mobile375')
        print('PASS: per-occurrence conflicts, skip/time exception, publish, reload and three-role ICS export',flush=True)
if __name__=='__main__':asyncio.run(main())
