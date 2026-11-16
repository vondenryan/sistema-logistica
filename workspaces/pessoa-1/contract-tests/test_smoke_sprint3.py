"""Smoke-client unit tests against a local HTTP stub, not the real backend."""
import importlib.util
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import threading
import unittest

path = Path(__file__).resolve().parents[1] / 'scripts' / 'smoke-sprint3.py'
spec = importlib.util.spec_from_file_location('smoke3', path)
smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        status = 401
        data = {'type': 'https://example.invalid/problems/unauthenticated', 'title': 'Unauthenticated', 'status': status, 'detail': 'Authentication required', 'code': 'UNAUTHENTICATED', 'details': []}
        if self.path == '/bad':
            del data['details']
        self.send_response(status)
        self.send_header('Content-Type', 'application/problem+json')
        self.end_headers()
        self.wfile.write(json.dumps(data).encode())

    def log_message(self, *_args):
        pass


class SmokeClientTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.previous = smoke.BASE
        smoke.BASE = 'http://127.0.0.1:%d' % cls.server.server_port

    @classmethod
    def tearDownClass(cls):
        smoke.BASE = cls.previous
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join()

    def test_valid_problem_is_accepted(self):
        self.assertEqual('UNAUTHENTICATED', smoke.request('GET', '/good', 401, code='UNAUTHENTICATED')['code'])

    def test_missing_details_is_rejected(self):
        with self.assertRaisesRegex(RuntimeError, 'invalid ProblemDetail'):
            smoke.request('GET', '/bad', 401, code='UNAUTHENTICATED')

    def test_wrong_status_is_rejected(self):
        with self.assertRaisesRegex(RuntimeError, 'expected HTTP 403, got 401'):
            smoke.request('GET', '/good', 403)


if __name__ == '__main__':
    unittest.main()
