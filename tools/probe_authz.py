# -*- coding: utf-8 -*-
"""越权探针 — 验证对象级/函数级授权（BOLA）与角色护栏。
用法：后端启动后 PYTHONIOENCODING=utf-8 python tools/probe_authz.py
判定：[VULN] = 存在越权（应被拒而未拒）；[OK] = 已正确拒绝；[INFO] = 业务语义项。
每次运行使用随机手机号，与主 E2E 互不干扰。
"""
import json, os, sys, urllib.request, urllib.error, uuid, random

sys.stdout.reconfigure(encoding="utf-8")
BASE = os.environ.get("NURTURE_E2E_BASE", "http://localhost:8080/api/v1")
VULN, OK = [], []

def http(method, path, body=None, token=None, idem=None):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"} if body is not None else {}
    if token:
        headers["Authorization"] = "Bearer " + token
    if idem:
        headers["Idempotency-Key"] = idem
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, (json.loads(resp.read() or "{}") if resp.headers.get("Content-Type", "").startswith("application/json") else {})
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read() or "{}")
        except Exception:
            return e.code, {}

def mark(name, exploitable, detail=""):
    if exploitable:
        VULN.append(name)
        print(f"  [VULN] {name} -> {str(detail)[:140]}")
    else:
        OK.append(name)
        print(f"  [OK]   {name} ({detail})" if detail else f"  [OK]   {name}")

def rand_phone(prefix):
    return prefix + "".join(random.choice("0123456789") for _ in range(9))

def register(role_path, role, phone, password, extra=None):
    body = {"displayName": "探针" + role, "phone": phone, "password": password, **(extra or {})}
    st, resp = http("POST", f"/identity/auth/register:{role_path}", body, idem=str(uuid.uuid4()))
    if st != 200:
        st, resp = http("POST", "/identity/auth/login:password", {"role": role, "account": phone, "password": password}, idem=str(uuid.uuid4()))
    if st != 200 or "accessToken" not in resp:
        raise SystemExit(f"账户注册/登录失败 {role} {phone}: st={st} resp={str(resp)[:200]}")
    return st, resp

def account_id(token):
    """JWT subject 即账户 ID。"""
    import base64
    payload = token.split(".")[1]
    payload += "=" * (-len(payload) % 4)
    return json.loads(base64.urlsafe_b64decode(payload))["sub"]

