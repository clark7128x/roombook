"""P1-P6 runner. Default full durations; --diagnostic is explicitly non-acceptance."""
import argparse
import collections
import concurrent.futures
import datetime as dt
import itertools
import json
import math
import queue
import random
import re
import sys
import threading
import time
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "container"))
from runtime import Client, Fixture, ROOT, docker

parser = argparse.ArgumentParser()
parser.add_argument("--diagnostic", action="store_true")
parser.add_argument("--p3-only", action="store_true", help="Targeted full-duration P3 retest; not a full P1-P6 acceptance")
parser.add_argument("--bcrypt-cost", type=int, choices=[10,12], default=12)
parser.add_argument("--rehearse-release", action="store_true", help="After measured load and its report, rehearse T-30 against a separate copy of the million-row DB")
args = parser.parse_args()
if args.diagnostic and args.p3_only:
    parser.error("Choose diagnostic or p3-only, not both")
profile = "diagnostic" if args.diagnostic else "p3" if args.p3_only else "full"
records = []
sequence = itertools.count()
stage = "setup"


def failed_run(kind, error, traceback):
    with (ROOT/"target/load-records.jsonl").open("w",encoding="utf-8") as stream:
        for record in records: stream.write(json.dumps(record)+"\n")
    report = {"result":"FAILED", "stage":stage, "exception":kind.__name__,
              "requestsCompleted":len(records), "errors5xx":sum(record["status"]>=500 for record in records),
              "profile":profile, "appLog":"target/container-app.log"}
    (ROOT/"target/load-failure.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
    sys.__excepthook__(kind,error,traceback)


sys.excepthook = failed_run


class LoadClient(Client):
    def request(self, method, path, body=None, form=False):
        request_id = "load-" + stage + "-" + str(next(sequence))
        started = time.monotonic()
        try:
            result = super().request(method, path, body, form, request_id)
            status = result[0]
        except Exception:
            result = (599, {}, "")
            status = 599
        records.append({"requestId": request_id, "stage": stage, "method": method, "path": path,
                        "status": status, "clientMs": round((time.monotonic()-started)*1000, 2)})
        return result


def p95(values):
    return sorted(values)[max(0, math.ceil(len(values)*.95)-1)] if values else None


with Fixture(bcrypt_cost=args.bcrypt_cost) as fixture:
    actual_cost = fixture.sql("SELECT split_part(password_hash,'$',3) FROM app_user ORDER BY id LIMIT 1;").strip()
    assert actual_cost == str(args.bcrypt_cost), "Unexpected test bcrypt cost: " + actual_cost
    print("Seeding 1,000,000 bookings", flush=True)
    seeded = time.monotonic()
    docker("exec", "-i", fixture.db, "psql", "-U", "postgres", "-d", "roombook", "-v", "ON_ERROR_STOP=1",
           data=(ROOT/"ops/load/seed.sql").read_bytes(), timeout=900)
    print("Seeded in %.2f seconds" % (time.monotonic()-seeded), flush=True)
    assert fixture.sql("SELECT count(*) FROM booking;").strip() == "1000000"
    assert fixture.sql("SELECT count(*) FROM booking WHERE status='ACTIVE';").strip() == "700000"
    fixture.sql("CHECKPOINT;")
    plan = fixture.sql("EXPLAIN (ANALYZE, BUFFERS) SELECT id FROM booking WHERE room_id=1 AND status='ACTIVE' AND start_at >= CURRENT_DATE+84 AND start_at < CURRENT_DATE+85 AND start_at < (CURRENT_DATE+84)+time '18:15' AND end_at > (CURRENT_DATE+84)+time '18:00';")
    (ROOT/"target/load-overlap-plan.txt").write_text(plan,encoding="utf-8")
    plan = fixture.sql("BEGIN; EXPLAIN (ANALYZE, BUFFERS) INSERT INTO booking(room_id,author_id,title,start_at,end_at,status,created_at) VALUES(1,1,'plan probe',(CURRENT_DATE+84)+time '18:00',(CURRENT_DATE+84)+time '18:15','ACTIVE',now()); ROLLBACK;")
    (ROOT/"target/load-insert-plan.txt").write_text(plan,encoding="utf-8")
    statements = ["\\timing on"]
    for index in range(50):
        start = "(CURRENT_DATE+%d)+time '18:00'+interval '%d minutes'" % (70+index//8,15*(index%8))
        statements.append("BEGIN;\nINSERT INTO booking(room_id,author_id,title,start_at,end_at,status,created_at) VALUES(50,1,'commit probe',%s,%s+interval '15 minutes','ACTIVE',now());\nCOMMIT;" % (start,start))
    statements.append("DELETE FROM booking WHERE title='commit probe';")
    commit_plan = fixture.sql("\n".join(statements))
    (ROOT/"target/load-commit-timing.txt").write_text(commit_plan,encoding="utf-8")
    commit_times = [float(value) for value in re.findall(r"COMMIT\s+Time: ([0-9.]+) ms",commit_plan)]
    print("Direct PostgreSQL commit p95: %s ms (%s samples)" % (p95(commit_times),len(commit_times)),flush=True)
    admin = LoadClient(fixture.base)
    admin.login()
    clients = []
    for i in range(1,51):
        client = LoadClient(fixture.base)
        client.login("load%03d" % i)
        clients.append(client)
    today = dt.date.fromisoformat(json.loads(admin.request("GET", "/api/v1/occupancy")[2])["date"])
    rooms = [room["id"] for room in json.loads(admin.request("GET", "/api/v1/rooms")[2])]
    created = queue.Queue()
    slot_sequence = itertools.count()
    metrics = []
    p4_checks = []

    def booking_payload(slot, hot=False):
        day = (84 + slot//8) if hot else (1 + slot//400)
        minute = 18*60 + 15*(slot%8 if hot else (slot//50)%8)
        start = dt.datetime.combine(today+dt.timedelta(days=day), dt.time(minute//60, minute%60))
        return {"roomId": rooms[0] if hot else rooms[slot%50], "title": "Навантаження", "startAt": start.isoformat(), "endAt": (start+dt.timedelta(minutes=15)).isoformat()}

    def work(index, writing):
        rng = random.Random(20261105 + index)
        client = clients[index%50]
        if not writing:
            choice = rng.randrange(100)
            date = today + dt.timedelta(days=rng.randrange(-30,31))
            if choice < 40:
                path = ("/occupancy" if index%2 else "/api/v1/occupancy") + "?date=" + str(date)
            elif choice < 60:
                path = "/api/v1/rooms" + ("?freeFrom="+str(today+dt.timedelta(days=1))+"T18:00&freeTo="+str(today+dt.timedelta(days=1))+"T18:15" if index%2 else "")
            elif choice < 85:
                path = "/api/v1/me/bookings?page=" + str(0 if rng.random()<.7 else rng.randrange(1,160))
            elif choice < 95:
                path = "/fragments/room-day?roomId=" + str(rooms[index%50]) + "&date=" + str(date)
            else:
                path = "/admin/bookings?roomId=" + str(rooms[index%50]) + "&dateFrom=" + str(today)
                client = admin
            return client.request("GET", path)[0]
        if rng.random() < .3:
            try:
                owner, booking_id = created.get_nowait()
                return owner.request("POST", "/api/v1/bookings/" + str(booking_id) + "/cancel")[0]
            except queue.Empty:
                pass
        slot = next(slot_sequence)
        payload = booking_payload(slot)
        if rng.random() < .15:
            payload.update(startAt=str(today+dt.timedelta(days=1))+"T09:30", endAt=str(today+dt.timedelta(days=1))+"T09:45")
        result = client.request("POST", "/api/v1/bookings", payload)
        if result[0] == 201:
            created.put((client,json.loads(result[2])["id"]))
        return result[0]

    def metric(name, query=""):
        response = Client(fixture.management).request("GET", "/actuator/metrics/" + name + query)
        return json.loads(response[2])["measurements"][0]["value"] if response[0] == 200 else None

    def timed(name, seconds, rate=30, logins=0):
        global stage
        stage = name
        print("Starting %s: %ss at %s requests/s" % (name,seconds,rate), flush=True)
        started = time.monotonic()
        futures = []
        login_sent = 0
        with concurrent.futures.ThreadPoolExecutor(max_workers=50) as pool:
            for index in range(seconds*rate):
                due = started + index/rate
                time.sleep(max(0,due-time.monotonic()))
                futures.append(pool.submit(work, index, index%6 == 5))
                if logins and login_sent < logins and index/rate >= login_sent*min(60,seconds)/logins:
                    def enter(number=login_sent):
                        LoadClient(fixture.base).login("load%03d" % (1+number%299))
                    futures.append(pool.submit(enter))
                    login_sent += 1
                if index and index % (rate*60) == 0:
                    snapshot = {"stage": name, "minute": index//(rate*60), "heapUsed": metric("jvm.memory.used", "?tag=area:heap"),
                                "heapAfterGc": metric("jvm.gc.live.data.size"), "activeConnections": metric("hikaricp.connections.active"),
                                "sessions": metric("tomcat.sessions.active.current")}
                    metrics.append(snapshot)
                    if name == "P6": Client(fixture.base).request("GET", "/login")
                    print(json.dumps(snapshot), flush=True)
            for future in futures:
                future.result()
        if name == "P6":
            metrics.append({"stage":name,"minute":seconds/60,"heapUsed":metric("jvm.memory.used", "?tag=area:heap"),
                            "heapAfterGc":metric("jvm.gc.live.data.size"),"activeConnections":metric("hikaricp.connections.active"),
                            "sessions":metric("tomcat.sessions.active.current")})
        print("Finished " + name, flush=True)

    timed("warmup",10 if args.diagnostic else 120)
    if not args.p3_only:
        timed("P1",30 if args.diagnostic else 900)
        timed("P2",20 if args.diagnostic else 600,60)
    timed("P3",20 if args.diagnostic else 300,logins=10 if args.diagnostic else 300)
    if not args.p3_only:
        stage = "P4"
        for repetition in range(2 if args.diagnostic else 20):
            payload = booking_payload(0,True)
            barrier = threading.Barrier(50)
            def clash(client):
                barrier.wait(timeout=30)
                return client.request("POST", "/api/v1/bookings", payload)
            with concurrent.futures.ThreadPoolExecutor(max_workers=50) as pool:
                results = list(pool.map(clash,clients))
            statuses = collections.Counter(result[0] for result in results)
            assert statuses == {201:1,409:49}, statuses
            for result in results:
                if result[0] == 409: assert json.loads(result[2])["code"] == "SLOT_TAKEN"
            booking_id = next(json.loads(result[2])["id"] for result in results if result[0] == 201)
            assert admin.request("POST", "/api/v1/bookings/" + str(booking_id) + "/cancel")[0] == 200
            with concurrent.futures.ThreadPoolExecutor(max_workers=50) as pool:
                results = list(pool.map(lambda pair: pair[1].request("POST", "/api/v1/bookings", booking_payload(pair[0],True)),enumerate(clients)))
            assert all(result[0] == 201 for result in results), [result[0] for result in results]
            for result in results:
                assert admin.request("POST", "/api/v1/bookings/" + str(json.loads(result[2])["id"]) + "/cancel")[0] == 200
            p4_checks.append({"repeat":repetition+1,"sameSlot":dict(statuses),"differentSlots":50})
        stage = "P5"
        top = fixture.sql("SELECT u.username FROM app_user u JOIN booking b ON b.author_id=u.id GROUP BY u.id ORDER BY count(*) DESC,u.id LIMIT 1;").strip()
        owner = LoadClient(fixture.base)
        owner.login(top)
        count = int(fixture.sql("SELECT count(*) FROM booking b JOIN app_user u ON u.id=b.author_id WHERE u.username='"+top+"';").strip())
        pages = range(min(3,math.ceil(count/20))) if args.diagnostic else range(math.ceil(count/20))
        for page in pages:
            for _ in range(2 if args.diagnostic else 100):
                assert owner.request("GET", "/api/v1/me/bookings?page="+str(page))[0] == 200
        for page in [0,1000,25000,49000]:
            for _ in range(2 if args.diagnostic else 100):
                assert admin.request("GET", "/api/v1/bookings?page="+str(page))[0] == 200
        timed("P6",20 if args.diagnostic else 3600)
    for path in sorted((ROOT/"ops/audit").glob("inv-*.sql")):
        assert not fixture.sql(path.read_text(encoding="utf-8")).strip(),path.name
    logs = docker("logs",fixture.app,timeout=120).stdout
    (ROOT/("target/load-"+profile+"-app.jsonl")).write_bytes(logs)
    server = {}
    for line in logs.splitlines():
        try:
            event = json.loads(line)
            if event.get("event") == "HTTP_REQUEST": server[event.get("requestId")] = event["durationMs"]
        except (ValueError,KeyError): pass
    groups = collections.defaultdict(list)
    for record in records:
        if record["stage"] not in ["setup","warmup"]:
            path = record["path"] if record["stage"] == "P5" else record["path"].split("?")[0]
            if "/cancel" in path: path = "/api/v1/bookings/{id}/cancel"
            duration = server.get(record["requestId"])
            record["serverMs"] = duration
            if duration is not None: groups[(record["stage"],record["method"],path,record["status"])].append(duration)
    summary = [{"stage":key[0],"method":key[1],"path":key[2],"status":key[3],"count":len(values),"p95ServerMs":p95(values)} for key,values in sorted(groups.items())]
    report = {"runId":fixture.prefix,"imageId":fixture.info["Image"],"profile":profile, "baselineBookings":1000000,"rooms":50,"users":300,
              "limits":{"memoryBytes":1024**3,"cpus":2,"pool":10,"bcryptCost":args.bcrypt_cost},"results":summary,"metrics":metrics,
              "p4Checks":p4_checks,"commitProbeP95Ms":p95(commit_times),"invariantAudit":"PASS","errors5xx":sum(record["status"]>=500 for record in records),
              "missingServerDurations":sum(record.get("serverMs") is None for record in records if record["stage"] not in ["setup","warmup"]),
              "boundary":"Separate app/DB containers and host generator share one Docker Desktop host. Diagnostic profile shortens durations and cannot satisfy TS-22. Heap-after-GC uses JVM metric; absent/zero measurements are inconclusive."}
    output = ROOT/("target/load-"+profile+".json")
    output.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding="utf-8")
    print(json.dumps({"report":str(output),"errors5xx":report["errors5xx"],"groups":len(summary)}),flush=True)
    if args.rehearse_release:
        stage = "T30-release"
        from release import rehearse
        rehearse(fixture, "roombook:rollback-test")
