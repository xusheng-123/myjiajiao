"""V18 additive rehearsal on an explicit isolated V17 database; always rolls back."""
import pathlib,subprocess,sys
name=sys.argv[1] if len(sys.argv)==2 else ''
if not name.startswith('nurture_gap_acceptance_') or not name.replace('_','').isalnum():raise SystemExit('Explicit isolated database required')
def sql(q):return subprocess.run(['docker','exec','-i','deploy-postgres-1','psql','-U','nurture','-d',name,'-v','ON_ERROR_STOP=1','-At'],input=q,text=True,encoding='utf-8',capture_output=True,check=True).stdout
assert sql('select max(version::int) from flyway_schema_history where success').strip()=='17'
tables=sql("select tablename from pg_tables where schemaname='public' order by tablename").splitlines()
snapshot='\n'.join("select md5(coalesce(string_agg(to_jsonb(r)::text,',' order by to_jsonb(r)::text),'')) from "+t+" r;" for t in tables)
before=sql(snapshot)
migration=(pathlib.Path(__file__).resolve().parents[1]/'backend/src/main/resources/db/migration/V18__support_cases_and_evidence.sql').read_text(encoding='utf-8')
assert before in sql('BEGIN;\n'+migration+snapshot+'ROLLBACK;') and sql(snapshot)==before
assert sql("select to_regclass('support_cases') is null and to_regclass('support_replies') is null and to_regclass('support_evidence') is null and to_regclass('support_storage_quotas') is null").strip()=='t'
assert sql('select max(version::int) from flyway_schema_history where success').strip()=='17'
print(f'PASS: V18 preserves all {len(tables)} existing table fingerprints and rollback restores V17')
