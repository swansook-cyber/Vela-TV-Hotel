"""Backup integrity and restore behavior using disposable local data only."""
import importlib.util
import json
from contextlib import closing
from pathlib import Path
import sqlite3
import tarfile
import tempfile
import unittest

import hotel_api

SCRIPT = Path(__file__).resolve().parent.parent / "deploy/homehub-staging/backup_restore.py"
spec = importlib.util.spec_from_file_location("backup_restore", SCRIPT)
backup_restore = importlib.util.module_from_spec(spec)
spec.loader.exec_module(backup_restore)


class BackupRestoreTests(unittest.TestCase):
    def test_backup_restore_and_no_overwrite(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            data = root / "data"
            store = hotel_api.Store(data)
            store.set_state("marker", "before")
            store.db.close()
            (data / "media" / "sample.png").write_bytes(b"test media")
            archive = root / "safe" / "backup.tar.gz"
            backup_restore.backup(data, archive)
            self.assertTrue(archive.is_file())
            store = hotel_api.Store(data)
            store.set_state("marker", "after")
            store.db.close()
            restored = root / "restored"
            backup_restore.restore(archive, restored)
            with closing(sqlite3.connect(restored / "hotel.sqlite3")) as db:
                self.assertEqual(db.execute("SELECT value FROM state WHERE key='marker'").fetchone()[0], "before")
            self.assertEqual((restored / "media" / "sample.png").read_bytes(), b"test media")
            with self.assertRaises(ValueError):
                backup_restore.restore(archive, restored)

    def test_rejects_archive_traversal_and_corruption(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            archive = root / "unsafe.tar.gz"
            payload = root / "payload"
            payload.write_text("bad", encoding="utf-8")
            with tarfile.open(archive, "w:gz") as tar:
                tar.add(payload, arcname="../escape")
            with self.assertRaises(ValueError):
                backup_restore.restore(archive, root / "target")
            data = root / "data"
            store = hotel_api.Store(data)
            store.db.close()
            good = root / "good.tar.gz"
            backup_restore.backup(data, good)
            altered = root / "altered.tar.gz"
            with tarfile.open(good, "r:gz") as source, tarfile.open(altered, "w:gz") as dest:
                for member in source.getmembers():
                    extracted = source.extractfile(member) if member.isfile() else None
                    if member.name == "manifest.json":
                        modified = json.loads(extracted.read())
                        modified["hotel.sqlite3"] = "0" * 64
                        content = json.dumps(modified).encode()
                        member.size = len(content)
                        import io
                        dest.addfile(member, io.BytesIO(content))
                    elif member.isfile():
                        dest.addfile(member, extracted)
                    else:
                        dest.addfile(member)
            with self.assertRaises(ValueError):
                backup_restore.restore(altered, root / "target")


if __name__ == "__main__":
    unittest.main()
