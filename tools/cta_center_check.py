"""第九屏 CTA 居中改版验收：1920/1280/768/375 四档视口截图 + 居中比例与溢出断言。"""
import base64, json, subprocess, time, urllib.request

OUT = r"E:/线上家教平台/线上家教平台（功能）/docs/ui-redesign-2026-10-11/images/home-sections"
edge = r"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
proc = subprocess.Popen([edge, "--headless=new", "--remote-debugging-port=9228",
                         "--user-data-dir=" + r"E:/线上家教平台/线上家教平台（功能）/.edge-tmp-cta1",
                         "--no-first-run", "--window-size=1980,1000", "about:blank"],
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
            await p("Page.navigate", {"url": "http://localhost:4200/"})
            time.sleep(5)
            await p("Runtime.evaluate", {"expression": """
                (() => { const st=document.createElement('style');
                st.textContent='*{animation:none!important;transition:none!important} html{scroll-behavior:auto!important}';
                document.head.appendChild(st); return 'ok'; })()"""})
            fails = []
            for w, h, mobile in [(1920, 1000, False), (1280, 900, False), (1024, 900, False), (768, 900, False), (375, 812, True)]:
                await p("Emulation.setDeviceMetricsOverride",
                        {"width": w, "height": h, "deviceScaleFactor": 1, "mobile": mobile})
                time.sleep(1.2)
                probe = await p("Runtime.evaluate", {"expression": """
                    (() => {
                      const sec = document.querySelector('.nur-section.cta');
                      const copy = sec.querySelector('.cta-copy');
                      const title = sec.querySelector('.cta-title');
                      const lead = sec.querySelector('.cta-lead');
                      const actions = sec.querySelector('.cta-actions');
                      const frame = document.querySelector('.cta-frame');
                      const btn = actions.querySelector('.nur-btn');
                      const s = sec.getBoundingClientRect(), c = copy.getBoundingClientRect();
                      const b = btn.getBoundingClientRect();
                      return JSON.stringify({
                        scrollW: document.documentElement.scrollWidth, vw: innerWidth,
                        frameGone: !frame,
                        secH: Math.round(s.height),
                        centerRatio: +(((c.top + c.bottom) / 2 - s.top) / s.height).toFixed(3),
                        titleFs: getComputedStyle(title).fontSize,
                        titleW: getComputedStyle(title).fontWeight,
                        leadFs: getComputedStyle(lead).fontSize,
                        leadLh: getComputedStyle(lead).lineHeight,
                        copyMaxW: getComputedStyle(sec.querySelector('.cta-inner')).maxWidth,
                        copyAlign: getComputedStyle(sec.querySelector('.cta-inner')).textAlign,
                        btnH: Math.round(b.height),
                        btnDir: getComputedStyle(actions).flexDirection,
                        btnGap: getComputedStyle(actions).columnGap || getComputedStyle(actions).gap,
                        hasImgBg: getComputedStyle(sec).backgroundImage.includes('courtyard')
                      });
                    })()""", "returnByValue": True})
                m0 = json.loads(probe["result"]["value"])
                y = await p("Runtime.evaluate", {"expression": """
                    (() => { const el = document.querySelector('.nur-section.cta');
                      const yy = el.getBoundingClientRect().top + scrollY - 40;
                      window.scrollTo({top: yy, behavior: 'instant'});
                      return JSON.stringify({y: Math.round(yy)}); })()""", "returnByValue": True})
                time.sleep(1.0)
                shot = await p("Page.captureScreenshot", {"format": "png"})
                open(f"{OUT}/cta-center-{w}.png", "wb").write(base64.b64decode(shot["data"]))
                print(w, json.dumps(m0), "scroll", y["result"]["value"])
                if m0["scrollW"] > m0["vw"]: fails.append(f"{w}: scrollW {m0['scrollW']}>{m0['vw']}")
                if not m0["frameGone"]: fails.append(f"{w}: cta-frame still present")
                if not m0["hasImgBg"]: fails.append(f"{w}: courtyard bg missing")
                if m0["copyAlign"] != "center": fails.append(f"{w}: not centered text")
                if not (0.42 <= m0["centerRatio"] <= 0.48): fails.append(f"{w}: centerRatio {m0['centerRatio']}")
                if m0["btnH"] != 52: fails.append(f"{w}: btnH {m0['btnH']}")
                if w == 1920 and (m0["titleFs"] != "44px" or m0["titleW"] != "700"): fails.append(f"1920 title {m0['titleFs']}/{m0['titleW']}")
                if w == 1920 and m0["copyMaxW"] != "900px": fails.append(f"1920 maxW {m0['copyMaxW']}")
                if w == 1280 and (m0["titleFs"] != "44px" or m0["copyMaxW"] != "900px"): fails.append(f"1280(桌面档) {m0['titleFs']}/{m0['copyMaxW']}")
                if w == 1024 and (m0["titleFs"] != "36px" or m0["copyMaxW"] != "760px"): fails.append(f"1024(平板档) {m0['titleFs']}/{m0['copyMaxW']}")
                if w == 768 and m0["titleFs"] != "36px": fails.append(f"768 title {m0['titleFs']}")
                if w == 375:
                    if m0["btnDir"] != "column": fails.append(f"375 btnDir {m0['btnDir']}")
                    lo, hi = 28, 34.01
                    fs = float(m0["titleFs"].replace("px", ""))
                    if not (lo <= fs <= hi): fails.append(f"375 title {m0['titleFs']}")
            print("FAILS:", fails if fails else "none — all assertions passed")
    asyncio.run(main())
finally:
    proc.terminate()
print("done")
