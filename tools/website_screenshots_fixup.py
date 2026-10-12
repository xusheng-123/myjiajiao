"""补拍 4 张截图（03 教师列表 / 04 教师详情 / 11 找老师匹配 / 18 阶段测评）：
- 轮询页面文本稳定 + 加载占位消失后再截；
- 收集 console error / 未捕获异常，供诊断空白页。
"""
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
TID = "6fbeb796-5bcf-4625-b0b9-d471acf2d882"  # 李老师（公开目录实测）

edge = r"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
proc = subprocess.Popen([edge, "--headless=new", "--remote-debugging-port=9227",
                         "--user-data-dir=" + r"E:/线上家教平台/线上家教平台（功能）/.edge-tmp-shot2",
                         "--no-first-run", "--window-size=1280,900", "about:blank"],
                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
try:
    ws_url = None
    for _ in range(30):
        try:
            with urllib.request.urlopen("http://127.0.0.1:9227/json/version", timeout=2) as resp:
                ws_url = json.loads(resp.read())["webSocketDebuggerUrl"]
            break
        except Exception:
            time.sleep(0.5)
    import websockets

    async def main():
        async with websockets.connect(ws_url, max_size=128 * 1024 * 1024) as ws:
            msg_id = 0
            console_errors = []
            async def send(method, params=None, sid=None):
                nonlocal msg_id
                msg_id += 1
                msg = {"id": msg_id, "method": method, "params": params or {}}
                if sid: msg["sessionId"] = sid
                await ws.send(json.dumps(msg))
                while True:
                    raw = json.loads(await ws.recv())
                    if raw.get("method") in ("Runtime.exceptionThrown",):
                        d = raw.get("params", {}).get("exceptionDetails", {})
                        console_errors.append(d.get("exception", {}).get("description", str(d))[:300])
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
            await page("Network.setCookie", {"name": "NURTURE_AT", "value": cookie,
                                             "domain": "localhost", "path": "/api", "httpOnly": True})
            summary = json.dumps({"accountNo": acct, "displayName": disp, "role": "STUDENT",
                                  "expiresAtEpochMs": int(time.time() * 1000) + 30 * 60 * 1000})
            await page("Runtime.evaluate",
                       {"expression": f"localStorage.setItem('nurture.session', {json.dumps(summary)}); 'ok'"})

            async def body_text():
                r = await page("Runtime.evaluate",
                               {"expression": "document.body ? document.body.innerText : ''", "returnByValue": True})
                return r.get("result", {}).get("value", "")

            async def wait_stable(timeout=25, must_lose=()):
                """轮询直到文本长度连续两次不变，且 must_lose 的加载占位消失。"""
                start, prev, stable = time.time(), None, 0
                while time.time() - start < timeout:
                    t = await body_text()
                    ok_lose = all(m not in t for m in must_lose)
                    if t == prev and len(t) > 0 and ok_lose:
                        stable += 1
                        if stable >= 2: return t
                    else:
                        stable = 0 if t != prev else stable
                    prev = t
                    time.sleep(0.8)
                return prev or ""

            async def shot(url, name, extra_click=None, must_lose=()):
                console_errors.clear()
                await page("Page.navigate", {"url": "http://localhost:4200" + url})
                time.sleep(2.5)
                if extra_click:
                    await page("Runtime.evaluate", {"expression": extra_click})
                    time.sleep(1.0)
                t = await wait_stable(must_lose=must_lose)
                await page("Runtime.evaluate", {"expression": """
                    (async () => { const h=document.body.scrollHeight;
                      for (let y=0;y<=h;y+=700){ window.scrollTo(0,y); await new Promise(r=>setTimeout(r,100)); }
                      window.scrollTo(0,0); await new Promise(r=>setTimeout(r,400)); })(); 'ok'"""})
                time.sleep(0.8)
                data = await page("Page.captureScreenshot", {"format": "png", "captureBeyondViewport": True})
                path = os.path.join(OUT, name + ".png")
                with open(path, "wb") as f:
                    f.write(base64.b64decode(data["data"]))
                print(f"[OK] {name}.png ({os.path.getsize(path)//1024}KB) 文本长度={len(t)}")
                for e in console_errors[:5]:
                    print("   [console]", e.replace(chr(10), ' ')[:220])

            # 03 教师列表：等骨架消失
            await shot("/teachers", "03-教师列表页", must_lose=("正在为你寻找合适的导师",))
            # 04 教师详情
            await shot("/teachers/" + TID, "04-教师详情页")
            # 11 找老师匹配：点「查看全部」后等结果
            await shot("/student/match", "11-找老师匹配",
                       extra_click="[...document.querySelectorAll('button')].find(b=>b.textContent.includes('查看全部'))?.click(); 'ok'",
                       must_lose=("正在为你挑选合适的老师", "搜索中"))
            # 18 阶段测评（学生角色）：诊断空白
            await shot("/learning/assessments", "18-阶段测评")
            t = await body_text()
            print("18 页面文本前 200 字：", t[:200].replace(chr(10), ' | '))

    import asyncio
    asyncio.run(main())
finally:
    proc.kill()
