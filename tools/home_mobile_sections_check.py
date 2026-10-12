"""375 移动档 5 新屏逐屏视口截图（instant 滚动 + 禁平滑滚动）。"""
import base64, json, subprocess, time, urllib.request

OUT = r"E:/线上家教平台/线上家教平台（功能）/docs/ui-redesign-2026-10-11/images/home-sections"
edge = r"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
proc = subprocess.Popen([edge, "--headless=new", "--remote-debugging-port=9227",
                         "--user-data-dir=" + r"E:/线上家教平台/线上家教平台（功能）/.edge-tmp-mob3",
                         "--no-first-run", "--window-size=400,900", "about:blank"],
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
    import websockets, asyncio

    async def main():
        async with websockets.connect(ws_url, max_size=64 * 1024 * 1024) as ws:
            mid = 0
            async def send(method, params=None, sid=None):
                nonlocal mid
                mid += 1
                m = {"id": mid, "method": method, "params": params or {}}
                if sid:
                    m["sessionId"] = sid
                await ws.send(json.dumps(m))
                while True:
                    raw = json.loads(await ws.recv())
                    if raw.get("id") == mid:
                        return raw.get("result", {})
            tgt = await send("Target.createTarget", {"url": "about:blank"})
            sid = (await send("Target.attachToTarget", {"targetId": tgt["targetId"], "flatten": True}))["sessionId"]
            p = lambda mth, pr=None: send(mth, pr, sid)
            await p("Page.enable"); await p("Runtime.enable")
            await p("Emulation.setDeviceMetricsOverride",
                    {"width": 375, "height": 812, "deviceScaleFactor": 2, "mobile": True})
            await p("Page.navigate", {"url": "http://localhost:4200/"})
            time.sleep(5)
            await p("Runtime.evaluate", {"expression": """
                const st=document.createElement('style');
                st.textContent='*{animation:none!important;transition:none!important} html{scroll-behavior:auto!important}';
                document.head.appendChild(st); 'ok'"""})
            await p("Runtime.evaluate", {"expression": "window.scrollTo(0, document.body.scrollHeight); 'ok'"})
            time.sleep(1.5)
            for name, sel in [("subjects", "#subjects"), ("process", "#process"), ("voices", "#voices"),
                              ("vision", ".nur-section.vision"), ("cta", ".nur-section.cta")]:
                r = await p("Runtime.evaluate", {"expression": """
                    (() => {
                      const el = document.querySelector('%s');
                      const y = el.getBoundingClientRect().top + scrollY - 70;
                      window.scrollTo({top: y, behavior: 'instant'});
                      return JSON.stringify({y: Math.round(y)});
                    })()""" % sel, "returnByValue": True})
                time.sleep(1.0)
                pos = await p("Runtime.evaluate", {"expression": "JSON.stringify({sy: Math.round(scrollY), docH: document.documentElement.scrollHeight})", "returnByValue": True})
                shot = await p("Page.captureScreenshot", {"format": "png"})
                open(f"{OUT}/m9-{name}.png", "wb").write(base64.b64decode(shot["data"]))
                print("shot", name, "target-resp", json.dumps(r, ensure_ascii=False)[:300], "actual", pos.get("result", {}).get("value"))
    asyncio.run(main())
finally:
    proc.terminate()
print("done")
