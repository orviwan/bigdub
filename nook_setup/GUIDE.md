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

## 3. Install E-Ink Launcher (ReLaunchX)

Android 2.1 (Eclair) stock launcher lacks high-contrast rendering and has unnecessary book carousels.

1. Connect the Nook to your host computer via USB.
2. Sideload **ReLaunchX**:
   ```bash
   adb install ReLaunchX.apk
   ```
3. Press the physical "n" (Home) button on the front bezel of the Nook.
4. When prompted by Android to choose the home application, select **ReLaunchX** and tap **Always**.
5. In ReLaunchX settings:
   * Set theme to **High Contrast E-Ink (Monochrome)**.
   * Enable 1-bit rendering optimizations.

---

## 4. Keep Display Awake (WakeLock & Display Settings)

E-ink displays require no electrical power to hold a static image, but the Android OS will sleep and stop JavaScript timers unless a wake lock is maintained or system timeout is disabled.

### Option A: ADB Display Command (Plugged In)
Run our automated script:
```bash
./nook_setup/scripts/setup_adb.sh
```
Or manually execute:
```bash
adb shell svc power stayon true
adb shell settings put global stay_on_while_plugged_in 3
adb shell settings put system screen_off_timeout 2147483647
```

### Option B: WakeLock v3 APK
1. Sideload a legacy wake lock utility:
   ```bash
   adb install WakeLock_v3.apk
   ```
2. Launch **WakeLock** from ReLaunchX.
3. Select **`SCREEN_DIM_WAKE_LOCK`** or **`PARTIAL_WAKE_LOCK`**.
4. Enable **Start on boot**.

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

## 7. E-Ink Optimization & Anti-Ghosting

E-Ink Pearl displays can experience subtle ghosting after several digit transitions. The 91Dub clock engine includes tailored mitigations:

1. **Minute-Only Repaint**: In standard mode (Seconds OFF), the engine wakes up on exact `:00` seconds, repainting only the changed digit images.
2. **Built-in Waveform Reset**: Every 30 minutes (or whenever you tap the bottom touch zone), the screen executes a rapid 200ms inverted high-contrast flash, resetting the microcapsules and eliminating ghosting.
3. **Interactive Settings Drawer**: Tap the top of the display to adjust:
   * 12-Hour / 24-Hour mode
   * Seconds display ON/OFF
   * Colon separator Blink vs. Solid
   * Color theme: Classic vs. Invert ("Junkie Mode")
   * Pebble Branding: Show vs. Hide
