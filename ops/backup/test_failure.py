import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from backup import main


class BackupFailureTest(unittest.TestCase):
    def test_missing_key_records_failure_without_losing_previous_success(self):
        root = Path(__file__).resolve().parents[2] / "target"
        with tempfile.TemporaryDirectory(dir=root) as temporary:
            directory = Path(temporary)
            state = directory / "state.json"
            state.write_text(json.dumps({"lastSuccess":"2026-11-04T00:00:00+00:00","lastResult":"success"}),encoding="utf-8")
            arguments = ["backup.py","--destination",str(directory/"backups"),"--key-file",str(directory/"absent.key"),
                         "--state",str(state),"--image-version","test","--schema-version","1"]
            output = io.StringIO()
            with patch("sys.argv",arguments), patch("backup.subprocess.run") as command, contextlib.redirect_stdout(output):
                with self.assertRaises(FileNotFoundError):
                    main()
            command.assert_not_called()
            self.assertEqual("BACKUP_FAILED",json.loads(output.getvalue())["event"])
            saved = json.loads(state.read_text(encoding="utf-8"))
            self.assertEqual("failed",saved["lastResult"])
            self.assertEqual("2026-11-04T00:00:00+00:00",saved["lastSuccess"])


if __name__ == "__main__": unittest.main()
