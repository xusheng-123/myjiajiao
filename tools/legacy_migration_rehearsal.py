"""Rehearse canonical V11/V12 on a restored V10 isolated database, always rolling back."""
import pathlib, subprocess, sys

db = sys.argv[1] if len(sys.argv) > 1 else ""
if not db.startswith("nurture_gap_acceptance_") or not db.replace("_", "").isalnum():
    raise SystemExit("Only an explicitly named nurture_gap_acceptance_* copy is allowed")
root = pathlib.Path(__file__).resolve().parents[1]
def psql(sql):
    r = subprocess.run(["docker", "exec", "-i", "deploy-postgres-1", "psql", "-U", "nurture", "-d", db,
                        "-v", "ON_ERROR_STOP=1", "-At"], input=sql, text=True, encoding="utf-8", capture_output=True)
    if r.returncode: raise RuntimeError(r.stderr)
    return r.stdout

assert psql("SELECT max(version::int) FROM flyway_schema_history WHERE success;").strip() == "10", "Requires restored V10 copy"
snapshot = """
SELECT count(*) FROM conversations;
SELECT count(*) FROM messages;
SELECT md5(coalesce(string_agg(id::text || ':' || net_cents || ':' || status, ',' ORDER BY id),'')) FROM settlement_entries;
SELECT md5(coalesce(string_agg(row_to_json(o)::text, ',' ORDER BY id),'')) FROM orders o;
SELECT md5(coalesce(string_agg(row_to_json(r)::text, ',' ORDER BY id),'')) FROM refund_cases r;
"""
before = psql(snapshot)
v11 = (root / "backend/src/main/resources/db/migration/V11__settlement_refund_reversal.sql").read_text(encoding="utf-8")
v12 = (root / "backend/src/main/resources/db/migration/V12__conversation_pair_uniqueness.sql").read_text(encoding="utf-8")
fixture = """
CREATE TEMP TABLE original_messages AS SELECT * FROM messages;
CREATE TEMP TABLE original_earnings AS SELECT id, gross_cents, platform_fee_cents, net_cents, status FROM settlement_entries;
INSERT INTO conversations(id,created_at,updated_at,student_id,teacher_id,subject,last_message_at)
 VALUES ('de000000-0000-4000-8000-000000000001',now(),now(),'de000000-0000-4000-8000-000000000011','de000000-0000-4000-8000-000000000012','migration fixture',now()-interval '1 day'),
 ('de000000-0000-4000-8000-000000000002',now(),now(),'de000000-0000-4000-8000-000000000011','de000000-0000-4000-8000-000000000012','migration fixture',now());
INSERT INTO messages(id,created_at,updated_at,conversation_id,sender_role,body,sent_at,read_at)
 VALUES ('de000000-0000-4000-8000-000000000021',now(),now(),'de000000-0000-4000-8000-000000000001','SYSTEM','older proof',now()-interval '1 day',now()),
 ('de000000-0000-4000-8000-000000000022',now(),now(),'de000000-0000-4000-8000-000000000002','SYSTEM','newer proof',now(),null);
CREATE TEMP TABLE expected_messages AS SELECT count(*) AS total FROM messages;
"""
assertions = """
DO $$ BEGIN
 IF (SELECT count(*) FROM messages) <> (SELECT total FROM expected_messages) THEN RAISE EXCEPTION 'Message count lost'; END IF;
 IF EXISTS (SELECT 1 FROM original_messages old LEFT JOIN messages m ON m.id=old.id
   WHERE m.id IS NULL OR ROW(m.body,m.sent_at,m.read_at,m.sender_id,m.sender_role)
     IS DISTINCT FROM ROW(old.body,old.sent_at,old.read_at,old.sender_id,old.sender_role)) THEN RAISE EXCEPTION 'Message content changed'; END IF;
 IF (SELECT count(*) FROM messages WHERE id IN ('de000000-0000-4000-8000-000000000021','de000000-0000-4000-8000-000000000022')
   AND conversation_id='de000000-0000-4000-8000-000000000002') <> 2 THEN RAISE EXCEPTION 'Fixture pair not merged'; END IF;
 IF EXISTS (SELECT 1 FROM messages m LEFT JOIN conversations c ON c.id=m.conversation_id WHERE c.id IS NULL) THEN RAISE EXCEPTION 'Orphan message'; END IF;
 IF EXISTS (SELECT 1 FROM conversations GROUP BY student_id,teacher_id HAVING count(*)>1) THEN RAISE EXCEPTION 'Duplicate pair remains'; END IF;
 IF EXISTS (SELECT 1 FROM original_earnings old JOIN settlement_entries e ON e.id=old.id
   WHERE ROW(old.gross_cents,old.platform_fee_cents,old.net_cents,old.status) IS DISTINCT FROM ROW(e.gross_cents,e.platform_fee_cents,e.net_cents,e.status)
     OR e.entry_kind<>'EARNING' OR e.reverses_entry_id IS NOT NULL) THEN RAISE EXCEPTION 'Legacy earnings modified'; END IF;
 BEGIN
   INSERT INTO conversations(id,created_at,updated_at,student_id,teacher_id,last_message_at)
   VALUES(gen_random_uuid(),now(),now(),'de000000-0000-4000-8000-000000000011','de000000-0000-4000-8000-000000000012',now());
   RAISE EXCEPTION 'Unique pair guard missing';
 EXCEPTION WHEN unique_violation THEN NULL; END;
END $$;
"""
out = psql("BEGIN;\n" + fixture + v11 + v12 + assertions + "\nROLLBACK;")
assert "ROLLBACK" in out and psql(snapshot) == before
assert psql("SELECT max(version::int) FROM flyway_schema_history WHERE success;").strip() == "10"
print("PASS: canonical V11/V12 preserve legacy earning amounts and all message identities/content/read state")
print("PASS: historical and synthetic duplicate pairs merge; new pair unique guard enforced; no orphan messages")
print("PASS: rollback restores original V10 schema, counts and order/refund/earning fingerprints")
