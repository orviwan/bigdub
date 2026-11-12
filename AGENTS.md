# AGENTS.md — Giant Pebble Classic Wall Clock (91Dub)

This document provides operational context, technical constraints, network topology, and execution runbooks for AI agents working on the **Giant Pebble Classic Wall Clock** project.

---

## 1. Project Overview & Architecture

* **Concept**: A large wall-mounted replica of the original **Pebble Classic (OG)** smartwatch running the iconic **91Dub** watchface on an authentic 6-inch E-Ink display.
* **Display Hardware**: Barnes & Noble Nook Simple Touch (model `BNRV300`), 6" E-Ink Pearl ($800 \times 600$, 167 ppi, 16 levels of grayscale, 1-bit high-contrast rendering).
* **Clock Engines & Deployment Modes**:
  1. **Native Android 2.1 `HOME` Launcher APK (`com.bigdub.clock`, Primary Untethered / Battery Mode)**: `58 KB` native API Level 7 (`DEX 035`, `SHA1withRSA` v1 JAR-signed) APK (`nook_setup/apks/com.bigdub.clock.apk`, source in `nook_setup/clock_apk/`). Programmatically binds itself as the default `CATEGORY_HOME` launcher (`SET_PREFERRED_APPLICATIONS`) + `BOOT_COMPLETED` receiver and dismisses the cold-boot swipe-to-unlock keyguard (`KeyguardLock` + `mWakeAndHandOff` retry) so the Nook boots directly into the 91Dub watchface with zero generic launcher or lockscreen, reads real hardware fuel-gauge battery % (`bq27510` / `BatteryManager`), ticks on `:00` minute boundaries via `AlarmManager.RTC_WAKEUP`, invalidates only changed `104×164` digit rectangles (`ClockView.invalidate(Rect)`), and powers off Wi-Fi (`tiwlan0`) while clamping CPU frequency (`300–600 MHz`).
  2. **Homelab Docker Container (Web Mode)**: Pure ES3/ES5 JavaScript (`dashboard/index.html`) hosted on `pro-nas` (`192.168.0.239:9191`) via `nginx:alpine` (< 3 MB RAM footprint).
  3. **100% Offline Local HTML Storage**: Self-contained `index.html` stored on the Nook's internal storage at `file:///media/clock/index.html` or `file:///sdcard/clock/index.html` via Zirco Browser.

---

## 2. Network Topology & Device Inventory

| Node / Device | Hostname / IP | Role / Service | Credentials & Access |
| :--- | :--- | :--- | :--- |
| **Nook Simple Touch** | `192.168.0.188:5555` | E-Ink Wall Display (Android 2.1 Eclair, rooted) | ADB over Wi-Fi (`adb connect 192.168.0.188:5555`). Root shell (`adbd` runs as root). |
| **Homelab Server (`pro-nas`)** | `192.168.0.239` (Port `9191`) | Host for `pebble_clock` Docker container | SSH `orviwan@192.168.0.239` (Portainer Environment ID 3). |
| **Git Repository** | `git@github.com:orviwan/bigdub.git` | Upstream remote on GitHub | Authenticated via local SSH key (`orviwan`). |

---

## 3. Critical Technical Constraints

### A. Android 2.1 (Eclair / API Level 7) WebKit Limits
* **No Modern JavaScript**: Never use ES6+ features (`const`, `let`, arrow functions `() => {}`, template literals, classes, `Promise`, `async/await`, or `fetch`).
* **Strict ES3/ES5**: Use `var`, standard anonymous functions, and `XMLHttpRequest` if networking is needed.
* **Browser Requirement (Avoid Opera Mini)**:
  * **Opera Mini (`com.opera.mini.android`) must NOT be used.** Opera Mini routes traffic through external cloud servers in Norway, causing `"file: said unknown protocol"` on local files and `"Server timed out"` on private LAN IPs (`192.168.x.x`). Additionally, Opera Mini only renders static server-side snapshots and disables client-side `setInterval()` execution.
  * **Use Zirco Browser (`org.zirco`)**: A local WebKit engine that runs client-side JavaScript timers and supports true fullscreen mode. The tested APK is committed at `nook_setup/apks/org.zirco_18.apk`.

### B. Battery Gauge — Real Hardware Battery & Day Countdown
* **Native APK (`com.bigdub.clock`) Default**: Reads the **real Nook hardware battery percentage** (`Intent.ACTION_BATTERY_CHANGED` / `max17042` fuel gauge) once per minute with zero background polling, showing `icon_battery_charge.png` when charging. Can be switched to **Day Countdown** in the top touch Settings drawer.
* **Web Dashboard Default (`day_countdown`)**: Dynamically counts down the **remaining duration of the day**:
  * **00:00 (Midnight)**: 100% remaining (solid 44px filled bar, percentage digits hidden per Pebble hardware spec).
  * **12:00 (Noon)**: 50% remaining (22px half-filled bar, `50%` digits shown).
  * **23:59**: 0% / 1% remaining.
