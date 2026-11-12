#!/usr/bin/env bash
# Install & configure the native Android 2.1 91Dub Home Launcher & Battery-Saver Clock APK
# Usage: ./nook_setup/scripts/setup_native_clock.sh [DEVICE_IP] [--keep-wifi]
#
# Examples:
#   # Over USB:
#   ./nook_setup/scripts/setup_native_clock.sh
#
#   # Over Wireless ADB (installs, launches, then turns Wi-Fi OFF for battery life):
#   ./nook_setup/scripts/setup_native_clock.sh 192.168.0.188
#
#   # Over Wireless ADB while keeping Wi-Fi ON for testing:
#   ./nook_setup/scripts/setup_native_clock.sh 192.168.0.188 --keep-wifi

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK_PATH="$SCRIPT_DIR/../apks/com.bigdub.clock.apk"

DEVICE_IP=""
KEEP_WIFI=0

for arg in "$@"; do
    case "$arg" in
        --keep-wifi)
            KEEP_WIFI=1
            ;;
        *)
            DEVICE_IP="$arg"
            ;;
    esac
done

if [ ! -f "$APK_PATH" ]; then
    echo "Error: $APK_PATH not found! Run ./nook_setup/clock_apk/build.sh first." >&2
    exit 1
fi

if [ -n "$DEVICE_IP" ]; then
    echo "Connecting to Nook at $DEVICE_IP:5555..."
    adb connect "$DEVICE_IP:5555"
fi

echo "Waiting for Nook device over ADB..."
adb wait-for-device

echo "Installing native 91Dub Clock & Home Launcher APK..."
adb install -r "$APK_PATH"

echo "Stopping any running Zirco Browser WebKit process to free RAM & CPU..."
ZIRCO_PID=$(adb shell ps | grep org.zirco | awk '{print $2}' | tr -d '\r' || true)
if [ -n "$ZIRCO_PID" ]; then
    adb shell kill "$ZIRCO_PID" 2>/dev/null || true
fi

echo "Applying low-power CPU scaling and display stay-on configuration..."
adb shell "
    if [ -f /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor ]; then
        echo ondemand > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null || true
    fi
    if [ -f /sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq ]; then
        echo 300000 > /sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq 2>/dev/null || true
    fi
    if [ -f /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq ]; then
        echo 600000 > /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq 2>/dev/null || true
    fi
" || true
adb shell settings put system screen_off_timeout 2147483647 2>/dev/null || true

# Configure initial preferences (Real Battery %, 24H, -14px offset, Wi-Fi maintenance match)
WIFI_PREF="false"
if [ "$KEEP_WIFI" -eq 1 ]; then
    WIFI_PREF="true"
fi

PREFS_XML="<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name=\"is_24h\" value=\"true\" />
    <boolean name=\"real_battery\" value=\"true\" />
    <boolean name=\"invert\" value=\"false\" />
    <boolean name=\"hide_branding\" value=\"false\" />
    <int name=\"offset_y\" value=\"-14\" />
    <int name=\"ghost_minutes\" value=\"360\" />
    <boolean name=\"wifi_maintenance\" value=\"$WIFI_PREF\" />
</map>"

TMP_PREFS="$(mktemp)"
echo "$PREFS_XML" > "$TMP_PREFS"
adb shell "mkdir /data/data/com.bigdub.clock/shared_prefs 2>/dev/null || true"
adb push "$TMP_PREFS" /data/data/com.bigdub.clock/shared_prefs/pebble_91dub_native_prefs.xml
rm -f "$TMP_PREFS"
adb shell "chmod 755 /data/data/com.bigdub.clock/shared_prefs && chmod 666 /data/data/com.bigdub.clock/shared_prefs/pebble_91dub_native_prefs.xml"

echo "Launching native 91Dub Clock..."
adb shell am start -n com.bigdub.clock/.ClockActivity

echo "Triggering crisp E-Ink Pearl waveform refresh..."
adb shell "echo 1 > /sys/class/graphics/fb0/epd_refresh 2>/dev/null || true"

if [ "$KEEP_WIFI" -eq 0 ]; then
    echo "Disabling Wi-Fi radio for maximum battery life (connection may close if on Wireless ADB)..."
    adb shell "svc wifi disable 2>/dev/null || true" &
    sleep 1
fi

echo "=================================================================="
echo " Native 91Dub Clock installed and running!"
echo " IMPORTANT (One-Time Default Home Setup):"
echo "   Press the physical front 'n' button on the Nook once."
echo "   Check 'Use by default for this action' and tap '91Dub Clock'."
echo "   From now on, every boot goes straight to the 91Dub watchface!"
echo ""
echo " Tip: Tap the top edge of the screen any time to toggle"
echo "      Settings, Real/Day Battery %, or Wi-Fi + Wireless ADB."
echo "=================================================================="
