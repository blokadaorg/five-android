#!/usr/bin/env python3
"""
Host-level DNS tests for CI.

Usage:
  - Install dependency: pip install dnspython
  - Run: ./tools/ci/test_dns.py
  - Configure servers/domains with env vars:
      DNS_SERVERS="1.1.1.1,8.8.8.8"
      DNS_DOMAINS="example.com,google.com"
  - Exit codes:
      0 = all checks passed
      2 = one or more checks failed
"""

from __future__ import annotations
import os
import sys
import argparse
import time
from typing import List
import dns.resolver
import dns.exception

DEFAULT_SERVERS = ["1.1.1.1", "8.8.8.8"]
DEFAULT_DOMAINS = ["example.com", "google.com"]


def parse_env_list(name: str, default: List[str]) -> List[str]:
    v = os.getenv(name)
    if not v:
        return default
    return [s.strip() for s in v.split(",") if s.strip()]


def run_checks(servers: List[str], domains: List[str], timeout: float, retries: int, retry_delay: float) -> int:
    resolver = dns.resolver.Resolver()
    resolver.lifetime = timeout
    resolver.timeout = timeout
    failed = False

    for srv in servers:
        resolver.nameservers = [srv]
        print(f"\n--- Testing DNS server: {srv} ---")
        for d in domains:
            ok = False
            last_exc = None
            for attempt in range(1, retries + 1):
                try:
                    ans = resolver.resolve(d, "A")
                    addrs = sorted({rdata.to_text() for rdata in ans})
                    print(f"[OK] {srv} -> {d} A: {', '.join(addrs)}")
                    ok = True
                    break
                except dns.exception.DNSException as e:
                    last_exc = e
                    print(f"[WARN] attempt {attempt} failed for {srv} -> {d}: {e}")
                    if attempt < retries:
                        time.sleep(retry_delay)
            if not ok:
                print(f"[FAIL] {srv} -> {d}: last error: {last_exc}")
                failed = True
    return 2 if failed else 0


def main(argv: List[str]) -> int:
    p = argparse.ArgumentParser(description="Simple DNS checks for CI")
    p.add_argument("--servers", "-s", type=str, help="Comma-separated list of DNS servers")
    p.add_argument("--domains", "-d", type=str, help="Comma-separated list of domains to query")
    p.add_argument("--timeout", type=float, default=5.0, help="Resolver timeout (seconds)")
    p.add_argument("--retries", type=int, default=2, help="Number of attempts per query")
    p.add_argument("--retry-delay", type=float, default=1.0, help="Delay between retries (seconds)")
    args = p.parse_args(argv)

    servers = [s.strip() for s in (args.servers.split(",") if args.servers else parse_env_list("DNS_SERVERS", DEFAULT_SERVERS)) if s.strip()]
    domains = [d.strip() for d in (args.domains.split(",") if args.domains else parse_env_list("DNS_DOMAINS", DEFAULT_DOMAINS)) if d.strip()]

    if not servers:
        print("No DNS servers specified. Use --servers or set DNS_SERVERS.", file=sys.stderr)
        return 2
    if not domains:
        print("No domains specified. Use --domains or set DNS_DOMAINS.", file=sys.stderr)
        return 2

    print(f"Testing servers: {servers}")
    print(f"Testing domains: {domains}")
    return run_checks(servers, domains, args.timeout, args.retries, args.retry_delay)


if __name__ == "__main__":
    try:
        rc = main(sys.argv[1:])
    except KeyboardInterrupt:
        rc = 2
    sys.exit(rc)
