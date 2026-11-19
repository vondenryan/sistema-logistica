#!/usr/bin/env python3
"""Run the Sprint 2 workflow against a running local API; creates two orders."""
import json
import os
import re
import uuid
from pathlib import Path
import shlex
import sys
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parent.parent


def load_env(path):
    if not path.exists():
        return
    for number, line in enumerate(path.read_text().splitlines(), 1):
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        if line.startswith('export '):
            line = line[7:].strip()
        key, sep, value = line.partition('=')
        if not sep or not key.isidentifier():
            raise ValueError(f'Invalid .env assignment on line {number}')
        words = shlex.split(value, comments=True, posix=True)
        if len(words) > 1:
            raise ValueError(f'Invalid .env value on line {number}')
        os.environ.setdefault(key, words[0] if words else '')


def run():
    load_env(ROOT / '.env')
    password = os.environ.get('DEMO_PASSWORD')
    if not password:
        raise ValueError('DEMO_PASSWORD is required in the environment or local .env')
    base = os.environ.get('SMOKE_BASE_URL', 'http://localhost:8080/api/v1').rstrip('/')
    def request(method, path, expected, body=None, token=None):
        headers = {'Accept': 'application/json'}
        if body is not None:
            headers['Content-Type'] = 'application/json'
        if token:
            headers['Authorization'] = 'Bearer ' + token
        req = urllib.request.Request(base + path, data=json.dumps(body).encode() if body is not None else None, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=15) as response:
                status, data = response.status, response.read()
        except urllib.error.HTTPError as error:
            raise RuntimeError(f'{method} {path}: expected HTTP {expected}, got {error.code}') from None
        if status != expected:
            raise RuntimeError(f'{method} {path}: expected HTTP {expected}, got {status}')
        try:
            return json.loads(data)
        except (ValueError, UnicodeDecodeError):
            raise RuntimeError(f'{method} {path}: invalid JSON') from None
    def check(condition, message):
        if not condition:
            raise RuntimeError(message)
    def valid_uuid(value):
        try:
            return isinstance(value, str) and str(uuid.UUID(value)) == value
        except (ValueError, AttributeError):
            return False

    def valid_quote(quote, oid):
        if not isinstance(quote, dict):
            return False
        patterns = {'distanceKm': r'(0|[1-9][0-9]*)(\.[0-9]{1,3})?',
                    'tariffPerKm': r'(0|[1-9][0-9]*)\.[0-9]{2}',
                    'amount': r'(0|[1-9][0-9]*)\.[0-9]{2}'}
        return (valid_uuid(quote.get('quoteId')) and quote.get('orderId') == oid
                and quote.get('currency') == 'BRL'
                and isinstance(quote.get('source'), str) and bool(quote['source'])
                and isinstance(quote.get('calculatedAt'), str)
                and bool(re.fullmatch(r'\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z', quote['calculatedAt']))
                and all(isinstance(quote.get(k), str) and re.fullmatch(pattern, quote[k])
                        for k, pattern in patterns.items()))

    login = request('POST', '/auth/login', 200, {'email': os.environ.get('SMOKE_EMAIL', 'admin@example.invalid'), 'password': password})
    token = login.get('accessToken')
    check(token and login.get('actor', {}).get('role') == 'ADMIN', 'Login must return an ADMIN token')
    store = os.environ.get('SMOKE_STORE_ID', '22222222-2222-4222-8222-222222222222')
    origin = os.environ.get('SMOKE_ORIGIN_ID', '11111111-1111-4111-8111-111111111111')
    order_ids = []
    for i in range(2):
        body = {'storeId': store, 'originId': origin, 'destination': {'line1': f'Rua Destino, {200+i}', 'line2': None, 'city': 'Cidade Fictícia', 'state': 'SC', 'postalCode': '00000-000', 'country': 'BR', 'latitude': -26.49-i*0.01, 'longitude': -49.08}, 'recipientName': 'Cliente Fictício', 'recipientEmail': 'cliente@example.invalid', 'items': [{'description': 'Produto fictício', 'quantity': 2, 'unitWeightKg': '20.000'}]}
        order = request('POST', '/orders', 201, body, token)
        oid = order.get('orderId')
        check(valid_uuid(oid) and order.get('status') == 'DRAFT' and oid not in order_ids, 'Invalid new order')
        order_ids.append(oid)
        for _ in range(2):
            confirmed = request('POST', f'/orders/{oid}/confirm', 200, {}, token)
            check(confirmed.get('orderId') == oid and confirmed.get('status') == 'CONFIRMED', 'Confirmation is not idempotent')
        quote = request('POST', f'/orders/{oid}/freight-quote', 200, {}, token)
        check(valid_quote(quote, oid), 'Invalid freight quote')
        repeated_quote = request('POST', f'/orders/{oid}/freight-quote', 200, {}, token)
        check(valid_quote(repeated_quote, oid) and repeated_quote['quoteId'] == quote['quoteId'], 'Repeated quote changed quoteId or returned invalid data')
    suggestion = request('POST', '/routes/suggest', 200, {'originId': origin, 'orderIds': order_ids}, token)
    suggested = suggestion.get('orderedOrderIds')
    check(suggestion.get('originId') == origin and isinstance(suggested, list) and len(suggested) == 2 and set(suggested) == set(order_ids), 'Invalid route suggestion')
    route = request('POST', '/routes', 201, {'originId': origin, 'orderIds': suggested}, token)
    rid = route.get('routeId')
    check(valid_uuid(rid) and route.get('status') == 'PLANNED' and route.get('orderIds') == suggested, 'Invalid persisted route')
    reordered = list(reversed(suggested))
    route = request('PUT', f'/routes/{rid}/stops', 200, {'orderedOrderIds': reordered}, token)
    check(route.get('routeId') == rid and route.get('orderIds') == reordered, 'Reorder was not persisted')
    assigned = request('POST', f'/routes/{rid}/assign', 200, {}, token)
    check(assigned.get('status') == 'ASSIGNED' and valid_uuid(assigned.get('driverId')) and valid_uuid(assigned.get('vehicleId')), 'Route assignment missing resources')
    again = request('POST', f'/routes/{rid}/assign', 200, {}, token)
    check(all(again.get(k) == assigned.get(k) for k in ('routeId', 'status', 'driverId', 'vehicleId', 'orderIds')), 'Repeated assignment changed the route')
    started = request('POST', f'/routes/{rid}/start', 200, {}, token)
    fetched = request('GET', f'/routes/{rid}', 200, token=token)
    for result in (started, fetched):
        check(result.get('routeId') == rid and result.get('originId') == origin and result.get('storeId') == store and result.get('status') == 'IN_PROGRESS' and result.get('orderIds') == reordered and result.get('driverId') == assigned['driverId'] and result.get('vehicleId') == assigned['vehicleId'] and result.get('startedAt'), 'Started route invariants failed')
    print('PASS: two orders, confirmation, quotes, suggestion, reorder, repeated assignment and start')


if __name__ == '__main__':
    try:
        run()
    except (RuntimeError, OSError, ValueError) as error:
        print(f'SMOKE FAILED: {error}', file=sys.stderr)
        sys.exit(1)
