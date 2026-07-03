#!/usr/bin/env python3
"""Functional test: villager freeze / wake-on-damage behaviour.

Covers the two bug reports fixed on top of the base freeze feature:
  A) A villager WITH a profession freezes unconditionally and stays frozen
     (does not wander) until it takes damage or loses its job.
  B) Any damage to a frozen mob wakes it immediately, so it can react/flee
     instead of dying quietly while frozen.
  C) A villager WITHOUT a profession freezes under crowd density exactly
     like any other mob (regression test: villagers used to keep walking
     while other mobs in the same crowd froze).

Uses the entity's PersistentDataContainer flag exposed by vanilla /data as
BukkitValues."moblimiter:frozen", and Pos snapshots to prove the entity is
actually stationary (not just flagged) while frozen.
"""
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rcon import RconClient

FROZEN_PATH = 'BukkitValues."moblimiter:frozen"'
TEST_X, TEST_Y, TEST_Z = 440.5, 200.0, 440.5
MOVE_EPSILON_SQ = 0.04  # ~0.2 blocks total drift tolerance


def is_frozen(client, selector):
    out = client.cmd(f"data get entity {selector} {FROZEN_PATH}")
    return "1b" in out


def get_pos(client, selector):
    out = client.cmd(f"data get entity {selector} Pos")
    matches = re.findall(r"(-?\d+\.?\d*)d", out)
    if len(matches) < 3:
        raise RuntimeError(f"could not parse Pos from: {out!r}")
    return tuple(float(v) for v in matches[:3])


def wait_until(predicate, timeout_s, interval_s=2, description="condition"):
    deadline = time.time() + timeout_s
    last = None
    while time.time() < deadline:
        last = predicate()
        if last:
            return True
        time.sleep(interval_s)
    print(f"FAIL: timed out waiting for {description} (last={last!r})")
    return False


def assert_stationary(client, selector, failures, label, settle_s=9):
    pos1 = get_pos(client, selector)
    time.sleep(settle_s)
    pos2 = get_pos(client, selector)
    dist2 = sum((a - b) ** 2 for a, b in zip(pos1, pos2))
    print(f"{label}: pos before={pos1} after={pos2} dist^2={dist2:.4f}")
    if dist2 > MOVE_EPSILON_SQ:
        failures.append(f"{label}: moved while frozen ({pos1} -> {pos2})")
    else:
        print(f"OK: {label} did not wander while frozen")


def main():
    client = RconClient("127.0.0.1", 25575, "test123")
    failures = []

    client.cmd("gamerule doMobSpawning false")
    client.cmd(f"forceload add 400 400 480 480")
    client.cmd(f"kill @e[type=minecraft:villager,x={TEST_X},y={TEST_Y},z={TEST_Z},distance=..80]")
    time.sleep(2)

    # --- Test A + B: profession villager freezes and stays put, then wakes on damage ---
    print("=== Test A: profession villager freezes and stops moving ===")
    client.cmd(
        f"summon minecraft:villager {TEST_X} {TEST_Y} {TEST_Z} "
        '{VillagerData:{profession:"farmer",type:"plains",level:1},'
        'PersistenceRequired:1b,Tags:["proftest"]}'
    )
    prof_selector = "@e[type=minecraft:villager,tag=proftest,limit=1]"

    if not wait_until(lambda: is_frozen(client, prof_selector), timeout_s=30,
                       description="profession villager to freeze"):
        failures.append("profession villager did not freeze unconditionally")
    else:
        print("OK: profession villager is frozen")
        assert_stationary(client, prof_selector, failures, "profession villager")

        print("=== Test B: damage wakes the frozen villager instantly ===")
        client.cmd(f"damage {prof_selector} 1 minecraft:generic")
        if not wait_until(lambda: not is_frozen(client, prof_selector), timeout_s=10,
                           description="villager to wake on damage"):
            failures.append("villager did not wake up immediately after taking damage")
        else:
            print("OK: villager woke up immediately after taking damage")

    client.cmd(f"kill {prof_selector}")

    # --- Test C: villager WITHOUT a profession freezes under crowd density ---
    print("=== Test C: professionless villager freezes in a crowd (regression test) ===")
    client.cmd(f"kill @e[type=minecraft:villager,x={TEST_X},y={TEST_Y},z={TEST_Z},distance=..80]")
    time.sleep(2)
    client.cmd(
        f"summon minecraft:villager {TEST_X} {TEST_Y} {TEST_Z} "
        '{VillagerData:{profession:"none",type:"plains",level:1},'
        'PersistenceRequired:1b,Tags:["crowdtest","crowdtest_marker"]}'
    )
    for i in range(1, 30):
        x = TEST_X + (i % 6) * 1.5
        z = TEST_Z + (i // 6) * 1.5
        client.cmd(
            f"summon minecraft:villager {x} {TEST_Y} {z} "
            '{VillagerData:{profession:"none",type:"plains",level:1},'
            'PersistenceRequired:1b,Tags:["crowdtest"]}'
        )

    marker_selector = "@e[type=minecraft:villager,tag=crowdtest_marker,limit=1]"
    if not wait_until(lambda: is_frozen(client, marker_selector), timeout_s=40,
                       description="professionless villager to freeze in a crowd"):
        failures.append("professionless villager in a crowd did not freeze (regression!)")
    else:
        print("OK: professionless villager froze in a crowd, same as other mobs")
        assert_stationary(client, marker_selector, failures, "professionless villager")

    client.cmd("kill @e[type=minecraft:villager,tag=crowdtest]")
    client.close()

    if failures:
        print("VILLAGER TEST FAILED:")
        for failure in failures:
            print(f" - {failure}")
        sys.exit(1)
    print("VILLAGER TEST PASSED")


if __name__ == "__main__":
    main()
