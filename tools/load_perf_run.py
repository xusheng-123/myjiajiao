"""Load/perf runner against the isolated loopback service and named database only.

Commands:
  validate    correctness / permission / pagination pass (content, scope, deep pages)
  mixed       weighted mixed read workload: --workers N --seconds S [--label L]
  scalecurve  five sorts x match-size slices at 8 workers
  coldstart   first-access burst right after a full app+database restart

Read-only against the platform: no business writes. Metrics: P50/P95/P99 (nearest-rank),
throughput, failure counts, app CPU/WS, PG connections and >1s active queries.
"""
import argparse, concurrent.futures, http.client, json, pathlib, random, statistics, subprocess, threading, time, uuid
from urllib.parse import urlencode

DB = 'nurture_gap_acceptance_load_perf_20261011'
ROOT = pathlib.Path('.')
FIXTURE = ROOT / 'backend/target/load-perf-fixture.json'
RESULTS = ROOT / 'backend/target/load-perf-results.json'
MARKER = 'LoadPerf2026学校'
LOGDIR = ROOT / 'backend/target'
PORTS = (8081, 8082)
APP_PID_FILE = LOGDIR / 'load-perf-app.pid'

parser = argparse.ArgumentParser()
parser.add_argument('command', choices=['validate', 'mixed', 'scalecurve', 'coldstart'])
parser.add_argument('--port', type=int, default=8081)
parser.add_argument('--workers', type=int, default=8)
parser.add_argument('--seconds', type=int, default=90)
parser.add_argument('--label', default='')
args = parser.parse_args()
if args.port not in PORTS:
    raise SystemExit('loopback perf port required (8081/8082)')
FIX = json.loads(FIXTURE.read_text(encoding='utf-8'))

def psql(query):
    r = subprocess.run(['docker', 'exec', '-i', 'deploy-postgres-1', 'psql', '-U', 'nurture',
                        '-d', DB, '-v', 'ON_ERROR_STOP=1', '-qAt'],
                       input=query.encode('utf-8'), capture_output=True)
    if r.returncode != 0:
        raise RuntimeError(f'psql failed: {r.stderr.decode("utf-8", "replace")[:300]}')
    return r.stdout.decode('utf-8').strip()

EXPECTED_CATALOG_SQL = """
SELECT count(*) FROM subject_offerings o
JOIN teacher_profiles p ON p.id=o.teacher_id
JOIN accounts a ON a.id=p.account_id AND a.role='TEACHER' AND a.status='ACTIVE'
JOIN LATERAL (SELECT v.status FROM verification_cases v WHERE v.teacher_id=a.id
              ORDER BY v.submitted_at DESC,v.id DESC LIMIT 1) verification ON verification.status='APPROVED'
WHERE o.active=true"""

def expected_total(where):
    return int(psql(EXPECTED_CATALOG_SQL + ' ' + where).strip())

# ---------- HTTP ----------
local = threading.local()
HDRS_BASE = {'Accept': 'application/json'}

def conn():
    c = getattr(local, 'conn', None)
    if c is None:
        c = http.client.HTTPConnection('127.0.0.1', args.port, timeout=30)
        local.conn = c
    return c

def request(method, path, body=None, cookie=None, idem=None):
    headers = dict(HDRS_BASE)
    if cookie: headers['Cookie'] = cookie
    if idem: headers['Idempotency-Key'] = idem
    payload = None
    if body is not None:
        payload = json.dumps(body).encode('utf-8')
        headers['Content-Type'] = 'application/json'
    start = time.perf_counter()
    c = conn()
    try:
        c.request(method, path, body=payload, headers=headers)
        resp = c.getresponse()
        data = resp.read()
    except (http.client.HTTPException, OSError):
        local.conn = None
        raise
    return resp.status, data, (time.perf_counter() - start) * 1000, resp.getheaders()

LOGIN_PATH = '/api/v1/identity/auth/login:password'

