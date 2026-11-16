"""Minute probes and deterministic alert rules; run on a different host than the app."""
import argparse
import datetime as dt
import json
import subprocess
import time
import urllib.request
from pathlib import Path
from zoneinfo import ZoneInfo


def in_window(instant):
    hour = instant.astimezone(ZoneInfo("Europe/Kyiv")).hour
    return 7 <= hour < 21


def evaluate(samples, now, backup, disk_usage, ntp_synchronized):
    scoped = [sample for sample in samples if in_window(dt.datetime.fromisoformat(sample["at"]))]
    current_month = now.astimezone(ZoneInfo("Europe/Kyiv")).strftime("%Y-%m")
    month = [sample for sample in scoped if dt.datetime.fromisoformat(sample["at"]).astimezone(ZoneInfo("Europe/Kyiv")).strftime("%Y-%m") == current_month]
    month_unique = {dt.datetime.fromisoformat(sample["at"]).replace(second=0, microsecond=0): sample for sample in month}
    available = sum(sample["ready"] and sample["login"] for sample in month_unique.values())
    month_start = now.astimezone(ZoneInfo("Europe/Kyiv")).replace(day=1, hour=0, minute=0, second=0, microsecond=0).astimezone(dt.timezone.utc)
    total = sum(in_window(month_start + dt.timedelta(minutes=i)) for i in range(int((now - month_start).total_seconds() // 60) + 1))
    alerts = []
    # Missing minute samples count as unavailable, not silently as healthy minutes.
    recent = {dt.datetime.fromisoformat(sample["at"]).replace(second=0, microsecond=0): sample for sample in samples}
    minutes = [now.replace(second=0, microsecond=0) - dt.timedelta(minutes=i) for i in range(3)]
    if all(in_window(minute) and not (recent.get(minute, {}).get("ready") and recent.get(minute, {}).get("login")) for minute in minutes):
        alerts.append("UNAVAILABLE_3_MINUTES")
    if not backup.get("lastSuccess") or now - dt.datetime.fromisoformat(backup["lastSuccess"]) > dt.timedelta(hours=26):
        alerts.append("BACKUP_OVER_26_HOURS")
    if backup.get("lastResult") == "failed":
        alerts.append("BACKUP_FAILED")
    if any(value > 80 for value in disk_usage.values()):
        alerts.append("DISK_OVER_80_PERCENT")
    if not ntp_synchronized:
        alerts.append("NTP_NOT_SYNCHRONIZED")
    return {"alerts": alerts, "observedMinutes": len(month_unique), "windowMinutes": total,
            "missingMinutes": total - len(month_unique), "availableMinutes": available,
            "monthToDateAvailabilityPercent": round(100 * available / total, 4) if total else None}


def probe(url, expected_status=200):
    start = time.monotonic()
    try:
        with urllib.request.urlopen(url, timeout=3) as response:
            return response.status == expected_status and time.monotonic() - start <= 3
    except OSError:
        return False


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ready-url", required=True)
    parser.add_argument("--login-url", required=True)
    parser.add_argument("--samples", type=Path, required=True)
    parser.add_argument("--host-state", type=Path, required=True, help="Fresh JSON exported by DB/backup host: collectedAt, backup, diskUsage, ntpSynchronized")
    args = parser.parse_args()
    now = dt.datetime.now(dt.timezone.utc).replace(second=0, microsecond=0)
    sample = {"at": now.isoformat(), "ready": probe(args.ready_url), "login": probe(args.login_url)}
    args.samples.parent.mkdir(parents=True, exist_ok=True)
    with args.samples.open("a", encoding="utf-8") as stream:
        stream.write(json.dumps(sample) + "\n")
    samples = [json.loads(line) for line in args.samples.read_text(encoding="utf-8").splitlines()]
    state = json.loads(args.host_state.read_text(encoding="utf-8"))
    result = evaluate(samples, now, state["backup"], state["diskUsage"], state["ntpSynchronized"])
    if now - dt.datetime.fromisoformat(state["collectedAt"]) > dt.timedelta(minutes=3):
        result["alerts"].append("HOST_STATE_STALE")
    print(json.dumps({"event": "MONITOR", "at": now.isoformat(), **result}))
    # Nonzero exit is consumed by the operator's existing alert delivery system.
    raise SystemExit(2 if result["alerts"] else 0)


if __name__ == "__main__":
    main()
