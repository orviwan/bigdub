# Nook Simple Touch (BNRV300) to Pebble 91Dub Clock Guide

This guide walks you through transforming a Barnes & Noble Nook Simple Touch (model BNRV300) into an authentic wall clock styled after the iconic Pebble **91Dub** smartwatch.

---

## 1. Prerequisites & Bill of Materials

### Hardware
* **Barnes & Noble Nook Simple Touch** (model `BNRV300`): 6-inch E-Ink Pearl display (800×600, 167 ppi).
* **MicroSD Card** (2 GB to 32 GB, formatted as FAT32): Required for rooting via NookManager.
* **Micro-USB Cable & 5V/1A USB Power Supply**: Recommended with a low-profile right-angle micro-USB adapter for clean wall routing.
* **3D Printed Chassis**:
  * Front Bezel (`cad/stls/pebble_clock_front_bezel.stl` or `cad/pebble_wall_clock.scad`)
  * Rear Wall Mount (`cad/stls/pebble_clock_rear_mount.stl`)
  * Side Button Caps (`cad/stls/pebble_clock_button_caps.stl`)
  * Optional Desktop Stand (`cad/stls/pebble_clock_desk_stand.stl`)
* **Fasteners**:
  * 4× M3 × 12mm or M3 × 16mm button-head screws (socket cap).
  * 2× Drywall screws or 3M Command Large Picture Hanging Strips for wall mounting.

---

## 2. Rooting the Nook (NookManager)

NookManager provides a completely non-destructive, standalone bootable SD card root method for the Nook Simple Touch.

