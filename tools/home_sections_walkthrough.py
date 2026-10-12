"""Edge headless CDP 首页 2-4 屏背景接入验收：全页截图 + 各屏实测尺寸 + 首屏回归 + 375 移动档。"""
import base64, json, subprocess, time, urllib.request, sys

OUT = r"E:/线上家教平台/线上家教平台（功能）/docs/ui-redesign-2026-10-11/images"
edge = r"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
proc = subprocess.Popen([edge, "--headless=new", "--remote-debugging-port=9224",
                         "--user-data-dir=" + r"E:/线上家教平台/线上家教平台（功能）/.edge-tmp-home",
                         "--no-first-run", "--window-size=1920,1080", "about:blank"],
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
            await page("Emulation.setDeviceMetricsOverride",
                       {"width": 1920, "height": 1080, "deviceScaleFactor": 1, "mobile": False})
            await page("Page.navigate", {"url": "http://localhost:4200/"})
            time.sleep(5)
            # 禁动画稳定截图
            await page("Runtime.evaluate", {"expression": """
                const st=document.createElement('style');
                st.textContent='*{animation:none!important;transition:none!important}';
                document.head.appendChild(st); 'ok'"""})
            # 触发懒加载图片：滚到底再回顶
            await page("Runtime.evaluate", {"expression": "window.scrollTo(0, document.body.scrollHeight); 'ok'"})
            time.sleep(1.5)
            await page("Runtime.evaluate", {"expression": "window.scrollTo(0, 0); 'ok'"})
            time.sleep(1.2)
            meas = await page("Runtime.evaluate", {"expression": """
                JSON.stringify(['philosophy','services','teachers'].map(id=>{
                  const el=document.getElementById(id);
                  const r=el.getBoundingClientRect();
                  const cs=getComputedStyle(el);
                  return {id, top: Math.round(r.top + scrollY), height: Math.round(r.height),
                          pos: cs.backgroundPosition, hasImg: cs.backgroundImage.includes('section-')};
                }))""", "returnByValue": True})
            print("sections =>", meas.get("result", {}).get("value"))
            shot = await page("Page.captureScreenshot", {"format": "png", "captureBeyondViewport": True})
            open(OUT + "/home-sections-1920-2026-10-12.png", "wb").write(base64.b64decode(shot["data"]))
            # 首屏回归
            shot = await page("Page.captureScreenshot", {"format": "png"})
            open(OUT + "/home-hero-regression-2026-10-12.png", "wb").write(base64.b64decode(shot["data"]))
            # 375 移动档
            await page("Emulation.setDeviceMetricsOverride",
                       {"width": 375, "height": 812, "deviceScaleFactor": 2, "mobile": True})
            await page("Page.navigate", {"url": "http://localhost:4200/"})
            time.sleep(5)
            await page("Runtime.evaluate", {"expression": "window.scrollTo(0, document.body.scrollHeight); 'ok'"})
            time.sleep(1.5)
            await page("Runtime.evaluate", {"expression": "window.scrollTo(0, 0); 'ok'"})
            time.sleep(1)
            m = await page("Runtime.evaluate", {"expression": """
                JSON.stringify({scrollW: document.documentElement.scrollWidth,
                                vw: document.documentElement.clientWidth})""", "returnByValue": True})
            print("mobile =>", m.get("result", {}).get("value"))
            shot = await page("Page.captureScreenshot", {"format": "png", "captureBeyondViewport": True})
            open(OUT + "/home-sections-375-2026-10-12.png", "wb").write(base64.b64decode(shot["data"]))
    import asyncio
    asyncio.run(main())
finally:
    proc.terminate()
print("done")
