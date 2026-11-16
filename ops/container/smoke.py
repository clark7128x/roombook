import json
import time
from runtime import Client, Fixture, ROOT

started = time.monotonic()
with Fixture() as fixture:
    client = Client(fixture.base)
    client.login()
    assert fixture.info["Config"]["User"] == "10001:10001"
    assert fixture.info["HostConfig"]["ReadonlyRootfs"]
    assert fixture.info["HostConfig"]["Memory"] == 1024 ** 3
    assert fixture.info["HostConfig"]["NanoCpus"] == 2_000_000_000
    for name in ["Я", "Є", "Б"]:
        assert client.request("POST", "/api/v1/rooms", {"name": name, "floor": 1, "capacity": 5})[0] == 201
    status, _, body = client.request("GET", "/api/v1/rooms")
    assert status == 200 and [room["name"] for room in json.loads(body)] == ["Б", "Є", "Я"]
    status, headers, body = client.request("GET", "/occupancy")
    assert status == 200 and "Зайнятість" in body
    assert headers["X-Content-Type-Options"] == "nosniff"
    assert headers["X-Frame-Options"] == "DENY"
    assert "unsafe-inline" not in headers["Content-Security-Policy"]
    assert "Access-Control-Allow-Origin" not in headers
    health = Client(fixture.management).request("GET", "/actuator/health/readiness")
    assert health[0] == 200 and json.loads(health[2]) == {"status": "UP"}
    report = {"result": "PASS", "image": fixture.image, "seconds": round(time.monotonic() - started, 2),
              "nonRoot": True, "readOnly": True, "memoryBytes": 1024 ** 3, "cpus": 2,
              "checks": ["empty DB migrations", "production role setup", "bootstrap", "login", "secure cookie", "Ukrainian ordering", "occupancy", "security headers", "readiness"],
              "boundary": "Local isolated Docker network; TLS reverse proxy and office-network restrictions are not exercised."}
    (ROOT / "target/container-smoke.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report))