def login(role, phone, password):
    st, data, _, headers = request('POST', LOGIN_PATH,
                                   {'role': role, 'account': phone, 'password': password},
                                   idem=str(uuid.uuid4()))
    if st != 200:
        raise SystemExit(f'login failed for {role} {phone}: {st} {data[:200]}')
    parts = []
    for name, value in headers:
        if name.lower() == 'set-cookie' and value.split('=')[0] in ('NURTURE_AT', 'NURTURE_RT'):
            parts.append(value.split(';')[0])
    if not parts:
        raise SystemExit('no session cookie issued')
    return '; '.join(parts)

COOKIES = {}

def cookie_for(role):
    if role not in COOKIES:
        creds = {'STUDENT': (FIX['student_main_phone'], 'Student#2026'),
                 'PARENT': (FIX['parent_main_phone'], 'Parent#2026'),
                 'TEACHER': (FIX['teacher_main_phone'], 'Teacher#2026'),
                 'ADMIN': (FIX['admin_phone'], 'Admin#2026')}
        phone, pwd = creds[role]
        COOKIES[role] = login(role, phone, pwd)
    return COOKIES[role]

# ---------- metrics ----------
def percentile(sorted_lat, q):
    if not sorted_lat: return None
    import math
    return sorted_lat[max(0, math.ceil(q * len(sorted_lat)) - 1)]

APP_PID = None
if APP_PID_FILE.exists():
    try: APP_PID = int(APP_PID_FILE.read_text().strip())
    except ValueError: APP_PID = None

def app_process_stats():
    if not APP_PID: return None
    r = subprocess.run(['powershell', '-NoProfile', '-Command',
        f'$p=Get-CimInstance Win32_Process -Filter "ProcessId={APP_PID}";'
        'if($p){"{0} {1} {2}" -f $p.ProcessId,($p.KernelModeTime+$p.UserModeTime),$p.WorkingSetSize}'],
        capture_output=True, text=True)
    out = r.stdout.strip().split()
    if len(out) != 3: return None
    return int(out[1]), int(out[2])

def pg_stats():
    row = psql("""select (select count(*) from pg_stat_activity where datname=current_database() and state='active'),
       (select count(*) from pg_stat_activity where datname=current_database()),
       (select coalesce(max(extract(epoch from now()-query_start))*1000,0) from pg_stat_activity
         where datname=current_database() and state='active' and pid<>pg_backend_pid()),
       (select coalesce(string_agg(left(regexp_replace(query,'\\s+',' ','g'),90), ' || '),'')
          from pg_stat_activity where datname=current_database() and state='active'
          and now()-query_start > interval '1 second' and pid<>pg_backend_pid());""")
    parts = row.split('|', 3)
    return {'pg_active': int(parts[0]), 'pg_total': int(parts[1]),
            'longest_active_ms': float(parts[2]), 'slow_queries': parts[3] if len(parts) > 3 else ''}

def sampler(stop_event, samples):
    last = None
    while not stop_event.is_set():
        entry = {'t': round(time.time(), 1)}
        st = app_process_stats()
        if st and last:
            dt = entry['t'] - last[0]
            if dt > 0:
                entry['app_cpu_cores'] = round((st[0] - last[1]) / 1e7 / dt, 2)
        if st: entry['app_ws_mb'] = round(st[1] / 1e6, 1)
        last = (entry['t'], st[0]) if st else last
        try: entry.update(pg_stats())
        except RuntimeError: pass
        samples.append(entry)
        stop_event.wait(5)

# ---------- workload groups ----------
def catalog_params(rng):
    sort = rng.choice(['recommended', 'price-asc', 'price-desc', 'rating-desc', 'reviews-desc'])
    mode = rng.choices(['kw', 'subj_grade', 'kw_price', 'subj'], weights=[60, 15, 15, 10])[0]
    params = {'sort': sort, 'page': rng.randint(0, 3), 'size': 20}
    if mode == 'kw': params['keyword'] = MARKER
    elif mode == 'subj_grade': params.update(subject='数学', gradeBand='初中')
    elif mode == 'kw_price': params.update(keyword=MARKER, maxPricePerHourCents=20000)
    else: params['subject'] = '数学'
    return params, sort, mode

