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

The automatic crowd scanner is strictly player-centric (by design, for Folia
performance) and this is a headless server with no player connected, so we
drive the exact same scan/freeze logic manually via the admin-only
"/moblimit scan <world> <x> <y> <z>" command instead of waiting on the
background timer.

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
WORLD = "world"
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


def trigger_scan(client):
    out = client.cmd(f"moblimit scan {WORLD} {TEST_X} {TEST_Y} {TEST_Z}")
    if "Scan triggered" not in out:
        print(f"WARNING: unexpected scan command output: {out!r}")


def wait_until_frozen(client, selector, timeout_s, interval_s=2, description="condition"):
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        trigger_scan(client)
        if is_frozen(client, selector):
            return True
        time.sleep(interval_s)
    print(f"FAIL: timed out waiting for {description}")
    return False


def wait_until_awake(client, selector, timeout_s, interval_s=1, description="condition"):
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        if not is_frozen(client, selector):
            return True
        time.sleep(interval_s)
    print(f"FAIL: timed out waiting for {description}")
    return False


def settle_with_scans(client, duration_s, interval_s=3):
    end = time.time() + duration_s
    while time.time() < end:
        trigger_scan(client)
        time.sleep(interval_s)


def assert_stationary(client, selector, failures, label, settle_s=9):
    pos1 = get_pos(client, selector)
    settle_with_scans(client, settle_s)
    pos2 = get_pos(client, selector)
    dist2 = sum((a - b) ** 2 for a, b in zip(pos1, pos2))
    print(f"{label}: pos before={pos1} after={pos2} dist^2={dist2:.4f}")
    if dist2 > MOVE_EPSILON_SQ:
        failures.append(f"{label}: moved while frozen ({pos1} -> {pos2})")
    else:
        print(f"OK: {label} did not wander while frozen")


def summon_villager(client, x, y, z, tags, profession):
    tag_list = ",".join(f'"{t}"' for t in tags)
    nbt = (
        f'{{VillagerData:{{profession:"{profession}",type:"plains",level:1}},'
        f'PersistenceRequired:1b,Tags:[{tag_list}]}}'
    )
    out = client.cmd(f"summon minecraft:villager {x} {y} {z} {nbt}")
    if "Unknown" in out or "Expected" in out or "Unable" in out or "Invalid" in out:
        print(f"WARNING: summon command may have failed: {out!r}")
    return out


def main():
    client = RconClient("127.0.0.1", 25575, "test123")
    failures = []

    client.cmd("gamerule doMobSpawning false")
    client.cmd(f"forceload add 400 400 480 480")
    client.cmd(f"kill @e[type=minecraft:villager,x={TEST_X},y={TEST_Y},z={TEST_Z},distance=..80]")
    time.sleep(2)

    # --- Test A + B: profession villager freezes and stays put, then wakes on damage ---
    print("=== Test A: profession villager freezes and stops moving ===")
    summon_villager(client, TEST_X, TEST_Y, TEST_Z, ["proftest"], "farmer")
    prof_selector = "@e[type=minecraft:villager,tag=proftest,limit=1]"

    if not wait_until_frozen(client, prof_selector, timeout_s=30,
                              description="profession villager to freeze"):
        failures.append("profession villager did not freeze unconditionally")
    else:
        print("OK: profession villager is frozen")
        assert_stationary(client, prof_selector, failures, "profession villager")

        print("=== Test B: damage wakes the frozen villager instantly ===")
        client.cmd(f"damage {prof_selector} 1 minecraft:generic")
        if not wait_until_awake(client, prof_selector, timeout_s=10,
                                 description="villager to wake on damage"):
            failures.append("villager did not wake up immediately after taking damage")
        else:
            print("OK: villager woke up immediately after taking damage")

    client.cmd(f"kill {prof_selector}")

    # --- Test C: villager WITHOUT a profession freezes under crowd density ---
    print("=== Test C: professionless villager freezes in a crowd (regression test) ===")
    client.cmd(f"kill @e[type=minecraft:villager,x={TEST_X},y={TEST_Y},z={TEST_Z},distance=..80]")
    time.sleep(2)
    summon_villager(client, TEST_X, TEST_Y, TEST_Z, ["crowdtest", "crowdtest_marker"], "none")
    for i in range(1, 30):
        x = TEST_X + (i % 6) * 1.5
        z = TEST_Z + (i // 6) * 1.5
        summon_villager(client, x, TEST_Y, z, ["crowdtest"], "none")

    marker_selector = "@e[type=minecraft:villager,tag=crowdtest_marker,limit=1]"
    if not wait_until_frozen(client, marker_selector, timeout_s=40,
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
