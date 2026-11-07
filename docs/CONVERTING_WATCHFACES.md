# Converting Pebble Watchfaces to E-Ink Web Clocks

This guide explains how to convert any classic **Pebble Smartwatch watchface** (from `.pbw` bundle packages or open-source C repositories) into an offline, zero-dependency, pixel-perfect web clock optimized for the **Barnes & Noble Nook Simple Touch** (or any E-Ink browser).

---

## 1. Architecture: Pebble vs. E-Ink Web

| Feature | Original Pebble Watchface | E-Ink Web Clock |
| :--- | :--- | :--- |
| **Runtime** | Pebble OS (FreeRTOS on ARM Cortex-M) | Legacy WebKit (Android 2.1 Eclair / Opera Mini) |
| **Package Format** | `.pbw` (ZIP archive containing binary + resources) | Standalone `index.html` (HTML + CSS + ES3/ES5 JS) |
| **Canvas Resolution** | $144 \times 168\text{ pixels}$ ($1\text{-bit}$ monochrome or $64\text{-color}$) | $576 \times 672\text{ pixels}$ ($4\times$ integer scale on $600 \times 800$ E-Ink) |
| **Asset Storage** | Compiled binary resource pack | Base64 Data URIs embedded directly in HTML |
| **Refresh Mode** | 30–60 Hz Memory LCD (instant response) | Electrophoretic Pearl E-Ink ($200\text{--}400\text{ ms}$ state change) |
| **Update Interval** | Per-second or per-minute | Per-minute (aligned to `:00`) + periodic ghosting clears |

---

## 2. Step 1: Extracting Assets from a Pebble `.pbw`

A `.pbw` file is simply a standard ZIP archive. You can rename `.pbw` to `.zip` or use the included extraction utility:

```bash
python3 tools/extract_pbw.py path/to/watchface.pbw output_dir/
```

### What's Inside a `.pbw`:
* **`appinfo.json`**: App metadata, UUID, watchface flags, and resource declarations (`media` array).
* **`pebble-app.bin`**: The compiled C binary for the Pebble hardware.
* **`resources/`**: The packed binary resources (PNG images, custom bitmap fonts, and raw data).

### Converting Pebble Bitmaps to Web PNGs:
Pebble 1-bit bitmaps are standard black-and-white PNGs:
1. Extract PNG files declared in `appinfo.json`.
2. Convert them into base64 data URIs so the final clock is a single, zero-dependency offline file:
   ```bash
   python3 -c "import base64; print('data:image/png;base64,' + base64.b64encode(open('digit_0.png', 'rb').read()).decode('utf-8'))"
   ```

---

## 3. Step 2: Display Geometry & Integer Scaling

The Barnes & Noble Nook Simple Touch display is **$600 \times 800\text{ pixels}$** ($6.0\text{''}$ diagonal, $167\text{ PPI}$).

The original Pebble display is **$144 \times 168\text{ pixels}$**.

### Why Integer Scaling ($4\times$) is Essential
To preserve the iconic sharp pixel art of Pebble watchfaces on E-Ink, **never use non-integer CSS scaling** (e.g. $4.16\times$ or percentage scaling), which causes subpixel blurring and muddy gray antialiasing on 1-bit E-Ink microcapsules.

* **Pebble $1\times$**: $144 \times 168\text{ px}$
* **Pebble $4\times$**: **$576 \times 672\text{ px}$**
* **Nook Display**: $600 \times 800\text{ px}$
* **Margins**:
  * Left & Right: $(600 - 576) / 2 = \mathbf{12\text{ px}}$ on each side.
  * Top & Bottom: $(800 - 672) / 2 = \mathbf{64\text{ px}}$ on each side.

The 3D-printed clock front bezel ([`pebble_clock_body_PRINT_READY.stl`](../cad/stls/pebble_clock_body_PRINT_READY.stl)) features an aperture of **$90.6 \times 105.7\text{ mm}$**, which corresponds to this $576 \times 672\text{ px}$ viewport while masking the unused Nook border and the physical "n" button!

---

## 4. Step 3: Legacy Android 2.1 WebKit Compatibility Rules

The stock browser and Opera Mini on Android 2.1 Eclair require strict backward compatibility. Follow these rules:

