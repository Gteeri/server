#!/usr/bin/env python3
"""Functional test: freeze density-exemption rules.

Spawns a dense crowd of plain zombies (should freeze, same as the existing
villager/summon tests confirm) plus one NAMED zombie in the same crowd
(should stay exempt and never freeze, per freeze.exempt.named in config).

Uses the same admin-only "/moblimit scan" hook as villager_test.py, since the
automatic scanner is player-centric and this is a headless server.
"""
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rcon import RconClient

FROZEN_PATH = 'BukkitValues."moblimiter:frozen"'
WORLD = "world"
TEST_X, TEST_Y, TEST_Z = 700.5, 200.0, 700.5


def is_frozen(client, selector):
    out = client.cmd(f"data get entity {selector} {FROZEN_PATH}")
    return "1b" in out


def trigger_scan(client):
    client.cmd(f"moblimit scan {WORLD} {TEST_X} {TEST_Y} {TEST_Z}")


def summon_zombie(client, x, y, z, tags, custom_name=None):
    tag_list = ",".join(f'"{t}"' for t in tags)
    nbt_parts = [f'PersistenceRequired:1b', f'Tags:[{tag_list}]']
    if custom_name:
        nbt_parts.append(f'CustomName:\'{{"text":"{custom_name}"}}\'')
    nbt = "{" + ",".join(nbt_parts) + "}"
    out = client.cmd(f"summon minecraft:zombie {x} {y} {z} {nbt}")
    if "Unknown" in out or "Expected" in out or "Unable" in out or "Invalid" in out:
        print(f"WARNING: summon command may have failed: {out!r}")
    return out


def main():
    client = RconClient("127.0.0.1", 25575, "test123")
    failures = []

    client.cmd("gamerule doMobSpawning false")
    client.cmd("forceload add 660 660 740 740")
    client.cmd(f"kill @e[type=minecraft:zombie,x={TEST_X},y={TEST_Y},z={TEST_Z},distance=..80]")
    time.sleep(2)

    print("=== Freeze exemption test: named mob stays exempt in a dense crowd ===")
    # One tagged "control" zombie (no exemptions) to prove the crowd actually
    # triggers density freezing.
    summon_zombie(client, TEST_X, TEST_Y, TEST_Z, ["exemptcrowd", "controlzombie"])
    # One NAMED zombie among the same crowd; must stay exempt.
    summon_zombie(client, TEST_X + 0.5, TEST_Y, TEST_Z, ["exemptcrowd", "namedzombie"],
                  custom_name="ExemptTest")
    # Fill out the crowd well past the default freeze-threshold (25).
    for i in range(2, 30):
        x = TEST_X + (i % 6) * 1.2
        z = TEST_Z + (i // 6) * 1.2
        summon_zombie(client, x, TEST_Y, z, ["exemptcrowd"])

    control_selector = "@e[type=minecraft:zombie,tag=controlzombie,limit=1]"
    named_selector = "@e[type=minecraft:zombie,tag=namedzombie,limit=1]"

    control_frozen = False
    deadline = time.time() + 40
    while time.time() < deadline:
        trigger_scan(client)
        if is_frozen(client, control_selector):
            control_frozen = True
            break
        time.sleep(2)

    if not control_frozen:
        failures.append("control zombie in the crowd never froze (density freeze broken?)")
    else:
        print("OK: control zombie froze under crowd density")

    # Keep scanning a bit longer and make sure the named zombie never freezes.
    for _ in range(5):
        trigger_scan(client)
        time.sleep(2)
        if is_frozen(client, named_selector):
            failures.append("named zombie froze despite freeze.exempt.named being enabled")
            break
    else:
        print("OK: named zombie stayed exempt from freezing")

    client.cmd("kill @e[type=minecraft:zombie,tag=exemptcrowd]")
    client.close()

    if failures:
        print("FREEZE EXEMPT TEST FAILED:")
        for failure in failures:
            print(f" - {failure}")
        sys.exit(1)
    print("FREEZE EXEMPT TEST PASSED")


if __name__ == "__main__":
    main()