def group_request(group, rng):
    """Returns (path, cookie, checker_key) for the weighted mix."""
    if group == 'catalog':
        params, sort, mode = catalog_params(rng)
        return '/api/v1/matching/public/teachers-page?' + urlencode(params), None, ('catalog', sort, mode)
    if group == 'deep':
        sort = rng.choice(['price-asc', 'price-desc'])
        params = {'sort': sort, 'page': rng.choice([0, 49, 199]), 'size': 50, 'keyword': MARKER}
        return '/api/v1/matching/public/teachers-page?' + urlencode(params), None, ('deep', sort)
    if group == 'cases':
        role = rng.choice(['STUDENT', 'PARENT', 'TEACHER'])
        params = {'page': rng.randint(0, 2), 'size': 20}
        st = rng.choices([None, 'OPEN', 'IN_PROGRESS'], weights=[60, 20, 20])[0]
        if st: params['status'] = st
        return ('/api/v1/support/cases?' + urlencode(params), cookie_for(role), ('cases', role, st))
    if group == 'options':
        role = rng.choice(['STUDENT', 'PARENT'])
        params = {'page': rng.randint(0, 1), 'size': 20}
        return ('/api/v1/support/cases/course-options?' + urlencode(params), cookie_for(role), ('options', role))
    if group == 'messages':
        page = rng.randint(0, 1)
        return (f"/api/v1/messaging/conversations/{FIX['main_conversation_id']}/messages?page={page}",
                cookie_for('STUDENT'), ('messages', page))
    if group == 'files':
        page = rng.randint(0, 1)
        return (f'/api/v1/learning/files?page={page}&size=20', cookie_for('STUDENT'), ('files', page))
    raise ValueError(group)

def cheap_check(key, status, data):
    """Per-response correctness beyond HTTP 200; raises AssertionError on contract violation."""
    if status != 200:
        raise AssertionError(f'unexpected status {status}: {data[:200]}')
    kind = key[0]
    if kind in ('catalog', 'deep'):
        body = json.loads(data)
        assert isinstance(body.get('totalElements'), int) and body['totalElements'] >= 0
        content = body.get('content')
        assert isinstance(content, list) and len(content) <= (50 if kind == 'deep' else 20)
        if content:
            if key[1] == 'price-asc':
                prices = [r['pricePerHourCents'] for r in content]
                assert prices == sorted(prices), 'price-asc not monotonic'
            elif key[1] == 'price-desc':
                prices = [r['pricePerHourCents'] for r in content]
                assert prices == sorted(prices, reverse=True), 'price-desc not monotonic'
            joined = json.dumps(content[0], ensure_ascii=False)
            if len(key) > 2 and key[2] in ('kw', 'kw_price'):
                assert MARKER in joined, 'keyword row missing marker'
            for forbidden in ('phone', 'idDocumentKey', 'degreeCertKey', 'storageKey'):
                assert forbidden not in joined, f'privacy leak: {forbidden}'
    elif kind == 'cases':
        body = json.loads(data)
        assert isinstance(body.get('totalElements'), int)
        assert isinstance(body.get('content'), list) and len(body['content']) <= 20
    elif kind == 'options':
        body = json.loads(data)
        assert isinstance(body.get('content'), list) and len(body['content']) <= 20
    elif kind == 'messages':
        body = json.loads(data)
        assert isinstance(body, list) and len(body) <= 100, 'messages page over 100'
    elif kind == 'files':
        body = json.loads(data)
        assert isinstance(body.get('content'), list) and len(body['content']) <= 20

GROUPS = ['catalog'] * 40 + ['deep'] * 15 + ['cases'] * 15 + ['options'] * 10 + ['messages'] * 15 + ['files'] * 5

