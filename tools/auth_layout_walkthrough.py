"""登录页布局精修验收（2026-10-12 用户规格书）：Edge headless CDP 四视口截图 + 坐标断言 + 登录冒烟 + 首页回归。

视口：2048x1078（设计基准）/ 1920x1080 / 1440x900 / 1280x800 / 375x812。
断言（2048）：卡片 left~1184 / right~1824 / width=640 / top 200-225；品牌文字起点 ~430；
导航返回按钮右缘 ~1824；无横向溢出；账号密码上下排列（account.top < password.top 且 left 相等）。
登录冒烟：学生 13900000002 真实登录 → 跳工作台（仅建立会话，无业务写入）。
产出：.edge-tmp-auth/*.png + 控制台断言汇总。
"""
import asyncio
import base64
import json
import subprocess
import time
import urllib.request

import websockets

OUT = r"E:/线上家教平台/线上家教平台（功能）/.edge-tmp-auth"
EDGE = r"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"

MEASURE_JS = r"""
(() => {
  const card = document.querySelector('.portal-card');
  const story = document.querySelector('.brand-story');
  const navBtn = document.querySelector('.nav-auth-btn');
  const aux = document.querySelector('.hero-aux-line');
  const account = document.querySelector('#account');
  const password = document.querySelector('#password');
  const r = el => el ? el.getBoundingClientRect() : null;
  const cs = story ? getComputedStyle(story) : null;
  const cardR = r(card), navR = r(navBtn), auxR = r(aux);
  return JSON.stringify({
    vw: innerWidth, vh: innerHeight,
    scrollW: document.documentElement.scrollWidth,
    overflowX: document.documentElement.scrollWidth > document.documentElement.clientWidth,
    card: cardR ? { left: Math.round(cardR.left), right: Math.round(cardR.right), top: Math.round(cardR.top), width: Math.round(cardR.width), height: Math.round(cardR.height) } : null,
    brandTextStartX: auxR ? Math.round(auxR.left) : null,
    brandPadLeft: cs ? cs.paddingLeft : null,
    navBtnRight: navR ? Math.round(navR.right) : null,
    fieldsStacked: (account && password) ? (r(account).top < r(password).top && Math.abs(r(account).left - r(password).left) < 2) : null,
    cardVisuallyFirst: (card && story) ? (r(card).top < r(story).top) : null,
  });
})()
"""

VIEWPORTS = [
    ("2048x1078", 2048, 1078, False),
    ("1920x1080", 1920, 1080, False),
    ("1440x900", 1440, 900, False),
    ("1280x800", 1280, 800, False),
    ("375x812", 375, 812, True),
]


async def main():
    proc = subprocess.Popen(
        [EDGE, "--headless=new", "--remote-debugging-port=9224",
         "--user-data-dir=" + OUT + "/profile",
         "--no-first-run", "--window-size=2048,1078", "about:blank"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        ws_url = None
        for _ in range(40):
            try:
                with urllib.request.urlopen("http://127.0.0.1:9224/json/version", timeout=2) as resp:
                    ws_url = json.loads(resp.read())["webSocketDebuggerUrl"]
                break
            except Exception:
                time.sleep(0.5)
        async with websockets.connect(ws_url, max_size=96 * 1024 * 1024) as ws:
            msg_id = 0

            async def send(method, params=None, sid=None):
                nonlocal msg_id
                msg_id += 1
                msg = {"id": msg_id, "method": method, "params": params or {}}
                if sid:
                    msg["sessionId"] = sid
                await ws.send(json.dumps(msg))
                while True:
                    raw = json.loads(await ws.recv())
                    if raw.get("id") == msg_id:
                        return raw.get("result", {})

            tgt = await send("Target.createTarget", {"url": "about:blank"})
            att = await send("Target.attachToTarget", {"targetId": tgt["targetId"], "flatten": True})
            sid = att["sessionId"]

            async def page(method, params=None):
                return await send(method, params, sid)

            await page("Page.enable")
            await page("Runtime.enable")

            report = {}
            for name, w, h, mobile in VIEWPORTS:
                await page("Emulation.setDeviceMetricsOverride",
                           {"width": w, "height": h, "deviceScaleFactor": 2 if mobile else 1, "mobile": mobile})
                await page("Page.navigate", {"url": "http://localhost:4200/auth"})
                await asyncio.sleep(3.0)
                await page("Runtime.evaluate", {"expression":
                    "const st=document.createElement('style');st.textContent='*{animation:none!important;transition:none!important}';document.head.appendChild(st);'ok'"})
                await asyncio.sleep(0.4)
                m = await page("Runtime.evaluate", {"expression": MEASURE_JS, "returnByValue": True})
                report[name] = json.loads(m["result"]["value"])
                shot = await page("Page.captureScreenshot", {"format": "png"})
                with open(f"{OUT}/auth_{name}.png", "wb") as f:
                    f.write(base64.b64decode(shot["data"]))

            # 首页回归（2048 基准）
            await page("Emulation.setDeviceMetricsOverride",
                       {"width": 2048, "height": 1078, "deviceScaleFactor": 1, "mobile": False})
            await page("Page.navigate", {"url": "http://localhost:4200/"})
            await asyncio.sleep(3.5)
            await page("Runtime.evaluate", {"expression":
                "const st=document.createElement('style');st.textContent='*{animation:none!important;transition:none!important}';document.head.appendChild(st);'ok'"})
            await asyncio.sleep(0.4)
            shot = await page("Page.captureScreenshot", {"format": "png"})
            with open(f"{OUT}/home_2048.png", "wb") as f:
                f.write(base64.b64decode(shot["data"]))

            # 登录冒烟（2048 视口，真实学生账号，仅建立会话）
            await page("Page.navigate", {"url": "http://localhost:4200/auth"})
            await asyncio.sleep(3.0)
            login = await page("Runtime.evaluate", {"returnByValue": True, "expression": """
(async () => {
  const setVal = (sel, v) => { const el = document.querySelector(sel); el.value = v; el.dispatchEvent(new Event('input', {bubbles: true})); };
  setVal('#account', '13900000002');
  setVal('#password', 'Student#2026');
  await new Promise(r => setTimeout(r, 200));
  const btn = document.querySelector('.btn-submit');
  if (!btn || btn.disabled) return 'submit-disabled';
  btn.click();
  await new Promise(r => setTimeout(r, 3500));
  return location.pathname + ' | session=' + (localStorage.getItem('nurture.session') ? 'yes' : 'no');
})()""", "awaitPromise": True})
            report["login_smoke"] = login["result"]["value"]
            shot = await page("Page.captureScreenshot", {"format": "png"})
            with open(f"{OUT}/auth_after_login.png", "wb") as f:
                f.write(base64.b64decode(shot["data"]))

            print(json.dumps(report, ensure_ascii=False, indent=1))
    finally:
        proc.terminate()


asyncio.run(main())
print("done")
