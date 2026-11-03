"""Windows pilot launcher; always binds HTTP only on loopback behind HTTPS proxy."""
from __future__ import annotations

import argparse
import logging
from logging.handlers import RotatingFileHandler
import os
from pathlib import Path
import re
from http.server import ThreadingHTTPServer

ALLOWED = {"HOTEL_PROFILE", "HOTEL_DATA_DIR", "HOTEL_INITIAL_CONTENT",
           "HOTEL_PUBLIC_MEDIA_HOST", "HOTEL_EDITOR_TOKEN", "HOTEL_PUBLISHER_TOKEN",
           "HOTEL_BIND_PORT"}


def valid_public_host(host: str) -> bool:
    """Require a real DNS hostname, never an example, local name or IP literal."""
    host = host.lower()
    if len(host) > 253 or host.endswith((".example", ".test", ".invalid", ".localhost",
                                       ".local", ".example.com", ".example.net", ".example.org")):
        return False
    if host in {"example.com", "example.net", "example.org", "localhost"}:
        return False
    labels = host.split(".")
    return len(labels) >= 2 and all(
        len(label) <= 63 and re.fullmatch(r"[a-z0-9](?:[a-z0-9-]*[a-z0-9])?", label)
        for label in labels
    ) and not all(label.isdigit() for label in labels)


def read_config(file: Path, seed_override: Path | None = None) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in file.read_text(encoding="utf-8-sig").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if not separator or key not in ALLOWED or key in values or not value or "\x00" in value:
            raise ValueError("invalid pilot configuration line")
        values[key] = value
    required = ALLOWED - {"HOTEL_BIND_PORT"}
    if not required.issubset(values):
        raise ValueError("missing pilot configuration")
    if values["HOTEL_PROFILE"] not in ("development", "pilot"):
        raise ValueError("this launcher cannot run production")
    data = Path(values["HOTEL_DATA_DIR"])
    seed = seed_override or Path(values["HOTEL_INITIAL_CONTENT"])
    if not data.is_absolute() or not seed.is_absolute() or not seed.is_file():
        raise ValueError("absolute private data path and approved seed required")
    if not valid_public_host(values["HOTEL_PUBLIC_MEDIA_HOST"]):
        raise ValueError("public HTTPS hostname required")
    if min(len(values["HOTEL_EDITOR_TOKEN"]), len(values["HOTEL_PUBLISHER_TOKEN"])) < 32 or \
            values["HOTEL_EDITOR_TOKEN"] == values["HOTEL_PUBLISHER_TOKEN"] or \
            any(token.startswith("REPLACE_") for token in
                (values["HOTEL_EDITOR_TOKEN"], values["HOTEL_PUBLISHER_TOKEN"])):
        raise ValueError("separate strong admin tokens required")
    port = int(values.get("HOTEL_BIND_PORT", "8131"))
    if port not in range(1024, 65536):
        raise ValueError("invalid loopback port")
    return values


def build_server(config_file: Path, port_override: int | None = None) -> ThreadingHTTPServer:
    values = read_config(config_file)
    os.environ.update(values)
    import hotel_api  # Load after setting HOTEL_DATA_DIR and approved seed.
    hotel_api.ROOT = Path(values["HOTEL_DATA_DIR"])
    hotel_api.SAMPLE = Path(values["HOTEL_INITIAL_CONTENT"])
    store = hotel_api.Store()
    store.db.execute("SELECT 1").fetchone()
    store.db.close()
    log_file = Path(values["HOTEL_DATA_DIR"]) / "hotel-pilot.log"
    logger = logging.getLogger("hotel_api")
    logger.setLevel(logging.INFO)
    if not logger.handlers:
        handler = RotatingFileHandler(log_file, maxBytes=2 * 1024 * 1024,
                                      backupCount=5, encoding="utf-8")
        handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))
        logger.addHandler(handler)
    server = ThreadingHTTPServer(("127.0.0.1", port_override if port_override is not None
                                  else int(values.get("HOTEL_BIND_PORT", "8131"))), hotel_api.Handler)
    server.daemon_threads = True
    return server


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--check", action="store_true", help="validate configuration without starting")
    parser.add_argument("--seed-override", type=Path,
                        help="validate staged seed before installation; --check only")
    args = parser.parse_args()
    if args.seed_override and not args.check:
        parser.error("--seed-override requires --check")
    if args.check:
        read_config(args.config, args.seed_override)
        print("Pilot configuration valid")
        return
    server = build_server(args.config)
    try:
        server.serve_forever(poll_interval=0.5)
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
