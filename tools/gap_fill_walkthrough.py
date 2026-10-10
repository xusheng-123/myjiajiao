"""Real browser acceptance for the local gap-fill release. Requires E2E fixtures and isolated DB server on 8080."""
import asyncio, base64, datetime, http.client, json, pathlib, secrets, subprocess, time, urllib.request, uuid, sys
import websockets
sys.stdout.reconfigure(encoding='utf-8')
ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / '.walk' / 'gap-fill'
OUT.mkdir(parents=True, exist_ok=True)
PDF = b'%PDF-1.4\nAcceptance proof\n%%EOF'
def api(method, path, body=None, token=None, raw=None):
    conn = http.client.HTTPConnection('localhost', 8080, timeout=20)
    headers = {'Idempotency-Key': str(uuid.uuid4())}
    if path.startswith('/identity/auth/register:'): headers['X-Forwarded-For']='10.210.31.1'
    if token: headers['Authorization'] = 'Bearer ' + token
    if raw: data, headers['Content-Type'] = raw
    else:
        data = json.dumps(body).encode() if body is not None else None
        if data: headers['Content-Type'] = 'application/json'
    conn.request(method, '/api/v1' + path, data, headers)
    r = conn.getresponse(); payload = r.read(); cookies = r.getheaders(); status = r.status; conn.close()
    assert 200 <= status < 300, f'{method} {path}: {status}'
    return (json.loads(payload) if payload else None), cookies
def login(role, phone, password):
    data, headers = api('POST', '/identity/auth/login:password', {'role': role, 'account': phone, 'password': password})
    cookie = next(v.split(';')[0].split('=',1)[1] for k,v in headers if k.lower()=='set-cookie' and v.startswith('NURTURE_AT='))
    summary = {k: data[k] for k in ('accountNo', 'displayName', 'role', 'expiresAtEpochMs')}
    return data['accessToken'], cookie, summary
def account_id(token): return json.loads(base64.urlsafe_b64decode(token.split('.')[1]+'=='))['sub']
def upload(token):
    boundary = uuid.uuid4().hex
    data=b''
    for field in ('idDocument','degreeCert'):
        data+=f'--{boundary}\r\nContent-Disposition: form-data; name="{field}"; filename="proof.pdf"\r\nContent-Type: application/pdf\r\n\r\n'.encode()+PDF+b'\r\n'
    data+=f'--{boundary}--\r\n'.encode()
    api('POST','/verification/cases',token=token,raw=(data,'multipart/form-data; boundary='+boundary))

