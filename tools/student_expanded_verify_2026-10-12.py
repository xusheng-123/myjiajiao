# -*- coding: utf-8 -*-
"""学生工作台「展开全部功能」后专项复验（2026-10-12 桌面宽屏改造）。
专注模式默认开启（signal(true)，既有设计），预约列表/画像卡/课程表需展开后才渲染。
量测：≥1280 预约列表必须 2 列网格；1024 单列；无横向溢出。
产出覆盖 student-dash-*.png 八档 + student-dash-expanded-1920 视口图。
"""
import base64, json, subprocess, time, urllib.request, http.client, os

OUT = r"E:/线上家教平台/线上家教平台（功能）/docs/screenshots-2026-10-12-desktop"

def api_login(role, account, password):
    conn = http.client.HTTPConnection("localhost", 8080, timeout=10)
    conn.request("POST", "/api/v1/identity/auth/login:password",
                 json.dumps({"role": role, "account": account, "password": password}).encode(),
                 {"Content-Type": "application/json"})
    r = conn.getresponse(); body = json.loads(r.read())
    cookie = ""
    for k, v in r.getheaders():
        if k.lower() == "set-cookie" and v.startswith("NURTURE_AT="):
            cookie = v.split(";")[0][len("NURTURE_AT="):]
    conn.close()
    assert cookie, "登录失败"
    return cookie, body.get("accountNo", ""), body.get("displayName", "")

cookie, acct, disp = api_login("STUDENT", "13900000002", "Student#2026")
edge = r"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
proc = subprocess.Popen([edge, "--headless=new", "--remote-debugging-port=9229",
                         "--user-data-dir=" + r"E:/线上家教平台/线上家教平台（功能）/.edge-tmp-dl14",
                         "--no-first-run", "--window-size=1920,1080", "about:blank"],
                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
results = []
try:
    ws_url = None
    for _ in range(30):
        try:
            with urllib.request.urlopen("http://127.0.0.1:9229/json/version", timeout=2) as resp:
                ws_url = json.loads(resp.read())["webSocketDebuggerUrl"]
            break
        except Exception:
            time.sleep(0.5)
    import websockets

    async def main():
        async with websockets.connect(ws_url, max_size=128 * 1024 * 1024) as ws:
            mid = [0]
            async def p(method, params=None, sid=None):
                mid[0] += 1
                msg = {"id": mid[0], "method": method, "params": params or {}}
                if sid: msg["sessionId"] = sid
                await ws.send(json.dumps(msg))
                while True:
                    raw = json.loads(await ws.recv())
                    if raw.get("id") == mid[0]:
                        return raw.get("result", {})
            tgt = await p("Target.createTarget", {"url": "http://localhost:4200/"})
            att = await p("Target.attachToTarget", {"targetId": tgt["targetId"], "flatten": True})
            sid = att["sessionId"]

            async def sp(method, params=None):
                return await p(method, params, sid)

            async def shot(w, h, full_page, name):
                await sp("Emulation.setDeviceMetricsOverride",
                        {"width": w, "height": h, "deviceScaleFactor": 1, "mobile": w < 700})
                await sp("Network.setCookie", {"name": "NURTURE_AT", "value": cookie,
                                              "domain": "localhost", "path": "/api", "httpOnly": True})
                summary = json.dumps({"accountNo": acct, "displayName": disp, "role": "STUDENT",
                                      "expiresAtEpochMs": int(time.time() * 1000) + 3600000})
                await sp("Runtime.evaluate", {"expression": f"localStorage.setItem('nurture.session', {json.dumps(summary)}); 'ok'"})
                await sp("Page.navigate", {"url": "http://localhost:4200/student/dashboard"})
                time.sleep(3.2)
                # 展开「全部功能」（专注模式默认开启，按钮文案=展开全部功能 才需要点）
                await sp("Runtime.evaluate", {"expression": """
                    [...document.querySelectorAll('button')].find(b => b.textContent.includes('展开全部功能'))?.click(); 'ok'"""})
                time.sleep(1.2)
                await sp("Runtime.evaluate", {"expression": "document.documentElement.style.scrollBehavior='auto'; 'ok'"})
                await sp("Runtime.evaluate", {"expression": """
                    (async () => { const h = document.body.scrollHeight;
                      for (let y = 0; y <= h; y += 700) { window.scrollTo(0, y); await new Promise(r=>setTimeout(r,80)); }
                      window.scrollTo(0, 0); await new Promise(r=>setTimeout(r,300)); })(); 'ok'"""})
                time.sleep(0.5)
                r = await sp("Runtime.evaluate", {"expression": """(() => {
                    const bl = document.querySelector('.booking-list');
                    const shell = document.querySelector('.dashboard-shell');
                    return {
                      cards: document.querySelectorAll('.booking-item-card').length,
                      cols: bl ? getComputedStyle(bl).gridTemplateColumns : null,
                      shellW: shell ? Math.round(shell.getBoundingClientRect().width) : null,
                      cw: document.documentElement.clientWidth,
                      scrollW: document.documentElement.scrollWidth, vw: window.innerWidth };
                })()""", "returnByValue": True})
                if "exceptionDetails" in r:
                    print("EVAL EXC:", json.dumps(r["exceptionDetails"], ensure_ascii=False)[:300])
                if "result" not in r:
                    results.append((w, h, "NO-RESULT"))
                    return
                v = r["result"]["value"]
                expect_cols = 2 if w >= 1280 else 1
                col_ok = v["cols"] is not None and len(str(v["cols"]).split(" ")) == expect_cols
                overflow = v["scrollW"] > v["vw"] + 1
                verdict = ("OK" if col_ok and not overflow and v["cards"] > 0
                           else f"FAIL(cols={v['cols']},cards={v['cards']},overflow={overflow})")
                print(f"[{w}x{h}] cards={v['cards']} cols={v['cols']} shell={v['shellW']} -> {verdict}")
                results.append((w, h, verdict))
                params = {"format": "png"}
                if full_page:
                    params["captureBeyondViewport"] = True
                data = await sp("Page.captureScreenshot", params)
                with open(os.path.join(OUT, name), "wb") as f:
                    f.write(base64.b64decode(data["data"]))
                print(f"    shot {name} ({os.path.getsize(os.path.join(OUT, name))//1024}KB)")

            for (w, h) in [(1920, 1080), (1700, 900), (1440, 900), (1280, 800), (1024, 768)]:
                await shot(w, h, True, f"student-dash-{w}x{h}.png")
            for (w, h) in [(390, 844), (375, 812)]:
                await shot(w, h, False, f"mob-student-dashboard-{w}x{h}.png")
    import asyncio
    asyncio.run(main())
finally:
    proc.kill()

fails = [r for r in results if not r[2].startswith("OK")]
print(f"\n学生端展开态复验：{len(results) - len(fails)}/{len(results)} 通过")
if fails:
    print("FAIL:", fails)
