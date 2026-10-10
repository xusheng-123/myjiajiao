"""Pair a local PostgreSQL backup with uploads; verify and restore only to a new isolated copy.

No deletes or overwrites. Backups/manifests contain private development data and stay in deploy/backups.
"""
import argparse
import hashlib
import json
import ntpath
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import uuid
import zipfile
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]
CONTAINER = "deploy-postgres-1"
DB_USER = "nurture"
sys.stdout.reconfigure(encoding="utf-8")

def require(condition, message):
    if not condition:
        raise ValueError(message)

def database_name(name):
    require(re.fullmatch(r"[a-z][a-z0-9_]{0,62}", name), "Invalid database name")
    return name

def run(args, **kwargs):
    result = subprocess.run(args, capture_output=True, **kwargs)
    if result.returncode:
        # Database diagnostics stay in local logs; commands contain no credentials.
        error = result.stderr.decode("utf-8", errors="replace") if isinstance(result.stderr, bytes) else result.stderr
        raise RuntimeError(error[:2000])
    return result.stdout

def psql(database, sql):
    return run(["docker", "exec", "-i", CONTAINER, "psql", "-U", DB_USER, "-d", database_name(database),
                "-v", "ON_ERROR_STOP=1", "-At"], input=sql, text=True, encoding="utf-8").strip()

def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

def no_links(path):
    require(not path.is_symlink() and not path.is_junction(), "Symlink/junction paths are not supported")

def contained(path, boundary):
    path = Path(path).absolute()
    for parent in (path, *path.parents):
        no_links(parent)
    resolved = path.resolve()
    require(resolved.is_relative_to(boundary.resolve()) and resolved != boundary.resolve(), "Path outside allowed directory")
    return resolved

def relative_name(name):
    require(isinstance(name, str) and name and "\\" not in name and ":" not in name and "\x00" not in name, "Unsafe archive member")
    parts = PurePosixPath(name)
    require(not parts.is_absolute() and str(parts) == name and all(p not in (".", "..") and not ntpath.isreserved(p) for p in parts.parts), "Unsafe archive member")
    return parts

def snapshot(database):
    tables = psql(database, "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename;").splitlines()
    require(tables and "flyway_schema_history" in tables, "Not a Nurture database")
    values = []
    for table in tables:
        require(re.fullmatch(r"[a-z_][a-z0-9_]*", table), "Unsupported table name")
        values.append(f"SELECT '{table}', count(*), encode(sha256(convert_to(coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text,'[]'),'UTF8')),'hex') FROM public.\"{table}\" t")
    rows = psql(database, "BEGIN READ ONLY;\n" + " UNION ALL ".join(values) + ";\nCOMMIT;")
    return {a: {"rows": int(b), "sha256": c} for line in rows.splitlines() if "|" in line for a, b, c in [line.split("|")]}

def uploads(root):
    result = {}
    for directory, dirs, files in os.walk(root, followlinks=False):
        for name in [*dirs, *files]:
            no_links(Path(directory) / name)
        for name in sorted(files):
            path = Path(directory) / name
            require(path.is_file(), "Upload is not a regular file")
            key = path.relative_to(root).as_posix()
            relative_name(key)
            result[key] = {"bytes": path.stat().st_size, "sha256": sha256(path)}
    return result

def references(database):
    result = psql(database, "SELECT coalesce(json_agg(storage_key), '[]'::json) FROM (SELECT id_document_key AS storage_key FROM verification_cases WHERE id_document_key IS NOT NULL UNION SELECT degree_cert_key FROM verification_cases WHERE degree_cert_key IS NOT NULL) x;")
    return sorted(json.loads(result))

def write_json(path, data):
    with path.open("x", encoding="utf-8") as stream:
        json.dump(data, stream, ensure_ascii=False, indent=2)
        stream.write("\n")

def backup(args):
    database_name(args.database)
    source = contained(args.files, ROOT / "backend")
    require(source.is_dir(), "Upload directory does not exist")
    destination = contained(args.output, ROOT / "deploy/backups")
    require(not destination.exists(), "Backup output must be new; existing backups are never overwritten")
    before = snapshot(args.database)
    files_before = uploads(source)
    storage_keys = references(args.database)
    destination.mkdir(parents=True)
    dump = destination / "database.dump"
    with dump.open("xb") as stream:
        result = subprocess.run(["docker", "exec", CONTAINER, "pg_dump", "-U", DB_USER, "-d", args.database,
                                 "-Fc", "--no-owner", "--no-privileges"], stdout=stream, stderr=subprocess.PIPE)
        require(result.returncode == 0, "pg_dump failed; incomplete backup retained for inspection")
    with dump.open("rb") as stream:
        run(["docker", "exec", "-i", CONTAINER, "pg_restore", "--list"], stdin=stream)
    archive = destination / "uploads.zip"
    with zipfile.ZipFile(archive, "x", compression=zipfile.ZIP_DEFLATED) as output:
        for key in sorted(files_before):
            output.write(source / key, key)
    require(uploads(source) == files_before and snapshot(args.database) == before,
            "Source changed during backup; pause its writes and take a new bundle")
    manifest = {"format": 1, "id": str(uuid.uuid4()), "created_utc": datetime.now(timezone.utc).isoformat(),
                "source_database": args.database, "source_upload_directory": str(source),
                "database_sha256": sha256(dump), "uploads_sha256": sha256(archive),
                "database_snapshot": before, "uploads": files_before, "referenced_keys": storage_keys,
                "missing_referenced_keys": sorted(set(storage_keys) - set(files_before))}
    write_json(destination / "manifest.json", manifest)
    verified = verify(destination)
    print(json.dumps({"bundle": str(destination), **verified}, ensure_ascii=False))

