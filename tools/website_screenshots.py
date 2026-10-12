"""全站多角色页面截图（供后续 UI 美化参考）。
产出：docs/screenshots-2026-10-11/*.png（桌面 1280px 视口，整页截图）。
方法：API 登录取 NURTURE_AT Cookie + localStorage 会话摘要注入 → Edge headless CDP 逐路由导航 →
      先滚动到底触发 nurReveal 渐显 → 回顶 → captureBeyondViewport 整页截图。
"""
import base64, json, subprocess, time, urllib.request, http.client, os

OUT = r"E:/线上家教平台/线上家教平台（功能）/docs/screenshots-2026-10-11"
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

# 找一位教师详情页 id（学生视角检索）
def first_teacher_id():
    try:
        cookie, _, _ = ROLES["student"]
        conn = http.client.HTTPConnection("localhost", 8080, timeout=10)
        conn.request("GET", "/api/v1/matching/search", headers={"Cookie": "NURTURE_AT=" + cookie})
        r = conn.getresponse(); data = json.loads(r.read()); conn.close()
        items = data if isinstance(data, list) else data.get("content", data.get("candidates", []))
        for c in items or []:
            tid = c.get("teacherId") or c.get("id")
            if tid: return tid
    except Exception as e:
        print("  [WARN] 检索教师失败：", e)
    return None

TID = first_teacher_id()
print("教师详情页 id:", TID or "未取得，跳过")

ROUTES = [
    (None,     "/",                          "01-landing-首页"),
    (None,     "/auth",                      "02-登录注册页"),
    (None,     "/teachers",                  "03-教师列表页"),
    ("student","/student/dashboard",         "10-学生工作台"),
    ("student","/student/match",             "11-找老师匹配"),
    ("student","/student/growth",            "12-成长中心"),
    ("student","/student/archive",           "13-学习档案"),
    ("student","/student/questions",         "14-课后答疑"),
    ("student","/learning/plans",            "15-学习计划"),
    ("student","/learning/mistakes",         "16-错题本"),
    ("student","/learning/materials",        "17-学习资料库"),
    ("student","/learning/assessments",      "18-阶段测评"),
    ("student","/support",                   "19-支持工单"),
    ("student","/messages",                  "20-站内信"),
    ("parent", "/parent/dashboard",          "30-家长中心"),
    ("parent", "/refunds",                   "31-退款工单"),
    ("teacher","/teacher/dashboard",         "40-教师工作台"),
    ("teacher","/scheduling/series",         "41-周期档期"),
    ("admin",  "/admin/dashboard",           "50-运营后台"),
]
if TID:
    ROUTES.insert(3, ("student", f"/teachers/{TID}", "04-教师详情页"))

edge = r"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
proc = subprocess.Popen([edge, "--headless=new", "--remote-debugging-port=9226",
                         "--user-data-dir=" + r"E:/线上家教平台/线上家教平台（功能）/.edge-tmp-shot",
                         "--no-first-run", "--window-size=1280,900", "about:blank"],
                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
results = []
try:
    ws_url = None
    for _ in range(30):
        try:
            with urllib.request.urlopen("http://127.0.0.1:9226/json/version", timeout=2) as resp:
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
            await page("Emulation.setDeviceMetricsOverride",
                       {"width": 1280, "height": 900, "deviceScaleFactor": 1, "mobile": False})
            await page("Runtime.evaluate", {"expression": "document.documentElement.style.scrollBehavior='auto'; 'ok'"})

            async def set_session(role_key):
                cookie, acct, disp = ROLES[role_key]
                role_map = {"student": "STUDENT", "parent": "PARENT", "teacher": "TEACHER", "admin": "ADMIN"}
                await page("Network.setCookie", {"name": "NURTURE_AT", "value": cookie,
                                                 "domain": "localhost", "path": "/api", "httpOnly": True})
                summary = json.dumps({"accountNo": acct, "displayName": disp, "role": role_map[role_key],
                                      "expiresAtEpochMs": int(time.time() * 1000) + 30 * 60 * 1000})
                await page("Runtime.evaluate",
                           {"expression": f"localStorage.setItem('nurture.session', {json.dumps(summary)}); 'ok'"})

            async def shot(url, name, role_key):
                try:
                    if role_key:
                        await set_session(role_key)
                    else:
                        await page("Runtime.evaluate", {"expression": "localStorage.removeItem('nurture.session'); 'ok'"})
                    await page("Page.navigate", {"url": "http://localhost:4200" + url})
                    time.sleep(3.2)
                    # 滚动到底触发渐显，再回顶
                    await page("Runtime.evaluate", {"expression": """
                        (async () => {
                          const h = document.body.scrollHeight;
                          for (let y = 0; y <= h; y += 700) { window.scrollTo(0, y); await new Promise(r=>setTimeout(r,120)); }
                          window.scrollTo(0, 0); await new Promise(r=>setTimeout(r,400));
                        })(); 'ok'"""})
                    time.sleep(1.2)
                    # 部分页面点「展开全部/查看全部」呈现更多内容（幂等：存在才点）
                    await page("Runtime.evaluate", {"expression": """
                        [...document.querySelectorAll('button')].find(b => /展开全部|查看全部/.test(b.textContent))?.click(); 'ok'"""})
                    time.sleep(1.5)
                    data = await page("Page.captureScreenshot",
                                      {"format": "png", "captureBeyondViewport": True})
                    path = os.path.join(OUT, name + ".png")
                    with open(path, "wb") as f:
                        f.write(base64.b64decode(data["data"]))
                    size = os.path.getsize(path)
                    results.append((name, "OK" if size > 8000 else f"小图{size}B"))
                    print(f"  [OK] {name}.png ({size//1024}KB)")
                except Exception as e:
                    results.append((name, "FAIL:" + str(e)[:80]))
                    print(f"  [FAIL] {name}: {str(e)[:120]}")

            for role_key, url, name in ROUTES:
                await shot(url, name, role_key)

    import asyncio
    asyncio.run(main())
finally:
    proc.kill()

fails = [r for r in results if not r[1].startswith("OK")]
print(f"\n完成：{len(results) - len(fails)}/{len(results)} 张，输出目录 {OUT}")
if fails:
    print("失败：", fails)
