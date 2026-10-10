"""Canonical V13/V14 rehearsal on a V12 isolated copy. Always rolls back all writes."""
import pathlib, subprocess, sys

database = sys.argv[1] if len(sys.argv) == 2 else ""
if not database.startswith("nurture_gap_acceptance_") or not database.replace("_", "").isalnum():
    raise SystemExit("An explicitly named isolated copy is required")
root = pathlib.Path(__file__).resolve().parents[1]
def sql(query):
    result = subprocess.run(["docker", "exec", "-i", "deploy-postgres-1", "psql", "-U", "nurture", "-d", database,
                             "-v", "ON_ERROR_STOP=1", "-At"], input=query, text=True, encoding="utf-8", capture_output=True, check=True)
    return result.stdout
assert sql("select max(version::int) from flyway_schema_history where success").strip() == "12"
snapshot = "\n".join(f"select md5(coalesce(string_agg(row_to_json(r)::text,',' order by id),'')) from {table} r;"
                     for table in ("bookings", "orders", "family_wallets", "wallet_transactions", "settlement_entries", "accounts", "student_profiles"))
before = sql(snapshot)
migration = (root / "backend/src/main/resources/db/migration/V13__trial_booking_entitlements.sql").read_text(encoding="utf-8")
migration += (root / "backend/src/main/resources/db/migration/V14__business_number_sequences.sql").read_text(encoding="utf-8")
fixture = """
CREATE TEMP TABLE fixture_students AS SELECT n,gen_random_uuid() student_id FROM generate_series(1,4) n;
CREATE TEMP TABLE fixture_teacher AS SELECT gen_random_uuid() teacher_id;
INSERT INTO bookings(id,created_at,updated_at,student_id,teacher_id,slot_id,subject,booking_type,status,price_cents)
SELECT gen_random_uuid(),now(),now(),s.student_id,t.teacher_id,gen_random_uuid(),'migration fixture',
 CASE WHEN s.n=4 THEN 'FORMAL' ELSE 'TRIAL' END,
 CASE WHEN s.n=1 THEN 'COMPLETED' WHEN s.n=2 THEN 'CANCELLED' ELSE 'CONFIRMED' END,0
FROM fixture_students s CROSS JOIN fixture_teacher t;
INSERT INTO bookings(id,created_at,updated_at,student_id,teacher_id,slot_id,subject,booking_type,status,price_cents)
SELECT gen_random_uuid(),now()+interval '1 second',now(),student_id,teacher_id,gen_random_uuid(),'duplicate active fixture','TRIAL','CONFIRMED',0
FROM fixture_students CROSS JOIN fixture_teacher WHERE n=3;
CREATE TEMP TABLE original_bookings AS SELECT * FROM bookings;
"""
assertions = """
DO $$ BEGIN
 IF EXISTS((SELECT * FROM bookings EXCEPT SELECT * FROM original_bookings)
  UNION ALL (SELECT * FROM original_bookings EXCEPT SELECT * FROM bookings)) THEN RAISE EXCEPTION 'Booking rewritten'; END IF;
 IF NOT EXISTS(SELECT 1 FROM trial_entitlements t JOIN fixture_students s ON s.student_id=t.student_id WHERE n=1 AND consumed AND current_booking_id IS NULL) THEN RAISE EXCEPTION 'Completed allowance not consumed'; END IF;
 IF NOT EXISTS(SELECT 1 FROM trial_entitlements t JOIN fixture_students s ON s.student_id=t.student_id WHERE n=2 AND NOT consumed AND current_booking_id IS NULL) THEN RAISE EXCEPTION 'Cancelled allowance not released'; END IF;
 IF NOT EXISTS(SELECT 1 FROM trial_entitlements t JOIN fixture_students s ON s.student_id=t.student_id WHERE n=3 AND NOT consumed AND current_booking_id IS NOT NULL) THEN RAISE EXCEPTION 'Active allowance not reserved'; END IF;
 IF EXISTS(SELECT 1 FROM trial_entitlements t JOIN fixture_students s ON s.student_id=t.student_id WHERE n=4) THEN RAISE EXCEPTION 'Formal booking consumed quota'; END IF;
 IF EXISTS(SELECT 1 FROM trial_entitlements GROUP BY student_id,teacher_id HAVING count(*)>1) THEN RAISE EXCEPTION 'Duplicate allowance'; END IF;
 IF (SELECT count(*) FROM original_bookings b JOIN fixture_students s ON s.student_id=b.student_id WHERE n=3)<>2 THEN RAISE EXCEPTION 'Duplicate active bookings lost'; END IF;
 IF nextval('account_business_number_seq') <= COALESCE((SELECT max(substring(account_no from '[0-9]+$')::bigint) FROM accounts WHERE account_no ~ '^NUR-(STU|PAR|TEA|ADM|AGT)-[0-9]{1,12}$'),0) THEN RAISE EXCEPTION 'Account sequence overlaps history'; END IF;
 IF nextval('student_business_number_seq') <= COALESCE((SELECT max(substring(student_no from '[0-9]+$')::bigint) FROM student_profiles WHERE student_no ~ '^NUR-STU-[0-9]{1,12}$'),0) THEN RAISE EXCEPTION 'Student sequence overlaps history'; END IF;
END $$;
"""
output = sql("BEGIN;\n"+fixture+migration+assertions+"\nROLLBACK;")
assert "ROLLBACK" in output
assert sql(snapshot) == before
assert sql("select to_regclass('trial_entitlements') is null").strip() == "t"
print("PASS: V13 preserves all booking fields, keeps legacy duplicates, seeds used/reserved/released allowance correctly, excludes FORMAL")
print("PASS: V14 starts above historical numbers; rollback preserves bookings/orders/wallets/ledger/settlements/accounts/student profiles and original V12 schema")
