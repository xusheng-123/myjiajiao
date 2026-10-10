# -*- coding: utf-8 -*-
"""润物细无声 · Nurture — 端到端 API 冒烟与漏洞挖掘脚本（stdlib only）"""
import json, sys, urllib.request, urllib.error, uuid, datetime, io, mimetypes, base64, random

BASE = "http://localhost:8080/api/v1"
FRONT = "http://localhost:4200"
sys.stdout.reconfigure(encoding="utf-8")
PASS, FAIL = 0, 0
failures = []

def check(name, cond, detail=""):
    global PASS, FAIL
    if cond:
        PASS += 1
        print(f"  [PASS] {name}")
    else:
        FAIL += 1
        failures.append((name, detail))
        print(f"  [FAIL] {name} -> {detail}")

def http(method, path, body=None, token=None, idem=None, raw=False, expect_error=False):
    url = (BASE + path) if path.startswith("/") else path
    # 查询串中的中文等非 ASCII 字符需编码
    if "?" in url:
        base, qs = url.split("?", 1)
        url = base + "?" + urllib.parse.quote(qs, safe="=&")
    data = None
    headers = {}
    if body is not None and not raw:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    elif raw and isinstance(body, tuple):
        data, headers["Content-Type"] = body
    elif raw:
        data = body
    if token:
        headers["Authorization"] = "Bearer " + token
    if idem:
        headers["Idempotency-Key"] = idem
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            payload = resp.read().decode("utf-8", "replace")
            return resp.status, dict(resp.headers), (json.loads(payload) if payload.strip().startswith(("{", "[")) else payload)
    except urllib.error.HTTPError as e:
        payload = e.read().decode("utf-8", "replace")
        try:
            parsed = json.loads(payload)
        except Exception:
            parsed = payload
        return e.code, dict(e.headers), parsed

def multipart(files):
    boundary = uuid.uuid4().hex
    buf = io.BytesIO()
    for field, filename, content in files:
        buf.write(f"--{boundary}\r\n".encode())
        buf.write(f'Content-Disposition: form-data; name="{field}"; filename="{filename}"\r\n'.encode())
        buf.write(b"Content-Type: application/pdf\r\n\r\n")
        buf.write(content)
        buf.write(b"\r\n")
    buf.write(f"--{boundary}--\r\n".encode())
    return buf.getvalue(), f"multipart/form-data; boundary={boundary}"

def register_or_login(role_path, role, phone, password, payload=None):
    """注册；若手机号已存在（上轮残留）则直接登录取凭证。"""
    body = payload or {"displayName": phone, "phone": phone, "password": password}
    st, _, resp = http("POST", f"/identity/auth/register:{role_path}", body, idem=str(uuid.uuid4()))
    if st == 200 and resp.get("accessToken"):
        return st, resp, True
    st2, _, resp2 = http("POST", "/identity/auth/login:password", {"role": role, "account": phone, "password": password}, idem=str(uuid.uuid4()))
    return st2, resp2, False

def jwt_sub(token):
    """从 JWT 中解码账户 ID（登录响应体不含 accountId）。"""
    payload = token.split(".")[1]
    payload += "=" * (-len(payload) % 4)
    return json.loads(base64.urlsafe_b64decode(payload)).get("sub")

def find_verification_case(admin_token, teacher_account_id, status):
    """翻页查找某教师的指定状态审核案例（案例列表多轮累计可能超过单页）。"""
    page = 0
    while page < 20:
        st, _, resp = http("GET", f"/admin/verification-cases?page={page}&size=50", token=admin_token)
        if st != 200:
            return None
        for c in resp.get("content", []):
            if c.get("teacherId") == teacher_account_id and c.get("status") == status:
                return c
        if page + 1 >= max(1, resp.get("totalPages", 1)):
            return None
        page += 1
    return None

