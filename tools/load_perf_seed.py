"""Seed synthetic load-perf fixtures into the explicitly named isolated database only.

Read-only for every other database; no production data. Foundation (students/parents),
phased teacher catalog growth (25k/50k/100k offerings) and business metadata
(support cases / consultation messages / learning file metadata) with real ownership.
Steps are idempotent-guarded by row counts; every statement runs via docker exec psql.
"""
import argparse, json, pathlib, subprocess

DB = 'nurture_gap_acceptance_load_perf_20261011'
TARGET_DIR = pathlib.Path('backend/target')
FIXTURE_JSON = TARGET_DIR / 'load-perf-fixture.json'
MARKER = 'LoadPerf2026'
# Reused BCrypt hashes of the project's own development acceptance accounts
# (isolated database only; same dev-only passwords Student#2026/Parent#2026/Teacher#2026).
PWD_STUDENT = '$2a$12$7VlvKbJKCsfOH/14D7rFquDCOsuV3VfsuBVb7aUh8LnL9wlrMTfAG'
PWD_PARENT = '$2a$12$u4awD80JT.Ry9f7h9at7hOqpSdCunWqIe3BfA8NGB7D0cCowhxq0K'
PWD_TEACHER = '$2a$12$Lau7FGHPxdDbAVy1P3yzIu4go6vHTblpljaJ2i9DEtM9reyByWlV6'
SUBJECTS_SQL = "ARRAY['数学','物理','化学','英语','语文','生物','历史','地理','信息']"
STYLES_SQL = "ARRAY['耐心细致','善于引导','严格高效']"
N_STUDENTS = 240
N_PARENTS = 60
N_TEACHERS = 5000
SQ_STUDENT = "role='STUDENT'"
SQ_PARENT = "role='PARENT'"
SQ_TEACHER_LOAD = "role='TEACHER' AND account_no LIKE 'LOAD-T-%'"

parser = argparse.ArgumentParser()
parser.add_argument('step', choices=['foundation', 'teachers', 'business', 'messaging', 'counts'])
parser.add_argument('count', nargs='?', type=int, help='teachers to add in this step')
args = parser.parse_args()

def sql(statement):
    result = subprocess.run(['docker', 'exec', '-i', 'deploy-postgres-1', 'psql', '-U', 'nurture',
                             '-d', DB, '-v', 'ON_ERROR_STOP=1', '-qAt'],
                            input=statement, text=True, encoding='utf-8', capture_output=True)
    if result.returncode != 0:
        raise SystemExit(f'SQL failed:\n{result.stderr}\n--- statement head: {statement[:400]}')
    return result.stdout

def count(table, where='true'):
    return int(sql(f'select count(*) from {table} where {where};').strip())

if args.step == 'counts':
    print(json.dumps({
        'offerings_total': count('subject_offerings'),
        'offerings_active': count('subject_offerings', 'active'),
        'teachers': count('accounts', "role='TEACHER'"),
        'students': count('accounts', "role='STUDENT'"),
        'parents': count('accounts', "role='PARENT'"),
        'bookings': count('bookings'), 'reviews': count('reviews'),
        'slots_open': count('availability_slots', "status='OPEN'"),
        'support_cases': count('support_cases'), 'support_replies': count('support_replies'),
        'support_evidence': count('support_evidence'), 'conversations': count('conversations'),
        'messages': count('messages'), 'consultation_files': count('consultation_files'),
        'learning_files': count('learning_files'),
    }, ensure_ascii=False))
    raise SystemExit(0)

