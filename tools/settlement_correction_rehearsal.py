"""Validate historical-correction SQL in an isolated acceptance DB, with all fixture changes rolled back."""
import pathlib, subprocess, sys

db = sys.argv[1] if len(sys.argv) > 1 else "nurture_gap_acceptance_20261010"
if not db.startswith("nurture_gap_acceptance_") or not db.replace("_", "").isalnum():
    raise SystemExit("Only an isolated nurture_gap_acceptance_* database is allowed")
root = pathlib.Path(__file__).resolve().parents[1]
def psql(sql):
    r = subprocess.run(["docker", "exec", "-i", "deploy-postgres-1", "psql", "-U", "nurture", "-d", db, "-v", "ON_ERROR_STOP=1", "-At"],
                       input=sql, text=True, encoding="utf-8", capture_output=True)
    if r.returncode: raise RuntimeError(r.stderr)
    return r.stdout
counts = "SELECT count(*) FROM settlement_entries WHERE entry_kind='REVERSAL'; SELECT count(*) FROM audit_logs;"
before = psql(counts)
assert int(before.splitlines()[0]) > 0, "Run E2E first so a fully refunded test order exists"
source = (root / "tools/sql/settlement_refund_correction_dry_run.sql").read_text(encoding="utf-8")
# Temporarily make one test entry resemble the pre-V11 legacy state. The canonical SQL rolls it back.
fixture = """
DELETE FROM settlement_entries WHERE id = (
 SELECT v.id FROM settlement_entries v JOIN orders o ON o.id=v.order_id
 WHERE v.entry_kind='REVERSAL' AND o.status='CLOSED' ORDER BY v.created_at LIMIT 1
);
"""
result = psql(source.replace("END $$;", "END $$;\n" + fixture, 1))
assert "INSERT 0 1" in result and "ROLLBACK" in result, result
assert psql(counts) == before, "Rehearsal must not persist a change to entries or audit rows"
second = psql(source)
assert "INSERT 0 0" in second, "Already reversed orders must be skipped"
assert psql(counts) == before
audit = psql((root / "tools/sql/settlement_refund_audit.sql").read_text(encoding="utf-8"))
assert "A_LEGACY" not in audit and "MISMATCH" not in audit and "MISSING" not in audit, audit
print("PASS: one legacy fixture corrected in transaction; original counts restored after rollback")
print("PASS: already reversed orders skipped; read-only audit reconciles refunds and wallet ledger")
