"""Verified backup and non-overwriting restore for the pilot data directory."""
from __future__ import annotations

import argparse
from contextlib import closing
import hashlib
import json
import os
from pathlib import Path
import sqlite3
import tempfile
import zipfile


def sha256(file: Path) -> str:
    digest = hashlib.sha256()
    with file.open("rb") as stream:
        for block in iter(lambda: stream.read(65536), b""):
            digest.update(block)
    return digest.hexdigest()


def backup(data_dir: Path, archive: Path) -> dict:
    data_dir = data_dir.resolve()
    archive = archive.resolve()
    if archive.exists() or not (data_dir / "hotel.sqlite3").is_file():
        raise ValueError("backup target must be new and source database must exist")
    archive.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=archive.parent) as temp:
        snapshot = Path(temp) / "hotel.sqlite3"
        with closing(sqlite3.connect(data_dir / "hotel.sqlite3")) as source, \
                closing(sqlite3.connect(snapshot)) as target:
            source.backup(target)
        files = {"hotel.sqlite3": snapshot}
        for file in (data_dir / "media").rglob("*") if (data_dir / "media").exists() else []:
            if file.is_symlink():
                raise ValueError("symlink in media directory")
            if file.is_file():
                files[file.relative_to(data_dir).as_posix()] = file
        records = {name: {"sha256": sha256(file), "size": file.stat().st_size}
                   for name, file in files.items()}
        partial = Path(temp) / "backup.zip"
        with zipfile.ZipFile(partial, "w", compression=zipfile.ZIP_DEFLATED) as zipout:
            for name, file in files.items():
                zipout.write(file, name)
            zipout.writestr("backup-manifest.json", json.dumps(records, sort_keys=True))
        os.replace(partial, archive)
        return records


def restore(archive: Path, destination: Path) -> dict:
    archive = archive.resolve()
    destination = destination.resolve()
    if destination.exists():
        raise ValueError("restore destination must not exist")
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=destination.parent) as temp:
        staged = Path(temp) / "data"
        staged.mkdir()
        with zipfile.ZipFile(archive) as zipin:
            if zipin.getinfo("backup-manifest.json").file_size > 1024 * 1024:
                raise ValueError("backup manifest too large")
            records = json.loads(zipin.read("backup-manifest.json"))
            if not isinstance(records, dict) or "hotel.sqlite3" not in records or \
                    len(zipin.namelist()) != len(records) + 1 or \
                    set(zipin.namelist()) != set(records) | {"backup-manifest.json"}:
                raise ValueError("invalid backup manifest")
            if sum(zipin.getinfo(name).file_size for name in records) > 2 * 1024 * 1024 * 1024:
                raise ValueError("backup exceeds pilot size limit")
            for name, metadata in records.items():
                parts = Path(name).parts
                if not parts or name.startswith("/") or ".." in parts or ":" in name or \
                        (name != "hotel.sqlite3" and (len(parts) != 2 or parts[0] != "media")):
                    raise ValueError("unsafe backup path")
                target = staged / name
                target.parent.mkdir(parents=True, exist_ok=True)
                digest = hashlib.sha256()
                size = 0
                with zipin.open(name) as source, target.open("wb") as output:
                    for block in iter(lambda: source.read(65536), b""):
                        size += len(block)
                        if size > metadata["size"]:
                            raise ValueError("backup size mismatch")
                        digest.update(block)
                        output.write(block)
                if size != metadata["size"] or digest.hexdigest() != metadata["sha256"]:
                    raise ValueError("backup hash mismatch")
        with closing(sqlite3.connect(f"file:{staged / 'hotel.sqlite3'}?mode=ro", uri=True)) as db:
            if db.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
                raise ValueError("backup database failed integrity check")
        os.replace(staged, destination)
        return records


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("backup", "restore"))
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    records = backup(args.source, args.destination) if args.action == "backup" else \
        restore(args.source, args.destination)
    print(f"{args.action}: verified {len(records)} files")


if __name__ == "__main__":
    main()
