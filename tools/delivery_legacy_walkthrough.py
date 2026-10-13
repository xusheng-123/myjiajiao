"""Verify the owner-only legacy wallet UI using synthetic delivery DB data."""
import asyncio, pathlib, sys
from browser_support import Browser
from gap_fill_walkthrough import api
from learning_plan_walkthrough import register, PASSWORD
from engineering_acceptance_walkthrough import seed_legacy
from delivery_transactions_walkthrough import click
sys.stdout.reconfigure(encoding='utf-8')
ROOT=pathlib.Path(__file__).resolve().parents[1]
async def main():
    owner,outsider=register('STUDENT'),register('STUDENT')
    assert api('GET','/student/me/legacy-wallet',token=owner['token'])[0]['present'] is False
    seed_legacy(owner)
    async with Browser(ROOT/'.walk'/'delivery-legacy',port=9256) as b:
        await b.role('STUDENT',owner['phone'],PASSWORD,'/student/dashboard');await click(b,'展开全部功能')
        await b.until('document.querySelector("#legacy-wallet-title")!==null','legacy owner card')
        assert await b.js('document.querySelector(".legacy-wallet-card").innerText.includes("777.00")')
        await b.js('document.querySelector(".legacy-wallet-card").scrollIntoView()');await b.shot('legacy-owner-desktop')
        await click(b,'下一页','.legacy-wallet-card button')
        await b.until('document.querySelector(".legacy-wallet-card").innerText.replace(/\\s+/g,"").includes("第2/2页")','real ledger page two')
        assert await b.js('document.querySelectorAll(".legacy-wallet-card .wallet-txn-row").length===1')
        assert not await b.js('[...document.querySelectorAll(".legacy-wallet-card button")].some(e=>/充值|付款|转账/.test(e.textContent))')
        await b.viewport(375);await b.shot('legacy-page2-mobile375');await b.viewport(1280)
        await b.role('STUDENT',outsider['phone'],PASSWORD,'/student/dashboard');await click(b,'展开全部功能')
        await b.until('document.querySelector("nur-notice-center")!==null','outsider expanded')
        assert await b.js('document.querySelector("#legacy-wallet-title")===null')
    actual=api('GET','/student/me/legacy-wallet',token=owner['token'])[0]
    assert actual['balanceCents']==77700 and actual['transactions']['totalElements']==21
    print('PASS: owner-only legacy balance777.00/21 real ledger rows/page2/no payment controls/outsider hidden; balance unchanged',flush=True)
if __name__=='__main__':asyncio.run(main())
