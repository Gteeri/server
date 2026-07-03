#!/usr/bin/env bash
# Boots a real Folia 1.21.11 server with the freshly built plugin and runs
# smoke + functional tests over RCON.
set -euo pipefail

cd ci-server
cp ../target/MobLimiter-*.jar plugins/

echo "eula=true" > eula.txt
cat > server.properties <<'EOF'
online-mode=false
spawn-protection=0
view-distance=4
simulation-distance=4
level-seed=moblimitertest
enable-rcon=true
rcon.port=25575
rcon.password=test123
EOF

# Pre-seed plugin config: additionally block COMMAND spawns so the /summon
# functional test exercises the limiter.
mkdir -p plugins/MobLimiter
sed 's/^  blocked-spawn-reasons:/  blocked-spawn-reasons:\n    - COMMAND/' \
  ../src/main/resources/config.yml > plugins/MobLimiter/config.yml
echo "--- seeded blocked-spawn-reasons ---"
grep -A4 'blocked-spawn-reasons' plugins/MobLimiter/config.yml | head -6

java -Xmx2G -jar folia.jar --nogui > server.log 2>&1 &
SERVER_PID=$!

echo "Waiting for server startup (pid $SERVER_PID)..."
STARTED=0
for _ in $(seq 1 60); do
  if grep -q 'Done (' server.log; then STARTED=1; break; fi
  if ! kill -0 "$SERVER_PID" 2>/dev/null; then
    echo '::error::Server process died during startup'
    tail -120 server.log
    exit 1
  fi
  sleep 5
done
if [ "$STARTED" -ne 1 ]; then
  echo '::error::Server did not start within timeout'
  tail -120 server.log
  exit 1
fi

echo '--- plugin enable check ---'
grep -i 'moblimiter' server.log || true
if ! grep -qi 'MobLimiter enabled' server.log; then
  echo '::error::Plugin did not enable'
  exit 1
fi
if grep -qiE 'Error occurred while enabling MobLimiter|Could not load plugin' server.log; then
  echo '::error::Plugin failed to load/enable'
  grep -iE -B2 -A20 'Error occurred while enabling|Could not load plugin' server.log || true
  exit 1
fi

echo '--- command smoke test ---'
python3 ../ci/rcon.py 'moblimit' 'moblimit reload' 'moblimit pets'

echo '--- functional limit test ---'
python3 ../ci/summon_test.py

echo '--- graceful shutdown ---'
python3 ../ci/rcon.py 'stop' || true
for _ in $(seq 1 24); do
  kill -0 "$SERVER_PID" 2>/dev/null || break
  sleep 5
done

if grep -iE 'moblimiter' server.log | grep -qiE 'exception|\berror\b'; then
  echo '::error::Log lines mentioning MobLimiter contain errors:'
  grep -iE 'moblimiter' server.log | grep -iE 'exception|\berror\b'
  exit 1
fi

echo 'INTEGRATION TEST PASSED'
