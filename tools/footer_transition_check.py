"""Footer 晨雾过渡验收：DOM 断言（渐变底/无顶边线/装饰/排版参数/渐隐层高度）+ 交界与页脚截图。"""
import base64, json, subprocess, time, urllib.request

OUT = r"E:/线上家教平台/线上家教平台（功能）/docs/ui-redesign-2026-10-11/images/home-sections"
edge = r"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
proc = subprocess.Popen([edge, "--headless=new", "--remote-debugging-port=9229",
                         "--user-data-dir=" + r"E:/线上家教平台/线上家教平台（功能）/.edge-tmp-footer1",
                         "--no-first-run", "--window-size=1980,1080", "about:blank"],
                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
try:
    ws_url = None
    for _ in range(30):
        try:
            with urllib.request.urlopen("http://127.0.0.1:9229/json/version", timeout=2) as resp:
                ws_url = json.loads(resp.read())["webSocketDebuggerUrl"]
            break
        except Exception:
            time.sleep(0.5)
    import websockets, asyncio

    PROBE = """
    (() => {
      const cs = (el, pe) => getComputedStyle(el, pe || null);
      const f = document.querySelector('.site-footer');
      const grid = f.querySelector('.sf-grid');
      const cols = cs(grid).gridTemplateColumns.split(' ').map(parseFloat);
      const ratio = cols.map(n => +(n / cols[1]).toFixed(2));
      const after = cs(document.querySelector('.nur-section.cta'), '::after');
      const bottom = f.querySelector('.sf-bottom');
      const bottomInner = f.querySelector('.sf-bottom-inner');
      return JSON.stringify({
        scrollW: document.documentElement.scrollWidth, vw: innerWidth,
        footBg: cs(f).backgroundImage, footBorderTop: cs(f).borderTopWidth,
        bamboo: (cs(f, '::before').backgroundImage || '').includes('footer-bamboo'),
        stone: (cs(f, '::after').backgroundImage || '').includes('footer-stone'),
        bambooOp: cs(f, '::before').opacity, stoneOp: cs(f, '::after').opacity,
        cols: ratio, contMax: cs(f.querySelector('.nur-container')).maxWidth,
        logoFs: cs(f.querySelector('.sf-logo')).fontSize, logoColor: cs(f.querySelector('.sf-logo')).color,
        colTitleFs: cs(f.querySelector('.sf-col-title')).fontSize,
        linkFs: cs(f.querySelector('.sf-link')).fontSize, linkLh: cs(f.querySelector('.sf-link')).lineHeight,
        linkColor: cs(f.querySelector('.sf-link')).color,
        verseColor: cs(f.querySelector('.sf-verse')).color,
        bottomBorder: cs(bottom).borderTopColor, innerH: bottomInner.getBoundingClientRect().height,
        gridPadTop: cs(grid).paddingTop, gridPadBottom: cs(grid).paddingBottom,
        gridGap: cs(grid).rowGap, contPadX: cs(grid).paddingLeft,
        ctaAfterH: after.height, ctaAfterBg: (after.backgroundImage || '').includes('245, 244, 237'),
        footTop: Math.round(f.getBoundingClientRect().top + scrollY),
        docH: document.documentElement.scrollHeight
      });
    })()
    """

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

            widths = [
                # (宽, 高, mobile, 渐隐层高期望, 竹影透明度期望, footer响应式档)
                (1920, 1000, False, "200px", "0.5", "desktop"),
                (768, 900, False, "200px", "0.26", "foot-mobile"),  # footer 自身 ≤768 即移动档；sections-2 移动块是 ≤767
                (375, 812, True, "120px", "0.26", "mobile"),
            ]
            fails = []
            for w, h, mobile, expAfterH, expBambooOp, fmode in widths:
                await p("Emulation.setDeviceMetricsOverride",
                        {"width": w, "height": h, "deviceScaleFactor": 1 if not mobile else 2, "mobile": mobile})
                await p("Page.navigate", {"url": "http://localhost:4200/"})
                time.sleep(5)
                await p("Runtime.evaluate", {"expression": """
                    const st=document.createElement('style');
                    st.textContent='*{animation:none!important;transition:none!important} html{scroll-behavior:auto!important}';
                    document.head.appendChild(st); 'ok'"""})
                r = await p("Runtime.evaluate", {"expression": PROBE, "returnByValue": True})
                m = json.loads(r["result"]["value"])
                print(f"--- {w}px:", json.dumps(m, ensure_ascii=False))

                def check(cond, msg):
                    if not cond:
                        fails.append(f"{w}px {msg}")

                check(m["scrollW"] <= m["vw"], f"横向溢出 scrollW={m['scrollW']} vw={m['vw']}")
                check(m["footBorderTop"] == "0px", f"footer 顶边线未移除: {m['footBorderTop']}")
                check("245, 244, 237" in m["footBg"] and "231, 238, 232" in m["footBg"], f"页脚渐变底色不对: {m['footBg'][:120]}")
                check(m["bamboo"] and m["stone"], "竹影/苔石装饰缺失")
                check(m["bambooOp"] == str(expBambooOp), f"竹影透明度 {m['bambooOp']} != {expBambooOp}")
                if len(m["cols"]) >= 3:
                    check(abs(m["cols"][0] - 1.3) < 0.05 and abs(m["cols"][2] - 1.2) < 0.05, f"三列比例 {m['cols']} != 1.3:1:1.2")
                elif fmode == "desktop":
                    fails.append(f"{w}px 桌面档应为三列, 实测 {m['cols']}")
                check(m["contMax"] == "1200px", f"内容最大宽 {m['contMax']} != 1200px")
                check(m["logoFs"] == "24px" and m["logoColor"] == "rgb(36, 70, 56)", f"品牌标题 {m['logoFs']}/{m['logoColor']}")
                check(m["colTitleFs"] == "17px", f"栏目标题 {m['colTitleFs']} != 17px")
                check(m["linkFs"] == "14px" and m["linkLh"] == "20px", f"正文链接 {m['linkFs']}/{m['linkLh']}")
                check(m["linkColor"] == "rgb(97, 118, 107)", f"正文颜色 {m['linkColor']}")
                check(m["verseColor"] == "rgb(178, 139, 87)", f"暖金点缀 {m['verseColor']}")
                check("rgba(47, 78, 60, 0.08)" in m["bottomBorder"], f"分隔线颜色 {m['bottomBorder']}")
                if fmode == "desktop":
                    check(m["innerH"] == 64, f"版权区高度 {m['innerH']} != 64")
                    check(m["contPadX"] == "24px", f"水平内边距 {m['contPadX']}")
                    check(m["gridPadTop"] == "64px" and m["gridPadBottom"] == "56px",
                          f"主内容上下内边距 {m['gridPadTop']}/{m['gridPadBottom']}")
                else:
                    check(m["gridPadTop"] == "48px" and m["gridPadBottom"] == "40px",
                          f"移动档上下内边距 {m['gridPadTop']}/{m['gridPadBottom']}")
                    check(m["gridGap"] == "28px", f"移动档组间距 {m['gridGap']} != 28px")
                check(m["ctaAfterH"] == expAfterH, f"渐隐层高度 {m['ctaAfterH']} != {expAfterH}")
                check(m["ctaAfterBg"], "渐隐层渐变色缺失")
                if fmode == "desktop":
                    check(m["innerH"] == 64, f"版权区高度 {m['innerH']} != 64")
                    check(m["contPadX"] == "24px", f"水平内边距 {m['contPadX']}")

                # 交界截图：页脚顶缝落在视口约 55% 高度处
                seam = m["footTop"]
                await p("Runtime.evaluate", {"expression": f"window.scrollTo({{top: {seam - int(h*0.55)}, behavior: 'instant'}}); 'ok'"})
                time.sleep(1.0)
                shot = await p("Page.captureScreenshot", {"format": "png"})
                open(f"{OUT}/footer-seam-{w}.png", "wb").write(base64.b64decode(shot["data"]))
                # 页脚完整截图
                await p("Runtime.evaluate", {"expression": "window.scrollTo({top: document.body.scrollHeight, behavior: 'instant'}); 'ok'"})
                time.sleep(0.8)
                shot = await p("Page.captureScreenshot", {"format": "png"})
                open(f"{OUT}/footer-full-{w}.png", "wb").write(base64.b64decode(shot["data"]))
                print(f"shots footer-seam/footer-full {w} done, seamY={seam}")

            print("FAILS:", fails if fails else "none — all assertions passed")
    asyncio.run(main())
finally:
    proc.terminate()
print("done")
