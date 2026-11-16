#!/usr/bin/env python3
"""Check P1 packaging with dev fixtures; NOT real P2/P3 integration readiness."""
import ipaddress
import json
import os
from pathlib import Path
import secrets
import subprocess
import time

ROOT = Path(__file__).resolve().parent.parent
BACKEND_IMAGE = 'sistema-logistica-p1-backend:sprint4-check'
FRONTEND_IMAGE = 'sistema-logistica-p1-frontend:sprint4-check'


def docker(*arguments, env=None):
    return subprocess.check_output(
        ['docker', *arguments], text=True, env=env, stderr=subprocess.PIPE
    ).strip()


def main():
    containers = []
    network = None
    password = secrets.token_hex(24)
    demo = secrets.token_hex(24)

    def start(image, alias, variables=None, address=None):
        env = dict(os.environ)
        env.update(variables or {})
        args = ['create', '--pull', 'never', '--network', network,
                '--network-alias', alias]
        if address:
            args.extend(['--ip', address])
        for name in variables or {}:
            args.extend(['-e', name])
        container = docker(*args, image, env=env)
        containers.append(container)
        docker('start', container)
        return container

    try:
        for image in (BACKEND_IMAGE, FRONTEND_IMAGE, 'postgres:16',
                      'axllent/mailpit:v1.27.8', 'python:3.12-slim'):
            docker('image', 'inspect', image)
        print('P1 component checks use dev/fakes; integration with P2/P3 is NOT tested.', flush=True)
        network = docker('network', 'create', '--internal',
                         'p4-components-' + secrets.token_hex(8))
        network_info = json.loads(docker('network', 'inspect', network))[0]
        subnet = next(ipaddress.ip_network(c['Subnet']) for c in network_info['IPAM']['Config'] if ipaddress.ip_network(c['Subnet']).version == 4)
        gateway_ip = str(subnet.network_address + 10)
        first_client_ip = str(subnet.network_address + 20)
        second_client_ip = str(subnet.network_address + 21)
        postgres = start('postgres:16', 'postgres', {
            'POSTGRES_USER': 'logistics', 'POSTGRES_DB': 'logistics',
            'POSTGRES_PASSWORD': password})
        deadline = time.monotonic() + 40
        while True:
            ready = subprocess.run(
                ['docker', 'exec', postgres, 'pg_isready', '-q', '-h',
                 '127.0.0.1', '-U', 'logistics', '-d', 'logistics'],
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if ready.returncode == 0:
                break
            if time.monotonic() > deadline:
                raise RuntimeError('Private PostgreSQL readiness timed out')
            time.sleep(.25)
        backend = start(BACKEND_IMAGE, 'backend', {
            'DB_URL': 'jdbc:postgresql://postgres:5432/logistics',
            'DB_USER': 'logistics', 'DB_PASSWORD': password,
            'JWT_SECRET': secrets.token_hex(32), 'DEMO_PASSWORD': demo,
            'SPRING_PROFILES_ACTIVE': 'dev', 'ROUTING_PROVIDER': 'fake',
            'FREIGHT_TARIFF_PER_KM': '2.50',
            'LOGIN_TRUSTED_PROXY_IPS': gateway_ip,
            'SERVER_FORWARD_HEADERS_STRATEGY': 'none',
            'SERVER_TOMCAT_ACCESSLOG_ENABLED': 'true',
            'SERVER_TOMCAT_ACCESSLOG_DIRECTORY': '/tmp/gateway-access',
            'SERVER_TOMCAT_ACCESSLOG_PATTERN': '%a|%{X-Real-IP}i|%s',
            'SERVER_TOMCAT_ACCESSLOG_BUFFERED': 'false'})  # Automated test fixture only.
        frontend = start(FRONTEND_IMAGE, 'frontend', address=gateway_ip)
        actual_gateway = json.loads(docker('inspect', frontend))[0]['NetworkSettings']['Networks']
        assert {v['IPAddress'] for v in actual_gateway.values()} == {gateway_ip}
        start('axllent/mailpit:v1.27.8', 'mailpit')
        assert docker('inspect', '--format', '{{.Config.User}}', backend) == '10001:10001'
        env = dict(os.environ)
        env['DEMO_PASSWORD'] = demo
        # Docker may inject a proxy. These owned local aliases must use the internal network.
        env['NO_PROXY'] = env['no_proxy'] = 'frontend,mailpit,backend,postgres,localhost,127.0.0.1'
        def run_client(address, mode=None):
            client = docker(
                'create', '--pull', 'never', '--network', network, '--ip', address,
                '-e', 'DEMO_PASSWORD', '-e', 'NO_PROXY', '-e', 'no_proxy', '-e', 'TEST_BLOCKED_IP',
                '-v', str(ROOT / 'scripts/sprint4-component-client.py') + ':/checks/client.py:ro',
                '-v', str(ROOT / 'scripts/smoke-sprint2.py') + ':/checks/smoke-sprint2.py:ro',
                'python:3.12-slim', 'python3', '/checks/client.py', *([mode] if mode else []), env=env)
            containers.append(client)
            assigned = json.loads(docker('inspect', client))[0]['NetworkSettings']['Networks']
            assert {v['IPAMConfig']['IPv4Address'] for v in assigned.values()} == {address}
            result = subprocess.run(['docker', 'start', '-a', client], capture_output=True,
                                    text=True, timeout=80)
            if result.returncode != 0 or docker('inspect', '--format', '{{.State.ExitCode}}', client) != '0':
                raise RuntimeError('Internal client failed: ' + result.stderr + result.stdout)
            print(result.stdout, end='')
            docker('rm', '-fv', client)

        env['TEST_BLOCKED_IP'] = first_client_ip
        run_client(first_client_ip)
        run_client(first_client_ip, '--rate-abuse')
        run_client(second_client_ip, '--rate-control')
        run_client(first_client_ip, '--rate-direct')
        access = docker('exec', backend, 'sh', '-c', 'cat /tmp/gateway-access/*')
        lines = set(access.splitlines())
        assert f'{gateway_ip}|{first_client_ip}|429' in lines, 'Gateway must preserve client A source'
        assert f'{gateway_ip}|{second_client_ip}|200' in lines, 'Gateway must preserve distinct client B source'
        assert f'{first_client_ip}|198.51.100.88|429' in lines, 'Direct A socket must share its exhausted window'
        print('PASS: socket access logs confirm gateway peer, two distinct clients and direct client A')
        print('PASS: backend non-root; internal Docker network has external access disabled')
    finally:
        for container in reversed(containers):
            subprocess.run(['docker', 'rm', '-fv', container],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if network:
            subprocess.run(['docker', 'network', 'rm', network],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


if __name__ == '__main__':
    main()
