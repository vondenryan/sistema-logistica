"""Serve the existing OSRM fixture only during integration verification."""
import runpy
from http.server import ThreadingHTTPServer
from pathlib import Path

root = Path(__file__).resolve().parents[1]
fixture = runpy.run_path(str(root / "workspaces/pessoa-3/scripts/smoke-integration.py"))
with ThreadingHTTPServer(("0.0.0.0", 19090), fixture["OsrmFixture"]) as server:
    server.serve_forever()