# ---------- commands ----------
def run_mixed():
    stop = threading.Event()
    samples = []
    threading.Thread(target=sampler, args=(stop, samples), daemon=True).start()
    lat = {}
    fails = []
    counts = {'done': 0}
    lock = threading.Lock()
    deadline = time.time() + args.seconds
    abort_reason = []

    def worker(_):
        rng = random.Random(threading.get_ident() ^ int(time.time() * 1000))
        while time.time() < deadline and not stop.is_set():
            group = rng.choice(GROUPS)
            try:
                path, cookie, key = group_request(group, rng)
                st, data, ms, _ = request('GET', path, cookie=cookie)
                with lock:
                    lat.setdefault(key, []).append(ms)
                    counts['done'] += 1
                cheap_check(key, st, data)
            except AssertionError as e:
                with lock:
                    fails.append(f'{group}: {e}')
            except Exception as e:
                with lock:
                    fails.append(f'{group}: {type(e).__name__} {e}')

    for role in ('STUDENT', 'PARENT', 'TEACHER'): cookie_for(role)
    t0 = time.time()
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = [pool.submit(worker, i) for i in range(args.workers)]
        while time.time() < deadline:
            time.sleep(15)
            el = time.time() - t0
            all_lat = sorted(x for v in lat.values() for x in v)
            fr = len(fails) / max(1, counts['done'])
            print(f'[{args.label or "mixed"} w{args.workers}] {el:.0f}s done={counts["done"]} '
                  f'p95={percentile(all_lat, 0.95) and round(percentile(all_lat, 0.95), 1)}ms '
                  f'fail={len(fails)} ({fr*100:.2f}%)', flush=True)
            if len(fails) / max(1, counts['done']) > 0.01:
                abort_reason.append('failure rate > 1%')
            alll = sorted(x for v in lat.values() for x in v)
            if alll and percentile(alll, 0.99) > 10000:
                abort_reason.append('P99 > 10s')
            if abort_reason:
                stop.set()
                break
        for f in futures: f.result()
    stop.set()
    time.sleep(0.3)
    report = {'command': 'mixed', 'label': args.label or 'mixed', 'workers': args.workers,
              'seconds': args.seconds, 'elapsed_s': round(time.time() - t0, 1),
              'done': counts['done'], 'failures': len(fails),
              'fail_samples': fails[:20], 'abort_reason': abort_reason,
              'groups': {}}
    for key, xs in sorted(lat.items(), key=lambda kv: '|'.join(map(str, kv[0]))):
        s = sorted(xs)
        report['groups']['|'.join(map(str, key))] = {
            'n': len(s), 'p50_ms': round(percentile(s, 0.5), 2),
            'p95_ms': round(percentile(s, 0.95), 2), 'p99_ms': round(percentile(s, 0.99), 2)}
    alll = sorted(x for v in lat.values() for x in v)
    report['overall'] = {'p50_ms': round(percentile(alll, 0.5), 2), 'p95_ms': round(percentile(alll, 0.95), 2),
                         'p99_ms': round(percentile(alll, 0.99), 2),
                         'throughput_rps': round(counts['done'] / report['elapsed_s'], 2)}
    report['samples'] = samples
    save(report)
    print(json.dumps({'overall': report['overall'], 'failures': report['failures'],
                      'abort_reason': abort_reason}, ensure_ascii=False), flush=True)

