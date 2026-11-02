#!/usr/bin/env bash
# Quick setup helper for rooted Nook Simple Touch over ADB
# Configures stay-on settings and checks device connectivity

set -e

echo "=== Nook Simple Touch ADB Configuration ==="

if [ -n "$1" ]; then
    echo "Connecting to Nook at $1:5555..."
    adb connect "$1:5555"
fi

adb wait-for-device

echo "Device connected:"
adb devices -l

echo "Configuring display settings via ADB..."
# Prevent screen timeout when plugged into USB/AC power (Stay Awake while charging = 3: AC + USB)
adb shell svc power stayon true || true
adb shell settings put global stay_on_while_plugged_in 3 || true

# Set screen timeout to maximum (or disabled)
adb shell settings put system screen_off_timeout 2147483647 || true

echo "Done! If WakeLock app is installed, ensure SCREEN_DIM_WAKE_LOCK is engaged."
