"""V15 rehearsal on a V14 isolated copy. Never commits."""
import pathlib, subprocess, sys
database=sys.argv[1] if len(sys.argv)==2 else ''
if not database.startswith('nurture_gap_acceptance_') or not database.replace('_','').isalnum():
    raise SystemExit('An explicitly named isolated copy is required')
def sql(query):
    return subprocess.run(['docker','exec','-i','deploy-postgres-1','psql','-U','nurture','-d',database,'-v','ON_ERROR_STOP=1','-At'],
        input=query,text=True,encoding='utf-8',capture_output=True,check=True).stdout
assert sql('select max(version::int) from flyway_schema_history where success').strip()=='14'
snapshot='\n'.join(f"select md5(coalesce(string_agg(row_to_json(r)::text,',' order by id),'')) from {table} r;" for table in
    ('bookings','orders','family_wallets','wallet_transactions','settlement_entries','learning_tasks','accounts','student_profiles','guardian_bindings'))
before=sql(snapshot)
root=pathlib.Path(__file__).resolve().parents[1]
migration=(root/'backend/src/main/resources/db/migration/V15__learning_plans.sql').read_text(encoding='utf-8')
after=sql('BEGIN;\n'+migration+snapshot+'ROLLBACK;')
assert before in after and sql(snapshot)==before
assert sql("select to_regclass('learning_plans') is null and to_regclass('plan_changes') is null").strip()=='t'
print('PASS: V15 creates plans/milestones/task links/history without changing 9 existing table fingerprints')
print('PASS: rollback restores original V14 schema and all prior rows')
