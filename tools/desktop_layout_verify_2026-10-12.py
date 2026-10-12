# -*- coding: utf-8 -*-
"""四角色工作台桌面宽屏改造 · 多尺寸验收（2026-10-12）。
方法：API 登录取 NURTURE_AT Cookie + localStorage 会话摘要注入 → Edge headless CDP
      逐（视口尺寸 × 路由）导航 → 滚动触发 nurReveal → 量测容器宽度/横向溢出/网格列数 → 截图。
产出：docs/screenshots-2026-10-12-desktop/*.png + 量测报告 stdout。
断言口径：≥1024 桌面壳宽 = min(1560, vw-80)±2；≤767 手机壳宽保持原 max-width；
         任何尺寸 scrollWidth 不得大于 innerWidth+1。
"""
import base64, json, subprocess, time, urllib.request, http.client, os, sys

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
    assert cookie, f"{role} 登录失败未取得 Cookie"
    return cookie, body.get("accountNo", ""), body.get("displayName", account)

ROLES = {
    "student": api_login("STUDENT", "13900000002", "Student#2026"),
    "parent":  api_login("PARENT",  "13900000003", "Parent#2026"),
    "teacher": api_login("TEACHER", "13900000001", "Teacher#2026"),
    "admin":   api_login("ADMIN",   "13800000000", "Admin#2026"),
}
print("四角色 API 登录成功")

SHELL_SEL = {
    "/student/dashboard": ".dashboard-shell", "/student/growth": ".growth-shell",
    "/student/archive": ".archive-shell", "/student/questions": ".questions-shell",
    "/parent/dashboard": ".dashboard-shell", "/teacher/dashboard": ".dashboard-shell",
    "/scheduling/series": ".plans-shell", "/admin/dashboard": ".dashboard-shell",
}
MOBILE_BASE_MAX = {"/student/dashboard": 860, "/student/growth": 820, "/student/archive": 820,
                   "/student/questions": 980, "/parent/dashboard": 860,
                   "/teacher/dashboard": 1120, "/scheduling/series": 1120, "/admin/dashboard": 1120}

DESKTOP_SIZES = [(1920, 1080), (1700, 900), (1440, 900), (1280, 800), (1024, 768)]
MOBILE_SIZES = [(390, 844), (375, 812)]

# 全尺寸 × 四工作台；业务页只测 1920 / 1440 / 390
DASH_ROUTES = ["/student/dashboard", "/parent/dashboard", "/teacher/dashboard", "/admin/dashboard"]
SUB_ROUTES = ["/student/growth", "/student/archive", "/student/questions", "/scheduling/series"]