def run_validate():
    v = {}
    # expected totals straight from the same predicates the projection uses
    v['total_marker_all'] = expected_total(f" AND strpos(concat(o.subject,' ',o.grade_band,' ',coalesce(p.school,'null'),' ',a.display_name),'{MARKER}')>0")
    v['total_marker_price'] = expected_total(f" AND strpos(concat(o.subject,' ',o.grade_band,' ',coalesce(p.school,'null'),' ',a.display_name),'{MARKER}')>0 AND o.price_per_hour_cents<=20000")
    v['total_subject_math'] = expected_total(" AND o.subject ILIKE '%数学%'")
    v['total_subject_grade'] = expected_total(" AND o.subject ILIKE '%数学%' AND o.grade_band='初中'")
    v['db_locked_teachers'] = int(psql("select count(*) from accounts where role='TEACHER' and account_no like 'LOAD-T-%' and status='LOCKED'").strip())
    # five sorts page 0 + totals equality + privacy + monotonic
    for sort in ('recommended', 'price-asc', 'price-desc', 'rating-desc', 'reviews-desc'):
        path = '/api/v1/matching/public/teachers-page?' + urlencode({'keyword': MARKER, 'sort': sort, 'page': 0, 'size': 20})
        st, data, ms, _ = request('GET', path)
        cheap_check(('catalog', sort, 'kw'), st, data)
        body = json.loads(data)
        assert body['totalElements'] == v['total_marker_all'], (sort, body['totalElements'], v['total_marker_all'])
        v[f'first_page_{sort}_ms'] = round(ms, 2)
    # adjacent pages disjoint + tail page
    keys = lambda rows: [(r['teacherId'], r['subject'], r['gradeBand']) for r in rows]
    p0 = json.loads(request('GET', '/api/v1/matching/public/teachers-page?' + urlencode({'keyword': MARKER, 'sort': 'price-asc', 'page': 0, 'size': 50}))[1])
    p1 = json.loads(request('GET', '/api/v1/matching/public/teachers-page?' + urlencode({'keyword': MARKER, 'sort': 'price-asc', 'page': 1, 'size': 50}))[1])
    assert not set(keys(p0['content'])) & set(keys(p1['content'])), 'page overlap'
    last_page = v['total_marker_all'] // 50
    tail = json.loads(request('GET', '/api/v1/matching/public/teachers-page?' + urlencode({'keyword': MARKER, 'sort': 'price-asc', 'page': last_page, 'size': 50}))[1])
    assert len(tail['content']) == v['total_marker_all'] - last_page * 50, 'tail page size wrong'
    beyond = json.loads(request('GET', '/api/v1/matching/public/teachers-page?' + urlencode({'keyword': MARKER, 'sort': 'price-asc', 'page': last_page + 1, 'size': 50}))[1])
    assert beyond['content'] == [] and beyond['totalElements'] == v['total_marker_all'], 'beyond page wrong'
    # filtered totals equality
    st, data, _, _ = request('GET', '/api/v1/matching/public/teachers-page?' + urlencode({'subject': '数学', 'gradeBand': '初中', 'sort': 'recommended'}))
    assert json.loads(data)['totalElements'] == v['total_subject_grade'], 'subject+grade total mismatch'
    st, data, _, _ = request('GET', '/api/v1/matching/public/teachers-page?' + urlencode({'subject': '数学', 'sort': 'recommended'}))
    assert json.loads(data)['totalElements'] == v['total_subject_math'], 'subject total mismatch'
    st, data, _, _ = request('GET', '/api/v1/matching/public/teachers-page?' + urlencode({'keyword': MARKER, 'maxPricePerHourCents': 20000}))
    assert json.loads(data)['totalElements'] == v['total_marker_price'], 'price total mismatch'
    # invalid sort -> 400
    st, data, _, _ = request('GET', '/api/v1/matching/public/teachers-page?' + urlencode({'keyword': MARKER, 'sort': 'evil; drop'}))
    v['invalid_sort_status'] = st
    assert st == 400, f'invalid sort should 400, got {st}'
    # locked / rejected / under-review teachers excluded (exact-school keywords)
    for idx, why in ((550, 'LOCKED'), (530, 'REJECTED'), (518, 'UNDER_REVIEW')):
        st, data, _, _ = request('GET', '/api/v1/matching/public/teachers-page?' + urlencode({'keyword': f'{MARKER}{idx}'}))
        body = json.loads(data)
        assert body['totalElements'] == 0, f'{why} teacher {idx} visible: {body["totalElements"]}'
    v['hidden_teachers_ok'] = ['550=LOCKED', '530=REJECTED', '518=UNDER_REVIEW']
    # anonymous & role permission matrix
    st, _, _, _ = request('GET', '/api/v1/support/cases?page=0&size=20')
    v['anon_cases'] = st
    assert st == 403, f'anon cases expected 403 got {st}'
    st, _, _, _ = request('GET', '/api/v1/support/cases/course-options?page=0&size=20', cookie=cookie_for('ADMIN'))
    v['admin_course_options'] = st
    assert st == 403, f'admin course-options expected 403 got {st}'
    # scoped totals per caller
    scopes = {
        'student_cases': ("select count(*) from support_cases c join accounts a on a.id=c.owner_id where a.account_no='LOAD-S-000001'", 'STUDENT', '/api/v1/support/cases?page=0&size=20'),
        'parent_cases': ("select count(*) from support_cases c join accounts a on a.id=c.owner_id where a.account_no='LOAD-P-000001'", 'PARENT', '/api/v1/support/cases?page=0&size=20'),
        'teacher_cases': ("select count(*) from support_cases c join accounts a on a.id=c.owner_id where a.account_no='LOAD-T-000001'", 'TEACHER', '/api/v1/support/cases?page=0&size=20'),
        'student_options': ("select count(*) from bookings b join accounts a on a.id=b.student_id where a.account_no='LOAD-S-000001'", 'STUDENT', '/api/v1/support/cases/course-options?page=0&size=20'),
        'parent_options': ("""select count(*) from bookings b where b.student_id in
                              (select sp.account_id from guardian_bindings g
                               join parent_profiles pp on pp.id=g.parent_id
                               join accounts pa on pa.id=pp.account_id
                               join student_profiles sp on sp.id=g.student_id
                               where pa.account_no='LOAD-P-000001' and g.status='ACTIVE')""", 'PARENT', '/api/v1/support/cases/course-options?page=0&size=20'),
    }
    for name, (q, role, path) in scopes.items():
        exp = int(psql(q).strip())
        st, data, _, _ = request('GET', path, cookie=cookie_for(role))
        body = json.loads(data)
        assert st == 200 and body['totalElements'] == exp, (name, st, body.get('totalElements'), exp)
        v[name] = exp
    # support case pages disjoint (S1 owns 150)
    seen = set()
    for page in range(0, 8):
        st, data, _, _ = request('GET', f'/api/v1/support/cases?page={page}&size=20', cookie=cookie_for('STUDENT'))
        rows = json.loads(data)['content']
        ids = [r['caseId'] for r in rows]
        assert not seen & set(ids), f'case page {page} overlap'
        seen |= set(ids)
    assert len(seen) == v['student_cases'], 'student cases page union != total'
    # cross-user detail must 403
    st, _, _, _ = request('GET', f"/api/v1/support/cases/{FIX['other_case_id']}", cookie=cookie_for('STUDENT'))
    v['cross_user_case_detail'] = st
    assert st == 403, f'cross-user case detail expected 403 got {st}'
    # messages scope + count
    exp_msgs = int(psql(f"select count(*) from messages where conversation_id='{FIX['main_conversation_id']}'").strip())
    st, data, _, _ = request('GET', f"/api/v1/messaging/conversations/{FIX['main_conversation_id']}/messages?page=0", cookie=cookie_for('STUDENT'))
    body = json.loads(data)
    assert st == 200 and len(body) == min(100, exp_msgs), (st, len(body), exp_msgs)
    v['main_conversation_messages'] = exp_msgs
    other_conv = psql("select c.id::text from conversations c join accounts a on a.id=c.student_id where a.account_no='LOAD-S-000004' limit 1")
    st, _, _, _ = request('GET', f'/api/v1/messaging/conversations/{other_conv}/messages?page=0', cookie=cookie_for('STUDENT'))
    v['cross_user_messages'] = st
    assert st == 403, f'cross-user messages expected 403 got {st}'
    # learning files scoped
    exp_files = int(psql("select count(*) from learning_files f join accounts a on a.id=f.owner_id where a.account_no='LOAD-S-000001' and f.deleted=false").strip())
    st, data, _, _ = request('GET', '/api/v1/learning/files?page=0&size=20', cookie=cookie_for('STUDENT'))
    body = json.loads(data)
    assert st == 200 and body['totalElements'] == exp_files, (st, body.get('totalElements'), exp_files)
    v['student_learning_files'] = exp_files
    v['PASS'] = True
    save({'command': 'validate', 'workers': 1, 'seconds': 0, 'elapsed_s': 0, 'done': 0,
          'failures': 0, 'fail_samples': [], 'abort_reason': [], 'groups': {}, 'overall': {},
          'samples': [], 'validate': v})
    print(json.dumps(v, ensure_ascii=False, indent=1), flush=True)

