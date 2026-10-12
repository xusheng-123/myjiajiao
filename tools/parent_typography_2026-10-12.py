# -*- coding: utf-8 -*-
"""家长端整页排版修复轮验收（2026-10-12 第三轮，前两轮为宽屏改造 + 间距精修）。

用法：
  python tools/parent_typography_2026-10-12.py snapshot <out.json>   # 375×812 关键元素 computed 快照
  python tools/parent_typography_2026-10-12.py verify <baseline.json>  # 桌面断言+整页截图+手机端前后比对

断言口径（任务书《家长端桌面页面完整排版修复》）：
  ≥1280：模块 padding 28px(上下)/32px(左右)；标题 22px/700；说明 15px；统计徽标 14px；
        表格 15px/表头 15px/600；单元格 16×18、行高 ≥54(钱包)/≥56(订单)；金额 17px；
        状态/类型标签 14px；绑定输入框 48px；充值按钮 40px/组间距 12px；
        家庭订单表列宽 30/22/20/28%；通知 toggle 20×24px/标题 16px。
  1024–1279：模块 padding 24px。
  375：快照与改前逐字段一致（手机端零改动实证）。
"""
import base64, json, subprocess, sys, time, urllib.request, http.client, os

ROOT = r"E:/线上家教平台/线上家教平台（功能）"
OUT = ROOT + "/docs/screenshots-2026-10-12-parent"
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
    conn.close()
    assert cookie, f"{role} 登录失败"
    return cookie, body.get("accountNo", ""), body.get("displayName", account)

COOKIE, ACCT, DISP = api_login("PARENT", "13900000003", "Parent#2026")
print("家长 API 登录成功:", ACCT, DISP)

