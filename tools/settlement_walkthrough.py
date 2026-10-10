"""Edge headless CDP 走查：教师仪表盘结算卡显示全量聚合 ¥18105（修复后，旧值 11220）。"""
import base64, json, subprocess, time, urllib.request, http.client

conn = http.client.HTTPConnection("localhost", 8080, timeout=10)
conn.request("POST", "/api/v1/identity/auth/login:password",
             json.dumps({"role": "TEACHER", "account": "13900000001", "password": "Teacher#2026"}).encode(),
             {"Content-Type": "application/json"})
r = conn.getresponse(); body = json.loads(r.read())
cookie_val = ""
for k, v in r.getheaders():
    if k.lower() == "set-cookie" and v.startswith("NURTURE_AT="):
        cookie_val = v.split(";")[0][len("NURTURE_AT="):]
acct = body.get("accountNo", "NUR-TEA-1"); disp = body.get("displayName", "教师")
conn.close()
assert cookie_val, "登录失败，未取得 NURTURE_AT"

edge = r"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
proc = subprocess.Popen([edge, "--headless=new", "--remote-debugging-port=9225",
                         "--user-data-dir=" + r"E:/线上家教平台/线上家教平台（功能）/.edge-tmp",
                         "--no-first-run", "--window-size=375,812", "about:blank"],
                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
try:
    ws_url = None
    for _ in range(30):
        try:
            with urllib.request.urlopen("http://127.0.0.1:9225/json/version", timeout=2) as resp:
                ws_url = json.loads(resp.read())["webSocketDebuggerUrl"]
            break
        except Exception:
            time.sleep(0.5)
    import websockets

    async def main():
        async with websockets.connect(ws_url, max_size=64 * 1024 * 1024) as ws:
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
            await page("Network.setCookie", {"name": "NURTURE_AT", "value": cookie_val,
                                             "domain": "localhost", "path": "/api", "httpOnly": True})
            await page("Page.navigate", {"url": "http://localhost:4200/"})
            time.sleep(2.5)
            summary = json.dumps({"accountNo": acct, "displayName": disp, "role": "TEACHER",
                                  "expiresAtEpochMs": int(time.time() * 1000) + 20 * 60 * 1000})
            await page("Runtime.evaluate", {"expression": f"localStorage.setItem('nurture.session', {json.dumps(summary)}); 'ok'"})
            await page("Page.navigate", {"url": "http://localhost:4200/teacher/dashboard"})
            time.sleep(4.0)
            await page("Emulation.setDeviceMetricsOverride",
                       {"width": 375, "height": 812, "deviceScaleFactor": 2, "mobile": True})
            time.sleep(1.0)
            check = await page("Runtime.evaluate", {"expression": """
                JSON.stringify((() => {
                  const h2 = [...document.querySelectorAll('h2')].find(x => x.textContent.includes('课酬结算流水'));
                  if (!h2) return {found: false};
                  const card = h2.closest('section');
                  const nums = [...card.querySelectorAll('.settle-num')].map(x => x.textContent.replace(/\\s+/g, ' ').trim());
                  const notes = [...card.querySelectorAll('.quiet-note')].map(x => x.textContent.trim());
                  const rows = card.querySelectorAll('.settle-list li').length;
                  return {found: true, nums, notes, rows, text: card.textContent.replace(/\\s+/g, ' ').slice(0, 400)};
                })())"""})
            data = json.loads(check["result"]["value"])
            print("结算卡:", json.dumps({k: v for k, v in data.items() if k != 'text'}, ensure_ascii=False))
            shot = await page("Page.captureScreenshot", {"format": "png"})
            with open(r"E:/线上家教平台/线上家教平台（功能）/tools/settlement_walkthrough.png", "wb") as f:
                f.write(base64.b64decode(shot["data"]))
            # 断言
            assert data.get("found"), "未找到课酬结算卡片"
            pending = data["nums"][0] if data["nums"] else ""
            assert "18105" in pending, f"待结算金额不含 18105：{pending}"
            assert "11220" not in data.get("text", ""), "仍显示旧少算值 11220"
            assert data.get("rows", 0) == 50, f"明细应 50 条上限，实际 {data.get('rows')}"
            print("✅ 走查通过：待结算 ¥18105（全量 84 条聚合），明细 50 条上限，无旧值 11220")

    import asyncio
    asyncio.run(main())
finally:
    proc.kill()