### Strict JavaScript Restrictions (ES3 / ES5 Only)
* ❌ **Do NOT use `let` or `const`** — Use `var` exclusively.
* ❌ **Do NOT use arrow functions** `() => {}` — Use `function() {}`.
* ❌ **Do NOT use template literals** `` `Hello ${name}` `` — Use string concatenation `'Hello ' + name`.
* ❌ **Do NOT use `async`/`await`, `Promise`, or `fetch`** — Use standard DOM manipulation and `setInterval`.
* ❌ **Do NOT use optional chaining `?.` or nullish coalescing `??`**.
* ❌ **Do NOT use ES6 Array methods** (`Array.from`, `for...of`, `find`).

### CSS Compatibility Rules
* Use `-webkit-` vendor prefixes for older WebKit engines.
* To prevent blurry image filtering on scaled images:
  ```css
  image-rendering: -webkit-optimize-contrast;
  image-rendering: -moz-crisp-edges;
  image-rendering: pixelated;
  -ms-interpolation-mode: nearest-neighbor;
  ```
* Set the viewport meta tag in `<head>`:
  ```html
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  ```

---

## 5. Step 4: E-Ink Refresh & Ghosting Management

Pearl E-Ink screens work by moving charged black and white micro-particles with electrostatic voltages. Frequent small updates cause microscopic particle residual shadows ("ghosting").

### 1. Minute-Aligned Updates
Do not run a continuous 1-second `setInterval(..., 1000)` on an E-Ink clock. Running every second drains battery and causes unnecessary E-Ink pixel cycling. Instead:
```javascript
function scheduleNextMinute(callback) {
  var now = new Date();
  var msUntilNextMinute = (60 - now.getSeconds()) * 1000 - now.getMilliseconds();
  setTimeout(function() {
    callback();
    setInterval(callback, 60000);
  }, msUntilNextMinute);
}
```

### 2. Anti-Ghosting Inversion Cycle
Every 15 to 30 minutes, perform a quick 150ms screen inversion to reset the E-Ink particles:
```javascript
function clearGhosting() {
  document.body.style.filter = "invert(100%)";
  document.body.style.webkitFilter = "invert(100%)";
  setTimeout(function() {
    document.body.style.filter = "none";
    document.body.style.webkitFilter = "none";
  }, 200);
}
```

---

## 6. Step 5: Turnkey Watchface Boilerplate

Copy this starter template to create your own converted watchface:

```html
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>Pebble Custom E-Ink Watchface</title>
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    html, body {
      width: 100%;
      height: 100%;
      background: #000;
      overflow: hidden;
      display: -webkit-box;
      -webkit-box-pack: center;
      -webkit-box-align: center;
    }
    #pebble-screen {
      position: relative;
      width: 576px;
      height: 672px;
      background: #fff;
      overflow: hidden;
      image-rendering: -webkit-optimize-contrast;
      image-rendering: pixelated;
    }
    .digit {
      position: absolute;
      width: 120px;
      height: 168px;
    }
  </style>
</head>
<body>
  <div id="pebble-screen">
    <div id="time-display" style="font-size: 80px; font-family: monospace; text-align: center; margin-top: 250px;">
      12:00
    </div>
  </div>

  <script>
    function updateClock() {
      var d = new Date();
      var hours = d.getHours();
      var mins = d.getMinutes();
      if (hours < 10) hours = "0" + hours;
      if (mins < 10) mins = "0" + mins;
      document.getElementById("time-display").innerHTML = hours + ":" + mins;

      // Invert flash on the hour to prevent ghosting
      if (mins == "00") {
        document.body.style.webkitFilter = "invert(100%)";
        setTimeout(function() { document.body.style.webkitFilter = "none"; }, 200);
      }
    }

    updateClock();
    var now = new Date();
    var delay = (60 - now.getSeconds()) * 1000 - now.getMilliseconds();
    setTimeout(function() {
      updateClock();
      setInterval(updateClock, 60000);
    }, delay);
  </script>
</body>
</html>
```

---

## 7. Popular Pebble Faces Ready for Conversion

* **Simplicity**: Ultra-clean Helvetica-style bold typography.
* **Text Watch**: Fuzzy text clock ("IT IS TWENTY MINUTES PAST TEN").
* **DIN Time**: Clean German industrial typography with weather and calendar slots.
* **Tictoc**: Minimalist analog hands rendered with 1-bit line algorithms.
* **Classy**: Retro LCD digital layout with date and battery meter.
