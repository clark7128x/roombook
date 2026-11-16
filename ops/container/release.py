"""TS-25 rehearsal on an isolated copy; never changes the source database."""
import datetime as dt
import html
import json
import time
from runtime import Client, Fixture, ROOT, docker


def rehearse(source, rollback_image):
    count = int(source.sql("SELECT count(*) FROM booking;").strip())
    dump = docker("exec", "-e", "PGPASSWORD=local-backup", source.db, "pg_dump",
                  "-h", "127.0.0.1", "-U", "roombook_backup", "-Fc", "roombook", timeout=600).stdout
    docker("exec", "-i", source.db, "pg_restore", "--list", data=dump)
    started = time.monotonic()
    checks = []
    with Fixture() as target:
        docker("rm", "-f", target.app)
        target.created.remove(target.app)
        target.sql("DROP DATABASE roombook WITH (FORCE);", database="postgres")
        target.sql("CREATE DATABASE roombook OWNER roombook_owner TEMPLATE template0;", database="postgres")
        docker("exec", "-i", target.db, "pg_restore", "--exit-on-error", "-U", "postgres",
               "-d", "roombook", data=dump, timeout=600)
        del dump
        assert int(target.sql("SELECT count(*) FROM booking;").strip()) == count
        for image in [source.image, rollback_image]:
            target.image = image
            target.start_app()
            client = Client(target.base)
            client.login()
            occupancy = client.request("GET", "/occupancy")
            assert occupancy[0] == 200
            today = dt.date.fromisoformat(json.loads(client.request("GET", "/api/v1/occupancy")[2])["date"])
            weekday = ["понеділок","вівторок","середа","четвер","п'ятниця","субота","неділя"][today.weekday()]
            rendered = html.unescape(occupancy[2]).replace("’", "'").replace("ʼ", "'")
            assert weekday in rendered, "Ukrainian weekday locale missing"
            # Separate floor makes the Ukrainian alphabet assertion independent of seed names.
            if not checks:
                for name in ["Ї", "І", "Є", "Ґ", "Г"]:
                    assert client.request("POST", "/api/v1/rooms", {"name":name,"floor":99,"capacity":4})[0] == 201
            status, _, body = client.request("GET", "/api/v1/rooms?floor=99")
            assert status == 200 and [room["name"] for room in json.loads(body)] == ["Г","Ґ","Є","І","Ї"]
            assert int(target.sql("SELECT count(*) FROM booking;").strip()) == count
            for path in sorted((ROOT/"ops/audit").glob("inv-*.sql")):
                assert not target.sql(path.read_text(encoding="utf-8")).strip(), path.name
            checks.append({"image":image,"imageId":target.info["Image"],"login":"PASS",
                           "occupancy":"PASS","ukrainianOrder":"PASS","invariantAudit":"PASS",
                           "schema":target.sql("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1;").strip()})
            (ROOT/("target/release-"+str(len(checks))+".jsonl")).write_bytes(docker("logs",target.app).stdout)
            docker("rm", "-f", target.app)
            target.created.remove(target.app)
        report = {"result":"PASS","copiedBookings":count,"seconds":round(time.monotonic()-started,2),
                  "checks":checks,"boundary":"Disposable database copy on the same Docker Desktop host. Image rollback with unchanged schema; production routing and incompatible migration recovery are not exercised."}
        (ROOT/"target/release-report.json").write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding="utf-8")
        print(json.dumps(report,ensure_ascii=False),flush=True)
