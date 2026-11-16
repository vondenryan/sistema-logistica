#!/usr/bin/env python3
"""Contract smoke test against a running isolated backend; creates one order."""
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path


def load_env():
    path = Path(__file__).resolve().parent.parent / '.env'
    if path.exists():
        for line in path.read_text().splitlines():
            if line and not line.startswith('#') and '=' in line:
                key, value = line.split('=', 1)
                os.environ.setdefault(key, value)


def request(method, path, body=None, token=None):
    headers = {'Accept': 'application/json'}
    if body is not None:
        headers['Content-Type'] = 'application/json'
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE + path, data=json.dumps(body).encode() if body is not None else None,
                                 headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            status, data = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, data = error.code, error.read()
    try:
        payload = json.loads(data) if data else {}
    except (ValueError, UnicodeDecodeError):
        raise RuntimeError(f'{method} {path}: invalid JSON response') from None
    return status, payload


def expect(method, path, expected, body=None, token=None):
    status, payload = request(method, path, body, token)
    if status != expected:
        raise RuntimeError(f'{method} {path}: expected HTTP {expected}, got {status}')
    return payload


def login(email, password):
    result = expect('POST', '/auth/login', 200, {'email': email, 'password': password})
    if not result.get('accessToken'):
        raise RuntimeError('Login response missing accessToken')
    return result['accessToken'], result.get('actor', {})


load_env()
BASE = os.environ.get('SMOKE_BASE_URL', 'http://localhost:8080/api/v1').rstrip('/')
try:
    password = os.environ.get('DEMO_PASSWORD')
    if not password:
        raise RuntimeError('Set DEMO_PASSWORD or generate .env')
    token, actor = login(os.environ.get('SMOKE_EMAIL', 'store@example.invalid'), password)
    store_id = os.environ.get('SMOKE_STORE_ID', actor.get('storeId') or '22222222-2222-4222-8222-222222222222')
    origin_id = os.environ.get('SMOKE_ORIGIN_ID', '11111111-1111-4111-8111-111111111111')
    body = {'storeId': store_id, 'originId': origin_id,
            'destination': {'line1': 'Rua Destino, 200', 'line2': None, 'city': 'Cidade Fictícia',
                            'state': 'SC', 'postalCode': '00000-000', 'country': 'BR',
                            'latitude': -26.49, 'longitude': -49.08},
            'recipientName': 'Cliente Fictício', 'recipientEmail': 'cliente@example.invalid',
            'items': [{'description': 'Produto fictício', 'quantity': 2, 'unitWeightKg': '20.000'}]}
    order = expect('POST', '/orders', 201, body, token)
    order_id = order.get('orderId')
    if not order_id or order.get('status') != 'DRAFT':
        raise RuntimeError('Create response missing orderId or DRAFT status')
    path = '/orders/' + order_id
    fetched = expect('GET', path, 200, token=token)
    if fetched.get('orderId') != order_id:
        raise RuntimeError('GET returned a different order')
    for _ in range(2):
        confirmed = expect('POST', path + '/confirm', 200, {}, token)
        if confirmed.get('orderId') != order_id or confirmed.get('status') != 'CONFIRMED':
            raise RuntimeError('Confirmation returned an unexpected order or status')
    print('PASS: login, create, get and repeat confirmation')
    other_email = os.environ.get('SMOKE_OTHER_STORE_EMAIL')
    if other_email:
        other_token, other_actor = login(other_email, os.environ.get('SMOKE_OTHER_STORE_PASSWORD', password))
        if other_actor.get('role') != 'STORE' or not other_actor.get('storeId') or other_actor['storeId'] == store_id:
            raise RuntimeError('Cross-store actor must be a STORE belonging to a different store')
        expect('GET', path, 404, token=other_token)
        print('PASS: cross-store GET hidden with 404')
    else:
        print('SKIP: cross-store check (set SMOKE_OTHER_STORE_EMAIL and optionally SMOKE_OTHER_STORE_PASSWORD)')
except (RuntimeError, OSError, ValueError) as error:
    print(f'SMOKE FAILED: {error}', file=sys.stderr)
    sys.exit(1)
