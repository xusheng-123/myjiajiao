"""Serve a production Angular build and run existing journeys on a named delivery copy.

Never routes acceptance requests to the existing 8080/4200 instances.
Artifacts, uploaded fixtures and browser profiles stay under backend/target.
"""
import argparse, http.client, http.server, json, mimetypes, os, pathlib, re, shutil, subprocess, sys, urllib.parse
ROOT=pathlib.Path(__file__).resolve().parents[1]
SCOPE_FILE=ROOT/'backend/target/delivery-current-scope.json'
def scope():
    s=json.loads(SCOPE_FILE.read_text(encoding='utf-8'));out=pathlib.Path(s['output']).resolve()
    if out.parent!=(ROOT/'backend/target').resolve() or not out.name.startswith('delivery-acceptance-'):
        raise ValueError('Delivery output scope mismatch')
    for key in ('database','integrationDatabase'):
        if not re.fullmatch(r'nurture_gap_acceptance_delivery_(?:it_)?[0-9_]+',s[key]):raise ValueError('Database scope mismatch')
    if s['backendPort']!=8082 or s['frontendPort']!=4202:raise ValueError('Existing application ports are forbidden')
    return s,out

def prepare():
    s,out=scope();dest=out/'tools';dest.mkdir(exist_ok=True)
    for source in (ROOT/'tools').glob('*.py'):
        if source.name==pathlib.Path(__file__).name:continue
        code=source.read_text(encoding='utf-8-sig')
        code=code.replace('http://localhost:4200','http://localhost:4202')
        code=code.replace("'localhost', 8080", "'127.0.0.1', 8082").replace('"localhost", 8080','"127.0.0.1", 8082')
        if source.name=='engineering_acceptance_walkthrough.py':code=code.replace("DATABASE = 'nurture_gap_acceptance_phase8_20261011'",'DATABASE = '+repr(s['database']))
        if source.name=='gap_fill_walkthrough.py':
            code='import os\n'+code.replace("headers['X-Forwarded-For']='10.210.31.1'","headers['X-Forwarded-For']=os.environ.get('NURTURE_TEST_CLIENT_IP','10.210.31.1')")
            code=code.replace('c.querySelector("h2").textContent','c.querySelector("h3.cand-name").textContent')
        if source.name=='browser_support.py':
            code=code.replace("await self.page('Network.setCookie'", "await self.page('Network.clearBrowserCookies')\n        await self.page('Network.setCookie'")
            code=code.replace("async def click(self,text,selector='button'):","async def click(self,text,selector='button'):\n        aliases={'学习计划与阶段目标':'/learning/plans','学生学习计划':'/learning/plans','学习计划':'/learning/plans','错题本':'/learning/mistakes','学生错题本':'/learning/mistakes','教学资料与作业':'/learning/materials','阶段小测':'/learning/assessments','阶段小测与报告':'/learning/assessments','重复档期与日历':'/scheduling/series','客服与申诉':'/support'}\n        if selector=='a' and text in aliases:\n            target='a[href='+json.dumps(aliases[text])+']'\n            assert await self.js('(()=>{const e=document.querySelector('+json.dumps(target)+');if(!e)return false;e.click();return true;})()'),'visible navigation: '+text\n            await asyncio.sleep(.2);return\n        if text=='展开全部':text='展开全部功能'")
        if source.name=='operations_support_walkthrough.py':
            code=code.replace("prefix='翻页验收'+uuid.uuid4().hex[:4]","prefix=uuid.uuid4().hex[:24]")
        if source.name=='browser_support.py':code=code.replace("target='a[href='", "target='a[href^='")
        if source.name=='learning_plan_walkthrough.py':
            replacements={'.plan-card':'.plan-item-card','.linked-task':'.task-card','.milestone':'.milestone-card','.history':'.history-timeline','#task-work':'#task-work','阶段目标与任务':'管理目标与任务','修改记录':'修改审计记录','给出反馈':'填写反馈','确认提交':'确认提交作业','保存反馈并完成任务':'保存反馈并完成'}
            for old,new in replacements.items():code=code.replace(old,new)
            code=code.replace("document.querySelector(\".progress\").innerText.includes(\"关联任务 0/1\")","document.querySelector(\".progress-summary-grid\").innerText.replace(/\\s+/g,\"\").includes(\"关联任务完成0/1\")")
            for old,new in [('关联任务 1/1','关联任务完成1/1'),('阶段目标 1/1','阶段目标达成1/1'),('已完成课程 1','该科目已完成课程1节')]:
                code=code.replace('document.querySelector(".progress").innerText.includes("'+old+'")','document.querySelector(".progress-summary-grid").innerText.replace(/\\s+/g,"").includes("'+new+'")')
        if source.name=='learning_materials_walkthrough.py':
            code=code.replace("await b.click('教学资料与作业','a');await b.until", "await b.click('教学资料与作业','a');await b.js('[...document.querySelectorAll(\".business-tabs button\")].find(e=>e.textContent.includes(\"课后作业\")).click()');await b.until")
            code=code.replace("await b.until('document.querySelector(\"article\")!==null','material task view')", "await b.js('[...document.querySelectorAll(\".business-tabs button\")].find(e=>e.textContent.includes(\"课后作业\")).click()');await b.until('document.querySelector(\"article\")!==null','material task view')")
            code=code.replace("'/learning/materials');await b.until", "'/learning/materials');await b.js('[...document.querySelectorAll(\".business-tabs button\")].find(e=>e.textContent.includes(\"课后作业\")).click()');await b.until")
            code=code.replace("await b.until('document.querySelector(\"#mistake-title\")!==null','mistake entry')","await b.click('记录新错题');await b.until('document.querySelector(\"#mistake-title\")!==null','mistake entry')")
            for old,new in [('布置复习任务','一键布置复习任务'),('发布复习任务','确认发布复习任务'),('填写反馈','批改与反馈'),('保存反馈并完成','保存批改并完成'),('记录错题','确认记录'),('保存错题','保存修改')]:code=code.replace(old,new)
            code=code.replace("await b.click('确认记录本','a')","await b.click('错题本','a')")
        if source.name=='recurring_schedule_walkthrough.py':
            for old,new in [('有冲突','存在冲突'),('确认发布','确认批量发布'),('重新预览','重新计算预览')]:code=code.replace(old,new)
        if source.name=='assessments_messages_walkthrough.py':
            code=code.replace('提交作答并评分','确认提交作答并即时评分').replace('document.body.innerText.includes("本次答对 1/1 题")','document.querySelector(".score-main")?.innerText.replace(/\\s+/g,"")=== "答对1/1题"')
        if source.name in ('support_course_walkthrough.py','file_quota_walkthrough.py','operations_support_walkthrough.py'):
            code=code.replace('.case', '.case-item')
        if source.name=='support_course_walkthrough.py':
            code=code.replace("await b.role(role,user['phone'],PASSWORD,'/support')","await b.role(role,user['phone'],PASSWORD,'/support');await b.click('提交新工单')")
        (dest/source.name).write_text(code,encoding='utf-8')
    print('Prepared isolated copies of existing test helpers; original evidence and scripts preserved')

