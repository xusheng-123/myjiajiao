"""补拍 11 找老师匹配 / 18 阶段测评：先落到 localhost 源再写 localStorage（否则 opaque origin 丢失会话）。"""
import base64, json, subprocess, time, urllib.request, http.client, os

OUT = r"E:/线上家教平台/线上家教平台（功能）/docs/screenshots-2026-10-11"
os.makedirs(OUT, exist_ok=True)

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
    conn.close(); assert cookie, role + " 登录失败"
    return cookie, body.get("accountNo", ""), body.get("displayName", account)

STUDENT = api_login("STUDENT", "13900000002", "Student#2026")

edge = r"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
proc = subprocess.Popen([edge, "--headless=new", "--remote-debugging-port=9228",
                         "--user-data-dir=" + r"E:/线上家教平台/线上家教平台（功能）/.edge-tmp-shot3",
                         "--no-first-run", "--window-size=1280,900", "about:blank"],
                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
try:
    ws_url = None
    for _ in range(30):
        try:
            with urllib.request.urlopen("http://127.0.0.1:9228/json/version", timeout=2) as resp:
                ws_url = json.loads(resp.read())["webSocketDebuggerUrl"]
            break
        except Exception:
            time.sleep(0.5)
    import websockets

    async def main():
        async with websockets.connect(ws_url, max_size=128 * 1024 * 1024) as ws:
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
            await page("Emulation.setDeviceMetricsOverride",
                       {"width": 1280, "height": 900, "deviceScaleFactor": 1, "mobile": False})

            cookie, acct, disp = STUDENT
            summary = json.dumps({"accountNo": acct, "displayName": disp, "role": "STUDENT",
                                  "expiresAtEpochMs": int(time.time() * 1000) + 30 * 60 * 1000})

            # 1) 先落到 localhost 源
            await page("Page.navigate", {"url": "http://localhost:4200/"})
            time.sleep(2.0)
            # 2) 在 localhost 源上写会话
            await page("Network.setCookie", {"name": "NURTURE_AT", "value": cookie,
                                             "domain": "localhost", "path": "/api", "httpOnly": True})
            await page("Runtime.evaluate",
                       {"expression": f"localStorage.setItem('nurture.session', {json.dumps(summary)}); 'ok'"})
            check = await page("Runtime.evaluate", {"expression": "localStorage.getItem('nurture.session')?.length || 0", "returnByValue": True})
            print("localStorage 会话长度:", check.get("result", {}).get("value"))

            async def body_text():
                r = await page("Runtime.evaluate",
                               {"expression": "document.body ? document.body.innerText : ''", "returnByValue": True})
                return r.get("result", {}).get("value", "")

            async def shot(url, name, extra_click=None, must_lose=()):
                await page("Page.navigate", {"url": "http://localhost:4200" + url})
                time.sleep(2.5)
                if extra_click:
                    await page("Runtime.evaluate", {"expression": extra_click})
                    time.sleep(1.0)
                start, prev, stable = time.time(), None, 0
                while time.time() - start < 25:
                    t = await body_text()
                    ok_lose = all(m not in t for m in must_lose)
                    if t == prev and len(t) > 0 and ok_lose:
                        stable += 1
                        if stable >= 2: break
                    else:
                        stable = 0 if t != prev else stable
                    prev = t
                    time.sleep(0.8)
                await page("Runtime.evaluate", {"expression": """
                    (async () => { const h=document.body.scrollHeight;
                      for (let y=0;y<=h;y+=700){ window.scrollTo(0,y); await new Promise(r=>setTimeout(r,100)); }
                      window.scrollTo(0,0); await new Promise(r=>setTimeout(r,400)); })(); 'ok'"""})
                time.sleep(0.8)
                data = await page("Page.captureScreenshot", {"format": "png", "captureBeyondViewport": True})
                path = os.path.join(OUT, name + ".png")
                with open(path, "wb") as f:
                    f.write(base64.b64decode(data["data"]))
                print(f"[OK] {name}.png ({os.path.getsize(path)//1024}KB) 文本长度={len(prev or '')} 前80字={(prev or '')[:80].replace(chr(10),' | ')}")

            await shot("/student/match", "11-找老师匹配",
                       extra_click="[...document.querySelectorAll('button')].find(b=>b.textContent.includes('查看全部'))?.click(); 'ok'",
                       must_lose=("正在为你挑选合适的老师", "搜索中"))
            await shot("/learning/assessments", "18-阶段测评")

    import asyncio
    asyncio.run(main())
finally:
    proc.kill()
