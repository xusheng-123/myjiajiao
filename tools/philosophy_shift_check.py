"""第二屏「办学理念」文字右移验收：1700/1440/1280/1024/375 五档 DOM 断言 + 视口截图。

--before 模式：只截图与量测（改前基线），不做断言。
默认模式：改后断言全跑。
"""
import base64, json, subprocess, sys, time, urllib.request

MODE = "before" if "--before" in sys.argv else "after"
OUT = r"E:/线上家教平台/线上家教平台（功能）/docs/ui-redesign-2026-10-11/images/home-sections"
TAG = "philosophy-before" if MODE == "before" else "philosophy-shift"
edge = r"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
proc = subprocess.Popen([edge, "--headless=new", "--remote-debugging-port=9228",
                         "--user-data-dir=" + r"E:/线上家教平台/线上家教平台（功能）/.edge-tmp-phil",
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
                      const lis = [...sec.querySelectorAll('.principle')];
                      const s = sec.getBoundingClientRect(), k = kick.getBoundingClientRect();
                      const t = title.getBoundingClientRect(), b = body.getBoundingClientRect();
                      const cs = getComputedStyle(c);
                      return JSON.stringify({
                        scrollW: document.documentElement.scrollWidth, vw: innerWidth,
                        secTop: Math.round(s.top + scrollY), secH: Math.round(s.height),
                        padL: cs.paddingLeft, padR: cs.paddingRight,
                        kickX: Math.round(k.left), kickTopRel: Math.round(k.top - s.top),
                        titleX: Math.round(t.left), titleFs: getComputedStyle(title).fontSize,
                        titleLh: (parseFloat(getComputedStyle(title).lineHeight) / parseFloat(getComputedStyle(title).fontSize)).toFixed(2),
                        titleH: Math.round(t.height),
                        bodyX: Math.round(b.left), bodyMaxW: getComputedStyle(body).maxWidth,
                        bodyH: Math.round(b.height),
                        liX: lis.map(li => Math.round(li.getBoundingClientRect().left)),
                        priMaxW: getComputedStyle(sec.querySelector('.principles')).maxWidth,
                        priCols: getComputedStyle(sec.querySelector('.principles')).gridTemplateColumns.split(' ').length,
                        priTopRel: Math.round(sec.querySelector('.principles').getBoundingClientRect().top - s.top),
                        priBorder: getComputedStyle(sec.querySelector('.principles')).borderTopWidth
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
                if tier == "d1600":
                    for key in ("kickX", "titleX", "bodyX"):
                        if not (405 <= m0[key] <= 435):
                            fails.append(f"{w}:{key}={m0[key]} 应≈420")
                    if not (405 <= m0["liX"][0] <= 435):
                        fails.append(f"{w}:第一组X={m0['liX'][0]} 应≈420")
                    if m0["bodyMaxW"] != "650px":
                        fails.append(f"{w}:正文max-width={m0['bodyMaxW']} 应650px")
                    if m0["priMaxW"] != "1100px":
                        fails.append(f"{w}:理念容器max-width={m0['priMaxW']} 应1100px")
                    if not (110 <= float(m0["padL"][:-2]) <= 150):
                        fails.append(f"{w}:paddingLeft={m0['padL']} 应在110–150px")
                    if w - m0["liX"][2] < 200:
                        fails.append(f"{w}:第三组距右缘不足 {w - m0['liX'][2]}px")
                elif tier == "mid":
                    if not (150 <= float(m0["padL"][:-2]) <= 260):
                        fails.append(f"{w}:中档paddingLeft={m0['padL']} 应在150–260px")
                    if m0["kickX"] < 0.20 * m0["vw"]:
                        fails.append(f"{w}:文字左缘{m0['kickX']} 未随视口比例右移（<20%vw）")
                    if m0["scrollW"] > m0["vw"]:
                        fails.append(f"{w}:横向溢出 {m0['scrollW']}>{m0['vw']}")
                elif tier == "legacy":
                    if m0["padL"] != "24px":
                        fails.append(f"{w}:≤1279 padding-left 被改动 {m0['padL']}")
                    if m0["kickX"] != 24:
                        fails.append(f"{w}:≤1279 左缘被改动 {m0['kickX']}")
                else:  # mobile 完全不变
                    if m0["padL"] != "24px" or m0["kickX"] != 24:
                        fails.append(f"{w}:移动档左缘被改动 padL={m0['padL']} kickX={m0['kickX']}")
                    if m0["priCols"] != 1:
                        fails.append(f"{w}:移动档列数={m0['priCols']} 应1")
                    if m0["titleFs"] != "30.4px":
                        fails.append(f"{w}:移动标题字号={m0['titleFs']} 应30.4px")
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
