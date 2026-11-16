"""TS-23: encrypted custom-format backup restored into a separate clean PostgreSQL container."""
import datetime as dt
import json
import secrets
import shutil
import sys
import time
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "container"))
from runtime import Client, Fixture, ROOT, docker
from backup import crypt, verify_size

gpg = shutil.which("gpg") or "C:/Program Files/Git/usr/bin/gpg.exe"
directory = ROOT / "target/backup-rehearsal"
directory.mkdir(exist_ok=True)
key = ROOT / "target/rehearsal.key"
key.write_text(secrets.token_hex(32), encoding="ascii")
plain = directory / "source.dump"
encrypted = directory / "source.dump.gpg"
restored = directory / "restored.dump"
try:
    with Fixture() as source:
        client = Client(source.base)
        client.login()
        status, _, body = client.request("POST", "/api/v1/rooms", {"name": "Відновлення", "floor": 1, "capacity": 8})
        assert status == 201
        room = json.loads(body)["id"]
        tomorrow = dt.date.fromisoformat(json.loads(client.request("GET", "/api/v1/occupancy")[2])["date"]) + dt.timedelta(days=1)
        assert client.request("POST", "/api/v1/bookings", {"roomId": room, "title": "Збережена зустріч", "startAt": str(tomorrow) + "T10:00", "endAt": str(tomorrow) + "T11:00"})[0] == 201
        plain.write_bytes(docker("exec", "-e", "PGPASSWORD=local-backup", source.db, "pg_dump", "-h", "127.0.0.1", "-U", "roombook_backup", "-Fc", "roombook").stdout)
        docker("exec", "-i", source.db, "pg_restore", "--list", data=plain.read_bytes())
        verify_size(plain.stat().st_size, None)
        crypt(plain, encrypted, key, gpg=gpg, home=ROOT / "target/gpg-rehearsal")
        plain.unlink()
        started = time.monotonic()
        with Fixture() as target:
            docker("rm", "-f", target.app)
            target.created.remove(target.app)
            target.sql("DROP DATABASE roombook WITH (FORCE);", database="postgres")
            target.sql("CREATE DATABASE roombook OWNER roombook_owner TEMPLATE template0;", database="postgres")
            crypt(encrypted, restored, key, decrypt=True, gpg=gpg, home=ROOT / "target/gpg-rehearsal")
            docker("exec", "-i", target.db, "pg_restore", "--exit-on-error", "-U", "postgres", "-d", "roombook", data=restored.read_bytes())
            audits = []
            for path in sorted((ROOT / "ops/audit").glob("inv-*.sql")):
                assert target.sql(path.read_text(encoding="utf-8")).strip() == "", path.name
                audits.append(path.name)
            target.start_app()
            checked = Client(target.base)
            checked.login()
            status, _, body = checked.request("GET", "/api/v1/bookings")
            assert status == 200 and json.loads(body)["items"][0]["title"] == "Збережена зустріч"
            elapsed = round(time.monotonic() - started, 2)
            assert elapsed < 7200
            report = {"result": "PASS", "secondsToReady": elapsed, "encryptedBytes": encrypted.stat().st_size,
                      "audits": audits, "sourceRole": "roombook_backup", "encryption": "OpenPGP AES256 symmetric", "readiness": "UP",
                      "boundary": "Separate fresh PostgreSQL container on the same local Docker host; production storage transfer and physical-host recovery remain unverified."}
            (ROOT / "target/restore-report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
            print(json.dumps(report))
finally:
    for path in [key, plain, restored]:
        path.unlink(missing_ok=True)
