"""Real owner quota feedback after upload/removal on support, learning, consultation."""
import asyncio, base64, datetime, json, pathlib, sys
from browser_support import Browser
from gap_fill_walkthrough import api
from learning_plan_walkthrough import register, PASSWORD
sys.stdout.reconfigure(encoding='utf-8')
ROOT=pathlib.Path(__file__).resolve().parents[1]
CONTENT='%PDF-1.4\n'+('Real quota acceptance file\n'*80)+'%%EOF'
async def upload(b,selector,name):
    expression='''(()=>{const e=SELECTOR;if(!e||e.disabled)return false;const d=new DataTransfer();d.items.add(new File([CONTENT],NAME,{type:'application/pdf'}));e.files=d.files;e.dispatchEvent(new Event('change',{bubbles:true}));return true;})()'''
    assert await b.js(expression.replace('SELECTOR',selector).replace('CONTENT',json.dumps(CONTENT)).replace('NAME',json.dumps(name)))
async def quota_shot(b,label):
    assert await b.js('document.documentElement.scrollWidth<=document.documentElement.clientWidth'),label+' overflow'
    clip=await b.js('(()=>{const y=Math.max(0,document.querySelector("nur-storage-quota").getBoundingClientRect().top+window.scrollY-20);return {x:0,y,width:document.documentElement.clientWidth,height:Math.min(812,document.documentElement.scrollHeight-y),scale:1};})()')
    (b.output/(label+'.png')).write_bytes(base64.b64decode((await b.page('Page.captureScreenshot',{'format':'png','captureBeyondViewport':True,'clip':clip}))['data']))
    print('PASS: '+label+'; quota region captured; no horizontal overflow',flush=True)
async def main():
    s,t=register('STUDENT'),register('TEACHER');at=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8)))+datetime.timedelta(days=20)
    slot,_=api('POST','/scheduling/availability-slots',{'startAt':at.isoformat(),'endAt':(at+datetime.timedelta(hours=1)).isoformat()},t['token']);course,_=api('POST','/booking/bookings',{'teacherId':t['id'],'slotId':slot['slotId'],'subject':'文件容量数学','bookingType':'FORMAL'},s['token']);api('POST','/booking/booking-requests/'+course['bookingId']+':decide?decision=approve',{},t['token'])
    case,_=api('POST','/support/cases',{'category':'LEARNING','title':'容量反馈验收','description':'核查实际上传、移除与保留容量','bookingId':course['bookingId']},s['token'])
    async with Browser(ROOT/'.walk'/'file-quota',port=9241) as b:
        await b.role('STUDENT',s['phone'],PASSWORD,'/support');await b.until('document.querySelector(".case")!==null','support queue')
        await b.js('document.querySelector(".case").click()');await b.until('document.querySelector("nur-storage-quota progress")!==null','real support capacity')
        assert await b.js('document.querySelector("nur-storage-quota progress").value')==0
        await upload(b,'document.querySelector("#support-detail input[type=file]")','客服容量.pdf');await b.until('document.querySelector("nur-storage-quota progress")?.value>0','capacity refresh after support upload')
        await b.js('[...document.querySelectorAll("#support-detail button")].find(e=>e.textContent.trim()==="移除").click()');await b.until('document.querySelectorAll("#support-detail .file").length===0 && document.querySelector("nur-storage-quota").innerText.includes("已移除但保留：2.1 KB")','removed bytes retained in UI')
        q,_=api('GET','/support/cases/quota',token=s['token']);assert q['storedBytes']==len(CONTENT.encode())==q['removedRetainedBytes']
        await b.viewport(375);await quota_shot(b,'support-removed-retention-mobile375');await b.viewport(1280)
        await b.role('TEACHER',t['phone'],PASSWORD,'/learning/materials');await b.until('document.querySelector("select option")!==null && document.querySelector("input[type=file]")!==null','teacher course files')
        await upload(b,'document.querySelector("input[type=file]")','教学容量.pdf');await b.until('document.querySelector("nur-storage-quota progress")?.value>0','learning owner quota refreshed')
        q,_=api('GET','/learning/files/quota',token=t['token']);assert q['storedBytes']==len(CONTENT.encode()) and q['remainingBytes']==q['limitBytes']-q['storedBytes'];await quota_shot(b,'learning-quota-desktop')
        conversation,_=api('POST','/messaging/conversations',{'teacherId':t['id'],'subject':'文件容量验收'},s['token']);await b.role('STUDENT',s['phone'],PASSWORD,'/messages?conversation='+conversation['conversationId']);await b.until('document.querySelector("nur-storage-quota progress")!==null','consultation quota loaded')
        await upload(b,'document.querySelector("input[type=file]")','咨询容量.pdf');await b.until('document.querySelector("nur-storage-quota progress")?.value>0 && document.body.innerText.includes("附件：咨询容量.pdf")','consultation quota refreshed with actual attachment')
        q,_=api('GET','/messaging/files/quota',token=s['token']);assert q['storedBytes']==len(CONTENT.encode()) and q['removedRetainedBytes']==0
        await b.viewport(375);await quota_shot(b,'consultation-quota-mobile375')
    print('PASS: three real capacities, automatic upload/remove refresh, retained removed bytes, remaining calculation, 375px no overflow')
if __name__=='__main__':asyncio.run(main())