if args.step == 'foundation':
    assert count('accounts', "account_no LIKE 'LOAD-S-%'") == 0, 'foundation already seeded; refusing to duplicate'
    sql(f"""
    BEGIN;
    INSERT INTO accounts(id,created_at,updated_at,role,account_no,phone,display_name,password_hash,status)
    SELECT gen_random_uuid(),now(),now(),'STUDENT','LOAD-S-'||lpad(i::text,6,'0'),
           '13920'||lpad(i::text,6,'0'),'负载学生'||i,'{PWD_STUDENT}','ACTIVE'
    FROM generate_series(1,{N_STUDENTS}) i;
    INSERT INTO student_profiles(id,created_at,updated_at,account_id,student_no,grade_level)
    SELECT gen_random_uuid(),now(),now(),a.id,a.account_no,
           CASE WHEN (split_part(a.account_no,'-',3)::int)%3=0 THEN '小学'
                WHEN (split_part(a.account_no,'-',3)::int)%3=1 THEN '初中' ELSE '高中' END
    FROM accounts a WHERE a.role='STUDENT' AND a.account_no LIKE 'LOAD-S-%';
    INSERT INTO accounts(id,created_at,updated_at,role,account_no,phone,display_name,password_hash,status)
    SELECT gen_random_uuid(),now(),now(),'PARENT','LOAD-P-'||lpad(i::text,6,'0'),
           '13921'||lpad(i::text,6,'0'),'负载家长'||i,'{PWD_PARENT}','ACTIVE'
    FROM generate_series(1,{N_PARENTS}) i;
    INSERT INTO parent_profiles(id,created_at,updated_at,account_id,parent_no,contact_name)
    SELECT gen_random_uuid(),now(),now(),a.id,a.account_no,'负载家长联系人'
    FROM accounts a WHERE a.role='PARENT' AND a.account_no LIKE 'LOAD-P-%';
    INSERT INTO guardian_bindings(id,created_at,updated_at,parent_id,student_id,status,relation)
    SELECT gen_random_uuid(),now(),now(),p.id,s.id,'ACTIVE','母亲'
    FROM (SELECT id,row_number() OVER (ORDER BY account_no) rn FROM accounts
          WHERE role='PARENT' AND account_no LIKE 'LOAD-P-%') p
    JOIN LATERAL (SELECT id FROM accounts WHERE role='STUDENT' AND account_no LIKE 'LOAD-S-%'
                  ORDER BY account_no OFFSET ((p.rn-1)*3)%{N_STUDENTS} LIMIT 3) s ON true;
    COMMIT;
    """)
    print(f"foundation done: students={count('accounts', SQ_STUDENT)}, "
          f"parents={count('accounts', SQ_PARENT)}, bindings={count('guardian_bindings')}")
    raise SystemExit(0)

