#!/usr/bin/env bash
# Push 91Dub standalone clock to Nook Simple Touch over ADB
# Usage: ./push_clock.sh [device_ip]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INDEX_HTML="$SCRIPT_DIR/../../dashboard/index.html"

if [ ! -f "$INDEX_HTML" ]; then
    echo "Error: $INDEX_HTML not found!"
    exit 1
fi

if [ -n "$1" ]; then
    echo "Connecting to Nook at $1:5555..."
    adb connect "$1:5555"
fi

echo "Waiting for Nook device over ADB..."
adb wait-for-device

echo "Creating /sdcard/clock/ directory..."
adb shell mkdir -p /sdcard/clock/

echo "Pushing standalone index.html to /sdcard/clock/..."
adb push "$INDEX_HTML" /sdcard/clock/index.html

echo "=================================================="
echo " Clock successfully transferred to Nook!"
echo " In your Nook browser, navigate to:"
echo " file:///sdcard/clock/index.html"
echo "=================================================="
