"""Edge headless CDP 窄屏走查：注入学生登录态 → /student/match 375px 截图 + 溢出检查。"""
import base64, json, subprocess, time, urllib.request, http.client, socket, sys

# 1. 登录拿 cookie
conn = http.client.HTTPConnection("localhost", 8080, timeout=10)
conn.request("POST", "/api/v1/identity/auth/login:password",
             json.dumps({"role": "STUDENT", "account": "13900000002", "password": "Student#2026"}).encode(),
             {"Content-Type": "application/json"})
r = conn.getresponse(); body = json.loads(r.read())
cookie_val = ""
for k, v in r.getheaders():
    if k.lower() == "set-cookie" and v.startswith("NURTURE_AT="):
        cookie_val = v.split(";")[0][len("NURTURE_AT="):]
assert cookie_val, "no cookie"
acct = body.get("accountNo", "NUR-STU-2"); disp = body.get("displayName", "学生")
conn.close()
print("login ok, cookie len", len(cookie_val))

# 2. 启动 Edge headless
edge = r"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
proc = subprocess.Popen([edge, "--headless=new", "--remote-debugging-port=9223",
                         "--user-data-dir=" + r"E:/线上家教平台/线上家教平台（功能）/.edge-tmp",
                         "--no-first-run", "--window-size=375,812", "about:blank"],
                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
try:
    # 3. 拿 websock url
    ws_url = None
    for _ in range(30):
        try:
            with urllib.request.urlopen("http://127.0.0.1:9223/json/version", timeout=2) as resp:
                ws_url = json.loads(resp.read())["webSocketDebuggerUrl"]
            break
        except Exception:
            time.sleep(0.5)
    assert ws_url, "CDP not up"

    import websockets, uuid as _uuid

    async def main():
        async with websockets.connect(ws_url, max_size=64 * 1024 * 1024) as ws:
            msg_id = 0
            sessions = {}

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

            browser_ws = ws
            # 新建 target
            tgt = await send("Target.createTarget", {"url": "about:blank"})
            tid = tgt["targetId"]
            att = await send("Target.attachToTarget", {"targetId": tid, "flatten": True})
            sid = att["sessionId"]

            async def page(method, params=None):
                return await send(method, params, sid)

            await page("Page.enable")
            await page("Runtime.enable")
            await page("Network.enable")
            await page("Network.setCookie", {
                "name": "NURTURE_AT", "value": cookie_val,
                "domain": "localhost", "path": "/api", "httpOnly": True})
            # 建立 origin 再写会话摘要
            await page("Page.navigate", {"url": "http://localhost:4200/"})
            time.sleep(2.5)
            session_summary = json.dumps({
                "accountNo": acct, "displayName": disp, "role": "STUDENT",
                "expiresAtEpochMs": int(time.time() * 1000) + 20 * 60 * 1000})
            await page("Runtime.evaluate", {
                "expression": f"localStorage.setItem('nurture.session', {json.dumps(session_summary)}); 'ok'"})
            await page("Page.navigate", {"url": "http://localhost:4200/student/match"})
            time.sleep(3.5)

            for label, w, h, mobile in [("mobile375", 375, 812, True), ("desktop1280", 1280, 900, False)]:
                await page("Emulation.setDeviceMetricsOverride",
                           {"width": w, "height": h, "deviceScaleFactor": 2, "mobile": mobile})
                time.sleep(1.0)
                # 触发查看全部（若按钮存在）
                await page("Runtime.evaluate", {"expression": """
                    (function(){
                      var b=[...document.querySelectorAll('button')].find(x=>x.textContent.trim()==='查看全部');
                      if(b){b.click(); return 'clicked';} return 'no-btn';
                    })()
                """})
                time.sleep(1.8)
                metrics = await page("Runtime.evaluate", {"expression": """
                    JSON.stringify({
                      url: location.pathname,
                      vw: innerWidth,
                      scrollW: document.documentElement.scrollWidth,
                      clientW: document.documentElement.clientWidth,
                      overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
                      cards: document.querySelectorAll('button.btn-fav, nur-teacher-card').length,
                      chips: [...document.querySelectorAll('button.time-chip')].map(c=>c.textContent.trim()),
                      chipRowW: (function(){var f=document.querySelector('.time-filter'); return f?f.scrollWidth:-1;}())
                    })
                """, "returnByValue": True})
                print(label, "=>", metrics.get("result", {}).get("value"))
                shot = await page("Page.captureScreenshot", {"format": "png"})
                data = base64.b64decode(shot["data"])
                out = rf"E:/线上家教平台/线上家教平台（功能）/.edge-tmp/match_{label}.png"
                open(out, "wb").write(data)
                print("saved", out)

    import asyncio
    asyncio.run(main())
finally:
    proc.terminate()
print("done")
