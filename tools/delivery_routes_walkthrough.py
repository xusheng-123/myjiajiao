"""Check all public and role routes against the real production build at desktop/mobile."""
import asyncio,json,pathlib,sys
from browser_support import Browser
from gap_fill_walkthrough import api
sys.stdout.reconfigure(encoding='utf-8')
ROOT=pathlib.Path(__file__).resolve().parents[1]
async def main():
    public,_=api('GET','/matching/public/teachers-page?size=1');teacher=public['content'][0]['teacherId']
    plans={
        'PUBLIC':(['/','/auth','/teachers','/teachers/'+teacher],None,None),
        'STUDENT':(['/student/dashboard','/student/match','/student/growth','/student/archive','/student/questions','/learning/plans','/learning/mistakes','/learning/materials','/learning/assessments','/messages','/support','/refunds'],'13900000002','Student#2026'),
        'TEACHER':(['/teacher/dashboard','/scheduling/series','/learning/plans','/learning/mistakes','/learning/materials','/learning/assessments','/messages','/support'],'13900000001','Teacher#2026'),
        'PARENT':(['/parent/dashboard','/learning/plans','/learning/mistakes','/learning/materials','/learning/assessments','/support','/refunds'],'13900000003','Parent#2026'),
        'ADMIN':(['/admin/dashboard','/support'],'13800000000','Admin#2026'),
    }
    results=[]
    async with Browser(ROOT/'.walk'/'delivery-routes',port=9251) as b:
        for role,(paths,phone,password) in plans.items():
            token=None;child=None
            if role!='PUBLIC':token=await b.role(role,phone,password,paths[0])
            if role=='PARENT':child=api('GET','/parent/me/children',token=token)[0][0]['studentId']
            for path in paths:
                route=path+('?childProfileId='+child if child and path.startswith('/learning/') else '')
                await b.viewport(1280);await b.nav(route);await b.until('document.querySelector("h1")!==null','route heading '+role+path)
                await asyncio.sleep(.8)
                assert await b.js('location.pathname')==path,'unexpected route redirect '+role+path
                if path=='/student/dashboard':await b.click('展开全部')
                await b.shot(role.lower()+'-'+path.strip('/').replace('/','-')+'-desktop')
                await b.viewport(375);await asyncio.sleep(.2);await b.shot(role.lower()+'-'+path.strip('/').replace('/','-')+'-mobile375')
                results.append({'role':role,'route':path,'desktop':True,'mobile375':True})
                print('PASS: route '+role+' '+path,flush=True)
            if role=='TEACHER':
                for forbidden in ('/student/dashboard','/parent/dashboard','/admin/dashboard','/refunds'):
                    await b.nav(forbidden);await b.until('location.pathname==="/auth"','role guard '+forbidden)
                    print('PASS: teacher denied '+forbidden,flush=True)
    (ROOT/'routes-result.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
    print('PASS: '+str(len(results))+' public and authorized role routes at two sizes',flush=True)
if __name__=='__main__':asyncio.run(main())
