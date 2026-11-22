#!/bin/bash
# Installs the app + tests WITHOUT uninstalling afterwards (so screenshots stay readable via run-as),
# runs the instrumented tests, and always collects screenshots and logcat.
mkdir -p build/emulator-out
./gradlew --no-daemon installDebug installDebugAndroidTest || exit 1
adb shell am instrument -w -e class app.atracker.SmokeTest app.atracker.test/androidx.test.runner.AndroidJUnitRunner | tee build/emulator-out/instrument.txt
adb shell "run-as app.atracker tar c -C files shots" 2>/dev/null | tar x -C build/emulator-out 2>&1 | tail -2
adb logcat -d -t 4000 > build/emulator-out/logcat.txt 2>&1
adb logcat -d -b crash > build/emulator-out/crash.txt 2>&1
grep -A400 "SMOKE" build/emulator-out/logcat.txt | cut -c1-220 | head -150
ls -la build/emulator-out/shots 2>&1 | tail -12
if grep -q "^OK (" build/emulator-out/instrument.txt; then exit 0; else exit 1; fi
