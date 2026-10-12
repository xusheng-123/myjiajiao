# -*- coding: utf-8 -*-
"""桌面间距精修轮验收（2026-10-12 第二轮）。
断言口径（仅 ≥1024px 生效的新规则）：
  ≥1280：主模块左右 padding 32 / 上下 24；业务卡 24；紧凑卡 20；
        预约双列 gap 20/24；标题-筛选栏-卡片共线；右侧统计 ≥28px 安全边距。
  1024–1279：主模块 24；业务卡 20。
  全尺寸零横向溢出。移动端零改动由 spacing_probe_mobile.py 前后比对证明。
产出：docs/screenshots-2026-10-12-desktop/spacing-*.png + stdout 报告。
"""
import base64, json, subprocess, time, urllib.request, http.client, os

OUT = r"E:/线上家教平台/线上家教平台（功能）/docs/screenshots-2026-10-12-desktop"
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

ROLES = {
    "student": api_login("STUDENT", "13900000002", "Student#2026"),
    "parent":  api_login("PARENT",  "13900000003", "Parent#2026"),
    "teacher": api_login("TEACHER", "13900000001", "Teacher#2026"),
    "admin":   api_login("ADMIN",   "13800000000", "Admin#2026"),
}
print("四角色 API 登录成功")

edge = r"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
proc = subprocess.Popen([edge, "--headless=new", "--remote-debugging-port=9229",
                         "--user-data-dir=" + r"E:/线上家教平台/线上家教平台（功能）/.edge-tmp-sp2",
                         "--no-first-run", "--window-size=1920,1080", "about:blank"],
                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
report = []
try:
    ws_url = None
    for _ in range(30):
        try:
            with urllib.request.urlopen("http://127.0.0.1:9229/json/version", timeout=2) as resp:
                ws_url = json.loads(resp.read())["webSocketDebuggerUrl"]
            break
        except Exception:
            time.sleep(0.5)
    import websockets

    async def main():
        async with websockets.connect(ws_url, max_size=128 * 1024 * 1024) as ws:
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

            async def set_session(role_key):
                cookie, acct, disp = ROLES[role_key]
                role_map = {"student": "STUDENT", "parent": "PARENT", "teacher": "TEACHER", "admin": "ADMIN"}
                await page("Network.setCookie", {"name": "NURTURE_AT", "value": cookie,
                                                 "domain": "localhost", "path": "/api", "httpOnly": True})
                summary = json.dumps({"accountNo": acct, "displayName": disp, "role": role_map[role_key],
                                      "expiresAtEpochMs": int(time.time() * 1000) + 60 * 60 * 1000})
                await page("Runtime.evaluate",
                           {"expression": f"localStorage.setItem('nurture.session', {json.dumps(summary)}); 'ok'"})

            await page("Page.navigate", {"url": "http://localhost:4200/"})
            time.sleep(3.5)

            # 各角色量测探针：返回关键模块的 computed padding / 对齐坐标 / gap / 溢出
            PROBE = r"""(() => {
              const num = v => v === null ? null : Math.round(v * 10) / 10;
              const box = el => { if (!el) return null; const r = el.getBoundingClientRect();
                const cs = getComputedStyle(el);
                return { x: num(r.left), right: num(r.right), w: num(r.width),
                         pt: cs.paddingTop, pr: cs.paddingRight, pb: cs.paddingBottom, pl: cs.paddingLeft }; };
              const out = { url: location.pathname, scrollW: document.documentElement.scrollWidth,
                            vw: window.innerWidth };
              const q = s => document.querySelector(s);
              if (location.pathname.startsWith('/student')) {
                const mod = box(q('.bookings-card')); out.module = mod;
                const title = q('.bookings-card .card-title');
                out.titleX = title ? num(title.getBoundingClientRect().left) : null;
                const tabs = q('.bookings-card .status-tab-group');
                out.tabsX = tabs ? num(tabs.getBoundingClientRect().left) : null;
                out.tabsMB = tabs ? getComputedStyle(tabs).marginBottom : null;
                const bl = q('.booking-list');
                if (bl) { const cs = getComputedStyle(bl);
                  out.bookingCols = cs.gridTemplateColumns.split(' ').length;
                  out.rowGap = cs.rowGap; out.colGap = cs.columnGap;
                  const card = bl.querySelector('.booking-item-card');
                  out.firstCardX = card ? num(card.getBoundingClientRect().left) : null;
                  out.cardPad = card ? getComputedStyle(card).paddingLeft : null; }
                const badge = q('.bookings-card .count-badge');
                if (badge && mod) out.badgeRightMargin = num(mod.right - badge.getBoundingClientRect().right);
                const desc = q('.bookings-card .card-desc');
                out.descMT = desc ? getComputedStyle(desc).marginTop : null;
              }
              if (location.pathname.startsWith('/parent')) {
                const mod = box(q('.dashboard-shell > section.nur-card')); out.module = mod;
                const title = q('.dashboard-shell > section.nur-card .card-title');
                out.titleX = title ? num(title.getBoundingClientRect().left) : null;
                const badge = q('.dashboard-shell > section.nur-card .count-badge');
                if (badge && mod) out.badgeRightMargin = num(mod.right - badge.getBoundingClientRect().right);
                const cc = q('.child-card'); out.childPad = cc ? getComputedStyle(cc).paddingLeft : null;
                const wr = q('.wallet-overview-row'); out.walletPad = wr ? getComputedStyle(wr).paddingLeft : null;
                const bn = q('.bind-new-card'); out.bindPad = bn ? getComputedStyle(bn).paddingLeft : null;
                const bc = q('.bookings-card');
              }
              if (location.pathname.startsWith('/teacher')) {
                const rc = q('.request-card'); out.reqPad = rc ? getComputedStyle(rc).paddingLeft : null;
                const dg = q('.dashboard-grid');
                if (dg) { const cs = getComputedStyle(dg); out.rowGap = cs.rowGap; out.colGap = cs.columnGap; }
                const pf = q('.profile'); out.profilePad = pf ? getComputedStyle(pf).paddingLeft : null;
              }
              if (location.pathname.startsWith('/admin')) {
                const os_ = q('.overview-section'); out.osPad = os_ ? getComputedStyle(os_).paddingLeft : null;
                out.osPadT = os_ ? getComputedStyle(os_).paddingTop : null;
                const tc = q('.tab-content'); out.tcPad = tc ? getComputedStyle(tc).paddingLeft : null;
                const mc = q('.metric-card'); out.mcPad = mc ? getComputedStyle(mc).paddingLeft : null;
                const qc = q('.queues-container'); out.qcGap = qc ? getComputedStyle(qc).rowGap : null;
              }
              return out;
            })()"""

            async def check(role_key, url, w, h, expand_student=False, name=None):
                await set_session(role_key)
                await page("Emulation.setDeviceMetricsOverride",
                           {"width": w, "height": h, "deviceScaleFactor": 1, "mobile": w < 700})
                await page("Page.navigate", {"url": "http://localhost:4200" + url})
                time.sleep(3.0)
                if expand_student:
                    await page("Runtime.evaluate", {"expression": """
                      (() => { const b=[...document.querySelectorAll('button')]
                        .find(b=>b.textContent.includes('展开全部功能'));
                        if (b) b.click(); return b ? 'expanded' : 'not-found'; })()"""})
                    time.sleep(1.5)
                await page("Runtime.evaluate", {"expression": "document.documentElement.style.scrollBehavior='auto'; 'ok'"})
                await page("Runtime.evaluate", {"expression": """
                    (async () => { const hh = document.body.scrollHeight;
                      for (let y = 0; y <= hh; y += 700) { window.scrollTo(0, y); await new Promise(r=>setTimeout(r,80)); }
                      window.scrollTo(0, 0); await new Promise(r=>setTimeout(r,250)); })(); 'ok'"""})
                time.sleep(0.4)
                r = await page("Runtime.evaluate", {"expression": PROBE, "returnByValue": True})
                m = r["result"]["value"]
                tag = f"{w}x{h}"
                fails = []
                overflow = m["scrollW"] > m["vw"] + 1
                if overflow:
                    fails.append(f"OVERFLOW({m['scrollW']}>{m['vw']})")
                wide = w >= 1280
                def expect_pad(actual, want_lr, want_tb=None):
                    # actual 形如 "24px"；want_tb None 表示四边同值
                    lr = f"{want_lr}px"
                    tb = lr if want_tb is None else f"{want_tb}px"
                    return actual == lr or actual == tb  # paddingLeft 只会比较 lr
                if url == "/student/dashboard":
                    mod = m.get("module")
                    if not mod: fails.append("NO-MODULE")
                    else:
                        want = 32 if wide else 24
                        if mod["pl"] != f"{want}px": fails.append(f"MOD-PL={mod['pl']}!={want}")
                        want_tb = (24 if wide else 24)
                        if mod["pt"] != f"{want_tb}px": fails.append(f"MOD-PT={mod['pt']}!={want_tb}")
                    if m.get("titleX") is not None and m.get("tabsX") is not None and abs(m["titleX"] - m["tabsX"]) > 1.5:
                        fails.append(f"ALIGN title{m['titleX']}!=tabs{m['tabsX']}")
                    if m.get("tabsX") is not None and m.get("firstCardX") is not None and abs(m["tabsX"] - m["firstCardX"]) > 1.5:
                        fails.append(f"ALIGN tabs{m['tabsX']}!=card{m['firstCardX']}")
                    if wide:
                        if m.get("bookingCols") != 2: fails.append(f"COLS={m.get('bookingCols')}")
                        if m.get("rowGap") != "20px" or m.get("colGap") != "24px":
                            fails.append(f"GAP={m.get('rowGap')}/{m.get('colGap')}")
                        if m.get("cardPad") != "24px": fails.append(f"CARDPAD={m.get('cardPad')}")
                        if m.get("badgeRightMargin") is not None and m["badgeRightMargin"] < 28:
                            fails.append(f"BADGE-M={m['badgeRightMargin']}<28")
                        if m.get("descMT") != "8px": fails.append(f"DESC-MT={m.get('descMT')}")
                        if m.get("tabsMB") != "20px": fails.append(f"TABS-MB={m.get('tabsMB')}")
                    else:
                        if m.get("bookingCols") != 1: fails.append(f"COLS={m.get('bookingCols')}")
                        if m.get("cardPad") != "20px": fails.append(f"CARDPAD={m.get('cardPad')}")
                if url == "/parent/dashboard":
                    mod = m.get("module")
                    if not mod: fails.append("NO-MODULE")
                    else:
                        want = 32 if wide else 24
                        if mod["pl"] != f"{want}px": fails.append(f"MOD-PL={mod['pl']}!={want}")
                        if mod["pt"] != "24px": fails.append(f"MOD-PT={mod['pt']}")
                    if wide and m.get("badgeRightMargin") is not None and m["badgeRightMargin"] < 28:
                        fails.append(f"BADGE-M={m['badgeRightMargin']}<28")
                    want_card = "24px" if wide else "20px"
                    if m.get("childPad") not in (None, want_card): fails.append(f"CHILDPAD={m['childPad']}!={want_card}")
                    if m.get("walletPad") not in (None, want_card): fails.append(f"WALLETPAD={m['walletPad']}!={want_card}")
                    if m.get("bindPad") not in (None, want_card): fails.append(f"BINDPAD={m['bindPad']}!={want_card}")
                if url == "/teacher/dashboard":
                    if m.get("reqPad") != "32px": fails.append(f"REQPAD={m.get('reqPad')}!=32")
                    if m.get("profilePad") != "32px": fails.append(f"PROFPAD={m.get('profilePad')}!=32")
                    if wide and (m.get("rowGap") != "20px" or m.get("colGap") != "24px"):
                        fails.append(f"GRIDGAP={m.get('rowGap')}/{m.get('colGap')}")
                if url == "/admin/dashboard":
                    if wide:
                        if m.get("osPad") != "32px" or m.get("osPadT") != "24px":
                            fails.append(f"OSPAD={m.get('osPadT')}/{m.get('osPad')}")
                        if m.get("tcPad") != "32px": fails.append(f"TCPAD={m.get('tcPad')}")
                        if m.get("mcPad") != "20px": fails.append(f"MCPAD={m.get('mcPad')}")
                        if m.get("qcGap") != "20px": fails.append(f"QCGAP={m.get('qcGap')}")
                verdict = "OK" if not fails else "FAIL[" + ";".join(fails) + "]"
                report.append((tag, url, verdict))
                print(f"  [{tag}] {url} -> {verdict}  {json.dumps({k: v for k, v in m.items() if k not in ('url',)}, ensure_ascii=False)[:200]}")
                if name:
                    data = await page("Page.captureScreenshot", {"format": "png", "captureBeyondViewport": True})
                    path = os.path.join(OUT, name + ".png")
                    with open(path, "wb") as f:
                        f.write(base64.b64decode(data["data"]))
                    print(f"        shot {os.path.basename(path)} ({os.path.getsize(path)//1024}KB)")

            for (w, h) in [(1920, 1080), (1700, 900), (1440, 900), (1280, 800), (1024, 768)]:
                await check("student", "/student/dashboard", w, h, expand_student=True,
                            name=f"spacing-student-{w}x{h}" if w in (1920, 1440, 1280) else None)
                await check("parent", "/parent/dashboard", w, h,
                            name=f"spacing-parent-{w}x{h}" if w in (1920, 1440, 1280) else None)
                await check("teacher", "/teacher/dashboard", w, h,
                            name=f"spacing-teacher-{w}x{h}" if w == 1920 else None)
                await check("admin", "/admin/dashboard", w, h,
                            name=f"spacing-admin-{w}x{h}" if w == 1920 else None)

    import asyncio
    asyncio.run(main())
finally:
    proc.kill()

fails = [r for r in report if r[2] != "OK"]
print(f"\n=== 间距验收：{len(report) - len(fails)}/{len(report)} 通过 ===")
for r in fails:
    print("  FAIL:", r)
