#!/usr/bin/env bash
# Install & configure the native Android 2.1 91Dub Home Launcher & Battery-Saver Clock APK
# Usage: ./nook_setup/scripts/setup_native_clock.sh [DEVICE_IP] [--keep-wifi]
#
# Examples:
#   # Over USB:
#   ./nook_setup/scripts/setup_native_clock.sh
#
#   # Over Wireless ADB (installs, binds HOME, launches, then turns Wi-Fi OFF for battery life):
#   ./nook_setup/scripts/setup_native_clock.sh 192.168.0.188
#
#   # Over Wireless ADB while keeping Wi-Fi + ADB :5555 ON for testing / reboot verification:
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

# Pre-authorize com.bigdub.clock in Superuser (com.noshufou.android.su) if present so root
# CPU clamping and ADB maintenance never block on an interactive prompt or toast over E-Ink.
SU_DB="/data/data/com.noshufou.android.su/databases/permissions.sqlite"
if adb shell "ls $SU_DB" 2>/dev/null | grep -q "permissions.sqlite"; then
    echo "Pre-authorizing silent root access in Superuser database..."
    APP_UID=$(adb shell "grep 'package name=\"com.bigdub.clock\"' /data/system/packages.xml" \
        | sed -n 's/.*userId="\([0-9]*\)".*/\1/p' | tr -d '\r' || true)
    SU_OWNER=$(adb shell "ls -l $SU_DB" | awk '{print $2}' | tr -d '\r' || true)
    if [ -n "$APP_UID" ] && command -v python3 >/dev/null 2>&1; then
        TMP_SU_DB="$(mktemp)"
        adb pull "$SU_DB" "$TMP_SU_DB" >/dev/null
        python3 - "$TMP_SU_DB" "$APP_UID" <<'PY'
import sqlite3, sys
db_path, uid = sys.argv[1], int(sys.argv[2])
conn = sqlite3.connect(db_path)
conn.execute("DELETE FROM apps WHERE package = 'com.bigdub.clock' OR uid = ?", (uid,))
conn.execute(
    "INSERT INTO apps (uid, package, name, exec_uid, exec_cmd, allow) VALUES (?, ?, ?, 0, '/system/bin/sh', 1)",
    (uid, 'com.bigdub.clock', '91Dub Clock'),
)
conn.execute("DELETE FROM prefs WHERE key = 'notifications'")
conn.execute("INSERT INTO prefs (key, value) VALUES ('notifications', '0')")
conn.commit()
conn.close()
PY
        adb push "$TMP_SU_DB" "$SU_DB" >/dev/null
        rm -f "$TMP_SU_DB"
        if [ -n "$SU_OWNER" ]; then
            adb shell "chown $SU_OWNER.$SU_OWNER $SU_DB && chmod 660 $SU_DB" 2>/dev/null || true
        fi
    fi
fi

echo "Applying low-power CPU frequency clamp (300-600 MHz)..."
adb shell "
    if [ -f /sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq ]; then
        echo 300000 > /sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq 2>/dev/null || true
    fi
    if [ -f /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq ]; then
        echo 600000 > /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq 2>/dev/null || true
    fi
" || true

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
adb push "$TMP_PREFS" /data/data/com.bigdub.clock/shared_prefs/pebble_91dub_native_prefs.xml >/dev/null
rm -f "$TMP_PREFS"
adb shell "chmod 755 /data/data/com.bigdub.clock/shared_prefs && chmod 666 /data/data/com.bigdub.clock/shared_prefs/pebble_91dub_native_prefs.xml"

echo "Launching native 91Dub Clock (auto-binding default HOME launcher & E-Ink refresh)..."
if [ "$KEEP_WIFI" -eq 1 ]; then
    adb shell "nohup sh -c 'sleep 2; echo 1 > /sys/class/graphics/fb0/epd_refresh' </dev/null >/dev/null 2>&1 & am start -n com.bigdub.clock/.ClockActivity" || true
else
    # Detach before ClockActivity powers off tiwlan0 so Wireless ADB exits cleanly without hanging
    adb shell "nohup sh -c 'sleep 1; am start -n com.bigdub.clock/.ClockActivity; sleep 2; echo 1 > /sys/class/graphics/fb0/epd_refresh' </dev/null >/dev/null 2>&1 &" || true
    if [ -n "$DEVICE_IP" ]; then
        adb disconnect "$DEVICE_IP:5555" >/dev/null 2>&1 || true
    fi
fi

echo "=================================================================="
echo " Native 91Dub Clock installed, bound as HOME launcher, and running!"
if [ "$KEEP_WIFI" -eq 1 ]; then
    echo " Mode: Wi-Fi + Wireless ADB (:5555) KEPT ON (--keep-wifi)"
else
    echo " Mode: Ultra-Low-Power Battery Saver (Wi-Fi OFF, 1-min ticks)"
fi
echo ""
echo " Tip: Tap the top edge of the screen any time to toggle"
echo "      Settings, Real/Day Battery %, or Wi-Fi + Wireless ADB."
echo "=================================================================="
