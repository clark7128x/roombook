import datetime as dt
import json
import sys
import time
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "container"))
from runtime import Fixture, ROOT, docker
from monitor import evaluate, probe, in_window

with Fixture() as fixture:
    samples = []
    started = time.monotonic()
    docker("pause", fixture.db)
    try:
        for minute in range(3):
            now = dt.datetime.now(dt.timezone.utc).replace(second=0, microsecond=0)
            samples.append({"at": now.isoformat(), "ready": probe(fixture.management + "/actuator/health/readiness"),
                            "login": probe(fixture.base + "/login")})
            deadline = started + (minute + 1) * 60
            time.sleep(max(0, deadline - time.monotonic()))
        result = evaluate(samples, now, {"lastSuccess": (now - dt.timedelta(hours=27)).isoformat(), "lastResult": "failed"}, {"db": 81}, True)
        if in_window(now):
            assert "UNAVAILABLE_3_MINUTES" in result["alerts"]
        assert all(not sample["ready"] and sample["login"] for sample in samples)
    finally:
        docker("unpause", fixture.db)
    fixture.wait_ready()
    report = {"samples": samples, "dbUnavailableSeconds": round(time.monotonic() - started, 2),
              "result": result, "windowExercised": in_window(now), "recovery": "UP",
              "delivery": "Alert decisions verified locally; no external notification sent."}
    (ROOT / "target/monitor-report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report))
