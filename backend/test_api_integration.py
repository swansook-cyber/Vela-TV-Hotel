import json
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer
from pathlib import Path
from unittest.mock import patch

import hotel_api


class ApiIntegrationTests(unittest.TestCase):
    def test_enrollment_manifest_and_revocation(self):
        with tempfile.TemporaryDirectory() as temp, patch.object(hotel_api, "ROOT", Path(temp)):
            store = hotel_api.Store()
            store.save_draft(store.draft())
            store.publish()
            code = store.create_code("Test TV")
            store.db.close()
            server = ThreadingHTTPServer(("127.0.0.1", 0), hotel_api.Handler)
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            base = f"http://127.0.0.1:{server.server_port}"

            def request(path, method="GET", data=None, token=None):
                headers = {"Content-Type": "application/json"}
                if token:
                    headers["Authorization"] = "Bearer " + token
                payload = json.dumps(data).encode() if data is not None else None
                return urllib.request.urlopen(
                    urllib.request.Request(base + path, payload, headers, method=method))

            try:
                with self.assertRaises(urllib.error.HTTPError) as error:
                    request("/api/v1/device/manifest")
                self.assertEqual(error.exception.code, 401)
                enrolled = json.load(request("/api/v1/device/enroll", "POST", {"code": code}))
                self.assertEqual(json.load(request("/api/v1/device/manifest",
                                                  token=enrolled["token"]))["revision"], "1")
                with self.assertRaises(urllib.error.HTTPError) as error:
                    request("/api/v1/device/enroll", "POST", {"code": code})
                self.assertEqual(error.exception.code, 403)
                store = hotel_api.Store()
                store.db.execute("UPDATE devices SET revoked=1 WHERE id=?",
                                 (enrolled["device_id"],))
                store.db.commit()
                store.db.close()
                with self.assertRaises(urllib.error.HTTPError) as error:
                    request("/api/v1/device/manifest", token=enrolled["token"])
                self.assertEqual(error.exception.code, 401)
            finally:
                server.shutdown()
                server.server_close()
                thread.join(timeout=2)


if __name__ == "__main__":
    unittest.main()
