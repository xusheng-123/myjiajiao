"""首页 2–9 屏背景清晰度二轮验收（2026-10-12）：真实浏览器抓屏 + 结构/衬托/面纱断言。

--before 模式：只抓基线截图与量测（写 tools/_garden_clarity_baseline.json）。
默认模式：改后断言（用户定稿「图片保持原摄影感，文字衬托加回」）——
  · 各屏 offsetTop/height 与基线逐字段相等（结构性零改动）；
  · 7 处文字衬托（6 处 ::before 羽化 + 1 处 pill 元素底）backgroundColor 与设计值相等
    且 backdrop-filter == none（::before 级探测，修元素级读数盲区）；
  · 桌面档 2–9 屏整屏白面纱清零（background-image 无任何 linear-gradient 纱层）；
  · hero 视口截图与基线感知级一致（第一屏绝对不动）。
"""
import base64, json, os, re, subprocess, sys, time, urllib.request

MODE = "after" if "--before" not in sys.argv else "before"
ROOT = r"E:/线上家教平台/线上家教平台（功能）"
OUT = ROOT + "/docs/ui-redesign-2026-10-11/images/home-clarity"
TAG = "clarity-before" if MODE == "before" else "clarity-after"
BASELINE_JSON = ROOT + "/tools/_garden_clarity_baseline.json"
edge = r"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"

SECTIONS = [("hero", ".hero"), ("philosophy", "#philosophy"), ("services", "#services"),
            ("teachers", "#teachers"), ("subjects", "#subjects"), ("process", "#process"),
            ("voices", "#voices"), ("vision", ".nur-section.vision"),
            ("cta", ".nur-section.cta"), ("footer", "nur-site-footer")]
MOBILE_KEYS = ["philosophy", "process", "vision", "cta", "footer"]
# 文字衬托清单：::before 羽化层按伪元素探测，pill 为真元素底（值=设计锁定值）
FROST_PSEUDO = {"#services .nur-section-head": "rgba(247, 248, 244, 0.58)",
                "#teachers .nur-section-head": "rgba(247, 248, 244, 0.58)",
                "#subjects .nur-section-head": "rgba(247, 248, 244, 0.58)",
                "#process .nur-section-head": "rgba(247, 248, 244, 0.58)",
                "#voices .nur-section-head": "rgba(247, 248, 244, 0.58)",
                "#process .steps-grid": "rgba(247, 248, 244, 0.52)",
                ".nur-section.vision .vision-inner": "rgba(247, 248, 244, 0.72)"}
FROST_ELEMENT = {".teachers-more .nur-link-arrow": "rgba(255, 255, 255, 0.72)"}
# 整屏白面纱必须清零的桌面档摄影屏（hero 不动、footer 自有渐变不在列）
VEIL_KEYS = ["philosophy", "services", "teachers", "subjects", "process", "voices", "vision", "cta"]

