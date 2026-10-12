"""重拍 01-landing-首页.png — 修复下半部 nurReveal 区块未显现（只有背景没有内容）的问题。

落地页下半部所有区块由 nurReveal 指令控制：进入视口才加 is-visible，否则 opacity:0。
首轮整页截图时这些区块停留在透明态。本脚本逐段慢滚、显式校验显现状态，
残余未显现元素先逐个 scrollIntoView、仍残余才直接补 is-visible（与真实滚动到位后的
最终视觉状态一致），然后整页截图替换原图。
"""
import base64, json, os, subprocess, time, urllib.request

OUT = r"E:/线上家教平台/线上家教平台（功能）/docs/screenshots-2026-10-11"
TARGET = os.path.join(OUT, "01-landing-首页.png")

edge = r"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
proc = subprocess.Popen([edge, "--headless=new", "--remote-debugging-port=9229",
                         "--user-data-dir=" + r"E:/线上家教平台/线上家教平台（功能）/.edge-tmp-shot4",
                         "--no-first-run", "--window-size=1280,900", "about:blank"],
                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
try:
    ws_url = None
    for _ in range(30):
        try:
            with urllib.request.urlopen("http://127.0.0.1:9229/json/version", timeout=2) as resp:
                ws_url = json.loads(resp.read())["webSocketDebuggerUrl"]
            break
        except Exception:
            time.sleep(0.5)
    assert ws_url, "Edge CDP 未就绪"
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
            async def ev(expression, await_promise=False):
                r = await page("Runtime.evaluate", {"expression": expression, "returnByValue": True,
                                                    "awaitPromise": await_promise})
                return r.get("result", {}).get("value")

            await page("Page.enable"); await page("Runtime.enable")
            await page("Emulation.setDeviceMetricsOverride",
                       {"width": 1280, "height": 900, "deviceScaleFactor": 1, "mobile": False})

            await page("Page.navigate", {"url": "http://localhost:4200/"})
            time.sleep(3.5)

            # 关平滑滚动 + 清 reduced-motion 干扰不存在于此，仅确保 window 滚动即时生效
            await ev("document.documentElement.style.scrollBehavior='auto'; 'ok'")

            h = await ev("document.documentElement.scrollHeight")
            print(f"页面高度: {h}px")

            # 分步慢滚到底（每步等待，让 IntersectionObserver 稳定触发）
            await ev("""
                (async () => {
                  const step = 500;
                  for (let y = 0; y <= document.documentElement.scrollHeight; y += step) {
                    window.scrollTo(0, y);
                    await new Promise(r => setTimeout(r, 260));
                  }
                  await new Promise(r => setTimeout(r, 600));
                })()
            """, await_promise=True)

            async def hidden_count():
                return await ev("document.querySelectorAll('.nur-reveal:not(.is-visible)').length")
            total = await ev("document.querySelectorAll('.nur-reveal').length")
            print(f"渐显元素总数: {total}, 滚动后未显现: {await hidden_count()}")

            # 残余元素逐个滚入视口中心
            remain = await hidden_count()
            if remain:
                await ev("""
                    (async () => {
                      const els = [...document.querySelectorAll('.nur-reveal:not(.is-visible)')];
                      for (const el of els) {
                        el.scrollIntoView({ block: 'center' });
                        await new Promise(r => setTimeout(r, 400));
                      }
                    })()
                """, await_promise=True)
                print(f"scrollIntoView 后未显现: {await hidden_count()}")

            # 仍残余（理论上不应发生）→ 直接补类，与滚动到位后的最终视觉状态一致
            remain = await hidden_count()
            if remain:
                await ev("document.querySelectorAll('.nur-reveal:not(.is-visible)')"
                         ".forEach(el => el.classList.add('is-visible')); 'ok'")
                print(f"直接补类: {remain} 个")

            # 回顶，等待过渡动画结束
            await ev("window.scrollTo(0, 0); 'ok'")
            time.sleep(1.2)

            data = await page("Page.captureScreenshot", {"format": "png", "captureBeyondViewport": True})
            tmp = TARGET + ".new.png"
            with open(tmp, "wb") as f:
                f.write(base64.b64decode(data["data"]))
            size = os.path.getsize(tmp)
            print(f"新图大小: {size/1024:.0f}KB ({size}B)")
            assert size > 300_000, f"截图异常偏小: {size}B"
            os.replace(tmp, TARGET)
            print("已替换:", TARGET)

    import asyncio
    asyncio.run(main())
finally:
    proc.kill()
print("done")
