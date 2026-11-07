#!/usr/bin/env bash
# Install & configure Zirco Browser for Pebble 91Dub Clock on Nook Simple Touch
# Usage: ./setup_zirco.sh [DEVICE_IP] [CLOCK_URL]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK_PATH="$SCRIPT_DIR/../apks/org.zirco_18.apk"
DEFAULT_URL="http://192.168.0.239:9191/"
TARGET_URL="${2:-$DEFAULT_URL}"

if [ -n "$1" ]; then
    echo "Connecting to Nook at $1:5555..."
    adb connect "$1:5555"
fi

echo "Waiting for device..."
adb wait-for-device

echo "Installing Zirco Browser..."
adb install -r "$APK_PATH"

echo "Configuring fullscreen and homepage preferences..."
PREFS_XML="<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name=\"GeneralFullScreen\" value=\"true\" />
    <boolean name=\"GeneralHideTitleBars\" value=\"true\" />
    <string name=\"GeneralHomePage\">$TARGET_URL</string>
</map>"

TMP_FILE=$(mktemp)
echo "$PREFS_XML" > "$TMP_FILE"

adb shell "mkdir /data/data/org.zirco/shared_prefs 2>/dev/null || true"
adb push "$TMP_FILE" /data/data/org.zirco/shared_prefs/org.zirco_preferences.xml
rm -f "$TMP_FILE"

adb shell "chmod 755 /data/data/org.zirco/shared_prefs && chmod 666 /data/data/org.zirco/shared_prefs/org.zirco_preferences.xml"

echo "Stopping any existing Zirco instance..."
PID=$(adb shell ps | grep org.zirco | awk '{print $2}')
if [ -n "$PID" ]; then
    adb shell kill "$PID" 2>/dev/null || true
fi

echo "Launching Zirco Browser at $TARGET_URL..."
adb shell am start -n org.zirco/.ui.activities.MainActivity -d "$TARGET_URL"

echo "=================================================="
echo " Zirco Browser installed and launched!"
echo " Pebble 91Dub Clock is running in true fullscreen."
echo "=================================================="
