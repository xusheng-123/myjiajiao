"""V17 isolated V16 rehearsal preserving old column data; always rolls back."""
import pathlib,subprocess,sys
database=sys.argv[1] if len(sys.argv)==2 else ''
if not database.startswith('nurture_gap_acceptance_') or not database.replace('_','').isalnum():raise SystemExit('Explicit isolated database required')
def sql(q):return subprocess.run(['docker','exec','-i','deploy-postgres-1','psql','-U','nurture','-d',database,'-v','ON_ERROR_STOP=1','-At'],input=q,text=True,encoding='utf-8',capture_output=True,check=True).stdout
assert sql('select max(version::int) from flyway_schema_history where success').strip()=='16'
tables=('availability_slots','bookings','orders','family_wallets','wallet_transactions','settlement_entries','learning_tasks','questions','accounts','student_profiles','guardian_bindings','learning_plans','mistake_notes','learning_files','learning_storage_quotas')
snapshot='\n'.join(f"select md5(coalesce(string_agg((to_jsonb(r)-'series_id')::text,',' order by {('owner_id' if t=='learning_storage_quotas' else 'id')}),'')) from {t} r;" for t in tables)
before=sql(snapshot);migration=(pathlib.Path(__file__).resolve().parents[1]/'backend/src/main/resources/db/migration/V17__availability_series.sql').read_text(encoding='utf-8')
assert before in sql('BEGIN;\n'+migration+snapshot+'ROLLBACK;') and sql(snapshot)==before
assert sql("select to_regclass('availability_series') is null and not exists(select 1 from information_schema.columns where table_name='availability_slots' and column_name='series_id')").strip()=='t'
print('PASS: V17 preserves 15 existing tables/old columns and rollback restores V16')
