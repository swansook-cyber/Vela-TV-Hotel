"""pywin32 service entrypoint. Install only after pilot deployment approval."""
from __future__ import annotations

import os
from pathlib import Path

import win32event
import win32service
import win32serviceutil

from pilot_runtime import build_server


class VelaHotelPilotService(win32serviceutil.ServiceFramework):
    _svc_name_ = "VelaTVHotelPilot"
    _svc_display_name_ = "Vela TV Hotel Pilot API"
    _svc_description_ = "Loopback hotel CMS and TV content API; HTTPS terminates at the pilot proxy."

    def __init__(self, args):
        super().__init__(args)
        self.stop_event = win32event.CreateEvent(None, 0, 0, None)
        self.server = None

    def SvcStop(self):
        self.ReportServiceStatus(win32service.SERVICE_STOP_PENDING)
        if self.server is not None:
            self.server.shutdown()
        win32event.SetEvent(self.stop_event)

    def SvcDoRun(self):
        config = Path(os.environ.get("HOTEL_CONFIG_FILE",
                                     r"C:\ProgramData\VelaTVHotel\pilot.env"))
        self.server = build_server(config)
        try:
            self.server.serve_forever(poll_interval=0.5)
        finally:
            self.server.server_close()


if __name__ == "__main__":
    win32serviceutil.HandleCommandLine(VelaHotelPilotService)
