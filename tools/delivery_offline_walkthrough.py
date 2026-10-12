"""Sandbox-off capability checks and human offline payment confirmation in an isolated DB."""
import asyncio,http.client,json,pathlib,sys,uuid
from browser_support import Browser
from gap_fill_walkthrough import api
from learning_plan_walkthrough import register,PASSWORD
from delivery_transactions_walkthrough import click
sys.stdout.reconfigure(encoding='utf-8')
ROOT=pathlib.Path(__file__).resolve().parents[1]
class ModeBrowser(Browser):
    async def nav(self,path):
        await self.page('Page.navigate',{'url':'http://localhost:4203'+path});await self.until("document.readyState==='complete'",'mode B page');await asyncio.sleep(.5)
def request(method,path,token=None,body=None):
    conn=http.client.HTTPConnection('127.0.0.1',8083,timeout=20);headers={'Idempotency-Key':str(uuid.uuid4())}
    if token:headers['Authorization']='Bearer '+token
    if body is not None:headers['Content-Type']='application/json'
    conn.request(method,'/api/v1'+path,json.dumps(body) if body is not None else None,headers);r=conn.getresponse();data=r.read();status=r.status;conn.close();return status,json.loads(data) if data else None
async def main():
    s,t,p=register('STUDENT'),register('TEACHER'),register('PARENT')
    assert request('GET','/payment/config',p['token'])[1]['sandboxEnabled'] is False
    assert request('GET','/identity/auth/capabilities')[1]['smsAvailable'] is False
    import datetime
    at=datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(days=4)
    slot=api('POST','/scheduling/availability-slots',{'startAt':at.isoformat(),'endAt':(at+datetime.timedelta(hours=1)).isoformat()},t['token'])[0]
    booking=api('POST','/booking/bookings',{'teacherId':t['id'],'slotId':slot['slotId'],'subject':'线下收款交付验证','bookingType':'FORMAL'},s['token'])[0]
    api('POST','/booking/booking-requests/'+booking['bookingId']+':decide?decision=approve',{},t['token'])
    profile=api('GET','/student/me/profile',token=s['token'])[0];api('POST','/parent/me/children',{'studentNo':profile['studentNo'],'relation':'父亲'},p['token']);api('POST','/parent/me/children:confirm',{},s['token'])
    order=api('POST','/order/orders',{'bookingId':booking['bookingId']},p['token'])[0]
    status,_=request('POST','/payment/payments:initiate',p['token'],{'orderId':order['orderId'],'channel':'MOCK'})
    assert status==409,'disabled sandbox must reject MOCK initiation: '+str(status)
    assert request('GET','/order/orders/'+order['orderId'],p['token'])[1]['status']=='PENDING_PAYMENT'
    before=api('GET','/parent/me/wallet',token=p['token'])[0]['balanceCents']
    async with ModeBrowser(ROOT/'.walk'/'delivery-offline',port=9254) as b:
        await b.role('PARENT',p['phone'],PASSWORD,'/parent/dashboard');await b.until('document.body.innerText.includes('+json.dumps(order['orderNo'])+')','pending order visible in mode B')
        assert not await b.js('[...document.querySelectorAll("button")].some(e=>e.textContent.includes("沙箱演示支付"))')
        assert await b.js('document.body.innerText.includes("模式 B 线下收款")')
        await b.viewport(375);await b.shot('parent-sandbox-off-mobile375');await b.viewport(1280)
        await b.role('ADMIN','13800000000','Admin#2026','/admin/dashboard');await click(b,'线下收款')
        await b.until('document.querySelector("nur-admin-orders").innerText.includes('+json.dumps(order['orderNo'])+')','operator actual pending order')
        assert await b.js('(()=>{const row=[...document.querySelectorAll("nur-admin-orders .order-card")].find(e=>e.innerText.includes('+json.dumps(order['orderNo'])+'));const e=[...row.querySelectorAll("button")].find(e=>e.textContent.includes("确认收款"));if(!e||e.disabled)return false;e.click();return true;})()')
        await b.until('document.querySelector("#confirm-order-title")!==null','offline receipt confirmation dialog')
        assert request('GET','/order/orders/'+order['orderId'],p['token'])[1]['status']=='PENDING_PAYMENT'
        await click(b,'确认已实际到账');await b.until('document.querySelector("#confirm-order-title")===null','offline confirmation completed')
        assert request('GET','/order/orders/'+order['orderId'],p['token'])[1]['status']=='PAID'
        assert api('GET','/parent/me/wallet',token=p['token'])[0]['balanceCents']==before,'mode B must not debit the family wallet'
        await b.shot('admin-offline-confirmed-desktop')
    print('PASS: sandbox=false, MOCK rejected409 and no state change, no sandbox UI, real offline receipt dialog+confirm, PAID with no wallet debit',flush=True)
if __name__=='__main__':asyncio.run(main())
