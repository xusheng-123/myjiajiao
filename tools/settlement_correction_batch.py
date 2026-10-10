"""Prepare an exact historical correction batch; execute only on its isolated test database.

Default execution rolls back. --commit supports rehearsal copies only, never business databases.
Cash/ownership/original ledger records remain untouched. Recovery restores the paired pre-batch backup.
"""
import argparse
import hashlib
import json
from pathlib import Path
import uuid
from datetime import datetime, timezone

from local_backup_bundle import ROOT, contained, database_name, psql, require, snapshot, write_json

TABLES = ("settlement_entries", "audit_logs", "orders", "refund_cases", "wallet_transactions", "family_wallets", "parent_profiles", "accounts")

def isolated(name):
    database_name(name)
    require(name.startswith("nurture_gap_acceptance_"), "Correction writes are restricted to an isolated nurture_gap_acceptance_* copy")
    return name

def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode()).hexdigest()

def uid(value):
    require(isinstance(value, str) and str(uuid.UUID(value)) == value, "Invalid canonical UUID in plan")
    return "'" + value + "'::uuid"

def fingerprint(table, exclude=()):
    require(table in TABLES, "Unsupported plan table")
    where = " WHERE id NOT IN (" + ",".join(uid(x) for x in exclude) + ")" if exclude else ""
    return f"(SELECT encode(sha256(convert_to(coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text,'[]'),'UTF8')),'hex') FROM {table} t{where})"

def prepare(args):
    isolated(args.database)
    destination = contained(args.output, ROOT / "deploy/backups")
    require(destination.parent.is_dir(), "Prepare a paired backup bundle first")
    require(not destination.exists() and not destination.with_suffix(".review.sql").exists(), "Plan output already exists")
    before = snapshot(args.database)
    audit = psql(args.database, (ROOT / "tools/sql/settlement_refund_audit.sql").read_text(encoding="utf-8"))
    rows = [line.split("|") for line in audit.splitlines() if line]
    require(all(len(row) == 7 and row[-1] in ("A_LEGACY_UNREVERSED", "OK_REVERSED") for row in rows),
            "Accounting anomaly or wallet reconciliation mismatch; resolve before preparing correction")
    sql = """
    SELECT coalesce(jsonb_agg(to_jsonb(x) ORDER BY earning_id), '[]') FROM (
      SELECT e.id AS earning_id, e.teacher_id, e.booking_id, e.order_id, e.gross_cents, e.net_cents
      FROM settlement_entries e JOIN orders o ON o.id=e.order_id
      WHERE e.entry_kind='EARNING' AND e.status='PENDING' AND o.status='CLOSED'
        AND (SELECT count(*) FROM refund_cases r WHERE r.order_id=o.id AND r.status='EXECUTED')=1
        AND (SELECT sum(computed_amount_cents) FROM refund_cases r WHERE r.order_id=o.id AND r.status='EXECUTED')=o.amount_cents
        AND (SELECT count(*) FROM wallet_transactions w WHERE w.related_order_id=o.id AND w.txn_type='REFUND')=1
        AND (SELECT sum(amount_cents) FROM wallet_transactions w WHERE w.related_order_id=o.id AND w.txn_type='REFUND')=o.amount_cents
        AND NOT EXISTS (SELECT 1 FROM settlement_entries v WHERE v.reverses_entry_id=e.id)
    ) x;
    """
    entries = json.loads(psql(args.database, sql))
    require(entries and len(entries) == sum(row[-1] == "A_LEGACY_UNREVERSED" for row in rows), "Eligible batch incomplete or empty")
    batch = str(uuid.uuid4())
    for entry in entries:
        entry["reversal_id"] = str(uuid.uuid5(uuid.NAMESPACE_URL, f"nurture:reversal:{batch}:{entry['earning_id']}"))
        entry["audit_id"] = str(uuid.uuid5(uuid.NAMESPACE_URL, f"nurture:audit:{batch}:{entry['earning_id']}"))
    require(snapshot(args.database) == before, "Source changed during preparation")
    plan = {"format": 1, "test_copy_only": True, "batch_id": batch, "database": args.database,
            "created_utc": datetime.now(timezone.utc).isoformat(), "entries": entries,
            "original_fingerprints": {table: before[table] for table in TABLES},
            "entry_count": len(entries), "net_reversal_cents": -sum(e["net_cents"] for e in entries)}
    plan["sha256"] = digest(plan)
    sql = batch_sql(plan, commit=False)
    write_json(destination, plan)
    with destination.with_suffix(".review.sql").open("x", encoding="utf-8") as stream:
        stream.write(sql)
    print(json.dumps({"plan": str(destination), "batch_id": batch, "entry_count": len(entries),
                      "net_reversal_cents": plan["net_reversal_cents"], "wallet_delta_cents": 0}, ensure_ascii=False))

