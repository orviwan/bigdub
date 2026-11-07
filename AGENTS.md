# AGENTS.md — Giant Pebble Classic Wall Clock (91Dub)

This document provides operational context, technical constraints, network topology, and execution runbooks for AI agents working on the **Giant Pebble Classic Wall Clock** project.

---

## 1. Project Overview & Architecture

* **Concept**: A large wall-mounted replica of the original **Pebble Classic (OG)** smartwatch running the iconic **91Dub** watchface on an authentic 6-inch E-Ink display.
* **Display Hardware**: Barnes & Noble Nook Simple Touch (model `BNRV300`), 6" E-Ink Pearl ($800 \times 600$, 167 ppi, 16 levels of grayscale, 1-bit high-contrast rendering).
* **Clock Engine**: Pure ES3/ES5 JavaScript rendering an exact $4\times$ integer-scaled ($576 \times 672$) replica of the 91Dub Pebble watchface ($144 \times 168$).
* **Dual Deployment Modes**:
  1. **Homelab Docker Container (Primary)**: Hosted on `pro-nas` (`192.168.0.239:9191`) via `nginx:alpine` (< 3 MB RAM footprint).
  2. **100% Offline Local Storage**: Self-contained `index.html` stored on the Nook's internal eMMC user storage at `file:///media/clock/index.html`.

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

### B. Battery Gauge — Real-Time Day Countdown
* Instead of showing a static or disconnected battery percentage, the clock dynamically counts down the **remaining duration of the day**:
  * **00:00 (Midnight)**: 100% remaining (solid 44px filled bar, percentage digits hidden per Pebble hardware spec).
  * **12:00 (Noon)**: 50% remaining (22px half-filled bar, `50%` digits shown).
  * **23:59**: 0% / 1% remaining.
* **Z-Index Layering Rule**: `#battery-bar` **MUST have `z-index: 4`**. The battery outline image (`#battery-icon-layer`) has an opaque white interior at `z-index: 3`. If the bar is at `z-index: 2`, it will be completely hidden behind the white icon background.
* **URL Parameter Overrides**:
  * `?battery=90` — Manually forces a fixed 90% battery reading.
  * `?battery=day` — Explicitly enables day countdown mode (default).
  * `?invert=1` — Inverted high-contrast color scheme ("Junkie Mode").

---

## 4. Agent Runbook & Common Operations

### Connecting to the Nook
```bash
adb connect 192.168.0.188:5555
```

### Launching / Relaunching the Clock on Nook
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
