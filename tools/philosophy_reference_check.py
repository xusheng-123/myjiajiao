"""第二屏「办学理念」参考图定稿验收（第二轮）：1700/1440/1280/1024/375 五档 DOM 断言 + 截图。

参考图几何（bg 1672 坐标，cover 宽度驱动 scale = layoutW/1672）：
  主文字左缘 X≈600 → 35.9% 布局宽；三组理念行宽≈810、行中心≈主标题视觉中心。
--before 模式：只截图与量测（改前基线 + 正文前 30/31 字宽测量，用于定 max-width）。
默认模式：改后断言全跑。
"""
import base64, json, subprocess, sys, time, urllib.request

MODE = "before" if "--before" in sys.argv else "after"
OUT = r"E:/线上家教平台/线上家教平台（功能）/docs/ui-redesign-2026-10-11/images/home-sections"
TAG = "philosophy-ref2-before" if MODE == "before" else "philosophy-ref2"
edge = r"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
proc = subprocess.Popen([edge, "--headless=new", "--remote-debugging-port=9228",
                         "--user-data-dir=" + r"E:/线上家教平台/线上家教平台（功能）/.edge-tmp-phil2",
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

            # (宽, 高, mobile, 档位)
            buckets = [(1700, 950, False, "d1600"), (1440, 900, False, "mid"),
                       (1280, 900, False, "mid"), (1024, 900, False, "legacy"), (375, 812, True, "mobile")]
            report, fails = {}, []
            for w, h, mobile, tier in buckets:
                await p("Emulation.setDeviceMetricsOverride",
                        {"width": w, "height": h, "deviceScaleFactor": 1, "mobile": mobile})
                time.sleep(1.2)
                probe = await p("Runtime.evaluate", {"expression": """
                    (() => {
                      const sec = document.querySelector('#philosophy');
                      const c = sec.querySelector('.philosophy');
                      const kick = sec.querySelector('.nur-kicker');
                      const title = sec.querySelector('.philosophy-quote');
                      const body = sec.querySelector('.philosophy-text');
                      const ul = sec.querySelector('.principles');
                      const lis = [...sec.querySelectorAll('.principle')];
                      const s = sec.getBoundingClientRect(), k = kick.getBoundingClientRect();
                      const t = title.getBoundingClientRect(), b = body.getBoundingClientRect();
                      const u = ul.getBoundingClientRect();
                      const LR = lis.map(li => li.getBoundingClientRect());
                      const tn = body.firstChild;
                      const i0 = tn.data.search(/\\S/);
                      const r30 = document.createRange(); r30.setStart(tn, i0); r30.setEnd(tn, i0 + 30);
                      const r31 = document.createRange(); r31.setStart(tn, i0); r31.setEnd(tn, i0 + 31);
                      const pt = lis[0].querySelector('.principle-title');
                      const prt = document.createRange(); prt.selectNodeContents(pt);
                      const prr = prt.getBoundingClientRect();
                      const trt = document.createRange(); trt.selectNodeContents(title);
                      const trr = trt.getBoundingClientRect();
                      const tcs = getComputedStyle(title);
                      const trail = parseFloat(tcs.letterSpacing) || 0;
                      const titleGlyphC = (trr.left + trr.right) / 2 - trail / 2;
                      const cs = getComputedStyle(c);
                      return JSON.stringify({
                        scrollW: document.documentElement.scrollWidth, vw: innerWidth,
                        secTop: Math.round(s.top + scrollY), secH: Math.round(s.height),
                        padL: cs.paddingLeft,
                        kickX: Math.round(k.left), kickTopRel: Math.round(k.top - s.top),
                        titleX: Math.round(t.left), titleW: +t.width.toFixed(1),
                        titleGlyphW: +trr.width.toFixed(1), titleGlyphC: +titleGlyphC.toFixed(1),
                        titleFs: getComputedStyle(title).fontSize,
                        titleLh: (parseFloat(getComputedStyle(title).lineHeight) / parseFloat(getComputedStyle(title).fontSize)).toFixed(2),
                        titleH: Math.round(t.height), titleTopRel: Math.round(t.top - s.top),
                        bodyX: Math.round(b.left), bodyMaxW: getComputedStyle(body).maxWidth,
                        bodyH: Math.round(b.height),
                        p30W: +r30.getBoundingClientRect().width.toFixed(1),
                        p30n: r30.getClientRects().length, p31n: r31.getClientRects().length,
                        ulL: +u.left.toFixed(1), ulR: +u.right.toFixed(1), ulW: +u.width.toFixed(1),
                        liC: LR.map(r => +(r.left + r.width / 2).toFixed(1)),
                        li1W: +LR[0].width.toFixed(1),
                        priTopRel: Math.round(u.top - s.top),
                        priBorder: getComputedStyle(ul).borderTopWidth,
                        taTitle: getComputedStyle(pt).textAlign,
                        taText: getComputedStyle(lis[0].querySelector('.principle-text')).textAlign,
                        pTitleC: +((prr.left + prr.right) / 2).toFixed(1)
                      });
                    })()""", "returnByValue": True})
                m0 = json.loads(probe["result"]["value"])
                # 滚到第二屏顶，视口截图
                await p("Runtime.evaluate", {"expression": f"scrollTo(0, {m0['secTop']})", "returnByValue": True})
                time.sleep(0.6)
                shot = await p("Page.captureScreenshot", {"format": "png"})
                with open(f"{OUT}/{TAG}-{w}.png", "wb") as f:
                    f.write(base64.b64decode(shot["data"]))
                report[w] = m0

                if MODE == "before":
                    continue
                # ---------- 改后断言（纵向基线取自 2026-10-12 改前实测，±14 容忍 reveal 抖动） ----------
                base = {"secH": {1700: 574, 1440: 574, 1280: 574, 1024: 529, 375: 712},
                        "kickTopRel": {1700: 127, 1440: 117, 1280: 117, 1024: 98, 375: 69},
                        "priTopRel": {1700: 372, 1440: 362, 1280: 362, 1024: 337, 375: 368},
                        "bodyH": {1700: 64, 1440: 64, 1280: 64, 1024: 64, 375: 96},
                        "titleH": {1700: 70, 1440: 70, 1280: 70, 1024: 65, 375: 91}}
                for key, tbl in base.items():
                    if abs(m0[key] - tbl[w]) > 14:
                        fails.append(f"{w}:纵向{key}={m0[key]} 基线{tbl[w]}±14")
                if m0["scrollW"] > m0["vw"]:
                    fails.append(f"{w}:横向溢出 {m0['scrollW']}>{m0['vw']}")
                if tier == "d1600":
                    if not (300 <= float(m0["padL"][:-2]) <= 345):
                        fails.append(f"{w}:paddingLeft={m0['padL']} 应在300–345px")
                    if not (585 <= m0["kickX"] <= 645):
                        fails.append(f"{w}:文字左缘={m0['kickX']} 应≈610（参考图35.9%vw）")
                    if abs(m0["titleX"] - m0["kickX"]) > 2 or abs(m0["bodyX"] - m0["kickX"]) > 2:
                        fails.append(f"{w}:标签/标题/正文未统一左对齐 {m0['kickX']}/{m0['titleX']}/{m0['bodyX']}")
                    if m0["bodyMaxW"] != "488px":
                        fails.append(f"{w}:正文max-width={m0['bodyMaxW']} 应488px")
                    if m0["p30n"] != 1 or m0["p31n"] != 2:
                        fails.append(f"{w}:正文换行点不对（前30字rects={m0['p30n']}，31字={m0['p31n']}）应首行止于「不催促，」")
                    if not (-140 <= m0["ulL"] - m0["kickX"] <= -105):
                        fails.append(f"{w}:理念行左伸出={m0['ulL'] - m0['kickX']} 应约-124px")
                    if not (790 <= m0["ulW"] <= 830):
                        fails.append(f"{w}:理念行宽={m0['ulW']} 应≈810px")
                    if abs((m0["ulL"] + m0["ulR"]) / 2 - m0["titleGlyphC"]) > 15:
                        fails.append(f"{w}:理念行中心{(m0['ulL'] + m0['ulR']) / 2} 与标题字心{m0['titleGlyphC']} 偏差>15px")
                    if not (530 <= m0["titleGlyphW"] <= 590):
                        fails.append(f"{w}:标题字宽={m0['titleGlyphW']} 异常（应≈557，若异常说明字号被改）")
                    sym = abs((m0["liC"][1] - m0["liC"][0]) - (m0["liC"][2] - m0["liC"][1]))
                    if sym > 1.5:
                        fails.append(f"{w}:三组列中心不对称 {sym}px")
                    if abs(m0["liC"][0] - m0["pTitleC"]) > 6:
                        fails.append(f"{w}:第一组标题未居中（列心{m0['liC'][0]} vs 字心{m0['pTitleC']}）")
                    if m0["taTitle"] != "center" or m0["taText"] != "center":
                        fails.append(f"{w}:理念组文字未居中 {m0['taTitle']}/{m0['taText']}")
                    if m0["ulR"] > m0["vw"] - 100:
                        fails.append(f"{w}:理念行距右缘不足 {m0['vw'] - m0['ulR']}px")
                elif tier == "mid":
                    pad_rng = {1440: (335, 380), 1280: (358, 400)}[w]
                    if not (pad_rng[0] <= float(m0["padL"][:-2]) <= pad_rng[1]):
                        fails.append(f"{w}:paddingLeft={m0['padL']} 应在{pad_rng}")
                    if not (0.34 <= m0["kickX"] / m0["vw"] <= 0.38):
                        fails.append(f"{w}:文字左缘比例={m0['kickX'] / m0['vw']:.3f} 应≈0.36")
                    if m0["bodyMaxW"] != "488px":
                        fails.append(f"{w}:正文max-width={m0['bodyMaxW']} 应488px")
                    if abs((m0["ulL"] + m0["ulR"]) / 2 - m0["titleGlyphC"]) > 15:
                        fails.append(f"{w}:理念行中心与标题字心偏差>15px")
                    if m0["taTitle"] != "center":
                        fails.append(f"{w}:理念组文字未居中 {m0['taTitle']}")
                    if m0["ulR"] > m0["vw"] - 80:
                        fails.append(f"{w}:理念行距右缘不足 {m0['vw'] - m0['ulR']}px")
                elif tier == "legacy":
                    if m0["padL"] != "24px":
                        fails.append(f"{w}:≤1279 padding-left 被改动 {m0['padL']}")
                    if m0["kickX"] != 24:
                        fails.append(f"{w}:≤1279 左缘被改动 {m0['kickX']}")
                    if m0["bodyMaxW"] != "620px":
                        fails.append(f"{w}:≤1279 正文max-width被改动 {m0['bodyMaxW']}")
                    if m0["taTitle"] == "center":
                        fails.append(f"{w}:≤1279 理念文字对齐被改动")
                else:  # mobile 完全不变
                    if m0["padL"] != "24px" or m0["kickX"] != 24:
                        fails.append(f"{w}:移动档左缘被改动 padL={m0['padL']} kickX={m0['kickX']}")
                    if m0["titleFs"] != "30.4px":
                        fails.append(f"{w}:移动标题字号={m0['titleFs']} 应30.4px")
                    if m0["bodyMaxW"] != "620px":
                        fails.append(f"{w}:移动正文max-width被改动 {m0['bodyMaxW']}")
                    if m0["taTitle"] == "center":
                        fails.append(f"{w}:移动理念文字对齐被改动")
                    if m0["scrollW"] != 375:
                        fails.append(f"{w}:scrollW={m0['scrollW']} 应375")
                # 全档：标题字号行高锁死（桌面 clamp 封顶 46.4 / 1024 4.2vw / 375 1.9rem）
                want_fs = {1700: "46.4px", 1440: "46.4px", 1280: "46.4px", 1024: "43.008px", 375: "30.4px"}
                if m0["titleFs"] != want_fs[w] or m0["titleLh"] != "1.50":
                    fails.append(f"{w}:标题字号/行高被改 {m0['titleFs']}/{m0['titleLh']}")
                if m0["priBorder"] != "1px":
                    fails.append(f"{w}:理念区分隔线丢失")
            await p("Runtime.evaluate", {"expression": "scrollTo(0,0)", "returnByValue": True})
            print(json.dumps(report, ensure_ascii=False, indent=1))
            if MODE == "after":
                print("FAILS:", fails if fails else "none — all assertions passed")
    asyncio.run(main())
finally:
    proc.terminate()
