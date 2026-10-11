"""Final local visual and read-only legacy-wallet acceptance; isolated database only."""
import asyncio, base64, json, pathlib, subprocess, sys, uuid
from browser_support import Browser
from gap_fill_walkthrough import api
from learning_plan_walkthrough import register, PASSWORD

sys.stdout.reconfigure(encoding='utf-8')
ROOT = pathlib.Path(__file__).resolve().parents[1]
DATABASE = 'nurture_gap_acceptance_phase8_20261011'

def seed_legacy(student):
    # A test-only reconstruction of the historical profile -> wallet ownership.
    profile, wallet = uuid.uuid4(), uuid.uuid4()
    statements = [
        'BEGIN;',
        f"INSERT INTO parent_profiles(id,created_at,updated_at,account_id,parent_no) VALUES ('{profile}',now(),now(),'{student['id']}','LEG-{str(profile)[:16]}');",
        f"INSERT INTO family_wallets(id,created_at,updated_at,parent_id,balance_cents,currency,version) VALUES ('{wallet}',now(),now(),'{profile}',77700,'CNY',0);",
    ]
    for index in range(21):
        statements.append(f"INSERT INTO wallet_transactions(id,created_at,updated_at,wallet_id,txn_type,amount_cents,balance_after_cents,memo) VALUES ('{uuid.uuid4()}',now()+interval '{index} seconds',now(),'{wallet}','TOPUP',3700,{3700*(index+1)},'历史只读验收{index+1}');")
    statements.append('COMMIT;')
    subprocess.run(['docker','exec','-i','deploy-postgres-1','psql','-U','nurture','-d',DATABASE,'-v','ON_ERROR_STOP=1'],input='\n'.join(statements),text=True,encoding='utf-8',check=True,stdout=subprocess.DEVNULL)

async def main():
    student, outsider = register('STUDENT'), register('STUDENT')
    before, _ = api('GET','/student/me/legacy-wallet',token=student['token'])
    assert before['present'] is False
    seed_legacy(student)
    async with Browser(ROOT/'.walk'/'engineering-final',port=9239) as b:
        for path, selector, label in [('/', '#hero-title','landing'),('/auth','#portal-title','auth')]:
            await b.nav(path)
            await b.until('document.querySelector('+json.dumps(selector)+')!==null',label+' loaded')
            await asyncio.sleep(2)
            assert await b.js('getComputedStyle(document.querySelector('+json.dumps(selector)+')).opacity === "1"'),label+' visible after animation'
            await b.shot(label+'-desktop')
            await b.viewport(375); await b.shot(label+'-mobile375'); await b.viewport(1280)
        await b.role('STUDENT',student['phone'],PASSWORD,'/student/match')
        await b.until('document.querySelector(".match-shell")!==null','matching page')
        await b.shot('matching-desktop'); await b.viewport(375); await b.shot('matching-mobile375'); await b.viewport(1280)
        await b.nav('/student/dashboard'); await b.click('展开全部')
        await b.until('document.querySelector("#legacy-wallet-title")!==null','actual legacy wallet')
        assert await b.js('document.querySelector("#legacy-wallet-title").parentElement.innerText.includes("777.00")')
        section='#legacy-wallet-title'
        await b.js('document.querySelector('+json.dumps(section)+').scrollIntoView()')
        await b.shot('legacy-wallet-readonly-desktop')
        await b.js('(()=>{const s=document.querySelector("#legacy-wallet-title").parentElement; [...s.querySelectorAll("button")].find(e=>e.textContent.trim()==="下一页").click();})()')
        await b.until('document.querySelector("#legacy-wallet-title").parentElement.innerText.includes("第2/2页")','legacy second page')
        await b.viewport(375)
        await b.js('document.querySelector("#legacy-wallet-title").scrollIntoView()')
        await b.shot('legacy-wallet-page2-mobile375')
        # Capture the full card as well: the document bottom prevents it reaching the viewport top.
        clip=await b.js('(()=>{const r=document.querySelector("#legacy-wallet-title").parentElement.getBoundingClientRect();return {x:r.left+scrollX,y:r.top+scrollY,width:r.width,height:r.height,scale:1};})()')
        capture=await b.page('Page.captureScreenshot',{'format':'png','captureBeyondViewport':True,'clip':clip})
        (b.output/'legacy-wallet-page2-mobile375.png').write_bytes(base64.b64decode(capture['data']))
        assert not await b.js('[...document.querySelector("#legacy-wallet-title").parentElement.querySelectorAll("button")].some(e=>/充值|转账|付款/.test(e.textContent))')
        await b.role('STUDENT',outsider['phone'],PASSWORD,'/student/dashboard'); await b.click('展开全部')
        await b.until('document.querySelector("nur-notice-center")!==null','outsider expanded')
        assert await b.js('document.querySelector("#legacy-wallet-title")===null')
    wallet, _=api('GET','/student/me/legacy-wallet',token=student['token'])
    assert wallet['balanceCents']==77700 and wallet['transactions']['totalElements']==21
    print('PASS: unchanged brand colors/layout, hero visible, desktop/375px no overflow; legacy balance/history only owner, paging, no financial writes',flush=True)

if __name__=='__main__': asyncio.run(main())