def verify(bundle):
    bundle = contained(bundle, ROOT / "deploy/backups")
    for name in ("manifest.json", "database.dump", "uploads.zip"):
        no_links(bundle / name)
    manifest = json.loads((bundle / "manifest.json").read_text(encoding="utf-8"))
    require(manifest.get("format") == 1, "Unknown manifest version")
    require(sha256(bundle / "database.dump") == manifest["database_sha256"], "Database archive hash mismatch")
    require(sha256(bundle / "uploads.zip") == manifest["uploads_sha256"], "Uploads archive hash mismatch")
    with (bundle / "database.dump").open("rb") as stream:
        run(["docker", "exec", "-i", CONTAINER, "pg_restore", "--list"], stdin=stream)
    with zipfile.ZipFile(bundle / "uploads.zip") as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)) and set(names) == set(manifest["uploads"]), "Archive members differ from manifest")
        for key, expected in manifest["uploads"].items():
            relative_name(key)
            require(archive.getinfo(key).file_size == expected["bytes"], "Upload size mismatch")
            digest = hashlib.sha256()
            with archive.open(key) as stream:
                for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                    digest.update(chunk)
            require(digest.hexdigest() == expected["sha256"], "Upload content hash mismatch")
    missing = sorted(set(manifest["referenced_keys"]) - set(manifest["uploads"]))
    require(missing == manifest["missing_referenced_keys"], "Material reference report mismatch")
    return {"archive_verified": True, "file_count": len(manifest["uploads"]), "missing_material_count": len(missing),
            "all_referenced_materials_present": not missing}

def restore(args):
    database_name(args.database)
    require(args.database.startswith("nurture_gap_acceptance_"), "Restore target must be a new isolated nurture_gap_acceptance_* database")
    bundle = contained(args.bundle, ROOT / "deploy/backups")
    verify(bundle)
    destination = contained(args.files, ROOT / "backend/target")
    require(not destination.exists(), "Restore upload destination must be new")
    require(psql("postgres", f"SELECT count(*) FROM pg_database WHERE datname='{args.database}';") == "0", "Restore database already exists")
    manifest = json.loads((bundle / "manifest.json").read_text(encoding="utf-8"))
    destination.mkdir(parents=True)
    with zipfile.ZipFile(bundle / "uploads.zip") as archive:
        for name in archive.namelist():
            parts = relative_name(name)
            target = destination.joinpath(*parts.parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            with archive.open(name) as source, target.open("xb") as output:
                for chunk in iter(lambda: source.read(1024 * 1024), b""):
                    output.write(chunk)
    psql("postgres", f'CREATE DATABASE "{args.database}" OWNER "{DB_USER}";')
    with (bundle / "database.dump").open("rb") as stream:
        run(["docker", "exec", "-i", CONTAINER, "pg_restore", "-U", DB_USER, "-d", args.database,
             "--no-owner", "--no-privileges", "--exit-on-error", "--single-transaction"], stdin=stream)
    require(snapshot(args.database) == manifest["database_snapshot"], "Restored database row fingerprints differ")
    require(uploads(destination) == manifest["uploads"], "Restored upload hashes differ")
    require(references(args.database) == manifest["referenced_keys"], "Restored material references differ")
    report = {"database": args.database, "upload_directory": str(destination), "database_fingerprints_match": True,
              "upload_hashes_match": True, "file_count": len(manifest["uploads"]),
              "missing_material_count": len(manifest["missing_referenced_keys"])}
    write_json(bundle / f"restore-{args.database}.json", report)
    print(json.dumps(report, ensure_ascii=False))

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    create = commands.add_parser("backup")
    create.add_argument("--database", required=True)
    create.add_argument("--files", required=True)
    create.add_argument("--output", required=True)
    check = commands.add_parser("verify")
    check.add_argument("bundle")
    recover = commands.add_parser("restore")
    recover.add_argument("bundle")
    recover.add_argument("--database", required=True)
    recover.add_argument("--files", required=True)
    args = parser.parse_args()
    if args.command == "backup": backup(args)
    elif args.command == "restore": restore(args)
    else: print(json.dumps(verify(args.bundle)))

if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, OSError, KeyError, zipfile.BadZipFile) as error:
        print("Backup operation rejected/failed: " + str(error), file=sys.stderr)
        raise SystemExit(1)