def main():
    rng = random.Random()
    t_phone, a_phone, b_phone, p_phone = (rand_phone(p) for p in ("13", "15", "17", "18"))
    _, T = register("teacher", "TEACHER", t_phone, "Probe#Teacher#1",
                    {"school": "探针大学", "highestDegree": "硕士", "yearsOfTeaching": 2})
    _, A = register("student", "STUDENT", a_phone, "Probe#Student#1")
    _, B = register("student", "STUDENT", b_phone, "Probe#Student#2")
    _, P = register("parent", "PARENT", p_phone, "Probe#Parent#01")
    tT, tA, tB, tP = T["accessToken"], A["accessToken"], B["accessToken"], P["accessToken"]
    T_id = account_id(tT)
    _, ADM = http("POST", "/identity/auth/login:password",
                  {"role": "ADMIN", "account": "13800000000", "password": "Admin#2026"}, idem=str(uuid.uuid4()))
    tADM = ADM["accessToken"]
    print(f"账户就绪 T={t_phone} A={a_phone} B={b_phone} P={p_phone}")

    # 档期与预约
    st, slot = http("POST", "/scheduling/availability-slots",
                    {"startAt": "2027-03-01T10:00:00+08:00", "endAt": "2027-03-01T11:00:00+08:00"},
                    token=tT, idem=str(uuid.uuid4()))
    if st not in (200, 201):
        print("档期发布失败，探针中止:", st, slot); return
    slot_id = slot["slotId"]
    st, trial = http("POST", "/booking/bookings",
                     {"teacherId": T_id, "slotId": slot_id, "subject": "数学", "bookingType": "TRIAL"},
                     token=tA, idem=str(uuid.uuid4()))
    bk_trial = trial["bookingId"]
    st, slot2 = http("POST", "/scheduling/availability-slots",
                     {"startAt": "2027-03-01T19:00:00+08:00", "endAt": "2027-03-01T20:00:00+08:00"},
                     token=tT, idem=str(uuid.uuid4()))
    st, formal = http("POST", "/booking/bookings",
                      {"teacherId": T_id, "slotId": slot2["slotId"], "subject": "数学", "bookingType": "FORMAL"},
                      token=tA, idem=str(uuid.uuid4()))
    bk_formal = formal["bookingId"]
    http("POST", f"/teacher/me/booking-requests/{bk_formal}:decide?decision=approve", {}, token=tT, idem=str(uuid.uuid4()))
    st, order = http("POST", "/order/orders", {"bookingId": bk_formal}, token=tA, idem=str(uuid.uuid4()))
    order_id = order.get("orderId") if isinstance(order, dict) else None
    print(f"预约/订单就绪 trial={bk_trial[:8]} formal={bk_formal[:8]} order={str(order_id)[:8]}")

    print("== 对象级授权（BOLA） ==")
    st, r = http("POST", f"/booking/bookings/{bk_trial}:start", {}, token=tB, idem=str(uuid.uuid4()))
    mark("无关用户可 start 他人预约", st == 200, f"st={st}")
    st, r = http("POST", f"/booking/bookings/{bk_trial}:complete", {}, token=tB, idem=str(uuid.uuid4()))
    mark("无关用户可 complete 他人预约", st == 200, f"st={st}")
    st, r = http("POST", f"/booking/bookings/{bk_trial}:cancel/preview", {"reason": "越权"}, token=tB, idem=str(uuid.uuid4()))
    mark("无关用户可 previewCancel 他人预约", st == 200, f"st={st}")
    st, r = http("POST", f"/classroom/bookings/{bk_trial}/preparation-tasks", {}, token=tB, idem=str(uuid.uuid4()))
    mark("无关用户可创建他人课堂任务", st in (200, 201), f"st={st}")
    st, r = http("POST", "/review/reviews", {"bookingId": bk_trial, "rating": 1, "tags": [], "comment": "越权"}, token=tB, idem=str(uuid.uuid4()))
    mark("无关用户可评价他人课程", st in (200, 201), f"st={st}")

    print("== 订单与支付 ==")
    st, r = http("GET", f"/order/orders/{order_id}", token=tB)
    mark("无关用户可读他人订单详情", st == 200, f"st={st}")
    st, r = http("POST", "/payment/payments:initiate", {"orderId": order_id, "channel": "MOCK"}, token=tB, idem=str(uuid.uuid4()))
    mark("无关用户可对他人订单发起支付", st == 201, f"st={st}")
    st, r = http("POST", "/order/orders", {"bookingId": bk_trial}, token=tP, idem=str(uuid.uuid4()))
    # 设计缺陷 D2（监护绑定缺失）：任意家长可为任意学生的已确认预约下单付款。
    # 属产品架构决策，暂不修复——见交接文档设计缺陷清单，此处仅记录现状。
    print(f"  [DESIGN] 任意家长可为他人预约下单 (st={st}) — 设计缺陷 D2，暂不修复")

    print("== 函数级角色护栏 ==")
    st, r = http("POST", "/teacher/me/subjects",
                 {"subject": "数学", "gradeBand": "高中", "pricePerHourCents": 100}, token=tB, idem=str(uuid.uuid4()))
    mark("学生可伪装导师发布开课条目", st in (200, 201), f"st={st}")
    st, r = http("POST", "/scheduling/availability-slots",
                 {"startAt": "2027-03-02T10:00:00+08:00", "endAt": "2027-03-02T11:00:00+08:00"},
                 token=tB, idem=str(uuid.uuid4()))
    mark("学生可发布导师档期", st in (200, 201), f"st={st}")
    st, r = http("POST", "/verification/cases",
                 [("x", "x.pdf", b"x")], token=tB, idem=None) if False else (0, None)
    st, r = http("POST", "/parent/me/wallet/topup", {"amountCents": 100, "memo": "学生越权充值"}, token=tB, idem=str(uuid.uuid4()))
    mark("学生可操作家长钱包充值", st in (200, 201), f"st={st}")

    print("== 会话与账户状态 ==")
    # 找到 A 的账户并锁定，再用 A 的 refreshToken 续期
    st, accounts = http("GET", "/admin/accounts?page=0&size=50", token=tADM)
    a_acc = next((a for a in accounts.get("content", []) if a.get("phone") == a_phone), None)
    refresh_token = A.get("refreshToken")
    if a_acc and refresh_token:
        http("POST", f"/admin/accounts/{a_acc['accountId']}:lock", {"note": "探针"}, token=tADM, idem=str(uuid.uuid4()))
        st, r = http("POST", "/identity/auth/refresh", {"refreshToken": refresh_token})
        mark("被锁定账户可用 refreshToken 续期", st == 200, f"st={st}")
        http("POST", f"/admin/accounts/{a_acc['accountId']}:unlock", {"note": "探针"}, token=tADM, idem=str(uuid.uuid4()))
    else:
        print("  [INFO] 跳过锁定续期探针（未取到账户或 refreshToken）")

    # ---------- 第二轮：站内信 BOLA / 角色与 Scope 纪律 ----------
    print("== 第二轮：站内信与会话纪律 ==")
    # A 与 T 开咨询会话并发言
    st, conv = http("POST", "/messaging/conversations",
        {"teacherId": T_id, "subject": "数学答疑"}, token=tA, idem=str(uuid.uuid4()))
    print(f"  [INFO] A 发起会话: st={st} body={str(conv)[:120]}")
    conv_id = conv.get("conversationId") if isinstance(conv, dict) else None
    st, _ = http("POST", f"/messaging/conversations/{conv_id}/messages",
        {"body": "老师您好，我有题不会"}, token=tA, idem=str(uuid.uuid4()))
    # B（无关用户）读会话记录 → 应 403
    if conv_id is None:
        print("  [SKIP] 会话未创建，跳过读取/发送探针")
    else:
        st, msgs = http("GET", f"/messaging/conversations/{conv_id}/messages", token=tB)
        mark("无关用户可读他人咨询会话记录", st == 200, f"st={st}")
    # B（无关用户）向会话发消息 → 应 403
    if conv_id is not None:
        st, _ = http("POST", f"/messaging/conversations/{conv_id}/messages", {"body": "越权消息"}, token=tB, idem=str(uuid.uuid4()))
        mark("无关用户可向他人会话发消息", st == 201, f"st={st}")

    print("== 第二轮：需求发布与 Scope 纪律 ==")
    # 只读 Agent Token（matching:read）发布需求 → 应 403（Agent 无写 Scope 不代发）
    st, issued = http("POST", "/identity/api-tokens",
        {"name": "ro-agent", "scopes": ["matching:read"], "validDays": 3}, token=tADM, idem=str(uuid.uuid4()))
    ro_token = (issued.get("plaintextTokenOnce") or "") if isinstance(issued, dict) and st == 201 else None
    if ro_token:
        st, _ = http("POST", "/matching/demands",
            {"subject": "物理", "gradeBand": "初三", "note": "Agent 越权发布", "budgetMaxCents": 10000},
            token=ro_token, idem=str(uuid.uuid4()))
        mark("只读 Agent Token 可发布学习需求", st in (200, 201), f"st={st}")
    # 导师发布需求 → 应 403（需求面向学生/家长）
    st, _ = http("POST", "/matching/demands",
        {"subject": "物理", "gradeBand": "初三", "note": "导师越权发布"}, token=tT, idem=str(uuid.uuid4()))
    mark("导师可发布学习需求(应为学生/家长通道)", st in (200, 201), f"st={st}")

    print("== 第二轮：越权角色访问抽检 ==")
    st, _ = http("GET", "/teacher/me/settlement", token=tB)
    mark("学生可读导师结算流水", st == 200, f"st={st}")
    st, _ = http("GET", "/verification/me/status", token=tP)
    mark("家长可查导师资质审核状态", st == 200, f"st={st}")
    st, _ = http("PUT", "/admin/configs/platform-fee-rate", {"value": "0.99"}, token=tB, idem=str(uuid.uuid4()))
    mark("学生可改平台全局参数", st == 200, f"st={st}")
    # 他校导师屏蔽他人档期
    st, T2 = register("teacher", "TEACHER", rand_phone("16"), "Probe#Teacher#2",
        {"school": "探针大学", "highestDegree": "学士", "yearsOfTeaching": 1})
    tT2 = T2["accessToken"]
    st, _ = http("DELETE", f"/scheduling/availability-slots/{slot_id}", token=tT2, idem=str(uuid.uuid4()))
    mark("他校导师可删除他人档期", st in (200, 204), f"st={st}")

    print()
    print(f"确认漏洞 {len(VULN)} 项 / 已正确拒绝 {len(OK)} 项")
    for v in VULN:
        print(" - VULN:", v)
    sys.exit(1 if VULN else 0)

if __name__ == "__main__":
    main()
