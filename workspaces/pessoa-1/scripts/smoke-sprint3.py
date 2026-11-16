#!/usr/bin/env python3
"""Offline fixture smoke against the verifier's local API; never reads .env."""
import importlib.util
import json
import os
from pathlib import Path
import sys
import urllib.error
import urllib.request

BASE = os.environ.get('SMOKE_BASE_URL', 'http://127.0.0.1:8080/api/v1').rstrip('/')
PASSWORD = os.environ.get('DEMO_PASSWORD', '')


def request(method, path, expected, body=None, token=None, code=None):
    headers = {'Accept': 'application/json'}
    if body is not None:
        headers['Content-Type'] = 'application/json'
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE + path, data=json.dumps(body).encode() if body is not None else None, headers=headers, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=5)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        status = response.status
        content_type = response.headers.get_content_type()
        data = response.read()
    if status != expected:
        raise RuntimeError(f'{method} {path}: expected HTTP {expected}, got {status}')
    try:
        result = json.loads(data)
    except (ValueError, UnicodeDecodeError):
        raise RuntimeError(f'{method} {path}: invalid JSON') from None
    if code is not None:
        if (content_type != 'application/problem+json' or not isinstance(result, dict)
                or result.get('status') != status or result.get('code') != code
                or not all(isinstance(result.get(k), str) and result[k] for k in ('type', 'title', 'detail'))
                or not isinstance(result.get('details'), list)):
            raise RuntimeError(f'{method} {path}: invalid ProblemDetail for {code}')
    return result


def run():
    if not PASSWORD:
        raise ValueError('DEMO_PASSWORD is required')
    request('GET', '/auth/me', 401, code='UNAUTHENTICATED')
    request('POST', '/auth/login', 401, {'email': 'admin@example.invalid', 'password': 'wrong'}, code='UNAUTHENTICATED')
    admin = request('POST', '/auth/login', 200, {'email': 'admin@example.invalid', 'password': PASSWORD})['accessToken']
    driver = request('POST', '/auth/login', 200, {'email': 'driver@example.invalid', 'password': PASSWORD})['accessToken']
    request('POST', '/routes/suggest', 403, {'originId': '11111111-1111-4111-8111-111111111111', 'orderIds': []}, driver, 'FORBIDDEN')
    request('POST', '/routes/suggest', 400, {'originId': '11111111-1111-4111-8111-111111111111', 'orderIds': []}, admin, 'VALIDATION_ERROR')
    # Reuse the Sprint 2 workflow without its .env loader or any network defaults.
    path = Path(__file__).with_name('smoke-sprint2.py')
    spec = importlib.util.spec_from_file_location('smoke_sprint2', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.load_env = lambda _path: None
    module.run()
    print('PASS: negative authentication, authorization and ProblemDetail validation')


if __name__ == '__main__':
    try:
        run()
    except (RuntimeError, OSError, ValueError, KeyError) as error:
        print(f'SMOKE FAILED: {error}', file=sys.stderr)
        sys.exit(1)