if args.step == 'teachers':
    assert args.count and args.count > 0, 'teacher step needs a count'
    existing = count('accounts', "role='TEACHER' AND account_no LIKE 'LOAD-T-%'")
    start = existing + 1
    end = existing + args.count
    assert end <= N_TEACHERS, f'cap {N_TEACHERS} exceeded'
    sql(f"""
    BEGIN;
    CREATE TEMP TABLE batch_teachers ON COMMIT DROP AS
      SELECT i,gen_random_uuid() AS account,gen_random_uuid() AS profile FROM generate_series({start},{end}) i;
    INSERT INTO accounts(id,created_at,updated_at,role,account_no,phone,display_name,password_hash,status)
    SELECT account,now(),now(),'TEACHER','LOAD-T-'||lpad(i::text,6,'0'),
           '13930'||lpad(i::text,6,'0'),'负载教师'||i,'{PWD_TEACHER}',
           CASE WHEN i%50=0 THEN 'LOCKED' ELSE 'ACTIVE' END
    FROM batch_teachers;
    INSERT INTO teacher_profiles(id,created_at,updated_at,account_id,teacher_no,school,highest_degree,years_of_teaching)
    SELECT profile,now(),now(),account,'LOAD-T-'||lpad(i::text,6,'0'),
           '{MARKER}学校'||i,CASE WHEN i%3=0 THEN '学士' WHEN i%3=1 THEN '硕士' ELSE '博士' END,3+i%15
    FROM batch_teachers;
    INSERT INTO teacher_style_tags(teacher_id,style_tag)
    SELECT profile,({STYLES_SQL})[1+((i+j)%3)] FROM batch_teachers CROSS JOIN generate_series(0,i%4) j;
    INSERT INTO verification_cases(id,created_at,updated_at,teacher_id,status,submitted_at,decision_note)
    SELECT gen_random_uuid(),now(),now(),account,
           CASE WHEN i%37=0 THEN 'UNDER_REVIEW' WHEN i%53=0 THEN 'REJECTED' ELSE 'APPROVED' END,
           now()-interval '1 day','负载验证夹具'
    FROM batch_teachers;
    -- 20 offerings per teacher; the 25th teacher also keeps #20 inactive and gets #21 so
    -- active offerings stay exactly deterministic while an inactive slice exists.
    INSERT INTO subject_offerings(id,created_at,updated_at,teacher_id,subject,grade_band,price_per_hour_cents,active)
    SELECT gen_random_uuid(),now(),now(),profile,
           ({SUBJECTS_SQL})[1+((j-1)%9)]||'-'||i||'-'||j,
           CASE WHEN j%3=0 THEN '小学' WHEN j%3=1 THEN '初中' ELSE '高中' END,
           8000+((i*20+j)*137)%52000,
           NOT (j=20 AND i%25=0)
    FROM batch_teachers CROSS JOIN generate_series(1,20) j;
    INSERT INTO subject_offerings(id,created_at,updated_at,teacher_id,subject,grade_band,price_per_hour_cents,active)
    SELECT gen_random_uuid(),now(),now(),profile,
           ({SUBJECTS_SQL})[1+(20%9)]||'-'||i||'-21','初中',8000+((i*20+21)*137)%52000,true
    FROM batch_teachers WHERE i%25=0;
    INSERT INTO availability_slots(id,created_at,updated_at,teacher_id,start_at,end_at,status,version)
    SELECT gen_random_uuid(),now(),now(),account,
           now()+interval '2 days'+((i%30)||' days')::interval+(j||' hours')::interval,
           now()+interval '2 days'+((i%30)||' days')::interval+((j+1)||' hours')::interval,'OPEN',0
    FROM batch_teachers CROSS JOIN generate_series(1,2+i%7) j;
    -- 0-8 completed bookings per teacher (i%9), each anchored to a real student account
    INSERT INTO bookings(id,created_at,updated_at,student_id,teacher_id,slot_id,subject,booking_type,status,price_cents)
    SELECT gen_random_uuid(),now(),now(),s.id,t.account,slot.id,'负载课程','FORMAL','COMPLETED',10000
    FROM batch_teachers t
    CROSS JOIN generate_series(1,t.i%9) j
    JOIN LATERAL (SELECT id FROM availability_slots WHERE teacher_id=t.account ORDER BY start_at LIMIT 1) slot ON true
    JOIN LATERAL (SELECT id FROM accounts WHERE role='STUDENT' AND account_no LIKE 'LOAD-S-%'
                  ORDER BY account_no OFFSET (t.i*7+j)%{N_STUDENTS} LIMIT 1) s ON true;
    -- one STUDENT_TO_TEACHER review per completed booking
    INSERT INTO reviews(id,created_at,updated_at,booking_id,reviewer_id,reviewee_id,direction,rating,comment)
    SELECT gen_random_uuid(),now(),now(),b.id,b.student_id,b.teacher_id,'STUDENT_TO_TEACHER',1+(t.i+b.k)%5,'隔离负载夹具评价'
    FROM batch_teachers t
    JOIN LATERAL (SELECT id,student_id,teacher_id,row_number() OVER (ORDER BY id) k
                  FROM bookings WHERE teacher_id=t.account) b ON b.k<=t.i%9;
    COMMIT;
    ANALYZE accounts; ANALYZE teacher_profiles; ANALYZE subject_offerings; ANALYZE verification_cases;
    ANALYZE availability_slots; ANALYZE bookings; ANALYZE reviews; ANALYZE teacher_style_tags;
    """)
    print(f'teachers now: {count("accounts", SQ_TEACHER_LOAD)}, '
          f'offerings={count("subject_offerings")}, bookings={count("bookings")}, reviews={count("reviews")}')
    raise SystemExit(0)

