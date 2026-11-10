#!/usr/bin/env python3
"""Run from the developer host after Compose startup; optionally verify a second device probe."""
import argparse
import json
import socket
import urllib.error
import urllib.request


def get_json(url):
    with urllib.request.urlopen(url, timeout=4) as response:
        return response.status, json.load(response)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--nonloopback', required=True, help='This Docker host LAN address as reached from a different device')
    parser.add_argument('--remote-only', action='store_true', help='Run this part from a different LAN device')
    args = parser.parse_args()
    if not args.remote_only:
        status, body = get_json('http://127.0.0.1:8080/api/health')
        assert status == 200 and body == {'success': True, 'meta': {}}
        with urllib.request.urlopen('http://127.0.0.1:3000/', timeout=4) as response:
            assert response.status == 200 and 'text/html' in response.headers.get('Content-Type', '')
        print('loopback UI/API: passed; run --remote-only from a separate LAN device for boundary evidence')
        return
    address = args.nonloopback
    import ipaddress
    parsed = ipaddress.ip_address(address)
    if parsed.is_loopback or parsed.is_unspecified or parsed.is_multicast:
        raise ValueError('Supply the Docker host nonloopback LAN address')
    for port in (3000, 8080):
        try:
            with socket.create_connection((address, port), timeout=4):
                raise AssertionError(f'LAN port {port} unexpectedly reachable')
        except (ConnectionRefusedError, TimeoutError):
            print(f'remote nonloopback port {port}: denied')


if __name__ == '__main__':
    main()
