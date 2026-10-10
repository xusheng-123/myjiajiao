"""V16: isolated V15 copy, fingerprints and rollback; never commits."""
import pathlib,subprocess,sys
database=sys.argv[1] if len(sys.argv)==2 else ''
if not database.startswith('nurture_gap_acceptance_') or not database.replace('_','').isalnum():raise SystemExit('Explicit isolated copy required')
def sql(q):return subprocess.run(['docker','exec','-i','deploy-postgres-1','psql','-U','nurture','-d',database,'-v','ON_ERROR_STOP=1','-At'],input=q,text=True,encoding='utf-8',capture_output=True,check=True).stdout
assert sql('select max(version::int) from flyway_schema_history where success').strip()=='15'
snapshot='\n'.join(f"select md5(coalesce(string_agg(row_to_json(r)::text,',' order by id),'')) from {t} r;" for t in ('bookings','orders','family_wallets','wallet_transactions','settlement_entries','learning_tasks','questions','accounts','student_profiles','guardian_bindings','learning_plans','plan_milestones','plan_task_links','plan_changes'))
before=sql(snapshot);migration=(pathlib.Path(__file__).resolve().parents[1]/'backend/src/main/resources/db/migration/V16__mistakes_and_learning_files.sql').read_text(encoding='utf-8')
assert before in sql('BEGIN;\n'+migration+snapshot+'ROLLBACK;') and sql(snapshot)==before
assert sql("select to_regclass('mistake_notes') is null and to_regclass('learning_files') is null and to_regclass('learning_storage_quotas') is null").strip()=='t'
print('PASS: V16 preserves 14 existing table fingerprints and rollback restores V15')