if args.step == 'business':
    assert count('support_cases') == 0, 'business metadata already seeded; refusing to duplicate'
    sql(f"""
    BEGIN;
    -- 3,000 support cases with real owners: main students 1-5 (150 each), 500 teachers,
    -- 60 parents (4 each), remaining students round-robin; every 5th linked to a real booking.
    CREATE TEMP TABLE biz_students ON COMMIT DROP AS
      SELECT id,row_number() OVER (ORDER BY account_no) rn FROM accounts
      WHERE role='STUDENT' AND account_no LIKE 'LOAD-S-%';
    CREATE TEMP TABLE biz_teachers ON COMMIT DROP AS
      SELECT id FROM accounts WHERE role='TEACHER' AND account_no LIKE 'LOAD-T-%' ORDER BY account_no LIMIT 500;
    CREATE TEMP TABLE biz_bookings ON COMMIT DROP AS
      SELECT id,student_id,teacher_id FROM bookings ORDER BY id;
    CREATE TEMP TABLE biz_cases ON COMMIT DROP AS
      SELECT gen_random_uuid() id,
             CASE WHEN g.c<=750 THEN (SELECT id FROM biz_students WHERE rn=1+(g.c-1)%5)
                  WHEN g.c<=1250 THEN (SELECT id FROM biz_teachers OFFSET (g.c-751) LIMIT 1)
                  WHEN g.c<=1490 THEN (SELECT id FROM accounts WHERE role='PARENT' AND account_no LIKE 'LOAD-P-%'
                                       ORDER BY account_no OFFSET (g.c-1251)%{N_PARENTS} LIMIT 1)
                  ELSE (SELECT id FROM biz_students WHERE rn=6+(g.c-1491)%({N_STUDENTS}-5)) END owner_id,
             g.c ownerrn,
             CASE WHEN g.c%5=0 THEN (SELECT id FROM biz_bookings OFFSET g.c%(SELECT count(*) FROM biz_bookings) LIMIT 1) END booking
      FROM generate_series(1,3000) g(c);
    INSERT INTO support_cases(id,created_at,updated_at,owner_id,category,title,description,booking_id,status,revision)
    SELECT id,now(),now(),owner_id,
           (ARRAY['SCHEDULE','PAYMENT','LEARNING','ACCOUNT','OTHER'])[1+ownerrn%5],
           '负载工单'||ownerrn,'隔离负载夹具工单描述',booking,
           CASE WHEN ownerrn%20=0 THEN 'CLOSED' WHEN ownerrn%4=1 THEN 'OPEN' WHEN ownerrn%4=2 THEN 'IN_PROGRESS'
                WHEN ownerrn%4=3 THEN 'WAITING_USER' ELSE 'RESOLVED' END,
           ownerrn%3
    FROM biz_cases;
    INSERT INTO support_replies(id,created_at,updated_at,case_id,author_id,author_role,body,kind)
    SELECT gen_random_uuid(),now(),now(),c.id,c.owner_id,
           (SELECT role FROM accounts WHERE id=c.owner_id),'负载回复一','REPLY' FROM biz_cases c;
    INSERT INTO support_replies(id,created_at,updated_at,case_id,author_id,author_role,body,kind)
    SELECT gen_random_uuid(),now(),now(),c.id,c.owner_id,
           (SELECT role FROM accounts WHERE id=c.owner_id),'负载回复二','REPLY' FROM biz_cases c WHERE c.ownerrn%2=0;
    INSERT INTO support_evidence(id,created_at,updated_at,case_id,owner_id,storage_key,filename,content_type,size_bytes,sha256,deleted)
    SELECT gen_random_uuid(),now(),now(),c.id,c.owner_id,
           'load-perf/case-'||c.ownerrn||'-1.dat','负载证据1.dat','application/octet-stream',1024+c.ownerrn,
           md5(c.id::text||'a'),c.ownerrn%10=0 FROM biz_cases c WHERE c.ownerrn%2=0;
    INSERT INTO support_evidence(id,created_at,updated_at,case_id,owner_id,storage_key,filename,content_type,size_bytes,sha256,deleted)
    SELECT gen_random_uuid(),now(),now(),c.id,c.owner_id,
           'load-perf/case-'||c.ownerrn||'-2.dat','负载证据2.dat','application/octet-stream',2048+c.ownerrn,
           md5(c.id::text||'b'),false FROM biz_cases c WHERE c.ownerrn%6=0;
    INSERT INTO support_storage_quotas(owner_id,stored_bytes)
    SELECT owner_id,COALESCE(sum(size_bytes),0) FROM support_evidence e
    WHERE NOT EXISTS (SELECT 1 FROM support_storage_quotas q WHERE q.owner_id=e.owner_id)
    GROUP BY owner_id;
    COMMIT;
    """)
    raise SystemExit(0)

