"""Stage pywin32's service host inside the dedicated Windows venv.

This never registers a service or modifies the base Python installation.
"""
from __future__ import annotations

import shutil
import sys
from pathlib import Path


def prepare(venv: Path, base: Path, backend: Path, version: str) -> list[Path]:
    scripts = venv / "Scripts"
    packages = venv / "Lib" / "site-packages"
    if not (venv / "pyvenv.cfg").is_file() or not scripts.is_dir() or not packages.is_dir():
        raise ValueError("dedicated virtual environment required")
    if backend.resolve() == packages.resolve() or not (backend / "windows_service.py").is_file():
        raise ValueError("installed service source required")
    sources = {
        "pythonservice.exe": packages / "win32" / "pythonservice.exe",
        f"pywintypes{version}.dll": packages / "pywin32_system32" / f"pywintypes{version}.dll",
        f"python{version}.dll": base / f"python{version}.dll",
    }
    optional = base / "python3.dll"
    if optional.is_file():
        sources[optional.name] = optional
    if any(not source.is_file() for source in sources.values()):
        raise ValueError("pywin32 host or base Python DLL missing")
    copied = []
    for name, source in sources.items():
        target = scripts / name
        if target.exists():
            raise ValueError(f"service host target already exists: {name}")
        shutil.copy2(source, target)
        copied.append(target)
    (packages / "vela_hotel_pilot.pth").write_text(str(backend.resolve()) + "\n", encoding="utf-8")
    return copied


def main() -> None:
    if sys.platform != "win32" or sys.prefix == sys.base_prefix or sys.version_info[:2] != (3, 12):
        raise SystemExit("Windows Python 3.12 virtual environment required")
    venv = Path(sys.prefix)
    backend = Path(__file__).resolve().parent
    copied = prepare(venv, Path(sys.base_prefix), backend, "312")
    # Verify the host and DLLs are present; SCM startup remains a pilot-machine gate.
    import servicemanager  # noqa: F401 - verifies pywin32 is importable in this venv
    print("Staged venv service host:", ", ".join(path.name for path in copied))


if __name__ == "__main__":
    main()
