import datetime as dt
import json
import sys
import tempfile
import unittest
from pathlib import Path
from monitor import evaluate
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "backup"))
from backup import retain, verify_size


class OperationsRulesTest(unittest.TestCase):
    def test_alerts_and_missing_minutes(self):
        now = dt.datetime(2026, 11, 1, 5, 3, tzinfo=dt.timezone.utc)
        samples = [{"at": (now - dt.timedelta(minutes=i)).isoformat(), "ready": i == 3, "login": True} for i in range(4)]
        result = evaluate(samples, now, {"lastSuccess": (now - dt.timedelta(hours=27)).isoformat(), "lastResult": "failed"}, {"db": 81, "backup": 79}, False)
        self.assertEqual(25, result["monthToDateAvailabilityPercent"])
        self.assertEqual({"UNAVAILABLE_3_MINUTES", "BACKUP_OVER_26_HOURS", "BACKUP_FAILED", "DISK_OVER_80_PERCENT", "NTP_NOT_SYNCHRONIZED"}, set(result["alerts"]))
        samples.pop(0)
        self.assertEqual(1, evaluate(samples, now, {}, {}, True)["missingMinutes"])
        samples = [{"at": now.isoformat(), "ready": True, "login": False}]
        self.assertEqual(0, evaluate(samples, now, {}, {}, True)["availableMinutes"])

    def test_backup_size_and_retention(self):
        verify_size(50, 100)
        for size in [0, 49]:
            with self.assertRaises(ValueError): verify_size(size, 100)
        root = Path(__file__).resolve().parents[2] / "target"
        root.mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=root) as temporary:
            directory = Path(temporary).resolve()
            self.assertTrue(directory.is_relative_to(root.resolve()))
            now = dt.datetime.now(dt.timezone.utc)
            for i in range(35):
                name = "roombook-" + str(i)
                (directory / (name + ".gpg")).write_bytes(b"encrypted fixture")
                (directory / (name + ".json")).write_text(json.dumps({"format": "roombook-backup-v1", "kind": "daily", "createdAt": (now - dt.timedelta(days=i)).isoformat(), "file": name + ".gpg"}), encoding="utf-8")
            retain(directory, now)
            self.assertEqual(30, len(list(directory.glob("*.gpg"))))


if __name__ == "__main__": unittest.main()