if args.step == 'messaging':
    assert count('conversations') == 0, 'messaging already seeded; refusing to duplicate'
    sql(f"""
    BEGIN;
    -- 300 real student-teacher conversations; message counts 20-80 (conversation #1 has 80)
    CREATE TEMP TABLE biz_convs ON COMMIT DROP AS
      SELECT g.c,gen_random_uuid() conv_id,20+(g.c*7)%61 mcount,
             (SELECT id FROM accounts WHERE role='STUDENT' AND account_no LIKE 'LOAD-S-%'
              ORDER BY account_no OFFSET g.c%{N_STUDENTS} LIMIT 1) student_id,
             (SELECT id FROM accounts WHERE role='TEACHER' AND account_no LIKE 'LOAD-T-%'
              ORDER BY account_no OFFSET g.c%500 LIMIT 1) teacher_id
      FROM generate_series(1,300) g(c);
    INSERT INTO conversations(id,created_at,updated_at,student_id,teacher_id,last_message_at)
    SELECT conv_id,now(),now(),student_id,teacher_id,now() FROM biz_convs;
    INSERT INTO messages(id,created_at,updated_at,conversation_id,sender_id,sender_role,body,sent_at)
    SELECT gen_random_uuid(),now(),now(),conv_id,
           CASE WHEN m%2=0 THEN student_id ELSE teacher_id END,
           CASE WHEN m%2=0 THEN 'STUDENT' ELSE 'TEACHER' END,
           '隔离负载消息'||m,now()-(80-m)*interval '5 minutes'
    FROM biz_convs CROSS JOIN LATERAL generate_series(1,CASE WHEN c=1 THEN 80 ELSE mcount END) m;
    INSERT INTO consultation_files(id,created_at,updated_at,conversation_id,owner_id,storage_key,filename,content_type,size_bytes,sha256)
    SELECT gen_random_uuid(),now(),now(),conv_id,student_id,
           'load-perf/conv-'||c||'.dat','负载咨询附件.dat','application/octet-stream',4096,md5(conv_id::text)
    FROM biz_convs WHERE c%3=1;
    INSERT INTO messages(id,created_at,updated_at,conversation_id,sender_id,sender_role,body,sent_at,attachment_id)
    SELECT gen_random_uuid(),now(),now(),f.conversation_id,f.owner_id,'STUDENT','见附件',now(),f.id
    FROM consultation_files f;
    -- 600 learning file metadata rows owned by students (main five own 100 each)
    CREATE TEMP TABLE biz_lfiles AS
      SELECT gen_random_uuid() id,g.c,
             (SELECT id FROM accounts WHERE role='STUDENT' AND account_no LIKE 'LOAD-S-%'
              ORDER BY account_no OFFSET CASE WHEN g.c<=500 THEN (g.c-1)%5 ELSE 5+(g.c-501)%({N_STUDENTS}-5) END LIMIT 1) student_id
      FROM generate_series(1,600) g(c);
    INSERT INTO learning_files(id,created_at,updated_at,owner_id,student_id,teacher_id,booking_id,category,storage_key,original_filename,content_type,size_bytes,sha256,deleted)
    SELECT f.id,now(),now(),f.student_id,f.student_id,b.teacher_id,b.id,'MATERIAL',
           'load-perf/lfile-'||f.c||'.dat','负载学习附件'||f.c||'.dat','application/octet-stream',2048+f.c,
           md5(f.id::text),f.c%15=0
    FROM biz_lfiles f
    JOIN LATERAL (SELECT id,teacher_id FROM bookings WHERE student_id=f.student_id ORDER BY id OFFSET f.c%9+1 LIMIT 1) b ON true;
    INSERT INTO learning_storage_quotas(owner_id,stored_bytes)
    SELECT owner_id,COALESCE(sum(size_bytes),0) FROM learning_files f
    WHERE NOT EXISTS (SELECT 1 FROM learning_storage_quotas q WHERE q.owner_id=f.owner_id)
    GROUP BY owner_id;
    COMMIT;
    ANALYZE support_cases; ANALYZE support_replies; ANALYZE support_evidence; ANALYZE conversations;
    ANALYZE messages; ANALYZE consultation_files; ANALYZE learning_files;
    """)
    print(f"cases={count('support_cases')} replies={count('support_replies')} evidence={count('support_evidence')} "
          f"convs={count('conversations')} messages={count('messages')} cfiles={count('consultation_files')} "
          f"lfiles={count('learning_files')}")
    manifest = json.loads(sql("""
    SELECT json_build_object(
      'student_main_phone',(SELECT phone FROM accounts WHERE role='STUDENT' AND account_no='LOAD-S-000001'),
      'parent_main_phone',(SELECT phone FROM accounts WHERE role='PARENT' AND account_no='LOAD-P-000001'),
      'teacher_main_phone',(SELECT phone FROM accounts WHERE role='TEACHER' AND account_no='LOAD-T-000001'),
      'admin_phone','13800000000',
      'student_main_id',(SELECT id::text FROM accounts WHERE role='STUDENT' AND account_no='LOAD-S-000001'),
      'parent_main_id',(SELECT id::text FROM accounts WHERE role='PARENT' AND account_no='LOAD-P-000001'),
      'teacher_main_id',(SELECT id::text FROM accounts WHERE role='TEACHER' AND account_no='LOAD-T-000001'),
      'main_case_id',(SELECT id::text FROM support_cases WHERE title='负载工单1'),
      'main_conversation_id',(SELECT c.id::text FROM conversations c JOIN accounts a ON a.id=c.student_id
                              WHERE a.account_no='LOAD-S-000001' ORDER BY c.created_at LIMIT 1),
      'other_case_id',(SELECT id::text FROM support_cases WHERE title='负载工单1500')
    );"""))
    FIXTURE_JSON.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f'fixture manifest -> {FIXTURE_JSON}')
    raise SystemExit(0)
