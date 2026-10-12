"""Real public booking, teacher approval, family payment and two-stage refund UI."""
import asyncio,datetime,json,pathlib,sys,uuid
from browser_support import Browser
from gap_fill_walkthrough import api,upload,login,PDF
from learning_plan_walkthrough import register,PASSWORD
from delivery_transactions_walkthrough import click
sys.stdout.reconfigure(encoding='utf-8')
ROOT=pathlib.Path(__file__).resolve().parents[1]
async def main():
    s,t,p=register('STUDENT'),register('TEACHER'),register('PARENT');subject='交付'+uuid.uuid4().hex[:20]
    admin,_,_=login('ADMIN','13800000000','Admin#2026')
    api('POST','/teacher/me/subjects',{'subject':subject,'gradeBand':'初中','pricePerHourCents':20000},t['token']);upload(t['token'])
    pending=api('GET','/admin/verification-cases?status=SUBMITTED&size=50',token=admin)[0]['content'];case=next(x for x in pending if x['teacherId']==t['id'])
    api('POST','/admin/verification-cases/'+case['caseId']+':decide',{'approve':True,'note':'隔离交付浏览器验证'},admin)
    at=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8)))+datetime.timedelta(days=2)
    api('POST','/scheduling/availability-slots',{'startAt':at.isoformat(),'endAt':(at+datetime.timedelta(hours=1)).isoformat()},t['token'])
    profile=api('GET','/student/me/profile',token=s['token'])[0];api('POST','/parent/me/children',{'studentNo':profile['studentNo'],'relation':'母亲'},p['token']);api('POST','/parent/me/children:confirm',{},s['token'])
    async with Browser(ROOT/'.walk'/'delivery-purchase',port=9253) as b:
        await b.role('STUDENT',s['phone'],PASSWORD,'/student/match?subject='+subject)
        await b.until('document.querySelector(".candidate-card")!==null','public teacher candidate')
        await click(b,'预约正式课','.candidate-card button');await b.until('document.querySelector(".slot-card-btn")!==null','real open availability')
        await b.js('document.querySelector(".slot-card-btn").click()');await click(b,'下一步：确认预约信息 →')
        assert await b.js('document.body.innerText.includes("本次提交不扣款")');await click(b,'确认提交正式课申请')
        await b.until('document.querySelector(".success-card")!==null','formal request success')
        bookings=api('GET','/booking/me/bookings?size=50',token=s['token'])[0]['content'];booking=next(x for x in bookings if x['teacherId']==t['id']);bid=booking['bookingId'];assert booking['status']=='REQUESTED'
        await b.shot('student-booking-from-ui-desktop')
        await b.role('TEACHER',t['phone'],PASSWORD,'/teacher/dashboard');await b.until('document.querySelector("nur-booking-requests-card .request-item")!==null','teacher approval request')
        assert await b.js('(()=>{const row=document.querySelector("nur-booking-requests-card .request-item");const e=[...row.querySelectorAll("button")].find(e=>e.textContent.includes("确认") || e.textContent.includes("通过"));if(!e||e.disabled)return false;e.click();return true;})()')
        await b.until('document.querySelector("nur-booking-requests-card .action-banner")!==null','teacher approval feedback')
        assert api('GET','/booking/bookings/'+bid,token=s['token'])[0]['status']=='CONFIRMED'
        await b.role('PARENT',p['phone'],PASSWORD,'/parent/dashboard');await b.until('document.querySelector("#child-select")!==null','family course selection')
        await b.js('(()=>{const e=document.querySelector("#child-select");e.value=e.options[e.options.length-1].value;e.dispatchEvent(new Event("change",{bubbles:true}));})()')
        await click(b,'查看可下单课程')
        await b.until('[...document.querySelectorAll("button")].some(e=>e.textContent.trim()==="生成订单")','owned orderable course')
        await click(b,'+ 充 ¥1000');await b.until('document.querySelector("section[aria-labelledby=wallet-title]").innerText.includes("1,000") || document.querySelector("section[aria-labelledby=wallet-title]").innerText.includes("1000")','sandbox wallet topup')
        await click(b,'生成订单');await b.until('[...document.querySelectorAll("button")].some(e=>e.textContent.trim()==="沙箱演示支付")','family order created')
        orders=api('GET','/order/me/orders',token=p['token'])[0];orders=orders.get('content',[]) if isinstance(orders,dict) else orders;order=next(x for x in orders if x['bookingId']==bid);oid=order['orderId'];assert order['amountCents']==20000
        await click(b,'沙箱演示支付');await b.until('document.body.innerText.includes("已支付")','actual sandbox payment')
        assert api('GET','/order/orders/'+oid,token=p['token'])[0]['status']=='PAID'
        before=api('GET','/parent/me/wallet',token=p['token'])[0]['balanceCents']
        await b.viewport(375);await b.shot('parent-paid-mobile375');await b.viewport(1280)
        await b.role('STUDENT',s['phone'],PASSWORD,'/refunds');await b.until('document.body.innerText.includes('+json.dumps(order['orderNo'])+')','student parent-paid order visible')
        await click(b,'申请退款');await b.fill('#refund-reason-input','隔离交付验收：取消后续课程');await click(b,'下一步：确认信息 →');await click(b,'确认提交申请')
        await b.until('document.querySelector(".modal-backdrop")===null','refund application submitted')
        cases=api('GET','/refund/me/cases?size=50',token=s['token'])[0]['content'];refund=next(x for x in cases if x['orderId']==oid)
        await b.role('ADMIN','13800000000','Admin#2026','/admin/dashboard');await click(b,'退款仲裁')
        await b.until('document.querySelector("nur-admin-refunds .case-card")!==null','refund queue')
        selector='[...document.querySelectorAll("nur-admin-refunds .case-card")].find(e=>e.innerText.includes('+json.dumps(oid[:8])+'))'
        assert await b.js('(()=>{const row='+selector+';if(!row)return false;[...row.querySelectorAll("button")].find(e=>e.textContent.includes("评估影响")).click();return true;})()')
        await b.until('document.querySelector(".impact-panel")!==null','refund impact preview')
        assert api('GET','/parent/me/wallet',token=p['token'])[0]['balanceCents']==before,'preview has no money side effects'
        assert await b.js('(()=>{const row='+selector+';const e=[...row.querySelectorAll("button")].find(e=>e.textContent.includes("执行退款入账"));if(!e||e.disabled)return false;e.click();return true;})()')
        await b.until('document.querySelector("nur-admin-refunds .modal-backdrop")!==null','final refund confirmation')
        assert await b.js('(()=>{const e=[...document.querySelectorAll("nur-admin-refunds .modal-backdrop button")].find(e=>e.textContent.includes("确认") && !e.textContent.includes("驳回"));if(!e||e.disabled)return false;e.click();return true;})()')
        await b.until('document.querySelector("nur-admin-refunds .modal-backdrop")===null','refund executed')
        assert api('GET','/parent/me/wallet',token=p['token'])[0]['balanceCents']==before+20000
        assert api('GET','/order/orders/'+oid,token=p['token'])[0]['status']=='CLOSED'
        await b.shot('admin-refund-executed-desktop')
        await b.role('STUDENT',s['phone'],PASSWORD,'/refunds');await b.until('document.body.innerText.includes("已退回") || document.body.innerText.includes("已退款")','refund result visible')
        await b.viewport(375);await b.shot('student-refund-result-mobile375')
        await b.role('ADMIN','13800000000','Admin#2026','/admin/dashboard')
        response=await b.js('(async()=>{const r=await fetch("/api/v1/admin/verification-cases/'+case['caseId']+'/materials/identity");return {status:r.status,bytes:[...new Uint8Array(await r.arrayBuffer())],cache:r.headers.get("Cache-Control")};})()')
        assert response['status']==200 and bytes(response['bytes'])==PDF and 'no-store' in response['cache']
    print('PASS: real UI formal booking/teacher approval/family order+200.00 sandbox payment/student refund/admin preview+confirm/wallet refunded exactly once/private verification download',flush=True)
if __name__=='__main__':asyncio.run(main())
