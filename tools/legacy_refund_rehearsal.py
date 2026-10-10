"""Audit a migrated isolated historical copy and verify correction SQL rolls back all changes."""
import collections, pathlib, subprocess, sys

db = sys.argv[1] if len(sys.argv) > 1 else ""
if not db.startswith("nurture_gap_acceptance_") or not db.replace("_", "").isalnum():
    raise SystemExit("Only an explicitly named nurture_gap_acceptance_* copy is allowed")
root = pathlib.Path(__file__).resolve().parents[1]
def psql(sql):
    r = subprocess.run(["docker", "exec", "-i", "deploy-postgres-1", "psql", "-U", "nurture", "-d", db,
                        "-v", "ON_ERROR_STOP=1", "-At"], input=sql, text=True, encoding="utf-8", capture_output=True)
    if r.returncode: raise RuntimeError(r.stderr)
    return r.stdout

snapshot = """
SELECT count(*) FROM settlement_entries;
SELECT count(*) FROM audit_logs;
SELECT md5(coalesce(string_agg(row_to_json(e)::text, ',' ORDER BY id),'')) FROM settlement_entries e;
SELECT md5(coalesce(string_agg(row_to_json(o)::text, ',' ORDER BY id),'')) FROM orders o;
SELECT md5(coalesce(string_agg(row_to_json(r)::text, ',' ORDER BY id),'')) FROM refund_cases r;
SELECT md5(coalesce(string_agg(row_to_json(w)::text, ',' ORDER BY id),'')) FROM wallet_transactions w;
"""
before = psql(snapshot)
audit_sql = (root / "tools/sql/settlement_refund_audit.sql").read_text(encoding="utf-8")
audit = psql(audit_sql)
rows = [line.split('|') for line in audit.splitlines() if line]
categories = collections.Counter(row[-1] for row in rows if len(row) == 7)
assert all(len(row) == 7 for row in rows), "Refund/wallet reconciliation mismatches require manual review"
legacy_net = sum(int(row[4]) for row in rows if row[-1] == "A_LEGACY_UNREVERSED")
sql = (root / "tools/sql/settlement_refund_correction_dry_run.sql").read_text(encoding="utf-8")
# Audit inside the existing correction transaction, before its mandatory rollback.
proof_sql = sql.replace("ROLLBACK;", audit_sql + "\nROLLBACK;")
out = psql(proof_sql)
assert "ROLLBACK" in out and "A_LEGACY_UNREVERSED" not in out
assert "MISMATCH" not in out and "MISSING" not in out and "B_MANUAL" not in out
inside = [line.split('|') for line in out.splitlines() if '|OK_REVERSED' in line]
assert len(inside) == categories.get("A_LEGACY_UNREVERSED", 0) + categories.get("OK_REVERSED", 0)
assert psql(snapshot) == before and psql(audit_sql) == audit, "Correction rehearsal must persist no changes"
print("PASS: historical snapshot categories="+str(dict(categories))+"; unreversed net cents="+str(legacy_net))
print("PASS: eligible legacy corrections reconcile inside transaction; rollback restores entries/audit/orders/refunds/wallet fingerprints")
