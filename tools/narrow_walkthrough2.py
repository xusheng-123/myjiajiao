"""Edge headless CDP 窄屏走查第二轮：全页截图 + 收藏面板 + 今日筛选空态。"""
import base64, json, subprocess, time, urllib.request, http.client, sys

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

edge = r"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
proc = subprocess.Popen([edge, "--headless=new", "--remote-debugging-port=9223",
                         "--user-data-dir=" + r"E:/线上家教平台/线上家教平台（功能）/.edge-tmp",
                         "--no-first-run", "--window-size=375,812", "about:blank"],
                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
try:
    ws_url = None
    for _ in range(30):
        try:
            with urllib.request.urlopen("http://127.0.0.1:9223/json/version", timeout=2) as resp:
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
            await page("Page.enable"); await page("Runtime.enable"); await page("Network.enable")
            await page("Network.setCookie", {"name": "NURTURE_AT", "value": cookie_val,
                                             "domain": "localhost", "path": "/api", "httpOnly": True})
            await page("Page.navigate", {"url": "http://localhost:4200/"})
            time.sleep(2.5)
            summary = json.dumps({"accountNo": acct, "displayName": disp, "role": "STUDENT",
                                  "expiresAtEpochMs": int(time.time() * 1000) + 20 * 60 * 1000})
            await page("Runtime.evaluate", {"expression": f"localStorage.setItem('nurture.session', {json.dumps(summary)}); 'ok'"})
            await page("Page.navigate", {"url": "http://localhost:4200/student/match"})
            time.sleep(3.5)
            await page("Emulation.setDeviceMetricsOverride",
                       {"width": 375, "height": 812, "deviceScaleFactor": 2, "mobile": True})
            # 查看全部
            await page("Runtime.evaluate", {"expression": """
                [...document.querySelectorAll('button')].find(x=>x.textContent.trim()==='查看全部')?.click(); 'ok'"""})
            time.sleep(1.8)
            # ① 全页截图（卡片收藏按钮区）
            shot = await page("Page.captureScreenshot", {"format": "png", "captureBeyondViewport": True})
            open(r"E:/线上家教平台/线上家教平台（功能）/.edge-tmp/match_full_mobile.png", "wb").write(base64.b64decode(shot["data"]))
            # ② 打开我的收藏面板
            await page("Runtime.evaluate", {"expression": """
                [...document.querySelectorAll('button')].find(x=>x.textContent.includes('我的收藏'))?.click(); 'ok'"""})
            time.sleep(1.5)
            shot = await page("Page.captureScreenshot", {"format": "png", "captureBeyondViewport": True})
            open(r"E:/线上家教平台/线上家教平台（功能）/.edge-tmp/match_fav_mobile.png", "wb").write(base64.b64decode(shot["data"]))
            fav = await page("Runtime.evaluate", {"expression": """
                JSON.stringify({
                  items: document.querySelectorAll('.fav-item').length,
                  overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
                  scrollW: document.documentElement.scrollWidth
                })""", "returnByValue": True})
            print("fav panel =>", fav.get("result", {}).get("value"))
            # ③ 回搜索入口 + 今日筛选空态
            await page("Runtime.evaluate", {"expression": """
                [...document.querySelectorAll('button')].find(x=>x.textContent.includes('搜索 / 筛选'))?.click(); 'ok'"""})
            time.sleep(0.8)
            await page("Runtime.evaluate", {"expression": """
                [...document.querySelectorAll('button.time-chip')].find(x=>x.textContent.trim()==='今日可约')?.click(); 'ok'"""})
            time.sleep(1.0)
            empty = await page("Runtime.evaluate", {"expression": """
                JSON.stringify({
                  emptyShown: [...document.querySelectorAll('p,div')].some(e=>e.children.length===0&&e.textContent.trim().startsWith('当前时间范围内暂无可约教师')),
                  note: document.querySelector('.time-filter-note')?.textContent?.trim()?.slice(0,50) ?? null,
                  overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth
                })""", "returnByValue": True})
            print("today empty =>", empty.get("result", {}).get("value"))
            shot = await page("Page.captureScreenshot", {"format": "png", "captureBeyondViewport": True})
            open(r"E:/线上家教平台/线上家教平台（功能）/.edge-tmp/match_empty_mobile.png", "wb").write(base64.b64decode(shot["data"]))
    import asyncio
    asyncio.run(main())
finally:
    proc.terminate()
print("done")
