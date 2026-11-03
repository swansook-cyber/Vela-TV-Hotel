"""Pure tests for Windows pilot packaging; no service or packages are installed."""
from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from pilot_runtime import read_config, valid_public_host
from prepare_windows_service import prepare


ROOT = Path(__file__).resolve().parent.parent


class InstallerHardeningTests(unittest.TestCase):
    def test_hostname_rejects_placeholders_and_non_dns_values(self):
        for host in ("hotel-tv.example.com", "hotel.example", "example.com",
                     "localhost", "hotel.local", "192.168.15.131", "https://hotel.velalab.net",
                     "bad..velalab.net", "-bad.velalab.net", "hotel.velalab.net:443"):
            with self.subTest(host=host):
                self.assertFalse(valid_public_host(host))
        self.assertTrue(valid_public_host("hotel.velalab.net"))

    def test_config_override_validates_before_seed_is_staged(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            seed = root / "approved.json"
            seed.write_text("{}", encoding="utf-8")
            config = root / "pilot.env"
            lines = ["HOTEL_PROFILE=pilot", f"HOTEL_DATA_DIR={root / 'data'}",
                     f"HOTEL_INITIAL_CONTENT={root / 'not-staged.json'}",
                     "HOTEL_PUBLIC_MEDIA_HOST=hotel.velalab.net",
                     "HOTEL_EDITOR_TOKEN=" + "e" * 40,
                     "HOTEL_PUBLISHER_TOKEN=" + "p" * 40, "HOTEL_BIND_PORT=8131"]
            config.write_text("\n".join(lines), encoding="utf-8")
            self.assertEqual(read_config(config, seed)["HOTEL_PROFILE"], "pilot")
            with self.assertRaises(ValueError):
                read_config(config)
            config.write_text("\n".join(lines).replace("hotel.velalab.net", "hotel-tv.example.com"),
                              encoding="utf-8")
            with self.assertRaises(ValueError):
                read_config(config, seed)

    def test_service_host_preparation_is_venv_local(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            venv, base, backend = (root / name for name in ("venv", "base", "backend"))
            (venv / "Scripts").mkdir(parents=True)
            (venv / "Lib" / "site-packages" / "win32").mkdir(parents=True)
            (venv / "Lib" / "site-packages" / "pywin32_system32").mkdir(parents=True)
            base.mkdir()
            backend.mkdir()
            (venv / "pyvenv.cfg").write_text("home = base")
            (backend / "windows_service.py").write_text("# test")
            (venv / "Lib" / "site-packages" / "win32" / "pythonservice.exe").write_bytes(b"exe")
            (venv / "Lib" / "site-packages" / "pywin32_system32" / "pywintypes312.dll").write_bytes(b"dll")
            (base / "python312.dll").write_bytes(b"python")
            copied = prepare(venv, base, backend, "312")
            self.assertEqual({p.name for p in copied},
                             {"pythonservice.exe", "pywintypes312.dll", "python312.dll"})
            self.assertTrue(all(p.parent == venv / "Scripts" for p in copied))
            self.assertEqual((venv / "Lib" / "site-packages" / "vela_hotel_pilot.pth")
                             .read_text(encoding="utf-8").strip(), str(backend.resolve()))
            with self.assertRaises(ValueError):
                prepare(venv, base, backend, "312")
            self.assertEqual((base / "python312.dll").read_bytes(), b"python")

    def test_scripts_keep_dependencies_inside_venv_and_preserve_data(self):
        install = (ROOT / "ops/windows/Install-Pilot.ps1").read_text(encoding="utf-8")
        service = (ROOT / "backend/windows_service.py").read_text(encoding="utf-8")
        self.assertIn("-m venv $venv", install)
        self.assertIn("$venvPython -m pip install", install)
        self.assertNotIn("pywin32_postinstall", install)
        self.assertIn("--username 'NT AUTHORITY\\LocalService'", install)
        self.assertIn("if (Test-Path -LiteralPath $data)", install)
        self.assertIn('"pythonservice.exe"', service)
        for script in ("Backup-Pilot.ps1", "Restore-Pilot.ps1", "Uninstall-Pilot.ps1"):
            self.assertIn('app\\.venv\\Scripts\\python.exe',
                          (ROOT / "ops/windows" / script).read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