# 与手机端零改动比对相关、同时服务桌面断言的探针：家长页全量关键元素
PROBE = r"""(() => {
  const num = v => (v === null || v === undefined || v === '') ? null : (Math.round(parseFloat(v) * 10) / 10);
  const box = el => { if (!el) return null; const r = el.getBoundingClientRect();
    return { x: Math.round(r.left * 10) / 10, right: Math.round(r.right * 10) / 10,
             w: Math.round(r.width * 10) / 10, h: Math.round(r.height * 10) / 10 }; };
  const st = (el, props) => { if (!el) return null; const c = getComputedStyle(el); const o = {};
    for (const p of props) o[p] = c.getPropertyValue(p); return o; };
  const q = s => document.querySelector(s);
  const qa = s => [...document.querySelectorAll(s)];
  const out = {};
  out.vw = window.innerWidth;
  out.scrollW = document.documentElement.scrollWidth;
  const shell = q('.dashboard-shell');
  out.shell = st(shell, ['max-width', 'width']);

  // 四大主模块 padding 与几何
  const secs = qa('.dashboard-shell > section.nur-card');
  out.sections = secs.map(s => { const c = getComputedStyle(s);
    return { pt: c.paddingTop, pr: c.paddingRight, pb: c.paddingBottom, pl: c.paddingLeft, box: box(s) }; });

  // 模块头：标题 / 说明 / 右侧徽标（首模块=绑定的子女，次模块=钱包，第三=课程与订单）
  out.heads = secs.map(s => {
    const t = s.querySelector('.card-title'); const d = s.querySelector('.card-desc');
    const b = s.querySelector('.count-badge, .gold-badge');
    return { title: t ? { box: box(t), ...st(t, ['font-size', 'font-weight']) } : null,
             desc: d ? { box: box(d), ...st(d, ['font-size', 'margin-top', 'line-height']) } : null,
             badge: b ? { box: box(b), ...st(b, ['font-size']) } : null,
             headMB: st(s.querySelector('.card-head'), ['margin-bottom']) };
  });

  // 子女卡 / 绑定表单
  out.childCard = q('.child-card') ? { ...st(q('.child-card'), ['padding', 'gap']),
    pill: st(q('.child-card .status-pill'), ['font-size']) } : null;
  const bind = q('.bind-new-card');
  out.bind = bind ? { ...st(bind, ['padding', 'gap']),
    titleFs: st(q('.bind-title'), ['font-size']),
    noteFs: st(bind.querySelector('.quiet-note'), ['font-size']),
    rowMt: st(q('.bind-form-row'), ['margin-top']), rowGap: st(q('.bind-form-row'), ['gap']),
    inputH: box(q('.bind-input')) ? box(q('.bind-input')).h : null,
    btnFs: st(q('.bind-form-row .nur-btn'), ['font-size']) } : null;

  // 钱包
  out.wallet = { row: st(q('.wallet-overview-row'), ['padding']),
    label: st(q('.balance-label'), ['font-size']),
    number: st(q('.balance-number'), ['font-size']),
    groupGap: st(q('.topup-group'), ['gap']),
    btn: (() => { const b = q('.topup-group .nur-btn'); return b ? { h: box(b).h, ...st(b, ['font-size']) } : null; })() };

  // 表格（钱包表 + 家庭订单表）
  const wt = q('.txn-table');
  out.walletTable = wt ? { fs: st(wt, ['font-size']),
    th: (() => { const th = wt.querySelector('th'); return { ...st(th, ['font-size', 'font-weight', 'padding']), box: box(th) }; })(),
    td: (() => { const td = wt.querySelector('tbody td'); return { ...st(td, ['padding', 'line-height', 'font-size']) }; })(),
    rowH: box(wt.querySelector('tbody tr')) ? box(wt.querySelector('tbody tr')).h : null,
    pill: st(wt.querySelector('.type-pill'), ['font-size']) } : null;
  const ft = q('.family-orders-block .txn-table');
  out.familyTable = ft ? { fs: st(ft, ['font-size']),
    ths: qa('.family-orders-block .txn-table th').map(th => ({ w: box(th).w, ...st(th, ['font-size', 'font-weight']) })),
    tds: qa('.family-orders-block .txn-table tbody tr:first-child td').map(td => ({ ...st(td, ['padding', 'font-size', 'font-variant-numeric']) })),
    rowH: box(ft.querySelector('tbody tr')) ? box(ft.querySelector('tbody tr')).h : null,
    amountFs: st(ft.querySelector('.amount-col'), ['font-size']),
    monoFs: st(ft.querySelector('.font-mono'), ['font-size']),
    statusFs: st(ft.querySelector('.status-pill'), ['font-size']),
    guideFs: st(ft.querySelector('.guide-tag'), ['font-size']),
    quietFs: st(ft.querySelector('td .quiet-note'), ['font-size']) } : null;

  // 订单查询条
  out.orderBar = q('.order-select-bar') ? { ...st(q('.order-select-bar'), ['padding', 'gap']),
    labelFs: st(q('.order-select-bar .form-label'), ['font-size']),
    selectH: box(q('.select-child-ctrl')) ? box(q('.select-child-ctrl')).h : null,
    btnH: box(q('.btn-query-orderable')) ? box(q('.btn-query-orderable')).h : null,
    btnFs: st(q('.btn-query-orderable'), ['font-size']) } : null;

  // 进度保障模块说明文字
  const progSec = secs[3];
  out.progress = progSec ? { note: st(progSec.querySelector('.quiet-note'), ['font-size', 'line-height', 'max-width']) } : null;

  // 通知中心（共享组件，家长端作用域覆盖）
  const nt = q('nur-notice-center .notice-toggle');
  out.notice = nt ? { ...st(nt, ['padding', 'font-size']), box: box(nt) } : null;
  out.noticeCard = q('nur-notice-center .notice-card') ? box(q('nur-notice-center .notice-card')) : null;

  // 按钮 sm 体系
  const smBtn = q('.dashboard-shell .nur-btn--sm');
  out.smBtn = smBtn ? { h: box(smBtn).h, ...st(smBtn, ['font-size']) } : null;

  out.sub = st(q('.section-sub'), ['font-size', 'margin-top']);
  return out;
})()"""