def batch_sql(plan, commit):
    isolated(plan["database"])
    require(plan.get("format") == 1 and plan.get("test_copy_only") is True, "Not a test-copy plan")
    uid(plan["batch_id"])
    require(set(plan["original_fingerprints"]) == set(TABLES), "Unexpected fingerprint set")
    rows = plan["entries"]
    require(rows and len(rows) == plan["entry_count"], "Wrong batch count")
    require(len({e["earning_id"] for e in rows}) == len(rows), "Duplicate original entry")
    for entry in rows:
        for field in ("earning_id", "teacher_id", "booking_id", "order_id", "reversal_id", "audit_id"):
            uid(entry[field])
        require(type(entry["gross_cents"]) is int and type(entry["net_cents"]) is int
                and 0 < entry["net_cents"] <= entry["gross_cents"], "Invalid reversal amount")
    require(plan["net_reversal_cents"] == -sum(e["net_cents"] for e in rows), "Wrong batch amount")
    batch_hash = plan["sha256"]
    require(len(batch_hash) == 64 and all(c in "0123456789abcdef" for c in batch_hash), "Invalid plan hash")
    statements = ["-- TEST COPY ONLY. Exact batch; no wallet, order, refund, owner or original-entry updates.",
                  "BEGIN;", "SET LOCAL lock_timeout='5s';", "SET LOCAL statement_timeout='60s';",
                  "LOCK TABLE " + ",".join(TABLES) + " IN SHARE ROW EXCLUSIVE MODE;",
                  f"DO $$ BEGIN IF current_database()<>'{plan['database']}' OR current_database() NOT LIKE 'nurture_gap_acceptance_%' THEN RAISE EXCEPTION 'Wrong test database'; END IF; END $$;"]
    for table in TABLES:
        exclude = tuple(e["reversal_id"] for e in rows) if table == "settlement_entries" else tuple(e["audit_id"] for e in rows) if table == "audit_logs" else ()
        expected = plan["original_fingerprints"][table]["sha256"]
        require(len(expected) == 64 and all(c in "0123456789abcdef" for c in expected), "Invalid fingerprint")
        statements.append(f"DO $$ BEGIN IF {fingerprint(table, exclude)}<>'{expected}' THEN RAISE EXCEPTION 'Stale batch: {table} changed'; END IF; END $$;")
    statements.append("CREATE TEMP TABLE correction_scope(earning_id uuid,teacher_id uuid,booking_id uuid,order_id uuid,gross_cents bigint,net_cents bigint,reversal_id uuid,audit_id uuid) ON COMMIT DROP;")
    statements.append("INSERT INTO correction_scope VALUES " + ",".join("(" + ",".join([
        uid(e["earning_id"]), uid(e["teacher_id"]), uid(e["booking_id"]), uid(e["order_id"]),
        str(e["gross_cents"]), str(e["net_cents"]), uid(e["reversal_id"]), uid(e["audit_id"])]) + ")" for e in rows) + ";")
    statements.append("""
    INSERT INTO settlement_entries(id,created_at,updated_at,teacher_id,booking_id,order_id,gross_cents,platform_fee_cents,net_cents,status,entry_kind,reverses_entry_id)
    SELECT s.reversal_id,now(),now(),s.teacher_id,s.booking_id,s.order_id,-s.gross_cents,0,-s.net_cents,'PENDING','REVERSAL',s.earning_id
    FROM correction_scope s WHERE NOT EXISTS(SELECT 1 FROM settlement_entries v WHERE v.id=s.reversal_id);
    """)
    statements.append(f"""
    INSERT INTO audit_logs(id,created_at,updated_at,caller_role,occurred_at,action_type,payload_hash,execution_status,detail)
    SELECT s.audit_id,now(),now(),'SYSTEM',now(),'settlement:historical-copy-correction','{batch_hash}','SUCCESS',
      'TEST COPY batch={plan['batch_id']}; original='||s.earning_id::text
    FROM correction_scope s WHERE NOT EXISTS(SELECT 1 FROM audit_logs a WHERE a.id=s.audit_id);
    """)
    statements.append(f"""
    DO $$ BEGIN
      IF EXISTS(SELECT 1 FROM correction_scope s LEFT JOIN settlement_entries v ON v.id=s.reversal_id
        LEFT JOIN audit_logs a ON a.id=s.audit_id WHERE v.id IS NULL OR a.id IS NULL
        OR ROW(v.teacher_id,v.booking_id,v.order_id,v.gross_cents,v.net_cents,v.platform_fee_cents,v.status,v.entry_kind,v.reverses_entry_id)
          IS DISTINCT FROM ROW(s.teacher_id,s.booking_id,s.order_id,-s.gross_cents,-s.net_cents,0::bigint,'PENDING'::varchar,'REVERSAL'::varchar,s.earning_id)
        OR a.payload_hash<>'{batch_hash}' OR a.action_type<>'settlement:historical-copy-correction' OR a.execution_status<>'SUCCESS'
        OR a.caller_role<>'SYSTEM' OR a.detail<>('TEST COPY batch={plan['batch_id']}; original='||s.earning_id::text))
      THEN RAISE EXCEPTION 'Batch result mismatch'; END IF;
    END $$;
    """)
    for table in TABLES:
        exclude = tuple(e["reversal_id"] for e in rows) if table == "settlement_entries" else tuple(e["audit_id"] for e in rows) if table == "audit_logs" else ()
        statements.append(f"DO $$ BEGIN IF {fingerprint(table, exclude)}<>'{plan['original_fingerprints'][table]['sha256']}' THEN RAISE EXCEPTION 'Original data changed'; END IF; END $$;")
    statements.append("COMMIT;" if commit else "ROLLBACK;")
    return "\n".join(statements) + "\n"

def execute(args):
    source = contained(args.plan, ROOT / "deploy/backups")
    plan = json.loads(source.read_text(encoding="utf-8"))
    expected = plan.pop("sha256")
    require(digest(plan) == expected, "Plan hash mismatch; use the reviewed original plan")
    plan["sha256"] = expected
    psql(plan["database"], batch_sql(plan, commit=args.commit))
    print(json.dumps({"database": plan["database"], "batch_id": plan["batch_id"], "entry_count": plan["entry_count"],
                      "net_reversal_cents": plan["net_reversal_cents"], "wallet_delta_cents": 0,
                      "mode": "committed_test_copy" if args.commit else "rolled_back"}))

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    prep = sub.add_parser("prepare")
    prep.add_argument("--database", required=True)
    prep.add_argument("--output", required=True)
    operation = sub.add_parser("run")
    operation.add_argument("plan")
    operation.add_argument("--commit", action="store_true", help="Commit on the named isolated copy only")
    args = parser.parse_args()
    if args.command == "prepare": prepare(args)
    else: execute(args)

if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, OSError, KeyError) as error:
        raise SystemExit("Correction rejected/failed: " + str(error))
