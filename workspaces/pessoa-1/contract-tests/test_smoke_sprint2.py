"""Offline protocol stub tests the smoke client, not a real integration."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import subprocess
import sys
import threading
import unittest

from test_contracts import validator

SCRIPT = Path(__file__).resolve().parents[1] / 'scripts/smoke-sprint2.py'
ORDERS = ['44444444-4444-4444-8444-444444444444', '44444444-4444-4444-8444-444444444445']
QUOTES = ['88888888-8888-4888-8888-888888888888', '88888888-8888-4888-8888-888888888889']
ROUTE = '33333333-3333-4333-8333-333333333333'
DRIVER = '55555555-5555-4555-8555-555555555555'
VEHICLE = '66666666-6666-4666-8666-666666666666'


class Fake(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def answer(self, code, data, schema=None):
        if schema and not self.server.bad:
            validator(schema).validate(data)
        data = json.dumps(data).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get('Content-Length', 0))) or b'{}')
        path = self.path.removeprefix('/api/v1')
        if path == '/auth/login':
            return self.answer(200, {'accessToken': 'fake', 'actor': {'role': 'ADMIN'}})
        if path == '/orders':
            oid = ORDERS[len(self.server.orders)]
            self.server.orders.append(oid)
            return self.answer(201, {'orderId': oid, 'status': 'DRAFT'})
        if path.endswith('/confirm'):
            return self.answer(200, {'orderId': path.split('/')[2], 'status': 'CONFIRMED'})
        if path.endswith('/freight-quote'):
            oid = path.split('/')[2]
            self.server.quote_calls[oid] = self.server.quote_calls.get(oid, 0) + 1
            quote = {'quoteId': QUOTES[ORDERS.index(oid)], 'orderId': oid, 'currency': 'BRL',
                     'distanceKm': '12.000', 'tariffPerKm': '2.50', 'amount': '30.00',
                     'calculatedAt': '2026-11-14T12:00:00.000Z', 'source': 'FAKE'}
            if self.server.bad == 'quote-id' and self.server.quote_calls[oid] == 2:
                quote['quoteId'] = QUOTES[1]
            if self.server.bad == 'quote-amount':
                quote['amount'] = 30.0
            return self.answer(200, quote, 'freight-quote')
        if path == '/routes/suggest':
            return self.answer(200, {'originId': body['originId'], 'orderedOrderIds': body['orderIds'],
                                     'estimatedDistanceKm': '12.000'}, 'route-suggestion')
        if path == '/routes':
            self.server.route = {'routeId': ROUTE, 'storeId': '22222222-2222-4222-8222-222222222222',
                                 'originId': body['originId'], 'orderIds': body['orderIds'], 'status': 'PLANNED',
                                 'driverId': None, 'vehicleId': None, 'startedAt': None}
            return self.answer(201, self.server.route, 'route-snapshot')
        if path.endswith('/assign'):
            self.server.assign_calls += 1
            self.server.route.update(status='ASSIGNED', driverId=DRIVER, vehicleId=VEHICLE)
            result = dict(self.server.route)
            if self.server.bad == 'assignment' and self.server.assign_calls == 2:
                result['vehicleId'] = '66666666-6666-4666-8666-666666666667'
            return self.answer(200, result, 'route-snapshot')
        if path.endswith('/start'):
            self.server.route.update(status='IN_PROGRESS', startedAt='2026-11-14T12:00:00.000Z')
            return self.answer(200, self.server.route, 'route-snapshot')
        self.answer(404, {})

    def do_PUT(self):
        body = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        self.server.route['orderIds'] = body['orderedOrderIds']
        self.answer(200, self.server.route, 'route-snapshot')

    def do_GET(self):
        self.answer(200, self.server.route, 'route-snapshot')


class SmokeStubTest(unittest.TestCase):
    def run_stub(self, bad=None):
        server = ThreadingHTTPServer(('127.0.0.1', 0), Fake)
        server.orders = []
        server.quote_calls = {}
        server.assign_calls = 0
        server.bad = bad
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            env = os.environ.copy()
            env.update(DEMO_PASSWORD='test-only', SMOKE_BASE_URL=f'http://127.0.0.1:{server.server_port}/api/v1',
                       SMOKE_EMAIL='admin@example.invalid', SMOKE_STORE_ID='22222222-2222-4222-8222-222222222222',
                       SMOKE_ORIGIN_ID='11111111-1111-4111-8111-111111111111')
            result = subprocess.run([sys.executable, str(SCRIPT)], env=env, capture_output=True, text=True, timeout=20)
            return result, server
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    def test_smoke_against_protocol_stub(self):
        result, server = self.run_stub()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(server.route['orderIds'], list(reversed(server.orders)))
        self.assertEqual(server.route['status'], 'IN_PROGRESS')
        self.assertEqual(server.quote_calls, {oid: 2 for oid in ORDERS})

    def test_bad_responses_fail(self):
        for bad, message in [('quote-id', 'Repeated quote'), ('quote-amount', 'Invalid freight quote'),
                             ('assignment', 'Repeated assignment')]:
            with self.subTest(bad=bad):
                result, _ = self.run_stub(bad)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(message, result.stderr)


if __name__ == '__main__':
    unittest.main()
