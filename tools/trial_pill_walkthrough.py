"""Edge headless CDP 走查：学生仪表盘试听行显示「免费试听」pill 且无下单按钮。"""
import base64, json, subprocess, time, urllib.request, http.client

conn = http.client.HTTPConnection("localhost", 8080, timeout=10)
conn.request("POST", "/api/v1/identity/auth/login:password",
             json.dumps({"role": "STUDENT", "account": "13900000002", "password": "Student#2026"}).encode(),
             {"Content-Type": "application/json"})
r = conn.getresponse(); body = json.loads(r.read())
cookie_val = ""
for k, v in r.getheaders():
    if k.lower() == "set-cookie" and v.startswith("NURTURE_AT="):
        cookie_val = v.split(";")[0][len("NURTURE_AT="):]
acct = body.get("accountNo", "NUR-STU-2"); disp = body.get("displayName", "学生")
conn.close()
assert cookie_val, "登录失败，未取得 NURTURE_AT"

edge = r"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
proc = subprocess.Popen([edge, "--headless=new", "--remote-debugging-port=9224",
                         "--user-data-dir=" + r"E:/线上家教平台/线上家教平台（功能）/.edge-tmp",
                         "--no-first-run", "--window-size=375,812", "about:blank"],
                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
try:
    ws_url = None
    for _ in range(30):
        try:
            with urllib.request.urlopen("http://127.0.0.1:9224/json/version", timeout=2) as resp:
                ws_url = json.loads(resp.read())["webSocketDebuggerUrl"]
            break
        except Exception:
            time.sleep(0.5)
    import websockets

    async def main():
        async with websockets.connect(ws_url, max_size=64 * 1024 * 1024) as ws:
            msg_id = 0
            async def send(method, params=None, sid=None):
                nonlocal msg_id
                msg_id += 1
                msg = {"id": msg_id, "method": method, "params": params or {}}
                if sid: msg["sessionId"] = sid
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
            await page("Page.enable"); await page("Runtime.enable")
            await page("Network.setCookie", {"name": "NURTURE_AT", "value": cookie_val,
                                             "domain": "localhost", "path": "/api", "httpOnly": True})
            await page("Page.navigate", {"url": "http://localhost:4200/"})
            time.sleep(2.5)
            summary = json.dumps({"accountNo": acct, "displayName": disp, "role": "STUDENT",
                                  "expiresAtEpochMs": int(time.time() * 1000) + 20 * 60 * 1000})
            await page("Runtime.evaluate", {"expression": f"localStorage.setItem('nurture.session', {json.dumps(summary)}); 'ok'"})
            await page("Page.navigate", {"url": "http://localhost:4200/student/dashboard"})
            time.sleep(3.5)
            await page("Emulation.setDeviceMetricsOverride",
                       {"width": 375, "height": 812, "deviceScaleFactor": 2, "mobile": True})
            # 仪表盘默认 focusMode 折叠课程表，先展开全部再断言
            await page("Runtime.evaluate", {"expression": """
                [...document.querySelectorAll('button')].find(x => x.textContent.includes('展开全部'))?.click(); 'ok'"""})
            time.sleep(1.8)
            check = await page("Runtime.evaluate", {"expression": """
                JSON.stringify((() => {
                  const pills = [...document.querySelectorAll('.status-pill')]
                    .filter(x => x.textContent.trim() === '免费试听');
                  let trialRowWithOrderBtn = 0;
                  for (const pill of pills) {
                    let node = pill;
                    for (let i = 0; i < 8 && node; i++) {
                      if ([...node.querySelectorAll('button')].some(b => b.textContent.includes('下单'))) {
                        trialRowWithOrderBtn++; break;
                      }
                      node = node.parentElement;
                    }
                  }
                  return {
                    trialPills: pills.length,
                    trialRowWithOrderBtn,
                    orderBtnTotal: [...document.querySelectorAll('button')].filter(b => b.textContent.trim() === '下单').length,
                    overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
                    scrollW: document.documentElement.scrollWidth
                  };
                })())""", "returnByValue": True})
            stats = json.loads(check["result"]["value"])
            print("走查断言:", json.dumps(stats, ensure_ascii=False))
            shot = await page("Page.captureScreenshot", {"format": "png", "captureBeyondViewport": True})
            out = r"E:/线上家教平台/线上家教平台（功能）/.edge-tmp/student_dashboard_trial.png"
            open(out, "wb").write(base64.b64decode(shot["data"]))
            print("截图:", out)
            ok = stats["trialPills"] > 0 and stats["trialRowWithOrderBtn"] == 0 and not stats["overflow"]
            print("结论:", "PASS" if ok else "FAIL")

    import asyncio
    asyncio.run(main())
finally:
    proc.terminate()