async def drive(mode, out_path):
    import websockets
    edge = r"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
    proc = subprocess.Popen([edge, "--headless=new", "--remote-debugging-port=9235",
                             "--user-data-dir=" + ROOT + "/.edge-tmp-parent1",
                             "--no-first-run", "--window-size=1700,900", "about:blank"],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        ws_url = None
        for _ in range(30):
            try:
                with urllib.request.urlopen("http://127.0.0.1:9235/json/version", timeout=2) as resp:
                    ws_url = json.loads(resp.read())["webSocketDebuggerUrl"]
                break
            except Exception:
                time.sleep(0.5)
        assert ws_url, "Edge CDP 启动失败"
        async with websockets.connect(ws_url, max_size=128 * 1024 * 1024) as ws:
            mid = 0
            async def send(method, params=None, sid=None):
                nonlocal mid; mid += 1
                msg = {"id": mid, "method": method, "params": params or {}}
                if sid: msg["sessionId"] = sid
                await ws.send(json.dumps(msg))
                while True:
                    raw = json.loads(await ws.recv())
                    if raw.get("id") == mid:
                        return raw.get("result", {})
            tgt = await send("Target.createTarget", {"url": "about:blank"})
            att = await send("Target.attachToTarget", {"targetId": tgt["targetId"], "flatten": True})
            sid = att["sessionId"]
            async def page(method, params=None):
                return await send(method, params, sid)
            await page("Page.enable"); await page("Runtime.enable"); await page("Network.enable")

            # 预热原点再注入会话（about:blank 注入不生效的历史坑）
            await page("Page.navigate", {"url": "http://localhost:4200/"})
            time.sleep(3)
            await page("Network.setCookie", {"name": "NURTURE_AT", "value": COOKIE,
                                             "domain": "localhost", "path": "/api", "httpOnly": True})
            await page("Runtime.evaluate", {"expression":
                "localStorage.setItem('nurture.session', " + json.dumps(json.dumps({
                    "accountNo": ACCT, "displayName": DISP, "role": "PARENT",
                    "expiresAtEpochMs": int(time.time() * 1000) + 60 * 60 * 1000})) + "); 'ok'"})

            async def goto_parent(w, h):
                await page("Emulation.setDeviceMetricsOverride",
                           {"width": w, "height": h, "deviceScaleFactor": 1, "mobile": w < 800})
                await page("Page.navigate", {"url": "http://localhost:4200/parent"})
                time.sleep(4)
                r = await page("Runtime.evaluate", {"expression": PROBE, "returnByValue": True})
                return r["result"]["value"]

            async def shot(name, full=True):
                params = {"format": "png"}
                if full:
                    params["captureBeyondViewport"] = True
                r = await page("Page.captureScreenshot", params)
                with open(OUT + "/" + name, "wb") as f:
                    f.write(base64.b64decode(r["data"]))

            if mode == "snapshot":
                data = await goto_parent(375, 812)
                await shot("parent-mobile-375.png", full=False)  # 视口截图（拼接伪影坑）
                with open(out_path, "w", encoding="utf-8") as f:
                    json.dump(data, f, ensure_ascii=False, indent=1)
                print("mobile snapshot ->", out_path)
                return

            # ---- verify 模式 ----
            baseline_path = out_path
            results, fails = [], []
            def check(name, cond, detail=""):
                results.append((name, cond, detail))
                if not cond: fails.append(f"{name}: {detail}")

            # 桌面三档
            for w, h, tag in [(1700, 900, "1700"), (1440, 900, "1440"), (1280, 800, "1280")]:
                d = await goto_parent(w, h)
                check(f"[{tag}] 零横向溢出", d["scrollW"] <= d["vw"], f"scrollW={d['scrollW']} vw={d['vw']}")
                for i, s in enumerate(d["sections"]):
                    check(f"[{tag}] 模块{i+1} padding 28/32",
                          s["pt"] == "28px" and s["pb"] == "28px" and s["pl"] == "32px" and s["pr"] == "32px",
                          f"{s['pt']}/{s['pr']}/{s['pb']}/{s['pl']}")
                for i, hd in enumerate(d["heads"]):
                    if hd["title"]:
                        check(f"[{tag}] 模块{i+1} 标题 22px/700",
                              hd["title"]["font-size"] == "22px" and hd["title"]["font-weight"] == "700",
                              f"{hd['title']['font-size']}/{hd['title']['font-weight']}")
                    if hd["desc"]:
                        check(f"[{tag}] 模块{i+1} 说明 15px/mt8",
                              hd["desc"]["font-size"] == "15px" and hd["desc"]["margin-top"] == "8px",
                              f"{hd['desc']['font-size']}/{hd['desc']['margin-top']}")
                    if hd["badge"]:
                        sec = d["sections"][i]
                        gap = sec["box"]["right"] - hd["badge"]["box"]["right"]
                        check(f"[{tag}] 模块{i+1} 徽标 14px/右距≥28",
                              hd["badge"]["font-size"] == "14px" and gap >= 28,
                              f"fs={hd['badge']['font-size']} rightGap={round(gap,1)}")
                    if hd["headMB"]:
                        check(f"[{tag}] 模块{i+1} 头部下距 20px", hd["headMB"]["margin-bottom"] == "20px",
                              hd["headMB"]["margin-bottom"])
                    # 左对齐基线：标题与说明共线，且等于模块内容左缘
                    if hd["title"] and hd["desc"]:
                        check(f"[{tag}] 模块{i+1} 标题-说明共线±1.5",
                              abs(hd["title"]["box"]["x"] - hd["desc"]["box"]["x"]) <= 1.5,
                              f"{hd['title']['box']['x']} vs {hd['desc']['box']['x']}")
                        target = d["sections"][i]["box"]["x"] + 32
                        check(f"[{tag}] 模块{i+1} 标题对齐内容左缘±1.5",
                              abs(hd["title"]["box"]["x"] - target) <= 1.5,
                              f"title={hd['title']['box']['x']} target={round(target,1)}")

                # 子女卡与绑定表单
                cc = d["childCard"]
                if cc:
                    check(f"[{tag}] 子女卡 padding 24", cc["padding"] == "24px", cc["padding"])
                    check(f"[{tag}] 子女卡状态标签 14px", cc["pill"]["font-size"] == "14px", cc["pill"]["font-size"])
                b = d["bind"]
                if b:
                    check(f"[{tag}] 绑定卡 padding 24/gap 12", b["padding"] == "24px" and b["gap"] == "12px",
                          f"{b['padding']}/{b['gap']}")
                    check(f"[{tag}] 绑定输入框高 48", abs(b["inputH"] - 48) <= 2, str(b["inputH"]))
                    check(f"[{tag}] 说明-表单行 20px",
                          b["rowMt"]["margin-top"] == "8px" and b["rowGap"]["gap"] == "12px",
                          f"mt={b['rowMt']['margin-top']} gap={b['rowGap']['gap']}")
                    check(f"[{tag}] 绑定按钮 15px", b["btnFs"]["font-size"] == "15px", b["btnFs"]["font-size"])

                # 钱包
                wl = d["wallet"]
                check(f"[{tag}] 钱包行 padding 24", wl["row"]["padding"] == "24px", wl["row"]["padding"])
                check(f"[{tag}] 余额说明 15px/金额 35.2px", wl["label"]["font-size"] == "15px",
                      wl["label"]["font-size"])
                check(f"[{tag}] 充值组 gap 12", wl["groupGap"]["gap"] == "12px", wl["groupGap"]["gap"])
                check(f"[{tag}] 充值按钮 40px/15px", abs(wl["btn"]["h"] - 40) <= 2 and wl["btn"]["font-size"] == "15px",
                      f"{wl['btn']['h']}/{wl['btn']['font-size']}")

                # 钱包表格
                wt = d["walletTable"]
                check(f"[{tag}] 钱包表 15px", wt["fs"]["font-size"] == "15px", wt["fs"]["font-size"])
                check(f"[{tag}] 钱包表头 15px/600/16×18",
                      wt["th"]["font-size"] == "15px" and wt["th"]["font-weight"] == "600"
                      and wt["th"]["padding"] == "16px 18px",
                      f"{wt['th']['font-size']}/{wt['th']['font-weight']}/{wt['th']['padding']}")
                check(f"[{tag}] 钱包行高 ≥54", wt["rowH"] >= 54, str(wt["rowH"]))
                check(f"[{tag}] 类型标签 14px", wt["pill"]["font-size"] == "14px", wt["pill"]["font-size"])

                # 家庭订单表
                ft = d["familyTable"]
                if ft:
                    check(f"[{tag}] 订单表行高 ≥56", ft["rowH"] >= 56, str(ft["rowH"]))
                    check(f"[{tag}] 金额列 17px", ft["amountFs"]["font-size"] == "17px", ft["amountFs"]["font-size"])
                    check(f"[{tag}] 订单号 15px", ft["monoFs"]["font-size"] == "15px", ft["monoFs"]["font-size"])
                    check(f"[{tag}] 状态标签 14px", ft["statusFs"]["font-size"] == "14px", ft["statusFs"]["font-size"])
                    tw = ft["ths"] and sum(t["w"] for t in ft["ths"]) or 1
                    ratios = [round(t["w"] / tw * 100, 1) for t in ft["ths"]]
                    if len(ratios) == 4:
                        check(f"[{tag}] 订单表列宽 30/22/20/28±2",
                              all(abs(a - e) <= 2 for a, e in zip(ratios, [30, 22, 20, 28])), str(ratios))

                # 订单查询条
                ob = d["orderBar"]
                if ob:
                    check(f"[{tag}] 查询条 padding 24/gap 12", ob["padding"] == "24px" and ob["gap"] == "12px",
                          f"{ob['padding']}/{ob['gap']}")

                # 进度保障说明
                pg = d["progress"]
                if pg and pg["note"]:
                    lh = pg["note"]["line-height"]
                    lh_px = float(lh.replace("px", "")) if lh.endswith("px") else None
                    check(f"[{tag}] 保障说明 15px/1.7(25.5px)/限宽≤1100",
                          pg["note"]["font-size"] == "15px" and lh_px is not None and abs(lh_px - 25.5) < 1
                          and 1000 <= float(pg["note"]["max-width"].replace("px", "")) <= 1100,
                          f"{pg['note']['font-size']}/{lh}/{pg['note']['max-width']}")

                # 通知中心
                nt = d["notice"]
                if nt:
                    check(f"[{tag}] 通知 toggle 20×24/16px",
                          nt["padding"] == "20px 24px" and nt["font-size"] == "16px",
                          f"{nt['padding']}/{nt['font-size']}")
                    if d["noticeCard"] and d["noticeCard"]["x"] is not None:
                        secx = d["sections"][0]["box"]["x"]
                        check(f"[{tag}] 通知卡与模块左缘对齐±1.5",
                              abs(d["noticeCard"]["x"] - secx) <= 1.5,
                              f"{d['noticeCard']['x']} vs {secx}")

                check(f"[{tag}] sm 按钮 40px/15px",
                      abs(d["smBtn"]["h"] - 40) <= 2 and d["smBtn"]["font-size"] == "15px",
                      f"{d['smBtn']['h']}/{d['smBtn']['font-size']}")
                await shot(f"parent-full-{tag}.png")

            # 中档 1024–1279：模块 24px，标题维持基础 20px/600
            d = await goto_parent(1024, 768)
            check("[1024] 零横向溢出", d["scrollW"] <= d["vw"], f"{d['scrollW']}/{d['vw']}")
            ok24 = all(s["pt"] == "24px" and s["pl"] == "24px" for s in d["sections"])
            check("[1024] 模块 padding 24", ok24, str([(s['pt'], s['pl']) for s in d['sections']][:1]))
            check("[1024] 标题保持 20px/600（中档不动）",
                  d["heads"][0]["title"]["font-size"] == "20px" and d["heads"][0]["title"]["font-weight"] == "600",
                  f"{d['heads'][0]['title']['font-size']}/{d['heads'][0]['title']['font-weight']}")
            await shot("parent-full-1024.png")

            # 手机端 375：改后快照与基线逐字段一致
            after = await goto_parent(375, 812)
            with open(ROOT + "/tools/_parent_mobile_after.json", "w", encoding="utf-8") as f:
                json.dump(after, f, ensure_ascii=False, indent=1)
            with open(baseline_path, encoding="utf-8") as f:
                before = json.load(f)
            check("[375] 手机端快照与改前零差异", before == after,
                  "PASS" if before == after else _first_diff(before, after))
            await shot("parent-mobile-375.png", full=False)

            npass = sum(1 for _, c, _ in results if c)
            print(f"\n===== 家长端排版验收：{npass}/{len(results)} 通过 =====")
            for name, cond, detail in results:
                print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {detail}"))
            return 0 if not fails else 1
    finally:
        proc.terminate()

def _first_diff(a, b, path="$"):
    if type(a) != type(b): return f"{path}: 类型 {type(a).__name__}!={type(b).__name__}"
    if isinstance(a, dict):
        for k in set(a) | set(b):
            if k not in a: return f"{path}.{k}: 仅改后出现={b[k]}"
            if k not in b: return f"{path}.{k}: 改后丢失"
            d = _first_diff(a[k], b[k], f"{path}.{k}")
            if d: return d
        return ""
    if isinstance(a, list):
        if len(a) != len(b): return f"{path}: 长度 {len(a)}!={len(b)}"
        for i, (x, y) in enumerate(zip(a, b)):
            d = _first_diff(x, y, f"{path}[{i}]")
            if d: return d
        return ""
    return "" if a == b else f"{path}: {a!r}!={b!r}"

def asyncio_run_drive(mode, out):
    import asyncio
    return asyncio.run(drive(mode, out))

if __name__ == "__main__":
    mode = sys.argv[1] if len(sys.argv) > 1 else "verify"
    out = sys.argv[2] if len(sys.argv) > 2 else ROOT + "/tools/_parent_mobile_baseline.json"
    rc = asyncio_run_drive(mode, out)
    sys.exit(rc or 0)
