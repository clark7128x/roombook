import argparse
import datetime as dt
import json
import shutil
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--backup-state", type=Path, required=True)
parser.add_argument("--db-volume", required=True)
parser.add_argument("--backup-volume", required=True)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
usage = {}
for name, path in {"db": args.db_volume, "backup": args.backup_volume}.items():
    volume = shutil.disk_usage(path)
    usage[name] = round(100 * volume.used / volume.total, 2)
try:
    ntp = subprocess.run(["timedatectl", "show", "-p", "NTPSynchronized", "--value"], capture_output=True, text=True, timeout=5)
    synchronized = ntp.returncode == 0 and ntp.stdout.strip() == "yes"
except (OSError, subprocess.TimeoutExpired):
    synchronized = False
state = {"collectedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
         "backup": json.loads(args.backup_state.read_text()) if args.backup_state.exists() else {},
         "diskUsage": usage, "ntpSynchronized": synchronized}
args.output.write_text(json.dumps(state), encoding="utf-8")