1. **Prepare NookManager SD Card:**
   * Download the latest `NookManager.img` release.
   * Write the image to your microSD card using [balenaEtcher](https://etcher.balena.io/) or `dd`:
     ```bash
     sudo dd if=NookManager.img of=/dev/sdX bs=4M status=progress && sync
     ```
2. **Boot NookManager:**
   * Power down the Nook completely by holding the rear power button for 10 seconds.
   * Insert the prepared microSD card into the Nook.
   * Power the device on. It will boot directly into NookManager's menu.
3. **Backup & Root:**
   * Follow the prompt to create a full system partition backup (saved to the SD card).
   * Select **Root > Root My Device**.
   * When prompted, select **Enable Superuser** and **Enable ADB over USB / WiFi**.
4. **Finish Root:**
   * Power off the Nook, eject the microSD card, and power on to return to the stock firmware.

---

## 3. Auto-Boot & Ultra-Low-Power Native Clock (Recommended)

By default, Android 2.1 boots into the Nook lockscreen or a generic launcher (`ReLaunch` / stock Nook Home), and running a full WebKit browser continuously drains battery within ~2 days.

To make the Nook boot **directly into the fullscreen 91Dub watchface** and maximize untethered battery life, we include a purpose-built `57 KB` native Android 2.1 (API Level 7, Dalvik DEX `035`) Home Launcher & Clock APK (`nook_setup/apks/com.bigdub.clock.apk`, source in `nook_setup/clock_apk/`).

### Key Power & Boot Features
* **True `CATEGORY_HOME` Replacement + `BOOT_COMPLETED` Receiver**: Replaces the generic launcher screen on boot and dismisses the slide-to-unlock keyguard (`FLAG_DISMISS_KEYGUARD | FLAG_SHOW_WHEN_LOCKED`).
* **Real Hardware Battery Gauge**: Reads the Nook's internal fuel gauge (`Intent.ACTION_BATTERY_CHANGED` / `max17042`) once per minute with zero background polling, displaying real battery percentage on the 91Dub battery bar (switchable to Day Countdown in the top touch menu).
* **Hardware `AlarmManager.RTC_WAKEUP` Minute Ticks**: Schedules exact `:00` minute hardware RTC wakeups with zero 1-second JS polling loops and zero WebKit memory overhead (`~1.3 MB` RGB565 sprite cache vs. `35+ MB` browser footprint).
* **Partial Screen Redraws (`invalidate(dirtyRect)`)**: Diffs every digit and status slot on each minute tick. On 53 out of every 60 minutes, **only the single `104×164` `minUnits` rectangle (`3.5%` of the screen)** is invalidated, letting `SurfaceFlinger` and the `omap3epfb` driver execute a low-energy regional E-Ink update instead of energizing the `±15V` charge pump across the entire panel.
* **Wi-Fi Off & CPU Idle Clamping**: Powers down the `tiwlan0` Wi-Fi radio and sets the TI OMAP3621 CPU governor to `ondemand` (`300 MHz` idle floor).
* **Built-In Wireless Maintenance Toggle**: Tap the top 80px of the screen any time to open the monochrome Settings drawer and toggle **`Wi-Fi+ADB`** (`<NOOK_IP>:5555`) or open `ReLaunch` / Android Settings, then tap **`Wi-Fi OFF`** to return to battery mode.

### One-Command Installation
> [!IMPORTANT]
> If connecting over USB, ensure you use a **data-capable Micro-USB cable** (many Micro-USB cables are 2-wire charge-only cables and will not appear in `lsusb` or `adb devices`). Alternatively, tap **ADB Konnect** on the Nook once to connect over Wi-Fi; the setup script will turn Wi-Fi OFF automatically when finished.

```bash
# Over USB:
./nook_setup/scripts/setup_native_clock.sh

# Or over Wireless ADB (installs, launches, and disables Wi-Fi at the end):
./nook_setup/scripts/setup_native_clock.sh 192.168.0.188

# Keep Wi-Fi enabled during testing:
./nook_setup/scripts/setup_native_clock.sh 192.168.0.188 --keep-wifi
```

### Setting as Default Home Screen (One-Time Step)
1. Press the physical **`n`** (Home) button on the front bezel of the Nook.
2. Check **"Use by default for this action"** (Always) and tap **91Dub Clock**.
3. Reboot (`adb reboot` or power-cycle) to verify — the Nook will boot straight into the 91Dub watchface.

### Rebuilding the Native APK from Source
```bash
./nook_setup/clock_apk/build.sh
```

---

## 4. Alternative Launcher & Plugged-In Display Setup (Browser Mode)

If you prefer running the HTML/JS dashboard inside a browser on wall power instead of the native launcher APK, you can install **ReLaunchX** and keep the display awake while plugged in.

### Install E-Ink Launcher (ReLaunchX)
1. Connect the Nook to your host computer via USB or Wireless ADB.
2. Sideload **ReLaunchX**:
   ```bash
   adb install ReLaunchX.apk
   ```
3. Press the physical "n" (Home) button, select **ReLaunchX**, and set theme to **High Contrast E-Ink (Monochrome)**.

### Keep Display Awake When Plugged Into Wall Power
Run our automated script (for permanently USB-powered wall installations):
```bash
./nook_setup/scripts/setup_adb.sh
```
Or manually execute:
```bash
adb shell svc power stayon true
adb shell settings put global stay_on_while_plugged_in 3
adb shell settings put system screen_off_timeout 2147483647
```

---

## 5. Running the 91Dub Clock Dashboard

You have two primary deployment modes:

### Mode 1: 100% Offline Local Storage (Zero Network Required)
Because `dashboard/index.html` is completely self-contained (all 72 authentic 91Dub PNG assets are embedded as base64 data URIs), you do not need WiFi enabled!

1. Push the clock file directly to the Nook's SD card or internal storage:
   ```bash
   ./nook_setup/scripts/push_clock.sh
   ```
2. In the Nook's browser, open the URL:
   ```
   file:///sdcard/clock/index.html
   ```
3. Bookmark this page and set it as the browser homepage.

### Mode 2: Local Network Server (Docker on NAS or Raspberry Pi)
If you prefer hosting centrally or updating the dashboard across multiple clocks without touching the device storage:

* **Using Docker on Homelab / NAS (e.g., `pro-nas`):**
  We host the clock in a tiny (< 3 MB RAM) `nginx:alpine` container.
  ```bash
  docker compose -f server/docker-compose.yml up -d
  ```
  The clock is served at `http://192.168.0.239:9191/` (port 9191 matches **91Dub**).

* **Using Standalone Python:**
  ```bash
  python3 server/server.py 8080
  ```

---

## 6. Full-Screen Browser Setup (Zirco Browser)

> [!WARNING]
> **Do NOT use Opera Mini:** Opera Mini is a cloud-transcoding proxy browser. All requests are routed through Opera's external cloud servers in Norway/Iceland, which cannot reach local `file:///` paths (reporting `"file: said unknown protocol"`) or RFC 1918 private LAN IPs (reporting `"Server timed out"`). Furthermore, Opera Mini only renders static snapshots and disables client-side JavaScript intervals (`setInterval`), preventing live time updates.

### Recommended Browser: Zirco Browser (Included)
Zirco Browser is an open-source (GPLv3) Android 2.1+ WebKit browser that runs completely locally on the device, executes full JavaScript, and supports true immersive fullscreen (hiding both the status bar and the address bar).

We provide the tested, verified APK in `nook_setup/apks/org.zirco_18.apk`.

#### Automated Setup via ADB
Run our automated configuration script:
```bash
./nook_setup/scripts/setup_zirco.sh 192.168.0.188 http://192.168.0.239:9191/
```
Or for 100% offline local file playback:
```bash
./nook_setup/scripts/setup_zirco.sh 192.168.0.188 file:///media/clock/index.html
```

#### Manual Setup
1. Sideload Zirco:
   ```bash
   adb install nook_setup/apks/org.zirco_18.apk
   ```
2. Enable Fullscreen Mode and Hide Title Bars in preferences (`/data/data/org.zirco/shared_prefs/org.zirco_preferences.xml`):
   ```xml
   <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
   <map>
       <boolean name="GeneralFullScreen" value="true" />
       <boolean name="GeneralHideTitleBars" value="true" />
       <string name="GeneralHomePage">http://192.168.0.239:9191/</string>
   </map>
   ```
3. Launch Zirco:
   ```bash
   adb shell am start -n org.zirco/.ui.activities.MainActivity -d "http://192.168.0.239:9191/"
   ```

---

## 7. E-Ink Optimization, Partial Redraws & Anti-Ghosting

E-Ink Pearl displays only consume display power when microcapsules change state. Both the native APK and web engine include targeted power and ghosting optimizations:

1. **Dirty-Rect Partial Screen Redraw**: Only changed digit/icon bounding boxes are invalidated on each `:00` minute tick (`104×164` px — `3.5%` of the screen on 53/60 minutes), allowing `SurfaceFlinger` and `omap3epfb` to perform low-power regional refreshes.
2. **6-Hour Waveform Reset**: Every 6 hours (4×/day, or whenever you tap the bottom touch zone), the screen executes a rapid black-to-white full-panel flash (`epd_refresh`), resetting microcapsules while saving ~92% of full-panel charge-pump cycles compared to 30-minute clears.
3. **Interactive Settings Drawer**: Tap the top of the display to adjust:
   * 12-Hour / 24-Hour mode
   * Battery Gauge: Real Hardware `%` vs. Day Countdown `%` (native APK)
   * Power / Maintenance: `Wi-Fi OFF` (Battery Saver) vs. `Wi-Fi+ADB` (`:5555`) (native APK)
   * Seconds display ON/OFF & Colon Blink vs. Solid (web dashboard)
   * Color theme: Normal vs. Outer Invert
   * Pebble Branding: Show vs. Hide & Vertical Bezel Offset (`-14px` default)

---

## 8. Battery Gauge Modes: Real Hardware Battery vs. Day Countdown

The Pebble battery bar (`44px` wide at `4×` scale, hidden `%` digits at `100%`) supports two modes:

* **Real Hardware Battery % (Default in Native APK `com.bigdub.clock`)**:
  * Reads the Nook's internal Li-Ion fuel gauge (`Intent.ACTION_BATTERY_CHANGED` / `/sys/class/power_supply/max17042-0/capacity`) once per minute with zero background listener wakeups, and displays the charging bolt (`icon_battery_charge.png`) when USB power is connected.
* **Ambient Day Countdown (Default in Web Dashboard & Selectable in Native APK)**:
  * Tracks and counts down the **remaining duration of the day**:
    * **00:00 (Midnight)**: 100% remaining (solid 44px filled battery bar).
    * **12:00 (Noon)**: 50% remaining (22px filled bar with `50%` digits).
    * **23:59 (End of Day)**: 0% / 1% remaining.
  * **Calculation**:
    $$\text{Minutes Elapsed} = \text{Hour} \times 60 + \text{Minute}$$
    $$\text{Day Remaining \%} = \text{round}\left(\frac{1440 - \text{Minutes Elapsed}}{1440} \times 100\right)$$
* **Web Dashboard URL Overrides**:
  * `http://192.168.0.239:9191/?battery=hardware` (Uses browser `navigator.getBattery()` API if supported)
  * `http://192.168.0.239:9191/?battery=90` (Forces 90% fixed)
  * `http://192.168.0.239:9191/?battery=day` (Default day countdown)
  * `http://192.168.0.239:9191/?invert=1` (Inverted outer bezel mode)

---

## 9. Remote Device Management via Wireless ADB

The rooted Nook Simple Touch can be completely managed, monitored, and inspected over Wi-Fi without needing a physical USB connection.

### 1. Connecting over Wi-Fi
```bash
adb connect 192.168.0.188:5555
```

### 2. Launching or Reloading the Clock
```bash
# Force stop previous instance
adb shell "kill \$(ps | grep org.zirco | awk '{print \$2}')"

# Launch Zirco pointing to pro-nas container
adb shell am start -n org.zirco/.ui.activities.MainActivity -d "http://192.168.0.239:9191/"
```

### 3. Remote Framebuffer Screen Capture
Android 2.1 lacks the modern `screencap` command. You can capture the live screen directly from the hardware Linux framebuffer (`/dev/graphics/fb0`) over ADB:
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

save_png_rgb(width, height, rgb, 'nook_screenshot.png')
print('Captured nook_screenshot.png!')
"
```

### 4. Forcing E-Ink Waveform Flashes (Clearing Ghosting)
To trigger an immediate full-panel E-Ink waveform inversion cycle:
```bash
adb shell "echo 1 > /sys/class/graphics/fb0/epd_refresh"
```