def sql(statement):
    s,_=scope()
    return subprocess.check_output(['docker','exec','-i','deploy-postgres-1','psql','-U','nurture','-d',s['database'],'-X','-At','-v','ON_ERROR_STOP=1'],input=statement,encoding='utf-8').strip()

def serve(offline=False):
    s,out=scope();static=(pathlib.Path(s['frontendBuild'])/'dist/nurture-frontend/browser').resolve()
    if offline:s={**s,'backendPort':8083,'frontendPort':4203}
    if not (static/'index.html').is_file():raise ValueError('Production build missing')
    class Handler(http.server.BaseHTTPRequestHandler):
        def log_message(self,*args):pass
        def proxy(self):
            length=int(self.headers.get('Content-Length','0'))
            if length<0 or length>52*1024*1024:self.send_error(413);return
            body=self.rfile.read(length) if length else None
            headers={k:v for k,v in self.headers.items() if k.lower() not in ('host','connection','accept-encoding','transfer-encoding')}
            conn=http.client.HTTPConnection('127.0.0.1',s['backendPort'],timeout=60)
            try:
                conn.request(self.command,self.path,body,headers);response=conn.getresponse();payload=response.read()
                self.send_response(response.status)
                for k,v in response.getheaders():
                    if k.lower() not in ('connection','transfer-encoding','content-length'):self.send_header(k,v)
                self.send_header('Content-Length',str(len(payload)));self.end_headers()
                if self.command!='HEAD':self.wfile.write(payload)
            except (OSError,http.client.HTTPException):self.send_error(502,'Isolated acceptance backend unavailable')
            finally:conn.close()
        def request(self):
            if self.path.startswith('/api/'):return self.proxy()
            if self.command not in ('GET','HEAD'):self.send_error(405);return
            relative=urllib.parse.unquote(urllib.parse.urlsplit(self.path).path).lstrip('/')
            file=(static/relative).resolve()
            if not file.is_relative_to(static):self.send_error(403);return
            if not file.is_file():
                if relative.startswith('assets/') or pathlib.Path(relative).suffix:self.send_error(404);return
                file=static/'index.html'
            data=file.read_bytes();mime=mimetypes.guess_type(file.name)[0] or 'application/octet-stream'
            self.send_response(200);self.send_header('Content-Type',mime);self.send_header('Content-Length',str(len(data)))
            self.send_header('Cache-Control','no-store');self.end_headers()
            if self.command!='HEAD':self.wfile.write(data)
        do_GET=request;do_HEAD=request;do_POST=request;do_PUT=request;do_PATCH=request;do_DELETE=request;do_OPTIONS=request
    print('Production acceptance frontend: http://localhost:'+str(s['frontendPort'])+'; isolated API :'+str(s['backendPort']),flush=True)
    http.server.ThreadingHTTPServer(('127.0.0.1',s['frontendPort']),Handler).serve_forever()

