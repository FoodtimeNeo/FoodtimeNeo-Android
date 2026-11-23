#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
output="$PWD/app/build/reports/compact-layout"
mkdir -p "$output"
export ANDROID_UI_OUTPUT="$output"
python3 app/src/androidTest/fixtures/compact-layout.py > "$output/fixture.log" 2>&1 &
fixture_pid=$!
trap 'kill "$fixture_pid" 2>/dev/null || true' EXIT
for attempt in $(seq 1 30); do
  if curl --fail --silent http://127.0.0.1:18530/api/v1/dining-halls > /dev/null; then break; fi
  sleep 1
done
curl --fail --silent http://127.0.0.1:18530/api/v1/dining-halls > /dev/null
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb reverse tcp:18530 tcp:18530
adb shell am instrument -w cn.edu.bjtu.foodtime.layoutreview.test/cn.edu.bjtu.foodtime.presentation.CompactLayoutRunner | tee "$output/instrumentation.txt"
adb logcat -d > "$output/logcat.txt"
adb pull /sdcard/Android/data/cn.edu.bjtu.foodtime.layoutreview/files "$output/screenshots" || true
grep -Eq 'Compact layout PASS [0-9]+ checks' "$output/instrumentation.txt"
