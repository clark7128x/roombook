"""Run on the DB host; destination must be an operator-configured off-host mount."""
import argparse
import datetime as dt
import hashlib
import json
import os
import re
import shutil
import subprocess
import tempfile
from pathlib import Path


def crypt(source, destination, key, decrypt=False, gpg="gpg", home=None):
    def path(value):
        resolved = Path(value).resolve().as_posix()
        if os.name == "nt" and "/git/" in gpg.replace("\\", "/").lower():
            return "/" + resolved[0].lower() + resolved[2:]
        return resolved
    command = [gpg, "--batch", "--yes", "--pinentry-mode", "loopback", "--passphrase-file", path(key)]
    if home:
        Path(home).mkdir(mode=0o700, parents=True, exist_ok=True)
        command += ["--homedir", path(home)]
    command += ["--output", path(destination)]
    command += ["--decrypt", path(source)] if decrypt else ["--symmetric", "--cipher-algo", "AES256", "--compress-algo", "none", path(source)]
    result = subprocess.run(command, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    if result.returncode:
        raise RuntimeError(result.stderr.decode("utf-8", errors="replace"))


def verify_size(size, previous):
    if size <= 0 or (previous and size < previous / 2):
        raise ValueError("Backup is empty or smaller than half of previous successful backup")


def retain(destination, now):
    destination = Path(destination).resolve()
    manifests = []
    for path in destination.glob("roombook-*.json"):
        record = json.loads(path.read_text(encoding="utf-8"))
        if record.get("format") == "roombook-backup-v1":
            manifests.append((path, record))
    daily = sorted((item for item in manifests if item[1]["kind"] == "daily"), key=lambda item: item[1]["createdAt"], reverse=True)
    obsolete = daily[30:] + [item for item in manifests if item[1]["kind"] == "prerelease" and
                             now - dt.datetime.fromisoformat(item[1]["createdAt"]) > dt.timedelta(days=30)]
    for manifest, record in obsolete:
        archive = (destination / record["file"]).resolve()
        if archive.parent != destination or not archive.name.startswith("roombook-") or archive.suffix != ".gpg":
            raise ValueError("Unsafe archive path in backup manifest")
        archive.unlink(missing_ok=True)
        manifest.unlink()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--destination", type=Path, required=True)
    parser.add_argument("--key-file", type=Path, required=True)
    parser.add_argument("--state", type=Path, required=True)
    parser.add_argument("--image-version", required=True)
    parser.add_argument("--schema-version", required=True)
    parser.add_argument("--kind", choices=["daily", "prerelease"], default="daily")
    args = parser.parse_args()
    destination = args.destination.resolve()
    key = args.key_file.resolve()
    state = {}
    now = dt.datetime.now(dt.timezone.utc)
    safe = lambda value: re.sub(r"[^a-zA-Z0-9_.-]", "_", value)
    name = "roombook-" + now.strftime("%Y%m%dT%H%M%S%fZ") + "-" + args.kind + "-" + safe(args.image_version) + "-schema" + safe(args.schema_version)
    plain = None
    encrypted = None
    try:
        if args.state.exists():
            saved_state = json.loads(args.state.read_text(encoding="utf-8"))
            if not isinstance(saved_state, dict):
                raise ValueError("Backup state must be a JSON object")
            state = saved_state
        if key.is_relative_to(destination):
            raise ValueError("Encryption key must be stored separately from backups")
        if len(key.read_bytes().strip()) < 32:
            raise ValueError("Encryption key file must contain at least 32 bytes")
        destination.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile(prefix="roombook-", suffix=".dump", delete=False) as temporary:
            plain = Path(temporary.name)
        subprocess.run(["pg_dump", "--format=custom", "--username=roombook_backup", "--file", str(plain)], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        subprocess.run(["pg_restore", "--list", str(plain)], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        size = plain.stat().st_size
        verify_size(size, state.get("plainBytes"))
        encrypted = plain.with_suffix(".gpg")
        crypt(plain, encrypted, key)
        plain.unlink()
        archive = destination / (name + ".gpg")
        shutil.copy2(encrypted, archive)
        with archive.open("rb") as stream:
            checksum = hashlib.file_digest(stream, "sha256").hexdigest()
        metadata = {"format": "roombook-backup-v1", "createdAt": now.isoformat(), "kind": args.kind,
                    "imageVersion": args.image_version, "schemaVersion": args.schema_version, "file": archive.name,
                    "plainBytes": size, "sha256": checksum}
        (destination / (name + ".json")).write_text(json.dumps(metadata), encoding="utf-8")
        retain(destination, now)
        state.update(lastSuccess=now.isoformat(), plainBytes=size, lastResult="success")
        print(json.dumps({"event": "BACKUP_SUCCESS", **metadata}))
    except Exception:
        state["lastResult"] = "failed"
        print(json.dumps({"event": "BACKUP_FAILED", "at": now.isoformat()}))
        raise
    finally:
        for file in [plain, encrypted]:
            if file:
                file.unlink(missing_ok=True)
        args.state.parent.mkdir(parents=True, exist_ok=True)
        args.state.write_text(json.dumps(state), encoding="utf-8")


if __name__ == "__main__":
    main()