def run_scalecurve():
    slices = {
        'one_teacher': {'keyword': f'{MARKER}1251'},
        'math_primary': {'subject': '数学', 'gradeBand': '小学'},
        'math_all': {'subject': '数学'},
        'marker_price20k': {'keyword': MARKER, 'maxPricePerHourCents': 20000},
        'marker_all': {'keyword': MARKER},
    }
    out = {}
    for name, params in slices.items():
        where = ' AND o.active=true'
        if 'keyword' in params:
            where += f" AND strpos(concat(o.subject,' ',o.grade_band,' ',coalesce(p.school,'null'),' ',a.display_name),'{params['keyword']}')>0"
        if 'subject' in params: where += f" AND o.subject ILIKE '%{params['subject']}%'"
        if 'gradeBand' in params: where += f" AND o.grade_band='{params['gradeBand']}'"
        if 'maxPricePerHourCents' in params: where += f" AND o.price_per_hour_cents<={params['maxPricePerHourCents']}"
        exp = expected_total(where)
        out[name] = {'expected_total': exp, 'sorts': {}}
        for sort in ('recommended', 'price-asc', 'price-desc', 'rating-desc', 'reviews-desc'):
            qs = urlencode({**params, 'sort': sort, 'page': 0, 'size': 20})
            request('GET', '/api/v1/matching/public/teachers-page?' + qs)  # warm
            lat = []
            def one(_):
                st, data, ms, _ = request('GET', '/api/v1/matching/public/teachers-page?' + qs)
                assert st == 200 and json.loads(data)['totalElements'] == exp, (st, data[:120])
                lat.append(ms)
            with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
                list(pool.map(one, range(40)))
            s = sorted(lat)
            out[name]['sorts'][sort] = {'p50_ms': round(percentile(s, 0.5), 2),
                                        'p95_ms': round(percentile(s, 0.95), 2),
                                        'p99_ms': round(percentile(s, 0.99), 2)}
        print(f'{name}: n={exp} ' + json.dumps(out[name]['sorts'], ensure_ascii=False), flush=True)
    save({'command': 'scalecurve', 'workers': 8, 'seconds': 0, 'elapsed_s': 0, 'done': 0,
          'failures': 0, 'fail_samples': [], 'abort_reason': [], 'groups': {}, 'overall': {},
          'samples': [], 'scalecurve': out})
    print('scalecurve done', flush=True)

