"""Actual admin audit queue beyond first20, with independent mobile table scroll."""
import asyncio,pathlib,sys
from browser_support import Browser
sys.stdout.reconfigure(encoding='utf-8')
async def main():
 async with Browser(pathlib.Path(__file__).resolve().parents[1]/'.walk'/'operations-support',port=9237) as b:
  await b.role('ADMIN','13800000000','Admin#2026','/admin/dashboard')
  await b.until('document.querySelector("section[aria-labelledby=audit-title] nur-queue-pager button:nth-of-type(2)")?.disabled===false','audit has next page')
  await b.js('document.querySelector("section[aria-labelledby=audit-title] nur-queue-pager button:nth-of-type(2)").click()')
  await b.until('document.querySelector("section[aria-labelledby=audit-title]").innerText.includes("第 2/") && !document.querySelector("section[aria-labelledby=audit-title]").innerText.includes("读取中")','audit page2 loaded')
  await b.viewport(375);await b.js('document.querySelector("section[aria-labelledby=audit-title]").scrollIntoView()');await b.shot('admin-audit-second-page-mobile375')
 print('PASS: actual admin audit second page and contained mobile table scroll')
if __name__=='__main__':asyncio.run(main())
