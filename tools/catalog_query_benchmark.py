"""Synthetic catalog benchmark on an explicit isolated database and loopback API only."""
import argparse, concurrent.futures, http.client, json, pathlib, statistics, subprocess, time, uuid
from urllib.parse import urlencode

parser=argparse.ArgumentParser()
parser.add_argument('database');parser.add_argument('--port',type=int,default=8081)
args=parser.parse_args()
if not args.database.startswith('nurture_gap_acceptance_catalog_perf_') or not args.database.replace('_','').isalnum():
    raise SystemExit('Explicit catalog performance database required')
if args.port not in (8081,8082):raise SystemExit('Dedicated loopback acceptance port required')

def sql(statement):
    result=subprocess.run(['docker','exec','-i','deploy-postgres-1','psql','-U','nurture','-d',args.database,'-v','ON_ERROR_STOP=1','-At'],
        input=statement,text=True,encoding='utf-8',capture_output=True,check=True)
    return result.stdout

def seed(count):
    marker='CatalogPerf'+uuid.uuid4().hex[:8]
    sql(f"""
    BEGIN;
    CREATE TEMP TABLE fixture_teachers ON COMMIT DROP AS
      SELECT i,gen_random_uuid() AS account,gen_random_uuid() AS profile,gen_random_uuid() AS slot,
             gen_random_uuid() AS booking FROM generate_series(1,{count}) i;
    INSERT INTO accounts(id,created_at,updated_at,role,account_no,phone,display_name,status)
      SELECT account,now(),now(),'TEACHER','P-{marker[-8:]}-'||i,'p{marker[-8:]}'||i,'隔离性能教师'||i,'ACTIVE' FROM fixture_teachers;
    INSERT INTO teacher_profiles(id,created_at,updated_at,account_id,teacher_no,school,highest_degree,years_of_teaching)
      SELECT profile,now(),now(),account,'P-{marker[-8:]}-'||i,'{marker}','硕士',3 FROM fixture_teachers;
    INSERT INTO teacher_style_tags(teacher_id,style_tag) SELECT profile,'耐心细致' FROM fixture_teachers;
    INSERT INTO subject_offerings(id,created_at,updated_at,teacher_id,subject,grade_band,price_per_hour_cents,active)
      SELECT gen_random_uuid(),now(),now(),profile,'性能课程'||j,CASE WHEN j%2=0 THEN '高中' ELSE '初中' END,10000+i+j,true
      FROM fixture_teachers CROSS JOIN generate_series(1,20) j;
    INSERT INTO verification_cases(id,created_at,updated_at,teacher_id,submitted_at,status)
      SELECT gen_random_uuid(),now(),now(),account,now(),'APPROVED' FROM fixture_teachers;
    INSERT INTO availability_slots(id,created_at,updated_at,teacher_id,start_at,end_at,status,version)
      SELECT slot,now(),now(),account,now()+interval '2 days',now()+interval '2 days 1 hour','OPEN',0 FROM fixture_teachers;
    INSERT INTO bookings(id,created_at,updated_at,student_id,teacher_id,slot_id,subject,booking_type,status,price_cents)
      SELECT booking,now(),now(),gen_random_uuid(),account,slot,'性能课程','FORMAL','COMPLETED',0 FROM fixture_teachers;
    INSERT INTO reviews(id,created_at,updated_at,booking_id,reviewer_id,reviewee_id,direction,rating,comment)
      SELECT gen_random_uuid(),now(),now(),booking,gen_random_uuid(),account,'STUDENT_TO_TEACHER',1+i%5,'仅隔离性能夹具' FROM fixture_teachers;
    COMMIT;
    ANALYZE accounts; ANALYZE teacher_profiles; ANALYZE subject_offerings; ANALYZE verification_cases; ANALYZE availability_slots; ANALYZE reviews;
    """)
    return marker

def request(marker,sort='recommended',page=0,size=20):
    start=time.perf_counter();conn=http.client.HTTPConnection('127.0.0.1',args.port,timeout=60)
    conn.request('GET','/api/v1/matching/public/teachers-page?'+urlencode({'keyword':marker,'sort':sort,'page':page,'size':size}))
    response=conn.getresponse();data=response.read();conn.close()
    assert response.status==200,(response.status,data[:500])
    return json.loads(data),(time.perf_counter()-start)*1000

assert int(sql("select max(version::int) from flyway_schema_history where success").strip())>=21
assert 'JdbcPublicTeacherCatalog' in pathlib.Path('backend/src/main/java/com/nurture/matching/infra/JdbcPublicTeacherCatalog.java').read_text(encoding='utf-8')
results=[]
for teacher_count in (100,500,1000):
    marker=seed(teacher_count);expected=teacher_count*20
    row={'teachers':teacher_count,'offerings':expected,'database_offerings':int(sql('select count(*) from subject_offerings').strip()),'sorts':{}}
    for sort in ('recommended','price-asc','price-desc','rating-desc','reviews-desc'):
        request(marker,sort);durations=[]
        for _ in range(10):
            payload,elapsed=request(marker,sort);assert payload['totalElements']==expected and len(payload['content'])==20
            durations.append(elapsed)
        row['sorts'][sort]={'median_ms':round(statistics.median(durations),2),'p95_ms':round(sorted(durations)[-1],2)}
    first,_=request(marker,'price-asc',0,50);second,_=request(marker,'price-asc',1,50)
    key=lambda x:(x['teacherId'],x['subject'],x['gradeBand'])
    assert not set(map(key,first['content']))&set(map(key,second['content']))
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        concurrent_results=list(pool.map(lambda _:request(marker),range(16)))
    assert all(payload['totalElements']==expected for payload,_ in concurrent_results)
    row['concurrent8_max_ms']=round(max(elapsed for _,elapsed in concurrent_results),2)
    results.append(row);print(json.dumps(row,ensure_ascii=False),flush=True)

out=pathlib.Path('backend/target/catalog-query-benchmark.json');out.write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
# Preserve a real plan for the catalog's eligibility/count path; no fictional throughput claims.
plan=sql(f"EXPLAIN (ANALYZE,BUFFERS) SELECT count(*) FROM subject_offerings o JOIN teacher_profiles p ON p.id=o.teacher_id JOIN accounts a ON a.id=p.account_id AND a.role='TEACHER' AND a.status='ACTIVE' JOIN LATERAL (SELECT v.status FROM verification_cases v WHERE v.teacher_id=a.id ORDER BY v.submitted_at DESC,v.id DESC LIMIT 1) v ON v.status='APPROVED' WHERE o.active=true AND strpos(p.school,'{marker}')>0;")
pathlib.Path('backend/target/catalog-count-explain.log').write_text(plan,encoding='utf-8')
print('PASS: 2,000/10,000/20,000 synthetic offerings; five sorts, stable pages, 8 concurrent clients. Results describe this workstation only.',flush=True)
