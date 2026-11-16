"""Disposable local verification infrastructure. Uses only the Python standard library."""
import json
import subprocess
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def docker(*arguments, data=None, check=True, timeout=120):
    result = subprocess.run(["docker", *arguments], input=data, capture_output=True, timeout=timeout)
    if check and result.returncode:
        raise RuntimeError(result.stderr.decode("utf-8", errors="replace"))
    return result


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Client:
    def __init__(self, base):
        self.base = base
        self.cookie = None
        self.token = None
        self.opener = urllib.request.build_opener(NoRedirect())

    def request(self, method, path, body=None, form=False, request_id=None):
        headers = {"Accept": "application/json"}
        if request_id:
            headers["X-Request-Id"] = request_id
        if self.cookie:
            headers["Cookie"] = self.cookie
        if self.token:
            headers["X-CSRF-TOKEN"] = self.token
        if body is not None:
            headers["Content-Type"] = "application/x-www-form-urlencoded" if form else "application/json"
            body = body.encode() if form else json.dumps(body).encode()
        request = urllib.request.Request(self.base + path, data=body, headers=headers, method=method)
        try:
            response = self.opener.open(request, timeout=20)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            text = response.read().decode("utf-8")
            return response.status, response.headers, text

    def login(self, username="bootstrap.admin", password="Local-Smoke-Password-123"):
        import urllib.parse
        status, headers, body = self.request("GET", "/api/v1/csrf")
        assert status == 200, status
        self.cookie = headers["Set-Cookie"].split(";", 1)[0]
        self.token = json.loads(body)["token"]
        status, headers, _ = self.request("POST", "/login", urllib.parse.urlencode({"username": username, "password": password}), True)
        assert status == 302 and "error" not in headers["Location"], "Login failed"
        cookie = headers["Set-Cookie"]
        assert all(flag.lower() in cookie.lower() for flag in ["HttpOnly", "Secure", "SameSite=Lax"])
        self.cookie = cookie.split(";", 1)[0]
        status, _, body = self.request("GET", "/api/v1/csrf")
        assert status == 200
        self.token = json.loads(body)["token"]


class Fixture:
    def __init__(self, image="roombook:verify", bcrypt_cost=12):
        self.image = image
        if bcrypt_cost not in [10, 12]:
            raise ValueError("Verification bcrypt cost must be 10 or 12")
        self.bcrypt_cost = bcrypt_cost
        self.prefix = "roombook-check-" + uuid.uuid4().hex[:12]
        self.network = self.prefix
        self.db = self.prefix + "-db"
        self.app = self.prefix + "-app"
        self.created = []
        self.network_created = False

    def __enter__(self):
        try:
            docker("network", "create", "--label", "roombook.verification=true", self.network)
            self.network_created = True
            docker("run", "-d", "--name", self.db, "--network", self.network, "--network-alias", "db",
                   "--label", "roombook.verification=true", "-e", "POSTGRES_PASSWORD=local-superuser",
                   "-e", "POSTGRES_DB=roombook", "postgres:16")
            self.created.append(self.db)
            deadline = time.monotonic() + 60
            while docker("exec", self.db, "pg_isready", "-h", "127.0.0.1", "-U", "postgres", "-d", "roombook", check=False).returncode:
                if time.monotonic() > deadline:
                    raise TimeoutError("PostgreSQL startup")
                time.sleep(0.5)
            docker("exec", "-i", "-e", "ROOMBOOK_OWNER_PASSWORD=local-owner", "-e", "ROOMBOOK_APP_PASSWORD=local-app",
                   "-e", "ROOMBOOK_BACKUP_PASSWORD=local-backup", self.db, "psql", "-U", "postgres", "-d", "roombook",
                   data=(ROOT / "ops/db/init-production.sql").read_bytes())
            self.start_app()
            return self
        except BaseException:
            self.close()
            raise

    def start_app(self):
        docker("run", "-d", "--name", self.app, "--network", self.network,
               "--label", "roombook.verification=true", "--read-only", "--user", "10001:10001",
               "--tmpfs", "/tmp:rw,noexec,nosuid,size=128m,uid=10001,gid=10001", "--memory", "1g", "--cpus", "2",
               "--cap-drop", "ALL", "--security-opt", "no-new-privileges:true",
               "-p", "127.0.0.1::8080", "-p", "127.0.0.1::8081",
               "-e", "ROOMBOOK_DB_URL=jdbc:postgresql://db:5432/roombook", "-e", "ROOMBOOK_DB_USERNAME=roombook_app",
               "-e", "ROOMBOOK_DB_PASSWORD=local-app", "-e", "ROOMBOOK_FLYWAY_USERNAME=roombook_owner",
               "-e", "ROOMBOOK_FLYWAY_PASSWORD=local-owner", "-e", "ROOMBOOK_ADMIN_USERNAME=bootstrap.admin",
               "-e", "ROOMBOOK_ADMIN_PASSWORD=Local-Smoke-Password-123", "-e", "ROOMBOOK_VERSION=" + self.image, self.image,
               "--roombook.security.password-hash-cost=" + str(self.bcrypt_cost))
        self.created.append(self.app)
        info = json.loads(docker("inspect", self.app).stdout)[0]
        self.info = info
        ports = info["NetworkSettings"]["Ports"]
        self.base = "http://127.0.0.1:" + ports["8080/tcp"][0]["HostPort"]
        self.management = "http://127.0.0.1:" + ports["8081/tcp"][0]["HostPort"]
        self.wait_ready()

    def wait_ready(self):
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            try:
                if Client(self.management).request("GET", "/actuator/health/readiness")[0] == 200:
                    return
            except (OSError, urllib.error.URLError):
                pass
            state = json.loads(docker("inspect", self.app).stdout)[0]["State"]
            if not state["Running"]:
                raise RuntimeError("App stopped during startup; inspect target/container-app.log")
            time.sleep(0.5)
        raise TimeoutError("Application readiness")

    def sql(self, sql, role="postgres", database="roombook"):
        return docker("exec", "-i", self.db, "psql", "-U", role, "-d", database, "-v", "ON_ERROR_STOP=1", "-At", data=sql.encode()).stdout.decode()

    def close(self):
        if self.app in self.created:
            (ROOT / "target/container-app.log").write_bytes(docker("logs", self.app, check=False).stdout)
        for name in reversed(self.created):
            docker("rm", "-f", "-v", name, check=False)
        if self.network_created:
            docker("network", "rm", self.network, check=False)

    def __exit__(self, *_):
        self.close()