* **Partial Screen Redraw Rule**: Both `ClockView.java` (`invalidate(Rect)`) and `clock.js` (`domSrcCache` dirty-checking) MUST only invalidate changed sprites so `SurfaceFlinger` + `omap3epfb` run regional EPD updates (`104×164` `minUnits` on 53/60 minutes) rather than full-panel `±15V` charge-pump cycles.
* **Z-Index / Draw Order Rule**: `#battery-bar` **MUST be drawn ON TOP OF `#battery-icon-layer`** (`z-index: 4` > `z-index: 3`). The battery outline image (`icon_battery.png`) has an opaque white interior; drawing the bar underneath hides it completely.
* **Web URL Parameter Overrides**:
  * `?battery=hardware` — Uses browser Battery API if available.
  * `?battery=90` — Manually forces a fixed 90% battery reading.
  * `?battery=day` — Explicitly enables day countdown mode (default).
  * `?invert=1` — Inverted outer bezel color scheme.

### C. Physical Screen Border vs. Active Matrix
* **Active Matrix**: $600 \times 800$ pixels ($90.6 \times 122.4\text{ mm}$ at 167 ppi).
* **The "3-Sided Light Border" Phenomenon**: On a bare Nook displaying a black background, a $\sim 0.5\text{--}1.0\text{ mm}$ off-white margin is visible at the top, left, and right between the active pixels and the plastic bezel. This is unaddressable E-Ink glass outside the active pixel grid:
  * Barnes & Noble molded the Nook casing for e-reading (white page background), where the light inactive margin blended seamlessly into white book margins.
  * The bottom bezel sits further forward over the ribbon connector, concealing the bottom edge.
  * **Resolution**: The 3D-printed Pebble enclosure (`cad/pebble_wall_clock.scad`) has its front aperture sized to $90.2 \times 121.8\text{ mm}$, deliberately overlapping $\sim 0.3\text{ mm}$ into the clock's black canvas padding ($12\text{px}$ / $1.8\text{ mm}$ margin) to physically mask this light border completely. Alternatively, `?invert=1` (white background) makes the border disappear on a bare Nook.

---

## 4. Agent Runbook & Common Operations

### Connecting to the Nook
```bash
adb connect 192.168.0.188:5555
```

### Building & Deploying Native Auto-Boot Battery Clock (`com.bigdub.clock`)
> Note: In battery-saver mode, Wi-Fi on the Nook is turned OFF. Tap the top 80px of the Nook screen and tap **`Wi-Fi+ADB`** (or connect a **data-capable** Micro-USB cable) before running ADB commands.
```bash
# 1. Rebuild Android 2.1 APK (API 7, DEX 035, v1 SHA1withRSA signature)
./nook_setup/clock_apk/build.sh

# 2. Install, configure real battery + CPU power clamp, launch, and turn Wi-Fi OFF
./nook_setup/scripts/setup_native_clock.sh 192.168.0.188
```

### Launching / Relaunching Browser Mode on Nook (Alternative)
```bash
# Terminate existing Zirco process
PID=$(adb shell ps | grep org.zirco | awk '{print $2}')
[ -n "$PID" ] && adb shell kill "$PID"

# Launch Zirco pointing to pro-nas container
adb shell am start -n org.zirco/.ui.activities.MainActivity -d "http://192.168.0.239:9191/"
```

### Remote Screen Capture (Framebuffer RGB565 to PNG)
Android 2.1 lacks `screencap`. Read `/dev/graphics/fb0` directly:
```bash
python3 -c "
import subprocess, zlib, struct

raw = subprocess.check_output(['adb', 'shell', 'cat /dev/graphics/fb0'])
width, height = 600, 800
fb16 = raw[:width*height*2]

def save_png_rgb(width, height, rgb_data, filename):
    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)
    raw_scanlines = bytearray()
    row_bytes = width * 3
    for y in range(height):
        raw_scanlines.append(0)
        raw_scanlines.extend(rgb_data[y * row_bytes : (y + 1) * row_bytes])
    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(bytes(raw_scanlines)))
    png += chunk(b'IEND', b'')
    with open(filename, 'wb') as f:
        f.write(png)

rgb = bytearray(width * height * 3)
for i in range(width * height):
    val = fb16[2*i] | (fb16[2*i+1] << 8)
    rgb[3*i] = ((val >> 11) & 0x1F) * 255 // 31
    rgb[3*i+1] = ((val >> 5) & 0x3F) * 255 // 63
    rgb[3*i+2] = (val & 0x1F) * 255 // 31

save_png_rgb(width, height, rgb, 'nook_screen.png')
print('Screenshot updated!')
"
```

### Forcing E-Ink Waveform Flashes (Clearing Ghosting)
```bash
adb shell "echo 1 > /sys/class/graphics/fb0/epd_refresh"
```

### Deploying Changes to `pro-nas` Container
Whenever `dashboard/index.html` is modified:
```bash
# 1. Syntax check
node -c dashboard/js/clock.js

# 2. Sync to NAS container html root
scp dashboard/index.html orviwan@192.168.0.239:/home/orviwan/docker/pebble-clock/html/index.html

# 3. Sync to Nook offline storage
adb push dashboard/index.html /media/clock/index.html

# 4. Relaunch browser on Nook
adb shell am start -n org.zirco/.ui.activities.MainActivity -d "http://192.168.0.239:9191/"
```

### Server Blueprint & Portainer Protocols
* Any modifications to Docker stacks or network ports on `pro-nas` require:
  1. Creating a Portainer backup (`python3 /home/orviwan/workspace/scripts/backup_portainer.py`).
  2. Updating `/home/orviwan/workspace/server-blueprint/references/server-blueprint.md`.
  3. Running `/home/orviwan/workspace/scripts/sync_server_blueprint.sh` to synchronize secondary blueprint copies and push to GitHub `origin/main`.
