"""375 移动档底部复核：DOM 计数 + 视口截图（非全页拼接）。"""
import base64, json, subprocess, time, urllib.request

OUT = r"E:/线上家教平台/线上家教平台（功能）/docs/ui-redesign-2026-10-11/images"
edge = r"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
proc = subprocess.Popen([edge, "--headless=new", "--remote-debugging-port=9225",
                         "--user-data-dir=" + r"E:/线上家教平台/线上家教平台（功能）/.edge-tmp-mob",
                         "--no-first-run", "--window-size=400,900", "about:blank"],
                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
try:
    ws_url = None
    for _ in range(30):
        try:
            with urllib.request.urlopen("http://127.0.0.1:9225/json/version", timeout=2) as resp:
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
                       {"width": 375, "height": 812, "deviceScaleFactor": 2, "mobile": True})
            await page("Page.navigate", {"url": "http://localhost:4200/"})
            time.sleep(5)
            await page("Runtime.evaluate", {"expression": """
                const st=document.createElement('style');
                st.textContent='*{animation:none!important;transition:none!important}';
                document.head.appendChild(st); 'ok'"""})
            m = await page("Runtime.evaluate", {"expression": """
                JSON.stringify({
                  heroes: document.querySelectorAll('.hero').length,
                  heroTop: Math.round(document.querySelector('.hero').getBoundingClientRect().top + scrollY),
                  heroH: Math.round(document.querySelector('.hero').getBoundingClientRect().height),
                  footerTop: Math.round(document.querySelector('nur-site-footer').getBoundingClientRect().top + scrollY),
                  docH: document.documentElement.scrollHeight,
                  scrollW: document.documentElement.scrollWidth
                })""", "returnByValue": True})
            print("probe =>", m.get("result", {}).get("value"))
            # 滚到底部，视口截图（不用 captureBeyondViewport）
            await page("Runtime.evaluate", {"expression": "window.scrollTo(0, document.body.scrollHeight); 'ok'"})
            time.sleep(1.5)
            shot = await page("Page.captureScreenshot", {"format": "png"})
            open(OUT + "/home-mobile-bottom-2026-10-12.png", "wb").write(base64.b64decode(shot["data"]))
    import asyncio
    asyncio.run(main())
finally:
    proc.terminate()
print("done")