os.makedirs(OUT, exist_ok=True)
proc = subprocess.Popen([edge, "--headless=new", "--remote-debugging-port=9228",
                         "--user-data-dir=" + ROOT + "/.edge-tmp-garden2/prof",
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

            async def shoot(tag, w, mobile, keys):
                await p("Emulation.setDeviceMetricsOverride",
                        {"width": w, "height": 950 if w == 1700 else 812,
                         "deviceScaleFactor": 1, "mobile": mobile})
                time.sleep(1.2)
                report = {}
                for key, sel in SECTIONS:
                    if key not in keys:
                        continue
                    probe = await p("Runtime.evaluate", {"expression": f"""
                        (() => {{
                          const el = document.querySelector({json.dumps(sel)});
                          if (!el) return JSON.stringify({{missing: true}});
                          const r = el.getBoundingClientRect();
                          const top = Math.round(r.top + scrollY);
                          const data = {{secTop: top, secH: Math.round(r.height),
                                        scrollW: document.documentElement.scrollWidth,
                                        clientW: document.documentElement.clientWidth}};
                          if ({'true' if w == 1700 else 'false'}) {{
                            data.bgImg = getComputedStyle(el).backgroundImage;
                            data.frost = {{}};
                            for (const [fs] of Object.entries({json.dumps(FROST_PSEUDO)})) {{
                              const fe = document.querySelector(fs);
                              if (!fe) {{ data.frost[fs] = 'NOEL'; continue; }}
                              const cs = getComputedStyle(fe, '::before');
                              data.frost[fs] = cs.backgroundColor + ' | bf=' + cs.backdropFilter;
                            }}
                            for (const [fs] of Object.entries({json.dumps(FROST_ELEMENT)})) {{
                              const fe = document.querySelector(fs);
                              if (!fe) {{ data.frost[fs] = 'NOEL'; continue; }}
                              const cs = getComputedStyle(fe);
                              data.frost[fs] = cs.backgroundColor + ' | bf=' + cs.backdropFilter;
                            }}
                          }}
                          return JSON.stringify(data);
                        }})()""", "returnByValue": True})
                    m = json.loads(probe["result"]["value"])
                    if m.get("missing"):
                        report[key] = m
                        continue
                    y = m["secTop"] if key != "footer" else "max"
                    await p("Runtime.evaluate", {"expression":
                        f"scrollTo(0, {'document.documentElement.scrollHeight' if y == 'max' else y})",
                        "returnByValue": True})
                    time.sleep(0.85)
                    shot = await p("Page.captureScreenshot", {"format": "png"})
                    with open(f"{OUT}/{tag}-{w}-{key}.png", "wb") as f:
                        f.write(base64.b64decode(shot["data"]))
                    report[key] = m
                return report

            base = {}
            base["desktop"] = await shoot(TAG, 1700, False, [k for k, _ in SECTIONS])
            base["mobile"] = await shoot(TAG, 375, True, MOBILE_KEYS)

            with open(BASELINE_JSON if MODE == "before" else ROOT + "/tools/_garden_clarity_after.json",
                      "w", encoding="utf-8") as f:
                json.dump(base, f, ensure_ascii=False, indent=1)

            fails = []
            if MODE == "after":
                with open(BASELINE_JSON, encoding="utf-8") as f:
                    before = json.load(f)
                for tier in ("desktop", "mobile"):
                    for key, m in base[tier].items():
                        b = before[tier].get(key, {})
                        if b.get("secTop") is not None and (b.get("secTop") != m.get("secTop") or b.get("secH") != m.get("secH")):
                            fails.append(f"STRUCT {tier}/{key}: top {b.get('secTop')}->{m.get('secTop')} h {b.get('secH')}->{m.get('secH')}")
                        if m.get("scrollW", 0) > m.get("clientW", 0):
                            fails.append(f"OVERFLOW {tier}/{key}: scrollW={m.get('scrollW')} clientW={m.get('clientW')}")
                for key, m in base["desktop"].items():
                    expected = {**FROST_PSEUDO, **FROST_ELEMENT}
                    for fs, val in (m.get("frost") or {}).items():
                        if val == 'NOEL':
                            fails.append(f"FROST {key}: {fs} element missing")
                            continue
                        bg, _, bf = val.partition(' | bf=')
                        if bf != "none":
                            fails.append(f"BLUR {key}: {fs} backdrop-filter={bf}")
                        if bg.replace(' ', '') != expected[fs].replace(' ', ''):
                            fails.append(f"SCRIM {key}: {fs} bg={bg} expected={expected[fs]}")
                    stops = [float(a) for a in re.findall(r"rgba\(\s*\d+\s*,\s*\d+\s*,\s*\d+\s*,\s*([\d.]+)\)", m.get("bgImg", ""))]
                    if key in VEIL_KEYS and stops:
                        fails.append(f"VEIL {key}: veil stops remain {stops[:4]}")
                # hero 视口截图与基线一致性（第一屏绝对不动）：
                # 跨 Edge 会话存在确定性光栅化抖动（同状态两轮逐字节相同、跨状态零几何偏移但
                # 高对比边缘 1–2 级 AA 差），故采用感知级判据：标题区行/列投影 shift0 相关 ≥0.999
                # + 全帧均值差 <2/255 + 差异>32 的像素占比 <1%；git 层面 hero 相关文件零改动另证。
                def hero_metrics(pa, pb):
                    from PIL import Image, ImageChops
                    da = Image.open(pa).convert("RGB"); db = Image.open(pb).convert("RGB")
                    if da.size != db.size:
                        return {"fatal": "size mismatch"}
                    # 只对比 hero 本体（secH=900）：视口 950 底部 50px 是第二屏顶部条带，
                    # 面纱撤除后该条带必然变化，不计入 hero 一致性
                    da = da.crop((0, 0, da.size[0], 900)); db = db.crop((0, 0, db.size[0], 900))
                    ga, gb = da.convert("L"), db.convert("L")
                    box = (290, 260, 830, 360)
                    W = box[2] - box[0]; H = box[3] - box[1]
                    def rows(g):
                        px = g.crop(box).load()
                        return [sum(px[x, y] for x in range(W)) / W for y in range(H)]
                    def cc(x, y):
                        n = len(x); mx = sum(x) / n; my = sum(y) / n
                        num = sum((i - mx) * (j - my) for i, j in zip(x, y))
                        de = (sum((i - mx) ** 2 for i in x) * sum((j - my) ** 2 for j in y)) ** 0.5
                        return num / de if de else 0
                    hist = ImageChops.difference(da, db).convert("L").histogram()
                    total = da.size[0] * da.size[1]
                    return {"corr": cc(rows(ga), rows(gb)),
                            "mean": sum(i * h for i, h in enumerate(hist)) / total,
                            "over32": sum(hist[33:]) / total}
                hp, ha = f"{OUT}/clarity-before-1700-hero.png", f"{OUT}/clarity-after-1700-hero.png"
                hm = hero_metrics(hp, ha)
                print("hero-metrics:", {k: round(v, 5) for k, v in hm.items()})
                if hm.get("fatal") or hm["corr"] < 0.999 or hm["mean"] >= 2 or hm["over32"] >= 0.01:
                    fails.append(f"HERO: differs from baseline beyond perceptual tolerance {hm}")
            print(("FAILS: " + "; ".join(fails)) if fails else "FAILS: none",
                  "| sections:", {k: (v.get("secH"), round(max([float(a) for a in re.findall(r'rgba\(\s*\d+\s*,\s*\d+\s*,\s*\d+\s*,\s*([\d.]+)\)', v.get('bgImg','')) ] or [0]), 2)) for k, v in base["desktop"].items()})

    asyncio.run(main())
finally:
    proc.terminate()
