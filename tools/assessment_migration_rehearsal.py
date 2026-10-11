"""V19/V20 on an explicit V18 isolated database; old rows retained, always rollback."""
import pathlib,subprocess,sys
name=sys.argv[1] if len(sys.argv)==2 else ''
if not name.startswith('nurture_gap_acceptance_') or not name.replace('_','').isalnum():raise SystemExit('Explicit isolated database required')
def sql(q):return subprocess.run(['docker','exec','-i','deploy-postgres-1','psql','-U','nurture','-d',name,'-v','ON_ERROR_STOP=1','-At'],input=q,text=True,encoding='utf-8',capture_output=True,check=True).stdout
assert sql('select max(version::int) from flyway_schema_history where success').strip()=='18'
tables=sql("select tablename from pg_tables where schemaname='public' order by tablename").splitlines()
snapshot='\n'.join("select md5(coalesce(string_agg((to_jsonb(r)-'attachment_id')::text,',' order by (to_jsonb(r)-'attachment_id')::text),'')) from "+t+" r;" for t in tables)
before=sql(snapshot);folder=pathlib.Path(__file__).resolve().parents[1]/'backend/src/main/resources/db/migration'
migration='\n'.join((folder/n).read_text(encoding='utf-8') for n in ['V19__learning_assessments.sql','V20__consultation_attachments.sql'])
assert before in sql('BEGIN;\n'+migration+snapshot+'ROLLBACK;') and sql(snapshot)==before
assert sql("select to_regclass('learning_assessments') is null and to_regclass('consultation_files') is null and not exists(select 1 from information_schema.columns where table_name='messages' and column_name='attachment_id')").strip()=='t'
print(f'PASS: V19/V20 retain old columns in {len(tables)} existing tables and rollback restores V18')