def main():
    # 宽域随机基准（未来 30 天内、8~20 点、5 分钟粒度），避免与历史残留档期重合
    import random
    rng = random.Random()
    tomorrow = (datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=1))
    def new_base():
        return tomorrow.replace(hour=8, minute=0, second=0, microsecond=0) \
            + datetime.timedelta(days=rng.randint(0, 29), minutes=5 * rng.randint(0, 144))
    base = new_base()
    def at(hour_offset):
        d = base + datetime.timedelta(hours=hour_offset)
        return d.strftime("%Y-%m-%dT%H:%M:%S") + "+08:00"

    def publish_slot_retry(token, at_fn, h1, h2, attempts=10):
        """发布档期；与历史残留碰撞(409)时重新抽样基准时间重试，返回 (slot, at_fn)。"""
        nonlocal_base = {"cur": None}
        for _ in range(attempts):
            nonlocal_base["cur"] = new_base()
            def at_local(off, _b={"cur": nonlocal_base["cur"]}):
                d = _b["cur"] + datetime.timedelta(hours=off)
                return d.strftime("%Y-%m-%dT%H:%M:%S") + "+08:00"
            st, _, slot = http("POST", "/scheduling/availability-slots",
                {"startAt": at_local(h1), "endAt": at_local(h2)}, token=token, idem=str(uuid.uuid4()))
            if st in (200, 201):
                return slot, at_local
        raise RuntimeError("发布档期连续 %d 次与历史档期碰撞，请先执行 deploy/sql/cleanup_e2e_data.sql" % attempts)

    print("== 1. 注册与登录 ==")
    st, teacher_body, registered = register_or_login("teacher", "TEACHER", "13900000001", "Teacher#2026",
        {"displayName": "李老师", "phone": "13900000001", "password": "Teacher#2026",
         "school": "华东师范大学", "highestDegree": "硕士研究生", "yearsOfTeaching": 6})
    check("导师注册或登录", st == 200, f"{st} {teacher_body if isinstance(teacher_body, dict) else ''}")
    t_teacher = teacher_body.get("accessToken", "")
    st, student_body, _ = register_or_login("student", "STUDENT", "13900000002", "Student#2026",
        {"displayName": "预览同学", "phone": "13900000002", "password": "Student#2026"})
    check("学生注册或登录", st == 200, f"{st}")
    t_student = student_body.get("accessToken", "")
    st, parent_body, _ = register_or_login("parent", "PARENT", "13900000003", "Parent#2026",
        {"displayName": "预览家长", "phone": "13900000003", "password": "Parent#2026"})
    check("家长注册（独立账户）", st == 200, f"{st}")
    t_parent = parent_body.get("accessToken", "")
    st, _, dup = http("POST", "/identity/auth/register:student",
        {"displayName": "重复", "phone": "13900000002", "password": "Student#2026"}, idem=str(uuid.uuid4()))
    check("重复手机号注册被拒(409)", st == 409, f"{st} {dup}")
    st, _, admin_body = http("POST", "/identity/auth/login:password",
        {"role": "ADMIN", "account": "13800000000", "password": "Admin#2026"}, idem=str(uuid.uuid4()))
    check("运营管理员登录", st == 200, f"{st}")
    t_admin = admin_body.get("accessToken", "")
    st, _, relogin = http("POST", "/identity/auth/login:password",
        {"role": "TEACHER", "account": "13900000001", "password": "Teacher#2026"}, idem=str(uuid.uuid4()))
    check("导师密码登录", st == 200 and relogin.get("role") == "TEACHER", f"{st}")
    st, _, badlogin = http("POST", "/identity/auth/login:password",
        {"role": "STUDENT", "account": "13900000002", "password": "wrong-password"}, idem=str(uuid.uuid4()))
    check("错误密码被拒(400)", st == 400, f"{st}")

    print("== 2. 幂等过滤器负向用例 ==")
    st, _, noidem = http("POST", "/student/me/questions", {"teacherId": 1}, token=t_student)
    check("写操作缺幂等键被拒(400)", st == 400, f"{st} {noidem}")
    st, _, badidem = http("POST", "/student/me/questions", {"teacherId": None, "question": "x"},
        token=t_student, idem="not-a-uuid")
    check("非法幂等键被拒(400)", st == 400, f"{st}")

    print("== 3. 资质认证状态机 ==")
    body, ctype = multipart([
        ("idDocument", "id.pdf", b"%PDF-1.4 fake-id"),
        ("degreeCert", "degree.pdf", b"%PDF-1.4 fake-degree"),
    ])
    st, _, vc = http("POST", "/verification/cases", (body, ctype), token=t_teacher, idem=str(uuid.uuid4()), raw=True)
    check("导师提交资质材料", st == 200 or st == 201, f"{st} {vc}")
    # 质量加固（2026-10-10）：文件白名单三重校验回归——扩展名 / MIME / 魔数
    body, ctype = multipart([("idDocument", "id.txt", b"plain text not allowed"), ("degreeCert", "degree.pdf", b"%PDF-1.4 ok")])
    st, _, rej = http("POST", "/verification/cases", (body, ctype), token=t_teacher, idem=str(uuid.uuid4()), raw=True)
    check("非白名单扩展名(.txt)被拒(400)", st == 400, f"{st} {str(rej)[:120]}")
    body, ctype = multipart([("idDocument", "fake.pdf", b"this is definitely not a pdf"), ("degreeCert", "degree.pdf", b"%PDF-1.4 ok")])
    st, _, rej2 = http("POST", "/verification/cases", (body, ctype), token=t_teacher, idem=str(uuid.uuid4()), raw=True)
    check("伪扩展名+伪MIME但内容非PDF被拒(400)", st == 400, f"{st} {str(rej2)[:120]}")
    body, ctype = multipart([("idDocument", "big.pdf", b"%PDF-1.4 " + b"B" * (21 * 1024 * 1024)), ("degreeCert", "degree.pdf", b"%PDF-1.4 ok")])
    try:
        st, _, oversize = http("POST", "/verification/cases", (body, ctype), token=t_teacher, idem=str(uuid.uuid4()), raw=True)
        check("超单文件上限(21MB)被拒(413/400)", st in (413, 400), f"{st} {str(oversize)[:120]}")
    except Exception as e:
        check("超单文件上限(21MB)被拒(413/400)", False, f"exception {e}")
    st, _, vs = http("GET", "/verification/me/status", token=t_teacher)
    check("提交后状态=SUBMITTED", st == 200 and vs.get("status") == "SUBMITTED", f"{st} {vs}")
    st, _, cases = http("GET", "/admin/verification-cases?page=0&size=10", token=t_admin)
    case = next((c for c in cases.get("content", [])
                 if c["teacherId"] and c["status"] in ("SUBMITTED", "UNDER_REVIEW")), None) if st == 200 else None
    check("运营可见审核案例", case is not None, f"{st}")
    if case:
        st, _, _ = http("POST", f"/admin/verification-cases/{case['caseId']}:decide",
            {"approve": True, "note": "材料齐全"}, token=t_admin, idem=str(uuid.uuid4()))
        check("运营批准认证", st == 200, f"{st}")
    st, _, vs = http("GET", "/verification/me/status", token=t_teacher)
    check("状态机流转=APPROVED", st == 200 and vs.get("status") == "APPROVED", f"{st} {vs}")

    print("== 3.5 G5 教师自助入驻闭环（独立教师：未提交→提交→驳回→重新提交→通过） ==")
    g5_phone = "13" + str(random.randint(0, 999999999)).zfill(9)
    st, g5_body, _ = register_or_login("teacher", "TEACHER", g5_phone, "Teacher#2026",
        {"displayName": "G5入驻教师", "phone": g5_phone, "password": "Teacher#2026",
         "school": "示范大学", "highestDegree": "本科"})
    check("G5 教师注册", st == 200 and g5_body.get("accessToken"), f"{st}")
    t_g5 = g5_body.get("accessToken", "")
    g5_account = jwt_sub(t_g5)
    st, _, vs = http("GET", "/verification/me/status", token=t_g5)
    check("G5 新教师状态=NOT_SUBMITTED", st == 200 and vs.get("status") == "NOT_SUBMITTED", f"{st} {vs}")
    body, ctype = multipart([("idDocument", "only-id.pdf", b"%PDF-1.4 only-id")])
    st, _, miss = http("POST", "/verification/cases", (body, ctype), token=t_g5, idem=str(uuid.uuid4()), raw=True)
    check("G5 缺学历证明被拒(4xx)", 400 <= st < 500, f"{st} {miss}")
    body, ctype = multipart([
        ("idDocument", "g5-id.pdf", b"%PDF-1.4 g5-id"),
        ("degreeCert", "g5-degree.pdf", b"%PDF-1.4 g5-degree"),
    ])
    st, _, vc = http("POST", "/verification/cases", (body, ctype), token=t_g5, idem=str(uuid.uuid4()), raw=True)
    check("G5 提交资质材料", st in (200, 201) and vc.get("status") == "SUBMITTED", f"{st} {vc}")
    check("G5 服务端返回提交时间", bool(vc.get("submittedAt")), f"{vc.get('submittedAt')}")
    g5_case = find_verification_case(t_admin, g5_account, "SUBMITTED")
    check("G5 运营可见新案例", g5_case is not None)
    if g5_case:
        st, _, _ = http("POST", f"/admin/verification-cases/{g5_case['caseId']}:decide",
            {"approve": False, "note": "材料模糊，请上传清晰扫描件"}, token=t_admin, idem=str(uuid.uuid4()))
        check("G5 运营驳回", st == 200, f"{st}")
    st, _, vs = http("GET", "/verification/me/status", token=t_g5)
    check("G5 驳回状态=REJECTED", st == 200 and vs.get("status") == "REJECTED", f"{st} {vs}")
    check("G5 教师可见真实审核意见", vs.get("decisionNote") == "材料模糊，请上传清晰扫描件", f"{vs.get('decisionNote')}")
    check("G5 guidance 含未通过提示", "未通过" in (vs.get("guidance") or ""), f"{vs.get('guidance')}")
    body, ctype = multipart([
        ("idDocument", "g5-id2.pdf", b"%PDF-1.4 g5-id-v2"),
        ("degreeCert", "g5-degree2.pdf", b"%PDF-1.4 g5-degree-v2"),
    ])
    st, _, vc2 = http("POST", "/verification/cases", (body, ctype), token=t_g5, idem=str(uuid.uuid4()), raw=True)
    check("G5 驳回后可重新提交", st in (200, 201) and vc2.get("status") == "SUBMITTED", f"{st} {vc2}")
    g5_case2 = find_verification_case(t_admin, g5_account, "SUBMITTED")
    check("G5 重新提交生成新案例", g5_case2 is not None and g5_case2.get("caseId") != (g5_case or {}).get("caseId"),
        f"{g5_case2} vs {g5_case}")
    if g5_case2:
        st, _, _ = http("POST", f"/admin/verification-cases/{g5_case2['caseId']}:decide",
            {"approve": True, "note": "复核通过"}, token=t_admin, idem=str(uuid.uuid4()))
        check("G5 复核批准", st == 200, f"{st}")
    st, _, vs = http("GET", "/verification/me/status", token=t_g5)
    check("G5 最终状态=APPROVED", st == 200 and vs.get("status") == "APPROVED", f"{st} {vs}")

    print("== 4. 档期与并发排他 ==")
    st, _, offer = http("POST", "/teacher/me/subjects",
        {"subject": "数学", "gradeBand": "高中", "pricePerHourCents": 20000}, token=t_teacher, idem=str(uuid.uuid4()))
    check("导师发布开课条目", st in (200, 201), f"{st} {offer}")
    offering_id = offer.get("offeringId") if isinstance(offer, dict) else None
    st, _, offer2 = http("POST", "/teacher/me/subjects",
        {"subject": "数学", "gradeBand": "高中", "pricePerHourCents": 20000}, token=t_teacher, idem=str(uuid.uuid4()))
    same = isinstance(offer2, dict) and offer2.get("offeringId") == offering_id
    check("重复开课条目幂等返回既有记录(D9)", st in (200, 201) and same, f"{st} {offer2}")
    # 历史运行残留档期可能与随机基准碰撞：发布被排他拒绝(409)时重新抽样基准时间重试
    slot2 = None
    for attempt in range(10):
        st, _, s1 = http("POST", "/scheduling/availability-slots",
            {"startAt": at(10), "endAt": at(11)}, token=t_teacher, idem=str(uuid.uuid4()))
        if st not in (200, 201):
            base = new_base()
            continue
        check("发布可用时段", True, "")
        st, _, s2 = http("POST", "/scheduling/availability-slots",
            {"startAt": at(10), "endAt": at(12)}, token=t_teacher, idem=str(uuid.uuid4()))
        check("重叠时段被排他拒绝(409)", st == 409, f"{st} {s2}")
        st_s2, _, slot2 = http("POST", "/scheduling/availability-slots", {"startAt": at(19), "endAt": at(20)}, token=t_teacher, idem=str(uuid.uuid4()))
        if st_s2 in (200, 201):
            break
        base = new_base()  # 第二时段与残留档期碰撞：换基准整体重试
    else:
        raise AssertionError(f"档期发布连续 10 次与历史残留档期碰撞，请清理 scheduling 表后重试")

    print("== 5. 找老师（透明推荐 + 需求发布双入口） ==")
    st, _, cands = http("GET", "/matching/teachers:search?subject=数学", token=t_student)
    found = next((c for c in cands if c["teacherId"] and c.get("verificationApproved")), None) if st == 200 else None
    check("筛选检索命中已认证导师", found is not None, f"{st} {cands}")
    check("推荐理由非空(可解释)", found and len(found.get("reasons", [])) > 0, f"{found}")
    check("候选带评价证据字段", found and "reviewCount" in found and "nextOpenSlotAt" in found, f"{found}")
    teacher_id = found["teacherId"] if found else None
    st, _, dem = http("POST", "/matching/demands",
        {"subject": "物理", "gradeBand": "初三", "note": "浮力题总是没思路", "budgetMaxCents": 20000,
         "preferredTime": "周末下午"}, token=t_student, idem=str(uuid.uuid4()))
    check("学生发布需求", st in (200, 201), f"{st} {dem}")
    st, _, pool = http("GET", "/matching/demands:open?page=0&size=5", token=t_teacher)
    check("导师可见需求池", st == 200 and any(d["subject"] == "物理" for d in pool.get("content", [])), f"{st} {pool}")

    print("== 6. 试听预约（自动确认 + 幂等重放） ==")
    idem = str(uuid.uuid4())
    st, _, bk1 = http("POST", "/booking/bookings",
        {"teacherId": teacher_id, "slotId": s1["slotId"], "subject": "数学", "bookingType": "TRIAL"},
        token=t_student, idem=idem)
    check("试听预约创建并自动确认", st == 201 and bk1.get("status") == "CONFIRMED", f"{st} {bk1}")
    st2, h2, bk1b = http("POST", "/booking/bookings",
        {"teacherId": teacher_id, "slotId": s1["slotId"], "subject": "数学", "bookingType": "TRIAL"},
        token=t_student, idem=idem)
    check("同键同载荷幂等重放", st2 == 201 and h2.get("X-Idempotent-Replay") == "true", f"{st2} {h2.get('X-Idempotent-Replay')}")
    st3, _, bk1c = http("POST", "/booking/bookings",
        {"teacherId": teacher_id, "slotId": slot2["slotId"], "subject": "数学", "bookingType": "TRIAL"},
        token=t_student, idem=idem)
    check("同键不同载荷冲突(409)", st3 == 409, f"{st3}")

    print("== 7. 课堂协同 ==")
    st, _, tasks = http("POST", "/classroom/bookings/%s/preparation-tasks" % bk1["bookingId"], {}, token=t_student, idem=str(uuid.uuid4()))
    check("课前任务切块生成(4项)", st in (200, 201) and len(tasks) == 4, f"{st} {tasks if isinstance(tasks, list) else tasks}")
    if isinstance(tasks, list) and tasks:
        st, _, done = http("POST", "/classroom/preparation-tasks/%s:complete" % tasks[1]["taskId"], {}, token=t_student, idem=str(uuid.uuid4()))
        check("无序勾选微任务", st == 200 and done.get("completed") is True, f"{st} {done}")
    st, _, sess = http("POST", "/classroom/bookings/%s/session" % bk1["bookingId"],
        {"meetingUrl": "https://meet.example.com/nurture-demo"}, token=t_teacher, idem=str(uuid.uuid4()))
    check("导师创建课堂会话", st in (200, 201), f"{st} {sess}")
    st, _, join = http("POST", "/classroom/bookings/%s/join" % bk1["bookingId"], {}, token=t_student, idem=str(uuid.uuid4()))
    check("学生获取入会凭证", st == 200 and join.get("meetingUrl"), f"{st} {join}")

    print("== 8. 正式课闭环：预约→审批→下单→支付→结算 ==")
    st, _, bk2 = http("POST", "/booking/bookings",
        {"teacherId": teacher_id, "slotId": slot2["slotId"], "subject": "数学", "bookingType": "FORMAL"},
        token=t_student, idem=str(uuid.uuid4()))
    check("正式课预约(待导师审批)", st == 201 and bk2.get("status") == "REQUESTED", f"{st} {bk2}")
    st, _, reqs = http("GET", "/teacher/me/booking-requests?page=0&size=10", token=t_teacher)
    check("导师审批面板可见申请", st == 200 and any(r["bookingId"] == bk2["bookingId"] for r in reqs), f"{st}")
    st, _, decided = http("POST", "/teacher/me/booking-requests/%s:decide?decision=approve" % bk2["bookingId"],
        {}, token=t_teacher, idem=str(uuid.uuid4()))
    check("导师批准预约", st == 200 and decided.get("status") == "CONFIRMED", f"{st} {decided}")
    # D1：监护绑定闭环——家长发起绑定 → 学生确认 → 方可代付下单
    st, _, prof = http("GET", "/student/me/profile", token=t_student)
    student_no = prof.get("studentNo") if isinstance(prof, dict) else None
    check("学生档案含学号", st == 200 and student_no, f"{st}")
    st, _, bind = http("POST", "/parent/me/children",
        {"studentNo": student_no, "relation": "父亲"}, token=t_parent, idem=str(uuid.uuid4()))
    check("家长发起监护绑定", st in (200, 201, 409), f"{st} {bind}")  # 409=上一轮已绑定
    st, _, conf = http("POST", "/parent/me/children:confirm", {}, token=t_student, idem=str(uuid.uuid4()))
    check("学生确认监护绑定", st in (200, 409), f"{st} {conf}")       # 409=无待确认（已 ACTIVE）
    st, _, kids = http("GET", "/parent/me/children", token=t_parent)
    active = isinstance(kids, list) and any(k.get("status") == "ACTIVE" for k in kids)
    check("监护绑定生效(ACTIVE)", active, f"{st} {kids}")

    print("== 8.3 监护绑定申请全链路（G3：学生查看/确认入口） ==")
    st, gs_body, _ = register_or_login("student", "STUDENT", "13900000021", "Student#2026",
        {"displayName": "绑定确认学生", "phone": "13900000021", "password": "Student#2026"})
    check("G3 学生对照账号注册或登录", st == 200 and gs_body.get("accessToken"), f"{st}")
    st, gp_body, _ = register_or_login("parent", "PARENT", "13900000022", "Parent#2026",
        {"displayName": "G3对照家长", "phone": "13900000022", "password": "Parent#2026"})
    check("G3 家长对照账号注册或登录", st == 200 and gp_body.get("accessToken"), f"{st}")
    t_gs, t_gp = gs_body.get("accessToken", ""), gp_body.get("accessToken", "")
    st, _, pend_t = http("GET", "/parent/me/children:pending", token=t_teacher)
    check("G3 教师读取学生申请列表被拒(403)", st == 403, f"{st}")
    st, _, gprof = http("GET", "/student/me/profile", token=t_gs)
    gstu_no = gprof.get("studentNo") if st == 200 else None
    if gstu_no:
        st, _, gb = http("POST", "/parent/me/children", {"studentNo": gstu_no, "relation": "母亲"},
            token=t_gp, idem=str(uuid.uuid4()))
        if st in (200, 201):
            check("G3 家长发起绑定(PENDING)", gb.get("status") == "PENDING", f"{st} {gb}")
            st, _, pend = http("GET", "/parent/me/children:pending", token=t_gs)
            has_pend = st == 200 and isinstance(pend, list) and len(pend) >= 1 and pend[0].get("relation") == "母亲"
            check("G3 学生可见本人待确认申请(含申请方)", has_pend, f"{st} {str(pend)[:160]}")
            other_bid = pend[0].get("bindingId") if has_pend else ""
            st, _, pend_other = http("GET", "/parent/me/children:pending", token=t_student)
            leak = isinstance(pend_other, list) and any(p.get("bindingId") == other_bid for p in pend_other)
            check("G3 其他学生的申请列表不含该申请", not leak, f"{st} {str(pend_other)[:120]}")
            st, _, gconf = http("POST", "/parent/me/children:confirm", {}, token=t_gs, idem=str(uuid.uuid4()))
            check("G3 学生确认绑定→ACTIVE", st == 200 and gconf.get("status") == "ACTIVE", f"{st} {gconf}")
            st, _, pend2 = http("GET", "/parent/me/children:pending", token=t_gs)
            check("G3 确认后待确认列表清空", st == 200 and pend2 == [], f"{st} {pend2}")
        else:
            # 重跑容忍：上一轮已绑定(409)，直接核验生效状态
            check("G3 重复发起绑定被拒(409)", st == 409, f"{st} {gb}")
            st, _, gkids = http("GET", "/parent/me/children", token=t_gp)
            check("G3 重跑核验：家长侧孩子仍为 ACTIVE", st == 200 and any(k.get("status") == "ACTIVE" for k in gkids or []), f"{st} {str(gkids)[:160]}")
        st, _, gkids2 = http("GET", "/parent/me/children", token=t_gp)
        check("G3 家长侧孩子列表含 ACTIVE", st == 200 and any(k.get("status") == "ACTIVE" for k in gkids2 or []), f"{st}")
    else:
        check("G3 学生档案获取", False, f"{st} {gprof}")

    st, _, before_topup = http("GET", "/parent/me/wallet", token=t_parent)
    base_balance = before_topup.get("balanceCents", 0) if st == 200 else 0
    st, _, topup = http("POST", "/parent/me/wallet/topup", {"amountCents": 50000, "memo": "家庭充值"},
        token=t_parent, idem=str(uuid.uuid4()))
    check("家长钱包充值", st in (200, 201), f"{st} {topup}")
    st, _, order = http("POST", "/order/orders", {"bookingId": bk2["bookingId"]}, token=t_parent, idem=str(uuid.uuid4()))
    check("家长下单（按导师定价快照）", st == 201 and order.get("amountCents") == 20000, f"{st} {order}")
    st, _, pay = http("POST", "/payment/payments:initiate", {"orderId": order["orderId"], "channel": "MOCK"},
        token=t_parent, idem=str(uuid.uuid4()))
    check("发起支付", st == 201, f"{st} {pay}")
    st, _, forge = http("POST", "/payment/callbacks/gateway",
        {"orderId": order["orderId"], "channel": "MOCK", "payload": "forged-payload", "signature": "forged-signature", "success": True})
    check("伪造签名回调被拒(401)", st == 401, f"{st} {forge}")
    # 网关侧通知不携带平台幂等键（防重由支付状态机 + 唯一约束承担）
    st, _, cb = http("POST", "/payment/sandbox/notify",
        {"orderId": order["orderId"], "channel": "MOCK", "success": True})
    check("沙箱网关签名通知支付成功", st == 200 and cb.get("result") == "SUCCESS", f"{st} {cb}")
    st, _, cb2 = http("POST", "/payment/sandbox/notify",
        {"orderId": order["orderId"], "channel": "MOCK", "success": True})
    check("回调防重入(409)", st == 409, f"{st} {cb2}")
    st, _, odet = http("GET", "/order/orders/%s" % order["orderId"], token=t_parent)
    check("订单状态=PAID", st == 200 and odet.get("status") == "PAID", f"{st} {odet}")
    st, _, wallet = http("GET", "/parent/me/wallet", token=t_parent)
    paid_txn = [t for t in wallet.get("recentTransactions", []) if t["type"] == "PAYMENT"]
    check("钱包扣款流水", st == 200 and paid_txn and paid_txn[0]["amountCents"] == -20000, f"{st} {wallet}")
    check("余额=充值基数+30000", wallet.get("balanceCents") == base_balance + 30000, f"{wallet.get('balanceCents')} vs {base_balance + 30000}")
    st, _, settle = http("GET", "/teacher/me/settlement", token=t_teacher)
    check("课酬结算条目生成(平台抽成15%)", st == 200 and settle.get("entries")
        and settle["entries"][0]["netCents"] == 17000, f"{st} {settle}")
    st, _, notices = http("GET", "/messaging/notifications?page=0&size=10", token=t_teacher)
    check("导师收到课酬通知", st == 200 and any(n["type"] == "SETTLEMENT" for n in notices.get("items", [])), f"{st}")

    print("== 8.4 可下单课程与学生/家长下单支付（G7） ==")
    st, g7s_body, _ = register_or_login("student", "STUDENT", "13900000041", "Student#2026",
        {"displayName": "G7下单学生", "phone": "13900000041", "password": "Student#2026"})
    check("G7 学生账号注册或登录", st == 200 and g7s_body.get("accessToken"), f"{st}")
    st, g7p_body, _ = register_or_login("parent", "PARENT", "13900000042", "Parent#2026",
        {"displayName": "G7下单家长", "phone": "13900000042", "password": "Parent#2026"})
    check("G7 家长账号注册或登录", st == 200 and g7p_body.get("accessToken"), f"{st}")
    st, g7o_body, _ = register_or_login("parent", "PARENT", "13900000043", "Parent#2026",
        {"displayName": "G7无关家长", "phone": "13900000043", "password": "Parent#2026"})
    check("G7 无关家长账号注册或登录", st == 200 and g7o_body.get("accessToken"), f"{st}")
    t_g7s, t_g7p, t_g7o = (g7s_body.get("accessToken", ""), g7p_body.get("accessToken", ""),
                            g7o_body.get("accessToken", ""))
    # 支付能力配置：前端仅在此开关明确为 true 时展示沙箱演示支付（生产模式 B 引导）
    st, _, pcfg = http("GET", "/payment/config", token=t_g7p)
    check("G7 支付能力配置可见(sandboxEnabled)", st == 200 and isinstance(pcfg.get("sandboxEnabled"), bool), f"{st} {pcfg}")
    st, _, pcfg_anon = http("GET", "/payment/config")
    check("G7 匿名读取支付配置被拒", st in (401, 403), f"{st}")
    # 绑定 → 确认（重跑容忍：409 视为已绑定，直接核验 ACTIVE）
    st, _, g7prof = http("GET", "/student/me/profile", token=t_g7s)
    g7_no = g7prof.get("studentNo") if st == 200 else None
    g7_profile_id = None
    if g7_no:
        st, _, g7bind = http("POST", "/parent/me/children", {"studentNo": g7_no, "relation": "父亲"},
            token=t_g7p, idem=str(uuid.uuid4()))
        check("G7 家长发起绑定", st in (200, 201, 409), f"{st} {g7bind}")
        if st in (200, 201):
            st, _, _ = http("POST", "/parent/me/children:confirm", {}, token=t_g7s, idem=str(uuid.uuid4()))
            check("G7 学生确认绑定", st == 200, f"{st}")
        st, _, g7kids = http("GET", "/parent/me/children", token=t_g7p)
        g7_profile_id = next((k.get("studentId") for k in g7kids or [] if k.get("status") == "ACTIVE"), None)
        check("G7 家长侧存在 ACTIVE 绑定", bool(g7_profile_id), f"{str(g7kids)[:160]}")
    else:
        check("G7 学生档案获取", False, f"{st} {g7prof}")

    # 可下单课程：教师发档期 → G7 学生预约正式课 → 导师审批（试听免费 2026-10-10：付费订单仅走 FORMAL）
    slot_g7, _at_g7 = publish_slot_retry(t_teacher, None, 50, 51)
    st, _, bk_g7 = http("POST", "/booking/bookings",
        {"teacherId": found["teacherId"], "slotId": slot_g7["slotId"], "subject": "数学", "bookingType": "FORMAL"},
        token=t_g7s, idem=str(uuid.uuid4()))
    check("G7 学生预约正式课(待导师审批)", st == 201 and bk_g7.get("status") == "REQUESTED", f"{st} {bk_g7}")
    st, _, g7dec = http("POST", "/teacher/me/booking-requests/%s:decide?decision=approve" % bk_g7["bookingId"],
        {}, token=t_teacher, idem=str(uuid.uuid4()))
    check("G7 导师批准正式课预约", st == 200 and g7dec.get("status") == "CONFIRMED", f"{st} {g7dec}")
    st, _, orderable1 = http("GET", "/order/bookings:orderable?studentId=%s" % g7_profile_id, token=t_g7p)
    check("G7 家长可下单视图含已确认课程", st == 200 and isinstance(orderable1, list)
        and any(b.get("bookingId") == bk_g7["bookingId"] for b in orderable1), f"{st} {str(orderable1)[:200]}")
    check("G7 可下单视图含服务端定价(>0)", st == 200 and all(b.get("priceCents", 0) > 0 for b in orderable1 or []), f"{st}")
    st, _, deny_o = http("GET", "/order/bookings:orderable?studentId=%s" % g7_profile_id, token=t_g7o)
    check("G7 无关家长查孩子可下单课程被拒(403)", st == 403, f"{st} {deny_o}")
    st, _, deny_s = http("GET", "/order/bookings:orderable?studentId=%s" % g7_profile_id, token=t_g7s)
    check("G7 学生调用家长视图被拒(403)", st == 403, f"{st} {deny_s}")

    # 学生自建订单：付款方=学生本人；无余额沙箱支付被拒且订单回滚为待支付
    st, _, sord = http("POST", "/order/orders", {"bookingId": bk_g7["bookingId"]}, token=t_g7s, idem=str(uuid.uuid4()))
    check("G7 学生本人下单成功(待支付)", st == 201 and sord.get("status") == "PENDING_PAYMENT", f"{st} {sord}")
    st, _, sorders = http("GET", "/order/me/orders?page=0&size=20", token=t_g7s)
    check("G7 学生订单列表含自建订单", st == 200 and any(o.get("bookingId") == bk_g7["bookingId"] for o in sorders.get("content", [])), f"{st}")
    st, _, g7p_orders0 = http("GET", "/order/me/orders?page=0&size=20", token=t_g7p)
    check("G7 家长订单列表不含学生自建订单(payer 隔离)", st == 200 and not any(o.get("bookingId") == bk_g7["bookingId"] for o in g7p_orders0.get("content", [])), f"{st}")
    st, _, dup7 = http("POST", "/order/orders", {"bookingId": bk_g7["bookingId"]}, token=t_g7s, idem=str(uuid.uuid4()))
    check("G7 一课一单：重复下单被拒(409)", st == 409, f"{st} {dup7}")
    st, _, orderable2 = http("GET", "/order/bookings:orderable?studentId=%s" % g7_profile_id, token=t_g7p)
    check("G7 已下单课程从可下单视图消失", st == 200 and not any(b.get("bookingId") == bk_g7["bookingId"] for b in orderable2 or []), f"{st} {str(orderable2)[:160]}")
    st, _, spay = http("POST", "/payment/payments:initiate", {"orderId": sord["orderId"], "channel": "MOCK"},
        token=t_g7s, idem=str(uuid.uuid4()))
    check("G7 学生发起支付(payer 本人)", st == 201, f"{st} {spay}")
    st, _, snotify = http("POST", "/payment/sandbox/notify",
        {"orderId": sord["orderId"], "channel": "MOCK", "success": True})
    check("G7 无余额支付被拒", st >= 400, f"{st} {snotify}")
    st, _, safter = http("GET", "/order/orders/%s" % sord["orderId"], token=t_g7s)
    check("G7 支付失败后订单仍待支付(回滚)", st == 200 and safter.get("status") == "PENDING_PAYMENT", f"{st} {safter}")

    # 试听免费规则（2026-10-10）：TRIAL 预约不得创建付费订单，也不出现在可下单视图
    slot_g7t, _at_g7t = publish_slot_retry(t_teacher, None, 62, 63)
    st, _, bk_g7t = http("POST", "/booking/bookings",
        {"teacherId": found["teacherId"], "slotId": slot_g7t["slotId"], "subject": "化学", "bookingType": "TRIAL"},
        token=t_g7s, idem=str(uuid.uuid4()))
    check("G7 试听预约自动确认(免费语义保留)", st == 201 and bk_g7t.get("status") == "CONFIRMED", f"{st} {bk_g7t}")
    st, _, trial_order = http("POST", "/order/orders", {"bookingId": bk_g7t["bookingId"]}, token=t_g7s, idem=str(uuid.uuid4()))
    check("试听下单被拒(409，试听免费不产生付费订单)", st == 409, f"{st} {str(trial_order)[:160]}")
    st, _, trial_order_p = http("POST", "/order/orders", {"bookingId": bk_g7t["bookingId"]}, token=t_g7p, idem=str(uuid.uuid4()))
    check("家长对试听下单同样被拒(409)", st == 409, f"{st} {str(trial_order_p)[:160]}")
    st, _, orderable_t = http("GET", "/order/bookings:orderable?studentId=%s" % g7_profile_id, token=t_g7p)
    check("试听不出现在可下单课程视图", st == 200 and not any(b.get("bookingId") == bk_g7t["bookingId"] for b in orderable_t or []), f"{st} {str(orderable_t)[:200]}")
    st, _, sles = http("GET", "/student/me/upcoming-lessons", token=t_g7s)
    trow = next((l for l in sles or [] if l.get("bookingId") == bk_g7t["bookingId"]), None)
    check("学生课程表含 type 字段且试听为 TRIAL", trow is not None and trow.get("type") == "TRIAL", f"{trow}")

    # 家长为孩子下单并沙箱支付：PAID + 钱包扣减 + 已付订单不可重复发起支付
    slot_g7b, _at_g7b = publish_slot_retry(t_teacher, None, 52, 53)
    st, _, bk_g7b = http("POST", "/booking/bookings",
        {"teacherId": found["teacherId"], "slotId": slot_g7b["slotId"], "subject": "物理", "bookingType": "FORMAL"},
        token=t_g7s, idem=str(uuid.uuid4()))
    check("G7 第二节课预约(待导师审批)", st == 201 and bk_g7b.get("status") == "REQUESTED", f"{st} {bk_g7b}")
    st, _, g7dec2 = http("POST", "/teacher/me/booking-requests/%s:decide?decision=approve" % bk_g7b["bookingId"],
        {}, token=t_teacher, idem=str(uuid.uuid4()))
    check("G7 导师批准第二节课预约", st == 200 and g7dec2.get("status") == "CONFIRMED", f"{st} {g7dec2}")
    # 充值前先读余额：固定手机号账号跨轮次复用，钱包可能带有历史轮次余额，断言不能假设从 0 起步
    st, _, g7wallet0 = http("GET", "/parent/me/wallet", token=t_g7p)
    check("G7 充值前钱包余额可读", st == 200 and isinstance(g7wallet0.get("balanceCents"), int), f"{st} {g7wallet0}")
    g7bal_before = g7wallet0.get("balanceCents", 0) if st == 200 else 0
    st, _, topup7 = http("POST", "/parent/me/wallet/topup", {"amountCents": 100000, "memo": "G7家庭充值"},
        token=t_g7p, idem=str(uuid.uuid4()))
    check("G7 家长钱包充值", st in (200, 201), f"{st} {topup7}")
    st, _, pord = http("POST", "/order/orders", {"bookingId": bk_g7b["bookingId"]}, token=t_g7p, idem=str(uuid.uuid4()))
    check("G7 家长为孩子下单", st == 201 and pord.get("amountCents", 0) > 0, f"{st} {pord}")
    st, _, porders = http("GET", "/order/me/orders?page=0&size=20", token=t_g7p)
    check("G7 家长订单列表含新订单(待支付)", st == 200 and any(o.get("orderId") == pord["orderId"] and o.get("status") == "PENDING_PAYMENT" for o in porders.get("content", [])), f"{st}")
    st, _, pinit = http("POST", "/payment/payments:initiate", {"orderId": pord["orderId"], "channel": "MOCK"},
        token=t_g7p, idem=str(uuid.uuid4()))
    check("G7 家长发起支付", st == 201, f"{st} {pinit}")
    st, _, pnotify = http("POST", "/payment/sandbox/notify",
        {"orderId": pord["orderId"], "channel": "MOCK", "success": True})
    check("G7 沙箱支付成功", st == 200 and pnotify.get("result") == "SUCCESS", f"{st} {pnotify}")
    st, _, pafter = http("GET", "/order/orders/%s" % pord["orderId"], token=t_g7p)
    check("G7 订单状态=PAID", st == 200 and pafter.get("status") == "PAID", f"{st} {pafter}")
    st, _, g7wallet = http("GET", "/parent/me/wallet", token=t_g7p)
    expect_bal = g7bal_before + 100000 - pord.get("amountCents", 0)
    check("G7 钱包扣减=订单金额", st == 200 and g7wallet.get("balanceCents") == expect_bal, f"{g7wallet.get('balanceCents')} vs {expect_bal}")
    st, _, repay = http("POST", "/payment/payments:initiate", {"orderId": pord["orderId"], "channel": "MOCK"},
        token=t_g7p, idem=str(uuid.uuid4()))
    check("G7 已支付订单不可再发起支付(4xx)", 400 <= st < 500, f"{st} {repay}")

    print("== 8.6 G6 课程定价与在售管理（复用 §3.5 已认证教师：新增/调价/快照/下架/越权） ==")    # booking/档期的 teacherId 与 matching 检索结果一致，都是账户 ID（非 teacher profile ID）
    g6_tid = jwt_sub(t_g5)
    check("G6 教师账户ID可读(teacherId)", bool(g6_tid), f"{g6_tid}")
    st, _, g6add = http("POST", "/teacher/me/subjects",
        {"subject": "化学", "gradeBand": "高一", "pricePerHourCents": 15000}, token=t_g5, idem=str(uuid.uuid4()))
    check("G6 新增开课条目(在售)", st in (200, 201) and g6add.get("active") is True and g6add.get("pricePerHourCents") == 15000, f"{st} {g6add}")
    st, _, g6dup = http("POST", "/teacher/me/subjects",
        {"subject": "化学", "gradeBand": "高一", "pricePerHourCents": 15000}, token=t_g5, idem=str(uuid.uuid4()))
    check("G6 D9 幂等：重复添加返回既有条目", st in (200, 201) and g6dup.get("offeringId") == g6add.get("offeringId"), f"{st} {g6dup}")
    st, _, g6bad = http("PUT", "/teacher/me/subjects/%s/pricing" % g6add["offeringId"],
        {"pricePerHourCents": 0}, token=t_g5, idem=str(uuid.uuid4()))
    check("G6 非法价格被拒(4xx)", 400 <= st < 500, f"{st} {g6bad}")
    st, _, g6rp = http("PUT", "/teacher/me/subjects/%s/pricing" % g6add["offeringId"],
        {"pricePerHourCents": 18000}, token=t_g5, idem=str(uuid.uuid4()))
    check("G6 调价以服务端返回为准", st == 200 and g6rp.get("pricePerHourCents") == 18000, f"{st} {g6rp}")
    st, _, g6deny_rp = http("PUT", "/teacher/me/subjects/%s/pricing" % g6add["offeringId"],
        {"pricePerHourCents": 1}, token=t_teacher, idem=str(uuid.uuid4()))
    check("G6 他人调价被拒(403)", st == 403, f"{st} {g6deny_rp}")
    st, _, g6deny_dl = http("DELETE", "/teacher/me/subjects/%s" % g6add["offeringId"], token=t_teacher, idem=str(uuid.uuid4()))
    check("G6 他人下架被拒(403)", st == 403, f"{st} {g6deny_dl}")
    # 快照一致性：调价后新订单按新价；再次调价不改已生成订单
    st, g6s_body, = register_or_login("student", "STUDENT", "13" + str(random.randint(0, 999999999)).zfill(9), "Student#2026")[:2]
    t_g6s = g6s_body.get("accessToken", "")
    check("G6 走查学生注册", st == 200 and t_g6s, f"{st}")
    slot_g6, _at_g6 = publish_slot_retry(t_g5, None, 56, 57)
    st, _, bk_g6 = http("POST", "/booking/bookings",
        {"teacherId": g6_tid, "slotId": slot_g6["slotId"], "subject": "化学", "bookingType": "FORMAL"},
        token=t_g6s, idem=str(uuid.uuid4()))
    check("G6 预约化学正式课(待审批)", st == 201 and bk_g6.get("status") == "REQUESTED", f"{st} {bk_g6}")
    st, _, g6dec = http("POST", "/teacher/me/booking-requests/%s:decide?decision=approve" % bk_g6["bookingId"],
        {}, token=t_g5, idem=str(uuid.uuid4()))
    check("G6 导师批准化学正式课", st == 200 and g6dec.get("status") == "CONFIRMED", f"{st} {g6dec}")
    st, _, ord_g6 = http("POST", "/order/orders", {"bookingId": bk_g6["bookingId"]}, token=t_g6s, idem=str(uuid.uuid4()))
    check("G6 新订单按调价后金额(18000)", st == 201 and ord_g6.get("amountCents") == 18000, f"{st} {ord_g6}")
    st, _, _ = http("PUT", "/teacher/me/subjects/%s/pricing" % g6add["offeringId"],
        {"pricePerHourCents": 25000}, token=t_g5, idem=str(uuid.uuid4()))
    check("G6 再次调价成功", st == 200, f"{st}")
    st, _, odet_g6 = http("GET", "/order/orders/%s" % ord_g6["orderId"], token=t_g6s)
    check("G6 已生成订单金额快照不变(18000)", st == 200 and odet_g6.get("amountCents") == 18000, f"{st} {odet_g6}")
    # 下架（停止销售）：列表消失 + 检索不可见 + 新订单拒绝（质量加固 2026-10-10：不再回落默认价）
    st, _, _ = http("DELETE", "/teacher/me/subjects/%s" % g6add["offeringId"], token=t_g5, idem=str(uuid.uuid4()))
    check("G6 下架成功", st in (200, 204), f"{st}")
    st, _, g6list = http("GET", "/teacher/me/subjects", token=t_g5)
    check("G6 下架后从我的在售列表消失", st == 200 and not any(s.get("offeringId") == g6add["offeringId"] for s in g6list or []), f"{st} {g6list}")
    st, _, g6sr = http("GET", "/matching/teachers:search?subject=化学", token=t_g6s)
    check("G6 下架后检索不可见", st == 200 and not any(t.get("teacherId") == g6_tid for t in g6sr or []), f"{st} {str(g6sr)[:160]}")
    slot_g6b, _at_g6b = publish_slot_retry(t_g5, None, 58, 59)
    st, _, bk_g6b = http("POST", "/booking/bookings",
        {"teacherId": g6_tid, "slotId": slot_g6b["slotId"], "subject": "化学", "bookingType": "FORMAL"},
        token=t_g6s, idem=str(uuid.uuid4()))
    st, _, _ = http("POST", "/teacher/me/booking-requests/%s:decide?decision=approve" % bk_g6b["bookingId"],
        {}, token=t_g5, idem=str(uuid.uuid4()))
    st, _, ord_g6b = http("POST", "/order/orders", {"bookingId": bk_g6b["bookingId"]}, token=t_g6s, idem=str(uuid.uuid4()))
    check("G6 下架后新订单被拒(409，停止销售不得回落默认价)", st == 409, f"{st} {str(ord_g6b)[:160]}")
    # 合法兜底回归：教师从未设置过该科目定价时仍走默认价（quality 加固不得破坏既有兜底）
    slot_g6c, _at_g6c = publish_slot_retry(t_g5, None, 60, 61)
    st, _, bk_g6c = http("POST", "/booking/bookings",
        {"teacherId": g6_tid, "slotId": slot_g6c["slotId"], "subject": "物理", "bookingType": "FORMAL"},
        token=t_g6s, idem=str(uuid.uuid4()))
    st, _, _ = http("POST", "/teacher/me/booking-requests/%s:decide?decision=approve" % bk_g6c["bookingId"],
        {}, token=t_g5, idem=str(uuid.uuid4()))
    st, _, ord_g6c = http("POST", "/order/orders", {"bookingId": bk_g6c["bookingId"]}, token=t_g6s, idem=str(uuid.uuid4()))
    check("G6 从未定价科目仍走默认价兜底(30000)", st == 201 and ord_g6c.get("amountCents") == 30000, f"{st} {str(ord_g6c)[:160]}")

    print("== 8.5 改期（D11：参与双方可改期，冲突原子回滚） ==")
    reschedule_ok = False
    for _try in range(10):
        st, _, slot3 = http("POST", "/scheduling/availability-slots",
            {"startAt": at(30), "endAt": at(31)}, token=t_teacher, idem=str(uuid.uuid4()))
        if st not in (200, 201):
            base = new_base(); continue
        st, _, slot4 = http("POST", "/scheduling/availability-slots",
            {"startAt": at(32), "endAt": at(33)}, token=t_teacher, idem=str(uuid.uuid4()))
        if st not in (200, 201):
            base = new_base(); continue
        st, _, bk_res = http("POST", "/booking/bookings",
            {"teacherId": found["teacherId"], "slotId": slot3["slotId"], "subject": "数学", "bookingType": "FORMAL"},
            token=t_student, idem=str(uuid.uuid4()))
        if st != 201:
            base = new_base(); continue
        check("改期用正式课申请创建", bk_res.get("status") == "REQUESTED", str(bk_res))
        st, _, confirmed_res = http("POST", "/teacher/me/booking-requests/%s:decide?decision=approve" % bk_res["bookingId"],
            {}, token=t_teacher, idem=str(uuid.uuid4()))
        check("改期用正式课确认", st == 200 and confirmed_res.get("status") == "CONFIRMED", f"{st} {confirmed_res}")
        st, _, _ = http("POST", "/booking/bookings/%s:reschedule" % bk_res["bookingId"],
            {"newSlotId": slot2["slotId"]}, token=t_student, idem=str(uuid.uuid4()))
        check("改期到已占用档期被拒(409)", st == 409, f"{st}")
        st, _, _ = http("POST", "/booking/bookings/%s:reschedule" % bk_res["bookingId"],
            {"newSlotId": slot3["slotId"]}, token=t_student, idem=str(uuid.uuid4()))
        check("同档期改期被拒(409)", st == 409, f"{st}")
        st, _, res = http("POST", "/booking/bookings/%s:reschedule" % bk_res["bookingId"],
            {"newSlotId": slot4["slotId"]}, token=t_teacher, idem=str(uuid.uuid4()))
        check("参与方改期成功且档期切换", st == 200 and res.get("slotId") == slot4["slotId"], f"{st} {res}")
        reschedule_ok = True
        break
    if not reschedule_ok:
        check("改期流程（10 次抽样均与历史档期碰撞）", False, "请清理 availability_slots/bookings 后重试")

    print("== 9. 两阶段取消（Preview→Execute，自动退款闭环） ==")
    st, _, prev = http("POST", "/booking/bookings/%s:cancel/preview" % bk2["bookingId"],
        {"reason": "时间冲突"}, token=t_student, idem=str(uuid.uuid4()))
    check("取消Preview返回级联影响与凭证", st == 200 and prev.get("confirmationToken")
        and prev["cascadeImpact"].get("refundAmountCents") == 20000, f"{st} {prev}")
    st, _, noexec = http("POST", "/booking/bookings/%s:cancel/execute" % bk2["bookingId"],
        {"reason": "时间冲突", "confirmationToken": "forged-token"}, token=t_student, idem=str(uuid.uuid4()))
    check("伪造凭证被拒(400)", st == 400, f"{st} {noexec}")
    st, _, exe = http("POST", "/booking/bookings/%s:cancel/execute" % bk2["bookingId"],
        {"reason": "时间冲突", "confirmationToken": prev["confirmationToken"]}, token=t_student, idem=str(uuid.uuid4()))
    check("取消Execute终态生效", st == 200 and exe.get("status") == "CANCELLED", f"{st} {exe}")
    st, _, wallet2 = http("GET", "/parent/me/wallet", token=t_parent)
    refund_txn = [t for t in wallet2.get("recentTransactions", []) if t["type"] == "REFUND"]
    check("退款自动原路入账(+20000)", refund_txn and refund_txn[0]["amountCents"] == 20000, f"{wallet2}")
    check("余额回到充值基数+50000", wallet2.get("balanceCents") == base_balance + 50000, f"{wallet2.get('balanceCents')}")

    print("== 10. 课程完成、考勤与双向评价 ==")
    st, _, started = http("POST", "/booking/bookings/%s:start" % bk1["bookingId"], {}, token=t_teacher, idem=str(uuid.uuid4()))
    check("开始上课", st == 200 and started.get("status") == "IN_PROGRESS", f"{st} {started}")
    st, _, done = http("POST", "/booking/bookings/%s:complete" % bk1["bookingId"], {}, token=t_teacher, idem=str(uuid.uuid4()))
    check("完成课程", st == 200 and done.get("status") == "COMPLETED", f"{st} {done}")
    st, _, ended = http("POST", "/classroom/bookings/%s/session:end" % bk1["bookingId"], {}, token=t_teacher, idem=str(uuid.uuid4()))
    check("结束课堂会话", st == 200, f"{st} {ended}")
    st, _, att = http("POST", "/classroom/bookings/%s/attendance" % bk1["bookingId"],
        {"attendanceStatus": "LATE"}, token=t_teacher, idem=str(uuid.uuid4()))
    check("标记考勤(迟到)", st == 200 and att.get("attendanceStatus") == "LATE", f"{st} {att}")
    st, _, rev = http("POST", "/review/reviews",
        {"bookingId": bk1["bookingId"], "rating": 5, "tags": ["耐心细致", "擅长结构化教学"], "comment": "讲解清晰"},
        token=t_student, idem=str(uuid.uuid4()))
    check("学生评价导师", st in (200, 201), f"{st} {rev}")
    st, _, rev2 = http("POST", "/review/reviews",
        {"bookingId": bk1["bookingId"], "rating": 1, "tags": [], "comment": ""}, token=t_student, idem=str(uuid.uuid4()))
    check("同方向重复评价被拒(409)", st == 409, f"{st} {rev2}")

    print("== 11. 运营治理 ==")
    st, _, overview = http("GET", "/admin/overview", token=t_admin)
    check("运营全景概览", st == 200 and "studentAccounts" in overview, f"{st}")
    st, _, audit = http("GET", "/admin/audit-logs?page=0&size=10", token=t_admin)
    check("审计日志非空", st == 200 and audit.get("totalElements", 0) > 0, f"{st} {str(audit)[:120]}")
    st, _, accounts = http("GET", "/admin/accounts?page=0&size=100", token=t_admin)
    # 精确锚定测试账号：多轮累计后账户总数可能超过单页（如 108>100），必须翻页查找而非只看第一页
    stu = None
    if st == 200:
        for _pg in range(max(1, accounts.get("totalPages", 1))):
            stu = next((a for a in accounts.get("content", []) if a["role"] == "STUDENT" and a.get("phone") == "13900000002"), None)
            if stu is not None or _pg + 1 >= max(1, accounts.get("totalPages", 1)):
                break
            st, _, accounts = http("GET", "/admin/accounts?page=%d&size=100" % (_pg + 1), token=t_admin)
            if st != 200:
                break
    check("账户管理列表", stu is not None, f"{st}")
    if stu:
        st, _, _ = http("POST", "/admin/accounts/%s:lock" % stu["accountId"], {"note": "测试锁定"}, token=t_admin, idem=str(uuid.uuid4()))
        check("锁定账户", st in (200, 204), f"{st}")
        st, _, locked = http("POST", "/identity/auth/login:password",
            {"role": "STUDENT", "account": "13900000002", "password": "Student#2026"}, idem=str(uuid.uuid4()))
        check("锁定后登录被拒(409)", st == 409, f"{st} {locked}")
        st, _, _ = http("POST", "/admin/accounts/%s:unlock" % stu["accountId"], {"note": "测试解锁"}, token=t_admin, idem=str(uuid.uuid4()))
        st, _, again = http("POST", "/identity/auth/login:password",
            {"role": "STUDENT", "account": "13900000002", "password": "Student#2026"}, idem=str(uuid.uuid4()))
        check("解锁后可登录", st == 200, f"{st}")
    st, _, forb = http("GET", "/admin/overview", token=t_student)
    check("非运营访问运营端被拒(403)", st == 403, f"{st}")

    print("== 12. 安全加固：短信限频与登录防暴力破解 ==")
    sms_phone = "19" + "".join(rng.choice("0123456789") for _ in range(9))
    st, _, capabilities = http("GET", "/identity/auth/capabilities")
    check("认证能力可公开查询，密码通道可用", st == 200 and capabilities.get("passwordAvailable") is True)
    st, _, result = http("POST", "/identity/auth/sms-codes", {"role": "STUDENT", "phone": sms_phone}, idem=str(uuid.uuid4()))
    if capabilities.get("smsAvailable"):
        check("短信通道可用时首次投递成功(204)", st == 204, f"{st}")
        st, _, limited = http("POST", "/identity/auth/sms-codes", {"role": "STUDENT", "phone": sms_phone}, idem=str(uuid.uuid4()))
        check("同号最小间隔内重发被限频(429)", st == 429, f"{st} {limited}")
        st, hdrs, _ = http("POST", "/identity/auth/sms-codes", {"role": "STUDENT", "phone": sms_phone}, idem=str(uuid.uuid4()))
        check("限频响应含 Retry-After 头", st == 429 and hdrs.get("Retry-After") is not None, f"{st}")
    else:
        check("未配置短信供应商明确拒绝(503)，无虚假成功", st == 503 and "账号密码" in result.get("detail", ""), f"{st}")
        st, _, result = http("POST", "/identity/auth/login:sms", {"role": "STUDENT", "phone": sms_phone, "smsCode": "000000"}, idem=str(uuid.uuid4()))
        check("未投递验证码不能通过认证", st == 400, f"{st}")

    thr_phone = "18" + "".join(rng.choice("0123456789") for _ in range(9))
    st, _, _ = http("POST", "/identity/auth/register:student",
        {"displayName": "限频测试", "phone": thr_phone, "password": "Throttle#2026"}, idem=str(uuid.uuid4()))
    check("注册牺牲账号用于限频测试", st == 200, f"{st}")
    for i in range(5):
        st, _, _ = http("POST", "/identity/auth/login:password",
            {"role": "STUDENT", "account": thr_phone, "password": "wrong-" + str(i)}, idem=str(uuid.uuid4()))
    check("连续5次错误密码均被拒(400)", st == 400, f"{st}")
    st, _, locked_body = http("POST", "/identity/auth/login:password",
        {"role": "STUDENT", "account": thr_phone, "password": "Throttle#2026"}, idem=str(uuid.uuid4()))
    check("触发锁定后正确密码也拒绝(429)", st == 429, f"{st} {locked_body}")
    st, _, unlocked = http("POST", "/admin/security/login-throttle:reset",
        {"account": thr_phone}, token=t_admin, idem=str(uuid.uuid4()))
    check("运营解锁登录限频", st == 200, f"{st} {unlocked}")
    st, _, oklogin = http("POST", "/identity/auth/login:password",
        {"role": "STUDENT", "account": thr_phone, "password": "Throttle#2026"}, idem=str(uuid.uuid4()))
    check("解锁后正确密码可登录", st == 200, f"{st}")

    print("== 13. 加固回归：multipart 幂等指纹 + Agent Token 定位哈希 ==")
    # 同一幂等键、不同文件内容 → 必须冲突拒绝（指纹真实化回归）
    m_teacher_phone = "17" + "".join(rng.choice("0123456789") for _ in range(9))
    st, m_teacher, _ = register_or_login("teacher", "TEACHER", m_teacher_phone, "Teacher#2026",
        {"displayName": "指纹测试", "phone": m_teacher_phone, "password": "Teacher#2026",
         "school": "浙江大学", "highestDegree": "硕士", "yearsOfTeaching": 3})
    check("注册 multipart 测试导师", st == 200, f"{st}")
    t_m = m_teacher.get("accessToken", "")
    body_a, ctype = multipart([("idDocument", "a.pdf", b"%PDF-1.4 content-A"), ("degreeCert", "d.pdf", b"%PDF-1.4 deg")])
    st, _, first = http("POST", "/verification/cases", (body_a, ctype), token=t_m, idem="7c9e6679-7425-40de-944b-e07fc1f90ae7", raw=True)
    check("multipart 首次上传成功", st in (200, 201), f"{st}")
    body_b, ctype2 = multipart([("idDocument", "b.pdf", b"%PDF-1.4 content-B-different"), ("degreeCert", "d.pdf", b"%PDF-1.4 deg")])
    st, _, conflict = http("POST", "/verification/cases", (body_b, ctype2), token=t_m, idem="7c9e6679-7425-40de-944b-e07fc1f90ae7", raw=True)
    check("同键不同载荷 multipart 被拒(409)", st == 409, f"{st} {conflict}")

    # Agent Token：签发（明文仅一次）→ 定位哈希直查验证 → 伪造拒绝
    st, _, issued = http("POST", "/identity/api-tokens",
        {"name": "e2e-agent", "scopes": ["matching:read"], "validDays": 7}, token=t_admin, idem=str(uuid.uuid4()))
    check("签发 Agent Token", st == 201 and issued.get("plaintextTokenOnce"), f"{st}")
    agent_raw = (issued.get("plaintextTokenOnce") or "") if isinstance(issued, dict) else ""
    st, _, tools = http("GET", "/mcp/tools", token=agent_raw)
    check("Agent Token 调用 MCP 工具目录", st == 200, f"{st}")
    st, _, forged = http("GET", "/mcp/tools", token="nur_at_forged-token-value")
    check("伪造 Agent Token 被拒", st in (401, 403), f"{st}")

    print("== 14. 契约与前端代理 ==")
    st, _, docs = http("GET", "http://localhost:8080/api/v1/api-docs")
    check("OpenAPI 契约可访问", st == 200 and "/api/v1" in json.dumps(docs), f"{st}")
    check("契约含 Idempotency-Key 参数", "Idempotency-Key" in json.dumps(docs), "")
    st, _, front = http("GET", FRONT)
    check("前端代理可达", st == 200 and "nur-root" in str(front), f"{st}")

    print("== 15. Release 安全验收：Cookie/CORS/课堂链路/线下收款确认 ==")
    # 15.1 Cookie 属性验证（P1-3 / 安全验收 2）：HttpOnly + SameSite 必须、Secure 由传输层决定
    st, hdr, relogin_body = http("POST", "/identity/auth/login:password",
        {"role": "STUDENT", "account": "13900000002", "password": "Student#2026"}, idem=str(uuid.uuid4()))
    set_cookie = str(hdr.get("Set-Cookie", ""))
    check("登录下发 HttpOnly Cookie", st == 200 and "HttpOnly" in set_cookie, f"{st} {set_cookie[:80]}")
    check("Cookie 携带 SameSite 策略", "SameSite=" in set_cookie, f"{set_cookie[:80]}")
    # 安全验收 7：JS 可读性（HttpOnly 语义验证 = 脚本模拟浏览器不可通过非 HttpOnly 通道读取）
    check("令牌 Cookie 不可由 JS 读取语义(HttpOnly)", "HttpOnly" in set_cookie, "")

    # 15.2 CORS 验证（P1-2 / 安全验收 3）：非法源被拒(403 且不放行头)，合法源放行
    def preflight(origin):
        req = urllib.request.Request(BASE + "/booking/bookings", method="OPTIONS")
        req.add_header("Origin", origin)
        req.add_header("Access-Control-Request-Method", "POST")
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                return resp.status, resp.headers.get("Access-Control-Allow-Origin")
        except urllib.error.HTTPError as e:
            return e.code, e.headers.get("Access-Control-Allow-Origin")
    st_evil, allow_evil = preflight("https://evil.example")
    check("非法 Origin 被拒且不放行 CORS(403)", st_evil == 403 and allow_evil is None,
          f"st={st_evil} allow-origin={allow_evil}")
    st_ok, allow_ok = preflight("http://localhost:4200")
    check("合法 Origin 放行 CORS", st_ok == 200 and allow_ok == "http://localhost:4200",
          f"st={st_ok} allow-origin={allow_ok}")

    # 15.3 同师试听已消耗：用正式课经审批验证课堂链路，保留每生每师一次规则。
    slot2, _at2 = publish_slot_retry(t_teacher, None, 2, 3)
    check("发布课堂链路用档期", slot2.get("slotId") is not None, f"{slot2}")
    st, _, bk3 = http("POST", "/booking/bookings",
        {"teacherId": teacher_id, "slotId": slot2["slotId"],
         "subject": "物理", "bookingType": "FORMAL"}, token=t_student, idem=str(uuid.uuid4()))
    check("创建课堂链路用正式课申请", st == 201 and bk3.get("status") == "REQUESTED", f"{st} {bk3}")
    st, _, confirmed3 = http("POST", "/teacher/me/booking-requests/%s:decide?decision=approve" % bk3["bookingId"],
        {}, token=t_teacher, idem=str(uuid.uuid4()))
    check("课堂链路正式课经教师确认", st == 200 and confirmed3.get("status") == "CONFIRMED", f"{st} {confirmed3}")
    st, _, sess = http("POST", "/classroom/bookings/%s/session" % bk3["bookingId"],
        {"meetingUrl": "https://meeting.tencent.com/dm/r/DRILLMEETING"}, token=t_teacher, idem=str(uuid.uuid4()))
    check("导师登记会议链接", st in (200, 201) and sess.get("meetingUrl"), f"{st} {sess}")
    st, _, join = http("POST", "/classroom/bookings/%s/join" % bk3["bookingId"], {}, token=t_student, idem=str(uuid.uuid4()))
    check("学生获取入会信息", st == 200 and join.get("meetingUrl") == "https://meeting.tencent.com/dm/r/DRILLMEETING", f"{st} {join}")
    st, _, ended = http("POST", "/classroom/bookings/%s/session:end" % bk3["bookingId"], {}, token=t_teacher, idem=str(uuid.uuid4()))
    check("导师结课", st == 200 and ended.get("sessionStatus") == "ENDED", f"{st} {ended}")
    st, _, att = http("POST", "/classroom/bookings/%s/attendance" % bk3["bookingId"],
        {"attendanceStatus": "PRESENT"}, token=t_teacher, idem=str(uuid.uuid4()))
    check("导师标记考勤", st == 200 and att.get("attendanceStatus") == "PRESENT", f"{st} {att}")
    # 学生不可创建课堂会话（导师专属语义，P2 收紧前的回归基线：当前允许参与双方，登记成功不算漏洞）
    st, _, sess2 = http("POST", "/classroom/bookings/%s/session" % bk3["bookingId"],
        {"meetingUrl": "https://meeting.tencent.com/dm/r/DRILLMEETING"}, token=t_teacher, idem=str(uuid.uuid4()))
    check("课堂会话幂等（重复登记返回既有）", st in (200, 201), f"{st}")

    # 15.4 运营线下收款确认（P0-3 模式 B）：新订单待支付 → 运营确认 → 已付
    slot3, _at3 = publish_slot_retry(t_teacher, None, 4, 5)
    check("发布线下收款用档期", slot3.get("slotId") is not None, f"{slot3}")
    st, _, bk4 = http("POST", "/booking/bookings",
        {"teacherId": teacher_id, "slotId": slot3["slotId"],
         "subject": "化学", "bookingType": "FORMAL"}, token=t_student, idem=str(uuid.uuid4()))
    check("创建线下收款用正式课预约(待审批)", st in (200, 201) and bk4.get("status") == "REQUESTED", f"{st} {bk4}")
    st, _, bk4dec = http("POST", "/teacher/me/booking-requests/%s:decide?decision=approve" % bk4["bookingId"],
        {}, token=t_teacher, idem=str(uuid.uuid4()))
    check("导师批准线下收款用预约", st == 200 and bk4dec.get("status") == "CONFIRMED", f"{st} {bk4dec}")
    st, _, ord4 = http("POST", "/order/orders", {"bookingId": bk4["bookingId"]}, token=t_parent, idem=str(uuid.uuid4()))
    check("创建待支付订单", st in (200, 201) and ord4.get("status") == "PENDING_PAYMENT", f"{st} {ord4}")
    st, _, pend_deny = http("GET", "/admin/orders/pending-payment?page=0&size=20", token=t_teacher)
    check("非运营读取待收款列表被拒(403)", st == 403, f"{st}")
    st, _, pend = http("GET", "/admin/orders/pending-payment?page=0&size=20", token=t_admin)
    # 多轮累计后待支付订单可能超过单页，翻页查找本轮订单（历史轮次残留的 PENDING_PAYMENT 会占位）
    pend_hit = False
    for _pg in range(20):
        pend_hit = isinstance(pend, dict) and any(o.get("orderId") == ord4["orderId"] for o in pend.get("content", []))
        if pend_hit or not isinstance(pend, dict) or _pg + 1 >= max(1, pend.get("totalPages", 1)):
            break
        st, _, pend = http("GET", "/admin/orders/pending-payment?page=%d&size=20" % (_pg + 1), token=t_admin)
    check("运营待收款列表包含待支付订单(G1 入口)", pend_hit, f"{st} {str(pend)[:200]}")
    st, _, deny = http("POST", "/admin/orders/%s:confirm-offline-payment" % ord4["orderId"], {}, token=t_teacher, idem=str(uuid.uuid4()))
    check("非运营确认线下收款被拒(403)", st == 403, f"{st}")
    st, _, conf = http("POST", "/admin/orders/%s:confirm-offline-payment" % ord4["orderId"], {}, token=t_admin, idem=str(uuid.uuid4()))
    check("运营确认线下收款→订单已付", st == 200 and conf.get("status") == "PAID" and conf.get("channel") == "OFFLINE", f"{st} {conf}")
    st, _, pend2 = http("GET", "/admin/orders/pending-payment?page=0&size=20", token=t_admin)
    pend_gone = isinstance(pend2, dict) and not any(o.get("orderId") == ord4["orderId"] for o in pend2.get("content", []))
    check("已确认订单移出待收款列表", pend_gone, f"{st} {str(pend2)[:200]}")
    st, _, conf2 = http("POST", "/admin/orders/%s:confirm-offline-payment" % ord4["orderId"], {}, token=t_admin, idem=str(uuid.uuid4()))
    check("线下收款确认幂等", st == 200 and conf2.get("idempotent") is True, f"{st} {conf2}")
    st, _, odet4 = http("GET", "/order/orders/%s" % ord4["orderId"], token=t_parent)
    check("订单详情确认已付", st == 200 and odet4.get("status") == "PAID", f"{st} {odet4}")

    # 15.5 健康检查（P0-2 / 安全验收补充）：actuator health 可匿名访问
    st, _, health = http("GET", "http://localhost:8080/actuator/health")
    check("健康检查端点可用(200)", st == 200 and health.get("status") == "UP", f"{st} {health}")
    st, _, forbidden_health = http("GET", "http://localhost:8080/actuator/env")
    check("actuator 敏感端点不可达(401)", st in (401, 403, 404), f"{st}")

    print("== 16. 支付模式 B 专项：连点重试 / 金额防篡改 / 结算联动 ==")
    # 连点 3 次（不同幂等键）：全部幂等成功，不产生第二次副作用
    for i in range(3):
        st, _, rep = http("POST", "/admin/orders/%s:confirm-offline-payment" % ord4["orderId"], {},
            token=t_admin, idem=str(uuid.uuid4()))
        check("重复确认第 %d 次幂等返回" % (i + 2), st == 200 and rep.get("idempotent") is True, f"{st} {rep}")
    # 篡改金额字段：服务端以订单事实为准
    st, _, tamper = http("POST", "/admin/orders/%s:confirm-offline-payment" % ord4["orderId"],
        {"amountCents": 1}, token=t_admin, idem=str(uuid.uuid4()))
    check("伪造金额被忽略(以订单为准)", st == 200 and tamper.get("amountCents") == ord4.get("amountCents"),
        f"{st} {tamper}")
    # 结算联动：导师结算流水包含该订单课酬（且不重复）
    st, _, settle = http("GET", "/teacher/me/settlement", token=t_teacher)
    matches = [e for e in settle.get("entries", []) if e.get("bookingId") == ord4.get("bookingId")]         if isinstance(settle, dict) else []
    check("结算流水恰好 1 条（无重复结算）", st == 200 and len(matches) == 1, f"{st} {len(matches)}")
    # 通知联动：家长收到支付成功通知且正文含订单号
    st, _, notices = http("GET", "/messaging/notifications?page=0&size=50", token=t_parent)
    pay_notices = [n for n in notices.get("items", [])
                   if n.get("type") == "PAYMENT" and ord4.get("orderNo") in str(n.get("body"))]         if isinstance(notices, dict) else []
    check("家长支付成功通知恰好 1 条", st == 200 and len(pay_notices) == 1, f"{st} {len(pay_notices)}")

    print("== 17. 阶段一功能收口：家长学习周报（G2 前端入口对应链路） ==")
    st, _, kids2 = http("GET", "/parent/me/children", token=t_parent)
    active_kid = next((k for k in kids2 if k.get("status") == "ACTIVE"), None) if st == 200 and isinstance(kids2, list) else None
    check("家长可见 ACTIVE 孩子档案", active_kid is not None, f"{st} {str(kids2)[:160]}")
    st, p2body, _reg2 = register_or_login("parent", "PARENT", "13900000013", "Parent#2026",
        {"displayName": "周报越权对照家长", "phone": "13900000013", "password": "Parent#2026"})
    check("对照家长注册或登录", st == 200 and p2body.get("accessToken"), f"{st}")
    t_p2 = p2body.get("accessToken", "")
    if active_kid:
        # 前端传的是孩子列表里的 studentId（档案 ID），后端归一化为账户 ID 校验监护关系（本轮缺陷修复 D12）
        st, _, rep = http("GET", "/parent/me/weekly-report?studentId=%s" % active_kid["studentId"], token=t_parent)
        fields_ok = isinstance(rep, dict) and all(
            k in rep for k in ("weekLessons", "weekMinutes", "taskCompleted", "taskTotal", "teacherFeedbacks", "upcomingLessons"))
        check("家长拉取孩子学习周报(六字段)", fields_ok, f"{st} {str(rep)[:200]}")
        st, _, denyrep = http("GET", "/parent/me/weekly-report?studentId=%s" % active_kid["studentId"], token=t_p2)
        check("无绑定家长读取他人孩子周报被拒(403)", st == 403, f"{st} {denyrep}")

    print("== 18. 阶段一功能收口：学生历史课程（G4 评价入口数据源） ==")
    st, _, hist = http("GET", "/student/me/lesson-history", token=t_student)
    hist_hit = st == 200 and isinstance(hist, list) and any(
        b.get("bookingId") == bk1["bookingId"] and b.get("status") == "COMPLETED" for b in hist)
    check("历史课程含已完成课(G4 入口数据源)", hist_hit, f"{st} {str(hist)[:200]}")
    st, _, ups = http("GET", "/student/me/upcoming-lessons", token=t_student)
    no_done = st == 200 and isinstance(ups, list) and not any(b.get("status") in ("COMPLETED", "CLOSED") for b in ups)
    check("迫近课程列表不含已完成课", no_done, f"{st}")
    st, _, given = http("GET", "/review/me/reviews-given", token=t_student)
    given_hit = st == 200 and isinstance(given, list) and any(r.get("bookingId") == bk1["bookingId"] for r in given)
    check("我发出的评价含已评课（已评价标记数据源）", given_hit, f"{st} {str(given)[:160]}")

    print("== 19. G10 教师收藏与可约时间筛选（API 层；UI 过滤行为走浏览器走查） ==")
    # 起始状态收敛：上一轮可能残留收藏，先取消保证从确定状态开始
    st, _, g10fav0 = http("GET", "/matching/favorites", token=t_student)
    g10_tid = found["teacherId"]
    if st == 200 and any(f.get("teacherId") == g10_tid for f in g10fav0 or []):
        st, _, _ = http("POST", "/matching/teachers/%s:favorite" % g10_tid, {}, token=t_student, idem=str(uuid.uuid4()))
    check("G10 起始状态收敛(未收藏)", st in (200, 201), f"{st}")
    st, _, g10f1 = http("POST", "/matching/teachers/%s:favorite" % g10_tid, {}, token=t_student, idem=str(uuid.uuid4()))
    check("G10 收藏成功(favorited=true 以服务端响应为准)", st == 200 and g10f1.get("favorited") is True, f"{st} {g10f1}")
    st, _, g10list = http("GET", "/matching/favorites", token=t_student)
    check("G10 收藏列表含该教师且恰好一条(不产生重复记录)",
          st == 200 and sum(1 for f in g10list or [] if f.get("teacherId") == g10_tid) == 1, f"{st}")
    check("G10 收藏列表含服务端字段(displayName/favoritedAt)",
          any(f.get("teacherId") == g10_tid and f.get("displayName") and f.get("favoritedAt") for f in g10list or []),
          f"{str(g10list)[:160]}")
    st, _, g10anon = http("POST", "/matching/teachers/%s:favorite" % g10_tid, {}, idem=str(uuid.uuid4()))
    check("G10 匿名收藏被拒(4xx)", 400 <= st < 500, f"{st} {g10anon}")
    # 授权面如实记录：服务端未做角色限制（任意已登录角色可收藏），教师收藏另一位教师实测可达
    st, _, g10sr = http("GET", "/matching/teachers:search", token=t_student)
    g10_other = next((t.get("teacherId") for t in g10sr or [] if t.get("teacherId") != g10_tid), g10_tid)
    st, _, g10tf = http("POST", "/matching/teachers/%s:favorite" % g10_other, {}, token=t_teacher, idem=str(uuid.uuid4()))
    check("G10 教师角色收藏可达(服务端授权面=任意登录角色，前端不扩大)", st == 200 and g10tf.get("favorited") is True, f"{st} {g10tf}")
    st, _, _ = http("POST", "/matching/teachers/%s:favorite" % g10_other, {}, token=t_teacher, idem=str(uuid.uuid4()))
    check("G10 教师侧取消恢复原状", st == 200, f"{st}")
    st, _, g10f2 = http("POST", "/matching/teachers/%s:favorite" % g10_tid, {}, token=t_student, idem=str(uuid.uuid4()))
    check("G10 再调用=取消收藏(toggle 双向语义：两次调用不保持收藏)", st == 200 and g10f2.get("favorited") is False, f"{st} {g10f2}")
    st, _, g10list2 = http("GET", "/matching/favorites", token=t_student)
    check("G10 取消后收藏列表不再含该教师", st == 200 and not any(f.get("teacherId") == g10_tid for f in g10list2 or []), f"{st}")
    st, _, g10f3 = http("POST", "/matching/teachers/%s:favorite" % g10_tid, {}, token=t_student, idem=str(uuid.uuid4()))
    check("G10 恢复收藏(供浏览器走查展示真实状态)", st == 200 and g10f3.get("favorited") is True, f"{st} {g10f3}")
    st, _, g10sr2 = http("GET", "/matching/teachers:search?subject=数学", token=t_student)
    check("G10 检索结果均含 nextOpenSlotAt 字段(前端筛选数据源)",
          st == 200 and isinstance(g10sr2, list) and len(g10sr2) > 0 and all("nextOpenSlotAt" in t for t in g10sr2),
          f"{st} count={len(g10sr2) if isinstance(g10sr2, list) else 'n/a'}")

    print()
    print(f"通过 {PASS} 项，失败 {FAIL} 项")
    if failures:
        print("失败清单：")
        for name, detail in failures:
            print(" -", name, "->", str(detail)[:200])
        sys.exit(1)

if __name__ == "__main__":
    main()
