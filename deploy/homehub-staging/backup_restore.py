"""Offline-capable staging data backup and restore into an empty directory.

The running SQLite database is copied via the SQLite backup API. Restore never
overwrites a directory; the operator must stop the service and move old data aside.
"""
import argparse
from contextlib import closing
import hashlib
import json
import os
from pathlib import Path
import shutil
import sqlite3
import tarfile
import tempfile


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def backup(source, archive):
    source, archive = Path(source).resolve(), Path(archive).resolve()
    if archive.exists():
        raise ValueError("Backup archive already exists")
    if not (source / "hotel.sqlite3").is_file():
        raise ValueError("Source hotel.sqlite3 missing")
    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        with closing(sqlite3.connect(source / "hotel.sqlite3")) as src, closing(sqlite3.connect(root / "hotel.sqlite3")) as dst:
            src.backup(dst)
        if (source / "media").exists():
            if any(path.is_symlink() for path in (source / "media").rglob("*")):
                raise ValueError("Media directory contains a symlink")
            shutil.copytree(source / "media", root / "media", symlinks=False)
        hashes = {str(p.relative_to(root)): digest(p) for p in root.rglob("*") if p.is_file()}
        (root / "manifest.json").write_text(json.dumps(hashes, sort_keys=True), encoding="utf-8")
        archive.parent.mkdir(parents=True, exist_ok=True)
        with tarfile.open(archive, "x:gz") as tar:
            for path in root.rglob("*"):
                tar.add(path, arcname=str(path.relative_to(root)), recursive=False)
    os.chmod(archive, 0o600)
    print(f"Backup: {archive} SHA-256 {digest(archive)}")


def restore(archive, destination):
    archive, destination = Path(archive).resolve(), Path(destination).resolve()
    if destination.exists():
        raise ValueError("Destination already exists; move existing data aside first")
    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        with tarfile.open(archive, "r:gz") as tar:
            for member in tar.getmembers():
                target = (root / member.name).resolve()
                if not target.is_relative_to(root) or not (member.isfile() or member.isdir()):
                    raise ValueError("Unsafe backup entry")
            tar.extractall(root)
        hashes = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
        for name, expected in hashes.items():
            path = (root / name).resolve()
            if not path.is_relative_to(root) or not path.is_file() or digest(path) != expected:
                raise ValueError(f"Backup integrity failure: {name}")
        if set(hashes) != {str(p.relative_to(root)) for p in root.rglob("*") if p.is_file() and p.name != "manifest.json"}:
            raise ValueError("Backup file list mismatch")
        with closing(sqlite3.connect(root / "hotel.sqlite3")) as db:
            if db.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
                raise ValueError("Backup database integrity failure")
        shutil.copytree(root, destination, ignore=shutil.ignore_patterns("manifest.json"))
    for path in destination.rglob("*"):
        if path.is_dir():
            path.chmod(0o700)
        else:
            path.chmod(0o600)
    destination.chmod(0o700)
    print(f"Restored into new directory: {destination}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("backup", "restore"))
    parser.add_argument("source")
    parser.add_argument("destination")
    args = parser.parse_args()
    (backup if args.action == "backup" else restore)(args.source, args.destination)
