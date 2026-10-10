"""Real local database/filesystem acceptance for backup and correction tooling.

Requires a prepared plan on the fresh migrated stage1 copy. Commits only on that isolated copy,
checks replay and restores a second new copy. Business source remains read-only throughout.
"""
import hashlib
import json
import shutil
import subprocess
import sys
import uuid
import zipfile
from pathlib import Path

from local_backup_bundle import ROOT, psql, sha256, snapshot, uploads, verify, write_json
from settlement_correction_batch import TABLES

sys.stdout.reconfigure(encoding="utf-8")
bundle = ROOT / "deploy/backups/stage1_20261010"
plan_path = bundle / "correction-plan.json"
plan = json.loads(plan_path.read_text(encoding="utf-8"))
db = plan["database"]
if db != "nurture_gap_acceptance_stage1_20261010":
    raise SystemExit("This acceptance run is limited to the explicitly prepared stage1 copy")
main_before = snapshot("nurture")
main_files_before = uploads(ROOT / "backend/data/files")
protected_before = sha256(ROOT / "tools/settlement_walkthrough.png")
fixture = bundle / ("guards-" + uuid.uuid4().hex)
fixture.mkdir()
checks = 0

def check(condition, label):
    global checks
    if not condition:
        raise AssertionError(label)
    checks += 1
    print("PASS: " + label, flush=True)

def command(script, *args, success=True):
    result = subprocess.run([sys.executable, str(ROOT / "tools" / script), *map(str, args)],
                            capture_output=True, text=True, encoding="utf-8")
    if success and result.returncode:
        raise RuntimeError(result.stderr)
    return result

command_result = command("local_backup_bundle.py", "restore", bundle, "--database", "nurture", "--files", ROOT / "backend/target/not-created-main-restore", success=False)
check(command_result.returncode != 0 and not (ROOT / "backend/target/not-created-main-restore").exists(), "business database restore refused before any destination is created")
command_result = command("settlement_correction_batch.py", "prepare", "--database", "nurture", "--output", fixture / "forbidden-plan.json", success=False)
check(command_result.returncode != 0 and not (fixture / "forbidden-plan.json").exists(), "business correction plan execution refused")
command_result = command("local_backup_bundle.py", "restore", bundle, "--database", db, "--files", ROOT / "backend/target/not-created-existing-db", success=False)
check(command_result.returncode != 0 and not (ROOT / "backend/target/not-created-existing-db").exists(), "existing isolated database is never overwritten")

bad_plan = json.loads(plan_path.read_text(encoding="utf-8"))
bad_plan["entries"][0]["net_cents"] += 1
write_json(fixture / "bad-plan.json", bad_plan)
copy_before = snapshot(db)
command_result = command("settlement_correction_batch.py", "run", fixture / "bad-plan.json", success=False)
check(command_result.returncode != 0 and snapshot(db) == copy_before, "changed plan rejected with no ledger/audit write")

# Even a correctly rehashed manifest must never authorize archive traversal or Windows devices.
for key in ("../outside.pdf", "C:/outside.pdf", "verification_id/CON", "verification_id/../outside.pdf"):
    case = fixture / ("archive-" + uuid.uuid4().hex)
    case.mkdir()
    shutil.copyfile(bundle / "database.dump", case / "database.dump")
    with zipfile.ZipFile(case / "uploads.zip", "x") as archive:
        archive.writestr(key, b"fixture")
    manifest = json.loads((bundle / "manifest.json").read_text(encoding="utf-8"))
    manifest["uploads"] = {key: {"bytes": 7, "sha256": hashlib.sha256(b"fixture").hexdigest()}}
    manifest["uploads_sha256"] = sha256(case / "uploads.zip")
    write_json(case / "manifest.json", manifest)
    result = command("local_backup_bundle.py", "verify", case, success=False)
    check(result.returncode != 0, "unsafe archive member rejected: " + key)

corrupt = fixture / "corrupt-archive"
corrupt.mkdir()
for name in ("manifest.json", "uploads.zip", "database.dump"):
    shutil.copyfile(bundle / name, corrupt / name)
with (corrupt / "uploads.zip").open("ab") as stream:
    stream.write(b"corruption")
check(command("local_backup_bundle.py", "verify", corrupt, success=False).returncode != 0, "corrupt archive rejected by hash")

wallet = psql(db, "SELECT id FROM family_wallets ORDER BY id LIMIT 1;")
uuid.UUID(wallet)
try:
    psql(db, f"UPDATE family_wallets SET balance_cents=balance_cents+1 WHERE id='{wallet}';")
    drifted = snapshot(db)
    result = command("settlement_correction_batch.py", "run", plan_path, success=False)
    check(result.returncode != 0 and snapshot(db) == drifted, "stale wallet fingerprint blocks entire batch without partial writes")
finally:
    psql(db, f"UPDATE family_wallets SET balance_cents=balance_cents-1 WHERE id='{wallet}';")
check(snapshot(db) == copy_before, "stale-plan fixture restored exactly")

command("settlement_correction_batch.py", "run", plan_path)
check(snapshot(db) == copy_before, "default dry run rolls back ledger and audit completely")
command("settlement_correction_batch.py", "run", plan_path, "--commit")
after = snapshot(db)
check(all(after[t] == copy_before[t] for t in TABLES if t not in ("settlement_entries", "audit_logs")), "committed test correction never changes wallet/order/refund/ownership/account rows")
count = psql(db, "SELECT count(*),coalesce(sum(net_cents),0) FROM settlement_entries WHERE entry_kind='REVERSAL';").split("|")
check(int(count[0]) == plan["entry_count"] and int(count[1]) == plan["net_reversal_cents"], "exact scoped reversal count and amount committed on copy")
command("settlement_correction_batch.py", "run", plan_path, "--commit")
check(snapshot(db) == after, "replaying committed batch adds neither reversal nor audit duplicates")

recovery_db = "nurture_gap_acceptance_stage1_recovery_20261010"
recovery_files = ROOT / "backend/target/stage1-recovery-files"
command("local_backup_bundle.py", "restore", bundle, "--database", recovery_db, "--files", recovery_files)
manifest = json.loads((bundle / "manifest.json").read_text(encoding="utf-8"))
check(snapshot(recovery_db) == manifest["database_snapshot"] and uploads(recovery_files) == manifest["uploads"], "pre-batch rollback point restores all database rows and 318 upload hashes on a second new copy")
check(snapshot("nurture") == main_before and uploads(ROOT / "backend/data/files") == main_files_before, "historical business source database and files remain untouched")
check(sha256(ROOT / "tools/settlement_walkthrough.png") == protected_before, "pre-existing untracked screenshot preserved")
print(f"PASS: {checks} safety/recovery checks; only isolated rehearsal copy committed")
