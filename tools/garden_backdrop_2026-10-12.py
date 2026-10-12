# -*- coding: utf-8 -*-
"""四角色工作台园林背景优化轮验收（2026-10-12「景观清晰，内容柔和」）。

用法：
  python tools/garden_backdrop_2026-10-12.py snapshot <out.json>    # 375×812 四角色背景层 computed 快照
  python tools/garden_backdrop_2026-10-12.py verify <baseline.json> # 桌面断言+截图+手机端前后比对

断言口径（任务书）：
  ≥1280：背景层高 400px（运营 380）、opacity 1、filter contrast(1.05) saturate(0.95)（零 blur）、
        cover/no-repeat、mask 55% 起渐隐（运营 60% 快过渡）、::after 横向蒙层 0.72/0.48/0.25。
  1024–1279：高 360（运营 340）、蒙层同族略强。
  全档零横向溢出、背景层 pointer-events:none。
  375：快照与改前逐字段一致（手机端零改动实证）。
"""
import base64, json, subprocess, sys, time, urllib.request, http.client, os

ROOT = r"E:/线上家教平台/线上家教平台（功能）"
OUT = ROOT + "/docs/screenshots-2026-10-12-garden"
os.makedirs(OUT, exist_ok=True)

ROLES = [
    ("student", "STUDENT", "13900000002", "Student#2026", "/student/dashboard"),
    ("teacher", "TEACHER", "13900000001", "Teacher#2026", "/teacher/dashboard"),
    ("parent",  "PARENT",  "13900000003", "Parent#2026",  "/parent/dashboard"),
    ("admin",   "ADMIN",   "13800000000", "Admin#2026",   "/admin/dashboard"),
]

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

SESSIONS = {}
for key, role, acct, pwd, _route in ROLES:
    SESSIONS[key] = api_login(role, acct, pwd)
print("四角色 API 登录成功")

# 背景层探针：宿主页面底色 + .dash-backdrop 全参 + ::after 蒙层
PROBE = r"""(() => {
  const q = s => document.querySelector(s);
  const bd = q('.dash-backdrop');
  const out = { url: location.pathname, vw: window.innerWidth,
                scrollW: document.documentElement.scrollWidth };
  if (!bd) { out.backdrop = null; return out; }
  const cs = getComputedStyle(bd);
  out.backdrop = {
    height: cs.height, opacity: cs.opacity,
    filter: cs.filter, pointerEvents: cs.pointerEvents,
    bgImage: cs.backgroundImage, bgSize: cs.backgroundSize,
    bgRepeat: cs.backgroundRepeat, bgPosition: cs.backgroundPosition,
    maskImage: cs.maskImage || cs.webkitMaskImage || '',
  };
  const af = getComputedStyle(bd, '::after');
  out.after = { background: af.backgroundImage || af.background };
  const host = document.querySelector('app-root') || document.body;
  out.pageBg = getComputedStyle(document.querySelector('.dashboard-shell')?.parentElement || document.body).backgroundColor;
  return out;
})()"""

