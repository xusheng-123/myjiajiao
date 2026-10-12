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
        code=code.replace("'localhost', 8080", "'localhost', 8082").replace('"localhost", 8080','"localhost", 8082')
        if source.name=='engineering_acceptance_walkthrough.py':code=code.replace("DATABASE = 'nurture_gap_acceptance_phase8_20261011'",'DATABASE = '+repr(s['database']))
        if source.name=='gap_fill_walkthrough.py':
            code='import os\n'+code.replace("headers['X-Forwarded-For']='10.210.31.1'","headers['X-Forwarded-For']=os.environ.get('NURTURE_TEST_CLIENT_IP','10.210.31.1')")
        (dest/source.name).write_text(code,encoding='utf-8')
    print('Prepared isolated copies of existing test helpers; original evidence and scripts preserved')

def sql(statement):
    s,_=scope()
    return subprocess.check_output(['docker','exec','-i','deploy-postgres-1','psql','-U','nurture','-d',s['database'],'-X','-At','-v','ON_ERROR_STOP=1'],input=statement,encoding='utf-8').strip()

def serve():
    s,out=scope();static=(pathlib.Path(s['frontendBuild'])/'dist/nurture-frontend/browser').resolve()
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
    print('Production acceptance frontend: http://localhost:4202; isolated API :8082',flush=True)
    http.server.ThreadingHTTPServer(('127.0.0.1',s['frontendPort']),Handler).serve_forever()

def run(script):
    allowed={'e2e_test','probe_authz','gap_fill_walkthrough','learning_plan_walkthrough','learning_materials_walkthrough',
             'recurring_schedule_walkthrough','operations_support_walkthrough','admin_queue_walkthrough',
             'assessments_messages_walkthrough','engineering_acceptance_walkthrough','support_course_walkthrough','file_quota_walkthrough'}
    if script not in allowed:raise ValueError('Script is outside the reviewed acceptance allowlist')
    s,out=scope()
    if int(sql("SELECT max(version::int) FROM flyway_schema_history WHERE success;"))!=22:raise ValueError('Expected isolated V22 schema')
    env=dict(os.environ,PYTHONIOENCODING='utf-8',NURTURE_E2E_BASE='http://localhost:8082/api/v1',NURTURE_TEST_CLIENT_IP='10.222.12.'+str(sorted(allowed).index(script)+1))
    subprocess.run([sys.executable,str(out/'tools'/(script+'.py'))],cwd=out,env=env,check=True)

if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    p=argparse.ArgumentParser();p.add_argument('action',choices=['prepare','serve','run']);p.add_argument('script',nargs='?');a=p.parse_args()
    if a.action=='run':run(a.script)
    else:globals()[a.action]()
