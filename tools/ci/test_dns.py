#!/usr/bin/env python3
"""
Host-level DNS tests for CI.

This script will try to discover DNS server IPs from the repository Kotlin source
(app/src/main/java/repository/DnsDataSource.kt) and test them. If none are found it
falls back to environment variables or built-in defaults.

Usage:
  - Install dependency: pip install dnspython
  - Run: ./tools/ci/test_dns.py
  - Configure servers/domains with env vars:
      DNS_SERVERS="1.1.1.1,8.8.8.8"
      DNS_DOMAINS="example.com,google.com"
  - You can override the kotlin source path with --kotlin-path

Exit codes:
  0 = all checks passed
  2 = one or more checks failed or no servers/domains
"""

from __future__ import annotations
import os
import sys
import argparse
import time
import re
from typing import List, Set
import dns.resolver
import dns.exception

DEFAULT_SERVERS = ["1.1.1.1", "8.8.8.8"]
DEFAULT_DOMAINS = ["example.com", "google.com"]
DEFAULT_KOTLIN_PATH = "app/src/main/java/repository/DnsDataSource.kt"

IP_LITERAL_RE = re.compile(r'"([0-9a-fA-F:\.]+)"')
LIST_OF_RE = re.compile(r'ips\s*=\s*listOf\((.*?)\)', re.DOTALL)


def parse_env_list(name: str, default: List[str]) -> List[str]:
    v = os.getenv(name)
    if not v:
        return default
    return [s.strip() for s in v.split(",") if s.strip()]


def extract_ips_from_kotlin(path: str) -> List[str]:
    """Extract IP string literals from listOf(...) occurrences in the kotlin file."""
    try:
        with open(path, "r", encoding="utf-8") as f:
            src = f.read()
    except FileNotFoundError:
        print(f"Kotlin file not found at {path}, skipping Kotlin-based discovery.")
        return []

    ips: Set[str] = set()
    for m in LIST_OF_RE.finditer(src):
        inner = m.group(1)
        for lit in IP_LITERAL_RE.findall(inner):
            # basic sanity: exclude empty and non-ip-like strings
            s = lit.strip()
            if s:
                ips.add(s)
    # also try a fallback: any standalone listOf that contains an IP
    if not ips:
        for m in re.finditer(r'listOf\((.*?)\)', src, re.DOTALL):
            inner = m.group(1)
            for lit in IP_LITERAL_RE.findall(inner):
                ips.add(lit.strip())

    # Filter out values that don't look like IPv4/IPv6 addresses
    def looks_like_ip(x: str) -> bool:
        return bool(re.match(r'^[0-9]+(?:\.[0-9]+){3}$', x)) or (":" in x and all(p for p in x.split(":")))

    filtered = [x for x in ips if looks_like_ip(x)]
    return sorted(filtered)


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
    p.add_argument("--kotlin-path", type=str, default=DEFAULT_KOTLIN_PATH, help="Path to DnsDataSource.kt to discover servers")
    args = p.parse_args(argv)

    # Discovery order:
    # 1) --servers arg
    # 2) Kotlin file ips
    # 3) DNS_SERVERS env
    # 4) DEFAULT_SERVERS

    if args.servers:
        servers = [s.strip() for s in args.servers.split(",") if s.strip()]
    else:
        discovered = extract_ips_from_kotlin(args.kotlin_path)
        if discovered:
            print(f"Discovered {len(discovered)} IPs from {args.kotlin_path}")
            servers = discovered
        else:
            servers = parse_env_list("DNS_SERVERS", DEFAULT_SERVERS)

    domains = [d.strip() for d in (args.domains.split(",") if args.domains else parse_env_list("DNS_DOMAINS", DEFAULT_DOMAINS)) if d.strip()]

    if not servers:
        print("No DNS servers specified or discovered. Use --servers or set DNS_SERVERS.", file=sys.stderr)
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