async def drive(mode, out_path):
    import websockets
    edge = r"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
    proc = subprocess.Popen([edge, "--headless=new", "--remote-debugging-port=9237",
                             "--user-data-dir=" + ROOT + "/.edge-tmp-garden1",
                             "--no-first-run", "--window-size=1700,900", "about:blank"],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        ws_url = None
        for _ in range(30):
            try:
                with urllib.request.urlopen("http://127.0.0.1:9237/json/version", timeout=2) as resp:
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

            await page("Page.navigate", {"url": "http://localhost:4200/"})
            time.sleep(3)

            async def probe_role(key, route, w, h):
                cookie, acct, disp = SESSIONS[key]
                role_map = {"student": "STUDENT", "teacher": "TEACHER", "parent": "PARENT", "admin": "ADMIN"}
                await page("Network.setCookie", {"name": "NURTURE_AT", "value": cookie,
                                                 "domain": "localhost", "path": "/api", "httpOnly": True})
                await page("Runtime.evaluate", {"expression":
                    "localStorage.setItem('nurture.session', " + json.dumps(json.dumps({
                        "accountNo": acct, "displayName": disp, "role": role_map[key],
                        "expiresAtEpochMs": int(time.time() * 1000) + 60 * 60 * 1000})) + "); 'ok'"})
                await page("Emulation.setDeviceMetricsOverride",
                           {"width": w, "height": h, "deviceScaleFactor": 1, "mobile": w < 800})
                await page("Page.navigate", {"url": "http://localhost:4200" + route})
                time.sleep(3.5)
                r = await page("Runtime.evaluate", {"expression": PROBE, "returnByValue": True})
                return r["result"]["value"]

            async def shot(name, full=True):
                params = {"format": "png"}
                if full: params["captureBeyondViewport"] = True
                r = await page("Page.captureScreenshot", params)
                with open(OUT + "/" + name, "wb") as f:
                    f.write(base64.b64decode(r["data"]))

            if mode == "snapshot":
                data = {}
                for key, _r, _a, _p, route in ROLES:
                    data[key] = await probe_role(key, route, 375, 812)
                with open(out_path, "w", encoding="utf-8") as f:
                    json.dump(data, f, ensure_ascii=False, indent=1)
                print("mobile snapshot ->", out_path)
                return

            baseline_path = out_path
            results, fails = [], []
            def check(name, cond, detail=""):
                results.append((name, cond, detail))
                if not cond: fails.append(f"{name}: {detail}")

            # 桌面三档 × 四角色
            for w, h, tag in [(1700, 900, "1700"), (1440, 900, "1440"), (1280, 800, "1280")]:
                for key, _r, _a, _p, route in ROLES:
                    d = await probe_role(key, route, w, h)
                    bd = d["backdrop"]
                    if not bd:
                        check(f"[{tag}/{key}] 背景层存在", False, "missing")
                        continue
                    check(f"[{tag}/{key}] 零横向溢出", d["scrollW"] <= d["vw"], f"{d['scrollW']}/{d['vw']}")
                    exp_h = "380px" if key == "admin" else "400px"
                    check(f"[{tag}/{key}] 高度 {exp_h}", bd["height"] == exp_h, bd["height"])
                    check(f"[{tag}/{key}] opacity 1", bd["opacity"] == "1", bd["opacity"])
                    check(f"[{tag}/{key}] 零 blur+轻滤镜",
                          bd["filter"] == "contrast(1.05) saturate(0.95)", bd["filter"])
                    check(f"[{tag}/{key}] pointer-events none", bd["pointerEvents"] == "none", bd["pointerEvents"])
                    check(f"[{tag}/{key}] cover/no-repeat",
                          bd["bgSize"] == "cover" and bd["bgRepeat"] == "no-repeat",
                          f"{bd['bgSize']}/{bd['bgRepeat']}")
                    fade_from = "60%" if key == "admin" else "55%"
                    # Chrome computed 序列化：#000→rgb(0, 0, 0)、transparent→rgba(0, 0, 0, 0)
                    check(f"[{tag}/{key}] mask 渐隐自 {fade_from}",
                          f"rgb(0, 0, 0) {fade_from}" in bd["maskImage"]
                          and bd["maskImage"].rstrip().endswith("rgba(0, 0, 0, 0) 100%)"),
                          bd["maskImage"])
                    veil = "rgba(247, 248, 243, 0.78)" if key == "admin" else "rgba(247, 248, 243, 0.72)"
                    check(f"[{tag}/{key}] 分区蒙层三档", veil in d["after"]["background"], d["after"]["background"][:80])
                    if tag == "1700":
                        await shot(f"garden-{key}-1700-full.png")

            # 中档 1024
            for key, _r, _a, _p, route in ROLES:
                d = await probe_role(key, route, 1024, 768)
                bd = d["backdrop"]
                exp_h = "340px" if key == "admin" else "360px"
                check(f"[1024/{key}] 高度 {exp_h}", bd and bd["height"] == exp_h, bd and bd["height"])
                check(f"[1024/{key}] 零横向溢出", d["scrollW"] <= d["vw"], f"{d['scrollW']}/{d['vw']}")
                await shot(f"garden-{key}-1024-full.png")

            # 手机端 375：改后快照与基线逐字段一致
            after = {}
            for key, _r, _a, _p, route in ROLES:
                after[key] = await probe_role(key, route, 375, 812)
            with open(ROOT + "/tools/_garden_mobile_after.json", "w", encoding="utf-8") as f:
                json.dump(after, f, ensure_ascii=False, indent=1)
            with open(baseline_path, encoding="utf-8") as f:
                before = json.load(f)
            check("[375] 四角色手机端快照与改前零差异", before == after,
                  "PASS" if before == after else _first_diff(before, after))
            for key, _r, _a, _p, _route in ROLES:
                await probe_role(key, _route, 375, 812)
                await shot(f"garden-{key}-mobile-375.png", full=False)

            npass = sum(1 for _, c, _ in results if c)
            print(f"\n===== 园林背景验收：{npass}/{len(results)} 通过 =====")
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
    out = sys.argv[2] if len(sys.argv) > 2 else ROOT + "/tools/_garden_mobile_baseline.json"
    rc = asyncio_run_drive(mode, out)
    sys.exit(rc or 0)
