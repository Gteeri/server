#!/usr/bin/env python3
"""Functional test: the area mob limit must cap zombies summoned by command.

The CI config additionally blocks the COMMAND spawn reason, so /summon goes
through the limiter. 80 spawn attempts in one area must yield at most the
configured limit (mobs: 60).
"""
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rcon import RconClient

LIMIT = 60
ATTEMPTS = 80

client = RconClient("127.0.0.1", 25575, "test123")

client.cmd("gamerule doMobSpawning false")
client.cmd("forceload add -16 -16 48 48")
client.cmd("kill @e[type=minecraft:zombie]")
time.sleep(3)

for i in range(ATTEMPTS):
    x = (i % 9) * 2
    z = ((i // 9) % 9) * 2
    client.cmd(
        f"execute in minecraft:overworld run summon minecraft:zombie {x}.5 200.0 {z}.5 "
        "{NoGravity:1b,NoAI:1b,PersistenceRequired:1b}"
    )

time.sleep(3)
out = client.cmd("execute if entity @e[type=minecraft:zombie]")
print(f"count check output: {out!r}")
match = re.search(r"(\d+)", out)
count = int(match.group(1)) if match else 0
print(f"zombies present: {count} (limit {LIMIT}, attempts {ATTEMPTS})")

client.close()

if count > LIMIT:
    print("FAIL: limit exceeded")
    sys.exit(1)
if count < LIMIT - 5:
    print("FAIL: limiter blocked too many spawns")
    sys.exit(1)
print("SUMMON TEST PASSED")