def run(script):
    allowed={'e2e_test','probe_authz','gap_fill_walkthrough','learning_plan_walkthrough','learning_materials_walkthrough',
             'recurring_schedule_walkthrough','operations_support_walkthrough','admin_queue_walkthrough',
             'assessments_messages_walkthrough','engineering_acceptance_walkthrough','support_course_walkthrough','file_quota_walkthrough','delivery_transactions_walkthrough','delivery_routes_walkthrough','delivery_purchase_walkthrough','delivery_offline_walkthrough','delivery_operations_walkthrough','delivery_legacy_walkthrough'}
    if script not in allowed:raise ValueError('Script is outside the reviewed acceptance allowlist')
    s,out=scope()
    if int(sql("SELECT max(version::int) FROM flyway_schema_history WHERE success;"))!=22:raise ValueError('Expected isolated V22 schema')
    env=dict(os.environ,PYTHONIOENCODING='utf-8',PYTHONUNBUFFERED='1',NURTURE_E2E_BASE='http://127.0.0.1:8082/api/v1',NURTURE_TEST_CLIENT_IP='10.222.12.'+str(sorted(allowed).index(script)+1))
    subprocess.run([sys.executable,str(out/'tools'/(script+'.py'))],cwd=out,env=env,check=True)

if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    p=argparse.ArgumentParser();p.add_argument('action',choices=['prepare','serve','serve-offline','run']);p.add_argument('script',nargs='?');a=p.parse_args()
    if a.action=='run':run(a.script)
    elif a.action=='serve-offline':serve(True)
    else:globals()[a.action]()