def run_coldstart():
    out = {'sequence': []}
    # login first (cold caches), then one burst per group, 5 sequential each
    t0 = time.perf_counter()
    COOKIES.clear()
    cookie_for('STUDENT'); cookie_for('PARENT'); cookie_for('TEACHER')
    out['logins_ms'] = round((time.perf_counter() - t0) * 1000, 1)
    bursts = {
        'catalog_recommended': ('/api/v1/matching/public/teachers-page?' + urlencode({'keyword': MARKER, 'sort': 'recommended'}), None),
        'catalog_price_asc': ('/api/v1/matching/public/teachers-page?' + urlencode({'keyword': MARKER, 'sort': 'price-asc'}), None),
        'cases_student': ('/api/v1/support/cases?page=0&size=20', 'STUDENT'),
        'options_student': ('/api/v1/support/cases/course-options?page=0&size=20', 'STUDENT'),
        'messages': (f"/api/v1/messaging/conversations/{FIX['main_conversation_id']}/messages?page=0", 'STUDENT'),
        'files': ('/api/v1/learning/files?page=0&size=20', 'STUDENT'),
    }
    for name, (path, role) in bursts.items():
        lat = []
        for i in range(5):
            st, data, ms, _ = request('GET', path, cookie=cookie_for(role) if role else None)
            assert st == 200, (name, st, data[:120])
            lat.append(round(ms, 2))
        out['sequence'].append({'group': name, 'latencies_ms': lat})
        print(f'{name}: {lat}', flush=True)
    save({'command': 'coldstart', 'workers': 1, 'seconds': 0, 'elapsed_s': 0, 'done': 0,
          'failures': 0, 'fail_samples': [], 'abort_reason': [], 'groups': {}, 'overall': {},
          'samples': [], 'coldstart': out})
    print('coldstart done', flush=True)

def save(run):
    data = {'env': {'database': DB, 'port': args.port,
                    'started_at': time.strftime('%Y-%m-%d %H:%M:%S')}, 'runs': []}
    if RESULTS.exists():
        try: data = json.loads(RESULTS.read_text(encoding='utf-8'))
        except Exception: pass
    data.setdefault('runs', []).append(run)
    RESULTS.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding='utf-8')
    print(f'results -> {RESULTS}', flush=True)

if __name__ == '__main__':
    {'validate': run_validate, 'mixed': run_mixed, 'scalecurve': run_scalecurve,
     'coldstart': run_coldstart}[args.command]()
