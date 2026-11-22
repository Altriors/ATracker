#!/bin/bash
# Runs the instrumented tests on the emulator, then always collects screenshots and logcat.
./gradlew --no-daemon connectedDebugAndroidTest
code=$?
mkdir -p build/emulator-out
adb pull /sdcard/Android/data/app.atracker/files/shots build/emulator-out/ 2>&1 | tail -2
adb logcat -d -t 3000 > build/emulator-out/logcat.txt 2>&1
adb logcat -d -b crash > build/emulator-out/crash.txt 2>&1
exit $code
