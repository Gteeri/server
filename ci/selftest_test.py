#!/usr/bin/env python3
"""Runs the plugin's in-process self-test (/moblimit selftest), which
exercises LimitService (all 5 categories: mobs, item-frames, armor-stands,
paintings, vehicles) and PetManager (per-type + total limits) directly.

This covers protection paths that cannot be exercised over RCON otherwise,
since real armor-stand/item-frame placement and animal taming normally
require a live player interaction (EntityPlaceEvent / HangingPlaceEvent /
EntityTameEvent), which command-based summoning bypasses entirely.

The self-test runs asynchronously on the region thread owning the test
location, so we fire it and then poll "/moblimit selftest-result" until a
final report is available.
"""
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rcon import RconClient

WORLD = "world"
X, Y, Z = 900, 250, 900


def main():
    client = RconClient("127.0.0.1", 25575, "test123")
    trigger = client.cmd(f"moblimit selftest {WORLD} {X} {Y} {Z}")
    print(f"trigger output: {trigger!r}")

    result = None
    deadline = time.time() + 20
    while time.time() < deadline:
        out = client.cmd("moblimit selftest-result")
        if "SELFTEST RESULT" in out:
            result = out
            break
        time.sleep(1)
    client.close()

    if result is None:
        print("FAIL: self-test did not complete in time")
        sys.exit(1)

    print("=== selftest result ===")
    print(result)

    if "FAIL" in result:
        print("SELFTEST FAILED")
        sys.exit(1)
    if "ALL PASSED" not in result:
        print("SELFTEST did not report a clean pass (unexpected output)")
        sys.exit(1)
    print("SELFTEST PASSED")


if __name__ == "__main__":
    main()