async def main():
    admin, _, _ = login('ADMIN','13800000000','Admin#2026')
    subject = '验收数学' + uuid.uuid4().hex[:4]
    teachers=[]
    for n in range(4):
        phone='14'+f'{secrets.randbelow(1000000000):09d}'
        password='Walkthrough#2026'
        data,_=api('POST','/identity/auth/register:teacher',{'displayName':f'验收老师{n+1}','phone':phone,'password':password,'school':'验收师范大学','highestDegree':'硕士','yearsOfTeaching':n+3})
        token=data['accessToken']; tid=account_id(token)
        api('PUT','/teacher/me/profile',{'school':'验收师范大学','highestDegree':'硕士','yearsOfTeaching':n+3,'intro':'以清晰步骤帮助学生建立解题思路。','styleTags':['耐心细致','思维推导']},token)
        api('POST','/teacher/me/subjects',{'subject':subject,'gradeBand':'初中','pricePerHourCents':15000+n*2000},token)
        upload(token)
        cases,_=api('GET','/admin/verification-cases?size=20',token=admin)
        case=next(c for c in cases['content'] if c['teacherId']==tid)
        api('POST',f"/admin/verification-cases/{case['caseId']}:decide",{'approve':True,'note':'本地验收材料'},admin)
        start=(datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8)))+datetime.timedelta(days=21+n)).replace(hour=15,minute=0,second=0,microsecond=0)
        api('POST','/scheduling/availability-slots',{'startAt':start.isoformat(),'endAt':(start+datetime.timedelta(hours=1)).isoformat()},token)
        teachers.append({'phone':phone,'password':password,'token':token,'id':tid})
    proc=subprocess.Popen([r'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe','--headless=new','--remote-debugging-port=9231',
                           '--user-data-dir='+str(OUT/'browser-profile'),'--no-first-run','--window-size=1280,1000','about:blank'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    try:
        ws_url=None
        for _ in range(40):
            try:
                with urllib.request.urlopen('http://localhost:9231/json/version',timeout=2) as r: ws_url=json.load(r)['webSocketDebuggerUrl']
                break
            except Exception: await asyncio.sleep(.3)
        assert ws_url, 'Browser did not start'
        async with websockets.connect(ws_url,max_size=64*1024*1024) as ws:
            seq=0
            async def send(method,params=None,sid=None):
                nonlocal seq
                seq+=1; ident=seq
                msg={'id':ident,'method':method,'params':params or {}}
                if sid: msg['sessionId']=sid
                await ws.send(json.dumps(msg))
                while True:
                    answer=json.loads(await asyncio.wait_for(ws.recv(),30))
                    if answer.get('id')==ident:
                        assert 'error' not in answer, answer.get('error')
                        return answer.get('result',{})
            target=await send('Target.createTarget',{'url':'about:blank'})
            sid=(await send('Target.attachToTarget',{'targetId':target['targetId'],'flatten':True}))['sessionId']
            async def page(method,params=None): return await send(method,params,sid)
            async def js(expression):
                r=await page('Runtime.evaluate',{'expression':expression,'returnByValue':True,'awaitPromise':True})
                assert 'exceptionDetails' not in r, r.get('exceptionDetails')
                return r.get('result',{}).get('value')
            async def until(expression,description):
                for _ in range(100):
                    if await js(expression): return
                    await asyncio.sleep(.15)
                print(await js('JSON.stringify({url:location.href,text:document.body.innerText.slice(0,3000)})'), flush=True)
                (OUT/'failure.png').write_bytes(base64.b64decode((await page('Page.captureScreenshot',{'format':'png'}))['data']))
                raise AssertionError(description)
            async def nav(path):
                await page('Page.navigate',{'url':'http://localhost:4200'+path})
                await until("document.readyState === 'complete'",'page loaded')
                await asyncio.sleep(.5)
            async def role(role,phone,password,path):
                token,cookie,summary=login(role,phone,password)
                await page('Network.setCookie',{'name':'NURTURE_AT','value':cookie,'domain':'localhost','path':'/api','httpOnly':True})
                await nav('/')
                await js('localStorage.setItem("nurture.session",'+json.dumps(json.dumps(summary))+')')
                await nav(path)
                return token
            async def click_text(text,selector='button'):
                expression='(()=>{const b=[...document.querySelectorAll('+json.dumps(selector)+')].find(b=>b.textContent.trim().includes('+json.dumps(text)+')); if(!b || b.disabled) return false; b.click(); return true;})()'
                assert await js(expression), 'button: '+text
                await asyncio.sleep(.25)
            async def fill(selector,value):
                assert await js('(()=>{const e=document.querySelector('+json.dumps(selector)+'); if(!e)return false; e.value='+json.dumps(value)+'; e.dispatchEvent(new Event("input",{bubbles:true})); return true;})()')
            async def shot(label):
                metrics=await js('({width:innerWidth, scroll:document.documentElement.scrollWidth, client:document.documentElement.clientWidth})')
                assert metrics['scroll'] <= metrics['client'], (label,metrics)
                (OUT/(label+'.png')).write_bytes(base64.b64decode((await page('Page.captureScreenshot',{'format':'png'}))['data']))
                print('PASS: '+label+'; no horizontal overflow',flush=True)
            await page('Page.enable'); await page('Runtime.enable'); await page('Network.enable')
            await send('Browser.setDownloadBehavior',{'behavior':'allow','downloadPath':str(OUT/'downloads')})
            await nav('/teachers')
            await until('document.querySelectorAll("nur-teacher-card").length > 0','public real listing')
            assert await js('document.body.innerText.includes("验收老师")'), 'actual teacher names'
            await nav('/teachers/'+teachers[0]['id'])
            await until('document.body.innerText.includes("验收师范大学")','teacher detail')
            await shot('teacher-detail-desktop')
            student=await role('STUDENT','13900000002','Student#2026','/student/match?subject='+urllib.parse.quote(subject))
            # pg_trgm intentionally returns similar subjects as well as exact matches.
            await until('document.querySelectorAll(".candidate-card").length >= 4','at least four real candidates')
            for i in range(4):
                assert await js(f'(()=>{{ const c=document.querySelectorAll(".candidate-card")[{i}]; const b=[...c.querySelectorAll("button")].find(b=>b.textContent.includes("加入对比")); b.click(); return true; }})()')
                await asyncio.sleep(.2)
            assert await js('document.querySelectorAll("nur-teacher-compare thead th").length === 4 && document.body.innerText.includes("最多对比 3")')
            await shot('compare-desktop')
            await page('Emulation.setDeviceMetricsOverride',{'width':375,'height':812,'deviceScaleFactor':1,'mobile':True})
            await shot('compare-mobile375')
            await page('Emulation.setDeviceMetricsOverride',{'width':1280,'height':1000,'deviceScaleFactor':1,'mobile':False})
            fixture_card='[...document.querySelectorAll(".candidate-card")].find(c=>c.querySelector("h2").textContent.trim()==='+json.dumps('验收老师1 · '+subject)+')'
            assert await js('(()=>{const c='+fixture_card+';if(!c)return false;[...c.querySelectorAll("button")].find(b=>b.textContent.includes("预约正式课")).click();return true;})()')
            await until('document.querySelector(".slot-btn") !== null','formal available slot')
            await js('document.querySelector(".slot-btn").click()'); await click_text('下一步')
            assert await js('document.body.innerText.includes("本次提交不扣款")')
            await click_text('确认预约')
            await until('document.body.innerText.includes("等待教师确认") && document.querySelector(".form-status--success") !== null','formal pending success')
            bookings,_=api('GET','/student/me/upcoming-lessons',token=student)
            if isinstance(bookings,dict): bookings=bookings.get('content',bookings.get('items',[]))
            booking=next(b for b in bookings if b.get('teacherId')==teachers[0]['id'] and b.get('type')=='FORMAL')
            bid=booking['bookingId']; assert booking['status']=='REQUESTED'
            print('PASS: formal booking made by UI; pending teacher approval; no payment',flush=True)
            await click_text('好的，回到列表')
            trial_teacher=teachers[1]
            trial_slots,_=api('GET','/scheduling/teachers/'+trial_teacher['id']+'/open-slots',token=student)
            trial,_=api('POST','/booking/bookings',{'teacherId':trial_teacher['id'],'slotId':trial_slots[0]['slotId'],'subject':subject,'bookingType':'TRIAL'},student)
            trial_card='[...document.querySelectorAll(".candidate-card")].find(c=>c.querySelector("h2").textContent.trim()==='+json.dumps('验收老师2 · '+subject)+')'
            async def open_trial():
                assert await js('(()=>{const c='+trial_card+';if(!c)return false;[...c.querySelectorAll("button")].find(b=>b.textContent.includes("预约试听")).click();return true;})()')
            await open_trial()
            await until('document.body.innerText.includes("已有该老师的试听预约")','reserved quota shown')
            assert await js('[...document.querySelectorAll("button")].find(b=>b.textContent.includes("下一步：确认信息")).disabled')
            await shot('trial-quota-reserved-desktop')
            await page('Emulation.setDeviceMetricsOverride',{'width':375,'height':812,'deviceScaleFactor':1,'mobile':True})
            await shot('trial-quota-reserved-mobile375')
            await page('Emulation.setDeviceMetricsOverride',{'width':1280,'height':1000,'deviceScaleFactor':1,'mobile':False})
            await click_text('取消')
            preview,_=api('POST','/booking/bookings/'+trial['bookingId']+':cancel/preview',{'reason':'开课前取消试听'},student)
            api('POST','/booking/bookings/'+trial['bookingId']+':cancel/execute',{'reason':'开课前取消试听','confirmationToken':preview['confirmationToken']},student)
            await open_trial()
            await until('document.body.innerText.includes("可使用一次免费试听") && document.querySelector(".slot-btn") !== null','early cancellation releases allowance')
            await js('document.querySelector(".slot-btn").click()'); await click_text('下一步'); await click_text('确认预约')
            await until('document.querySelector(".form-status--success") !== null','trial booking success after cancellation')
            await shot('trial-quota-rebook-success')
            await click_text('好的，回到列表')
            rows,_=api('GET','/student/me/upcoming-lessons',token=student)
            live_trial=next(b for b in rows if b['teacherId']==trial_teacher['id'] and b['type']=='TRIAL')
            api('POST','/booking/bookings/'+live_trial['bookingId']+':start',{},trial_teacher['token'])
            api('POST','/booking/bookings/'+live_trial['bookingId']+':complete',{},trial_teacher['token'])
            await open_trial()
            await until('document.body.innerText.includes("已使用该老师的免费试听")','used quota permanently shown')
            await click_text('改约正式课')
            await until('document.body.innerText.includes("预约正式课")','quota does not prevent formal switch')
            await click_text('取消')
            print('PASS: quota UI blocks duplicate, allows early cancellation/rebooking, preserves consumed limit and offers FORMAL',flush=True)
            assert await js('(()=>{const c='+fixture_card+';[...c.querySelectorAll("button")].find(b=>b.textContent.includes("课前咨询")).click();return true;})()')
            await until('location.pathname === "/messages" && document.querySelector("#message-draft") !== null','consultation route')
            await fill('#message-draft','希望每节课先拆解三个学习目标。'); await click_text('发送', 'button[type=submit]')
            await until('document.querySelector(".messages").innerText.includes("三个学习目标")','message sent')
            await shot('consultation-student-desktop')
            conversation=(api('GET','/messaging/conversations',token=teachers[0]['token'])[0])[0]['conversationId']
            await role('TEACHER',teachers[0]['phone'],teachers[0]['password'],'/messages?conversation='+conversation)
            await until('document.querySelector("#message-draft") !== null','teacher conversation opens')
            await fill('#message-draft','可以，我们会分步骤完成。'); await click_text('发送','button[type=submit]')
            await until('document.querySelector(".messages").innerText.includes("分步骤完成")','teacher replies')
            await nav('/teacher/dashboard')
            await until('document.body.innerText.includes("完善公开资料")','teacher profile editor')
            await click_text('完善公开资料')
            await fill('#profile-intro','课程按三步任务推进，并保留提问时间。')
            await fill('#profile-tags','耐心细致，思维推导，启发式提问')
            await shot('teacher-profile-editor-desktop')
            await click_text('保存资料')
            await until('document.body.innerText.includes("公开资料已保存")','public profile saved')
            detail,_=api('GET','/matching/public/teachers/'+teachers[0]['id'])
            assert detail['intro']=='课程按三步任务推进，并保留提问时间。' and '启发式提问' in detail['styleTags']
            print('PASS: teacher edits public profile and public API reflects actual saved data',flush=True)
            await role('STUDENT','13900000002','Student#2026','/messages?conversation='+conversation)
            await until('document.querySelector(".messages")?.innerText.includes("分步骤完成")','student receives reply')
            assert await js('document.querySelector(".messages").innerText.includes("对方已读")')
            await page('Emulation.setDeviceMetricsOverride',{'width':375,'height':812,'deviceScaleFactor':1,'mobile':True}); await shot('consultation-mobile375')
            api('POST',f'/booking/booking-requests/{bid}:decide?decision=approve',{},teachers[0]['token'])
            parent,_,_=login('PARENT','13900000003','Parent#2026')
            order,_=api('POST','/order/orders',{'bookingId':bid},parent)
            oid=order['orderId']; api('POST',f'/admin/orders/{oid}:confirm-offline-payment',{},admin)
            await nav('/student/dashboard'); await until('document.body.innerText.includes("展开全部")','student dashboard')
            await click_text('展开全部')
            await until('[...document.querySelectorAll(".schedule-list li")].some(li=>li.querySelector(".schedule-subject")?.textContent.trim()==='+json.dumps(subject)+' && li.textContent.includes("已支付"))','student sees parent payment on course')
            assert await js('![...document.querySelectorAll(".schedule-list button")].some(b=>b.textContent.includes("下单"))')
            await shot('student-parent-paid-mobile375')
            await nav('/refunds'); await until('document.body.innerText.includes('+json.dumps(order['orderNo'])+')','student sees parent-paid course')
            await click_text('申请退款'); await fill('#refund-reason','本地验收：调整学习安排。'); await click_text('确认提交申请')
            await until('document.body.innerText.includes("申请已提交") && document.body.innerText.includes("待审核")','refund submitted by UI')
            await shot('refund-mobile375')
            cases,_=api('GET','/refund/me/cases?size=50',token=student)
            case=next(c for c in cases['content'] if c['orderId']==oid)
            await page('Emulation.setDeviceMetricsOverride',{'width':1280,'height':1000,'deviceScaleFactor':1,'mobile':False})
            await role('ADMIN','13800000000','Admin#2026','/admin/dashboard')
            await until('document.body.innerText.includes('+json.dumps(oid[:8])+')','operator sees actual refund case')
            refund_card='[...document.querySelectorAll(".case-item")].find(c=>c.textContent.includes('+json.dumps(oid[:8])+'))'
            assert await js('(()=>{const c='+refund_card+';[...c.querySelectorAll("button")].find(b=>b.textContent.includes("评估影响")).click();return true;})()')
            await until('document.querySelector(".impact-box")?.innerText.includes("红冲")','operator previews actual reversal impact')
            assert await js('(()=>{const c='+refund_card+';[...c.querySelectorAll("button")].find(b=>b.textContent.includes("执行退款")).click();return true;})()')
            await until('document.body.innerText.includes("退款已执行")','operator executes refund through UI')
            await role('STUDENT','13900000002','Student#2026','/refunds')
            await page('Emulation.setDeviceMetricsOverride',{'width':375,'height':812,'deviceScaleFactor':1,'mobile':True})
            await until('document.body.innerText.includes("已退回付款人钱包")','refund completion visible')
            await shot('refund-complete-mobile375')
            await page('Emulation.setDeviceMetricsOverride',{'width':1280,'height':1000,'deviceScaleFactor':1,'mobile':False})
            await role('ADMIN','13800000000','Admin#2026','/admin/dashboard')
            await until('document.body.innerText.includes("下载身份证明")','admin materials buttons')
            await click_text('下载身份证明')
            await until('document.body.innerText.includes("材料已下载")','admin download success')
            for _ in range(50):
                files=list((OUT/'downloads').glob('identity-*.pdf'))
                if files: break
                await asyncio.sleep(.1)
            assert files and files[0].read_bytes()==PDF, 'downloaded private fixture equals uploaded proof'
            await nav('/auth'); await until('document.body.innerText.includes("短信登录暂未开通")','SMS honesty')
            assert await js('!!document.querySelector("#password") && [...document.querySelectorAll("button")].find(b=>b.textContent.trim()==="验证码登录").disabled')
            await shot('auth-password-fallback-desktop')
            print('PASS: public details, 3-teacher compare limit, formal UI booking, bidirectional consultation/read receipts, student refund of parent-paid order, private admin download, password fallback',flush=True)
    finally: proc.terminate()

if __name__=='__main__': asyncio.run(main())
