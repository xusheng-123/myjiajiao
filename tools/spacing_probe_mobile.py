# -*- coding: utf-8 -*-
"""移动端基线探针（375×812 四角色工作台关键元素 computed style 快照）。
用法：python tools/spacing_probe_mobile.py <输出.json>
间距精修轮的零改动证明：改 CSS 前后各跑一次，两份 JSON 必须完全一致。
"""
import base64, json, subprocess, time, urllib.request, http.client, os, sys

OUT_JSON = sys.argv[1] if len(sys.argv) > 1 else r"E:/线上家教平台/线上家教平台（功能）/tools/_mobile_spacing_baseline.json"

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

# 每个路由探测的选择器 → 记录 padding / margin / gap / display
PROBES = {
    "/student/dashboard": [
        ".dashboard-shell", ".bookings-card", ".card-head", ".card-desc",
        ".status-tab-group", ".focus-card", ".schedule-section-card",
        ".student-profile-card", ".learning-profile-card", ".quick-ask-card",
        ".legacy-wallet-card", ".notice-binding-card", ".booking-item-card",
        ".booking-list", ".booking-main-info", ".profile-cards-grid",
    ],
    "/parent/dashboard": [
        ".dashboard-shell", "section.nur-card", ".card-head", ".card-desc",
        ".child-card", ".bind-new-card", ".wallet-overview-row", ".topup-group",
        ".order-select-bar", ".feedback-card", ".report-metric-cell", ".txn-table",
    ],
    "/teacher/dashboard": [
        ".dashboard-shell", ".request-card", ".today-card", ".availability-card",
        ".dashboard-grid", ".grid-column", ".profile",
    ],
    "/admin/dashboard": [
        ".dashboard-shell", ".overview-section", ".tab-content", ".metric-card",
        ".metric-grid", ".queues-container", ".tab-bar",
    ],
}

PROPS = ["paddingTop", "paddingRight", "paddingBottom", "paddingLeft",
         "marginTop", "marginBottom", "gap", "rowGap", "columnGap",
         "display", "maxWidth", "width"]

edge = r"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
proc = subprocess.Popen([edge, "--headless=new", "--remote-debugging-port=9228",
                         "--user-data-dir=" + r"E:/线上家教平台/线上家教平台（功能）/.edge-tmp-sp",
                         "--no-first-run", "--window-size=375,812", "about:blank"],
                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
result = {}
try:
    ws_url = None
    for _ in range(30):
        try:
            with urllib.request.urlopen("http://127.0.0.1:9228/json/version", timeout=2) as resp:
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

            await page("Emulation.setDeviceMetricsOverride",
                       {"width": 375, "height": 812, "deviceScaleFactor": 1, "mobile": True})

            for url, sels in PROBES.items():
                role_key = ("student" if url.startswith("/student") else
                            "parent" if url.startswith("/parent") else
                            "teacher" if url.startswith("/teacher") else "admin")
                await set_session(role_key)
                await page("Page.navigate", {"url": "http://localhost:4200" + url})
                time.sleep(3.0)
                if url == "/student/dashboard":
                    # 学生端默认专注模式只渲染聚焦卡，先点「展开全部功能」再探测
                    await page("Runtime.evaluate", {"expression": """
                      (() => { const b=[...document.querySelectorAll('button')]
                        .find(b=>b.textContent.includes('展开全部功能'));
                        if (b) b.click(); return b ? 'expanded' : 'not-found'; })()"""})
                    time.sleep(1.5)
                expr = """(() => {
                  const out = {};
                  const sels = %s;
                  for (const sel of sels) {
                    const el = document.querySelector(sel);
                    if (!el) { out[sel] = null; continue; }
                    const cs = getComputedStyle(el);
                    const o = {};
                    for (const p of %s) o[p] = cs[p];
                    out[sel] = o;
                  }
                  out.__scrollW = document.documentElement.scrollWidth;
                  out.__vw = window.innerWidth;
                  return out;
                })()""" % (json.dumps(sels), json.dumps(PROPS))
                r = await page("Runtime.evaluate", {"expression": expr, "returnByValue": True})
                result[url] = r["result"]["value"]
                print(f"probed {url}: {sum(1 for v in result[url].values() if v is not None)}/{len(sels)} 命中")

    import asyncio
    asyncio.run(main())
finally:
    proc.kill()

with open(OUT_JSON, "w", encoding="utf-8") as f:
    json.dump(result, f, ensure_ascii=False, indent=1, sort_keys=True)
print(f"基线已写入 {OUT_JSON}")