edge = r"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
proc = subprocess.Popen([edge, "--headless=new", "--remote-debugging-port=9227",
                         "--user-data-dir=" + r"E:/线上家教平台/线上家教平台（功能）/.edge-tmp-dl12",
                         "--no-first-run", "--window-size=1920,1080", "about:blank"],
                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
report = []
try:
    ws_url = None
    for _ in range(30):
        try:
            with urllib.request.urlopen("http://127.0.0.1:9227/json/version", timeout=2) as resp:
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

            async def probe():
                """量测：壳宽/壳 max-width/横向溢出/预约网格列数/指标卡列数"""
                res = await page("Runtime.evaluate", {"expression": """(() => {
                  const shell = document.querySelector('.dashboard-shell,.growth-shell,.archive-shell,.questions-shell,.plans-shell');
                  const out = { hasShell: !!shell };
                  if (shell) {
                    const r = shell.getBoundingClientRect();
                    const cs = getComputedStyle(shell);
                    out.shellWidth = Math.round(r.width);
                    out.shellMax = cs.maxWidth;
                  }
                  out.scrollW = document.documentElement.scrollWidth;
                  out.vw = window.innerWidth;
                  out.cw = document.documentElement.clientWidth;
                  const bl = document.querySelector('.booking-list');
                  out.bookingCols = bl ? getComputedStyle(bl).gridTemplateColumns.split(' ').length : null;
                  const mg = document.querySelector('.metric-grid');
                  out.metricCols = mg ? getComputedStyle(mg).gridTemplateColumns.split(' ').length : null;
                  const dg = document.querySelector('.dashboard-grid');
                  out.teacherGridCols = dg ? getComputedStyle(dg).gridTemplateColumns.split(' ').length : null;
                  return out;
                })()""", "returnByValue": True})
                return res["result"]["value"]

            # 预热：先以公开页建立 localhost 原点，否则首次 localStorage 注入落在 about:blank 上不生效
            await page("Page.navigate", {"url": "http://localhost:4200/"})
            time.sleep(3.5)

            async def settle_and_scroll():
                await page("Runtime.evaluate", {"expression": "document.documentElement.style.scrollBehavior='auto'; 'ok'"})
                await page("Runtime.evaluate", {"expression": """
                    (async () => {
                      const h = document.body.scrollHeight;
                      for (let y = 0; y <= h; y += 700) { window.scrollTo(0, y); await new Promise(r=>setTimeout(r,90)); }
                      window.scrollTo(0, 0); await new Promise(r=>setTimeout(r,300));
                    })(); 'ok'"""})
                time.sleep(0.6)

            def role_of(url):
                if url.startswith("/student"): return "student"
                if url.startswith("/parent"): return "parent"
                if url.startswith("/teacher"): return "teacher"
                if url.startswith("/scheduling"): return "teacher"
                if url.startswith("/admin"): return "admin"
                raise ValueError(url)

            async def shot(url, name, role_key, w, h, full_page, admin_tab=None):
                try:
                    await set_session(role_key)
                    await page("Emulation.setDeviceMetricsOverride",
                               {"width": w, "height": h, "deviceScaleFactor": 1, "mobile": w < 700})
                    await page("Page.navigate", {"url": "http://localhost:4200" + url})
                    time.sleep(3.0)
                    if admin_tab:
                        await page("Runtime.evaluate", {"expression": f"""
                          (() => {{ const btn=[...document.querySelectorAll('.tab-btn')].find(b=>b.textContent.includes('{admin_tab}'));
                            if (btn) btn.click(); return btn ? 'clicked' : 'not-found'; }})()"""})
                        time.sleep(1.8)
                    await settle_and_scroll()
                    m = await probe()
                    if not m.get("hasShell"):  # 首次导航偶发慢加载，补测一次
                        time.sleep(2.5)
                        m = await probe()
                    # 判定：≥1024 壳宽 = min(1560, clientWidth-80)（clientWidth 已剔除滚动条）；
                    # <1024 壳 computed max-width 必须保持原值（证明手机/平板默认样式未被改动）
                    tag = f"{w}x{h}"
                    if m.get("hasShell"):
                        if w >= 1024:
                            expect = min(1560, m["cw"] - 80)
                            ok = abs(m["shellWidth"] - expect) <= 2
                            verdict = "OK" if ok else f"SHELL {m['shellWidth']}!={expect}"
                        else:
                            base = f"{MOBILE_BASE_MAX[url]}px"
                            ok = m.get("shellMax") == base
                            verdict = "OK(max" + base + ")" if ok else f"MAX-W {m.get('shellMax')}!={base}"
                    else:
                        verdict = "NO-SHELL"
                    overflow = m["scrollW"] > m["vw"] + 1
                    if overflow:
                        verdict += f" +OVERFLOW({m['scrollW']}>{m['vw']})"
                    cols = m.get("bookingCols") or m.get("metricCols") or m.get("teacherGridCols")
                    report.append((tag, url + (f"[{admin_tab}]" if admin_tab else ""), verdict,
                                   f"shell={m.get('shellWidth')}", f"cols={cols}" if cols else ""))
                    print(f"  [{tag}] {url}{admin_tab or ''} -> {verdict} shell={m.get('shellWidth')} cols={cols}")
                    params = {"format": "png"}
                    if full_page:
                        params["captureBeyondViewport"] = True
                    data = await page("Page.captureScreenshot", params)
                    path = os.path.join(OUT, name + ".png")
                    with open(path, "wb") as f:
                        f.write(base64.b64decode(data["data"]))
                    print(f"        shot {os.path.basename(path)} ({os.path.getsize(path)//1024}KB)")
                except Exception as e:
                    report.append((f"{w}x{h}", url, "EXC:" + str(e)[:100], "", ""))
                    print(f"  [FAIL] {w}x{h} {url}: {str(e)[:140]}")

            for (w, h) in DESKTOP_SIZES:
                for url in DASH_ROUTES:
                    await shot(url, f"{role_of(url)}-dash-{w}x{h}", role_of(url), w, h, True)
            # admin 关键队列面板 1920 宽屏
            for tab in ["账户治理", "线下收款"]:
                await shot("/admin/dashboard", f"admin-tab-{tab}-1920x1080", "admin", 1920, 1080, True, admin_tab=tab)
            for (w, h) in [(1920, 1080), (1440, 900)]:
                for url in SUB_ROUTES:
                    await shot(url, f"sub{url.replace('/', '-')}-{w}x{h}", role_of(url), w, h, True)
            for (w, h) in MOBILE_SIZES:
                for url in DASH_ROUTES + SUB_ROUTES:
                    # 移动档用视口截图（整页拼接在 vh 布局有串帧伪影）
                    await shot(url, f"mob-{role_of(url)}-{url.split('/')[-1]}-{w}x{h}", role_of(url), w, h, False)

    import asyncio
    asyncio.run(main())
finally:
    proc.kill()

fails = [r for r in report if "OK" not in r[2]]
print(f"\n=== 量测汇总：{len(report) - len(fails)}/{len(report)} 通过 ===")
for r in report:
    if "OK" not in r[2]:
        print("  FAIL:", r)
