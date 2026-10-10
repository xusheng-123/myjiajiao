"""Small CDP browser helper for local acceptance; uses actual pages and APIs."""
import asyncio, base64, json, pathlib, subprocess, urllib.request
import websockets
from gap_fill_walkthrough import login

class Browser:
    def __init__(self, output, port=9233):
        self.output=pathlib.Path(output); self.output.mkdir(parents=True,exist_ok=True)
        self.port=port; self.sequence=0
    async def __aenter__(self):
        self.process=subprocess.Popen([r'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
            '--headless=new',f'--remote-debugging-port={self.port}','--user-data-dir='+str(self.output/'browser-profile'),
            '--no-first-run','--window-size=1280,1000','about:blank'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        endpoint=None
        for _ in range(60):
            try:
                with urllib.request.urlopen(f'http://localhost:{self.port}/json/version',timeout=1) as r: endpoint=json.load(r)['webSocketDebuggerUrl']
                break
            except Exception: await asyncio.sleep(.2)
        assert endpoint,'Browser did not start'
        self.websocket=await websockets.connect(endpoint,max_size=64*1024*1024)
        target=await self.send('Target.createTarget',{'url':'about:blank'})
        self.session=(await self.send('Target.attachToTarget',{'targetId':target['targetId'],'flatten':True}))['sessionId']
        for method in ('Page.enable','Runtime.enable','Network.enable'): await self.page(method)
        return self
    async def __aexit__(self,*args):
        await self.websocket.close(); self.process.terminate()
    async def send(self,method,params=None,session=None):
        self.sequence+=1; ident=self.sequence; data={'id':ident,'method':method,'params':params or {}}
        if session: data['sessionId']=session
        await self.websocket.send(json.dumps(data))
        while True:
            r=json.loads(await asyncio.wait_for(self.websocket.recv(),30))
            if r.get('id')==ident:
                assert 'error' not in r,r.get('error'); return r.get('result',{})
    async def page(self,method,params=None): return await self.send(method,params,self.session)
    async def js(self,expression):
        r=await self.page('Runtime.evaluate',{'expression':expression,'returnByValue':True,'awaitPromise':True})
        assert 'exceptionDetails' not in r,r.get('exceptionDetails')
        return r.get('result',{}).get('value')
    async def until(self,expression,label):
        for _ in range(100):
            if await self.js(expression): return
            await asyncio.sleep(.15)
        (self.output/'failure.png').write_bytes(base64.b64decode((await self.page('Page.captureScreenshot',{'format':'png'}))['data']))
        raise AssertionError(label+': '+str(await self.js('document.body.innerText.slice(0,2200)')))
    async def nav(self,path):
        await self.page('Page.navigate',{'url':'http://localhost:4200'+path})
        await self.until("document.readyState === 'complete'",'page loaded'); await asyncio.sleep(.4)
    async def role(self,role,phone,password,path):
        token,cookie,summary=login(role,phone,password)
        await self.page('Network.setCookie',{'name':'NURTURE_AT','value':cookie,'domain':'localhost','path':'/api','httpOnly':True})
        await self.nav('/'); await self.js('localStorage.setItem("nurture.session",'+json.dumps(json.dumps(summary))+')')
        await self.nav(path); return token
    async def click(self,text,selector='button'):
        assert await self.js('(()=>{const e=[...document.querySelectorAll('+json.dumps(selector)+')].find(e=>e.textContent.trim()==='+json.dumps(text)+');if(!e||e.disabled)return false;e.click();return true;})()'),'button: '+text
        await asyncio.sleep(.2)
    async def fill(self,selector,value):
        assert await self.js('(()=>{const e=document.querySelector('+json.dumps(selector)+');if(!e)return false;e.value='+json.dumps(value)+';e.dispatchEvent(new Event("input",{bubbles:true}));return true;})()'),'field: '+selector
    async def viewport(self,width):
        await self.page('Emulation.setDeviceMetricsOverride',{'width':width,'height':812 if width==375 else 1000,'deviceScaleFactor':1,'mobile':width==375})
    async def shot(self,label):
        assert await self.js('document.documentElement.scrollWidth<=document.documentElement.clientWidth'),label+' horizontal overflow'
        (self.output/(label+'.png')).write_bytes(base64.b64decode((await self.page('Page.captureScreenshot',{'format':'png'}))['data']))
        print('PASS: '+label+'; no horizontal overflow',flush=True)
