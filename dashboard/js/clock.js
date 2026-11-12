/**
 * Pebble 91Dub E-Ink Clock Engine
 * Pure ES3/ES5 JavaScript - Compatible with Android 2.1 Eclair & modern browsers
 */

(function () {
  'use strict';

  // Config & State
  var settings = {
    is24h: true,
    showSeconds: false,
    blinkColon: false, // Default false for e-ink power saving
    invert: false,
    brandingMask: false, // false = show "PEBBLE" logo
    ghostClearMinutes: 360, // Periodic anti-ghosting waveform refresh (every 6h for battery life)
    batteryMode: 'day_countdown', // 'day_countdown' (00:00=100%, 12:00=50%), 'manual', or 'hardware'
    batteryPercent: 100,
    isCharging: false,
    offsetY: -14 // Vertical offset in px (shifts watchface up inside 3D-printed bezel)
  };

  // Asset path resolver (overridden if ASSETS_MAP is provided)
  function getAsset(name) {
    if (window.EMBEDDED_ASSETS && window.EMBEDDED_ASSETS[name]) {
      return window.EMBEDDED_ASSETS[name];
    }
    return 'assets/images/' + name;
  }

  // Calculate remaining percentage of day (00:00 = 100%, 12:00 = 50%, 23:59 ≈ 0%)
  function getDayRemainingPercent() {
    var now = new Date();
    var minutesElapsed = now.getHours() * 60 + now.getMinutes();
    var totalMinutes = 1440; // 24 * 60
    var remaining = Math.round(((totalMinutes - minutesElapsed) / totalMinutes) * 100);
    if (remaining > 100) remaining = 100;
    if (remaining < 0) remaining = 0;
    return remaining;
  }

  // Load settings from localStorage and URL query params
  function loadSettings() {
    try {
      if (window.localStorage) {
        var saved = localStorage.getItem('pebble_91dub_settings');
        if (saved) {
          var parsed = JSON.parse(saved);
          for (var key in parsed) {
            if (parsed.hasOwnProperty(key)) {
              if (key === 'offsetY' && parsed[key] === -28) continue;
              settings[key] = parsed[key];
            }
          }
        }
      }
    } catch (e) {
      // Ignore localStorage errors in restricted webviews
    }

    // URL parameters (e.g., ?battery=90, ?battery=day, ?battery=hardware, or ?invert=1)
    try {
      if (window.location && window.location.search) {
        var qs = window.location.search.substring(1);
        if (qs) {
          var pairs = qs.split('&');
          for (var i = 0; i < pairs.length; i++) {
            var parts = pairs[i].split('=');
            var k = decodeURIComponent(parts[0]);
            var v = decodeURIComponent(parts[1] || '');
            if (k === 'battery') {
              if (v === 'day' || v === 'countdown') {
                settings.batteryMode = 'day_countdown';
              } else if (v === 'hardware' || v === 'real') {
                settings.batteryMode = 'hardware';
              } else {
                var b = parseInt(v, 10);
                if (!isNaN(b)) {
                  settings.batteryPercent = b;
                  settings.batteryMode = 'manual';
                }
              }
            } else if (k === 'charging') {
              settings.isCharging = (v === '1' || v === 'true');
            } else if (k === 'invert') {
              settings.invert = (v === '1' || v === 'true');
            } else if (k === '24h') {
              settings.is24h = (v === '1' || v === 'true');
            } else if (k === 'seconds') {
              settings.showSeconds = (v === '1' || v === 'true');
            } else if (k === 'offsetY' || k === 'offsety') {
              var oy = parseInt(v, 10);
              if (!isNaN(oy)) {
                settings.offsetY = oy;
              }
            }
          }
        }
      }
    } catch (e2) {
      // Ignore
    }
  }

  function saveSettings() {
    try {
      if (window.localStorage) {
        localStorage.setItem('pebble_91dub_settings', JSON.stringify(settings));
      }
    } catch (e) {
      // Ignore
    }
  }

  // DOM Elements cache
  var el = {};
  function cacheElements() {
    el.screen = document.getElementById('pebble-screen');
    el.bg = document.getElementById('bg-layer');
    el.brandingMask = document.getElementById('branding-mask-layer');
    el.meter = document.getElementById('meter-layer');
    el.bluetooth = document.getElementById('bluetooth-layer');
    el.batteryBar = document.getElementById('battery-bar');
    el.batteryIcon = document.getElementById('battery-icon-layer');
    el.batTens = document.getElementById('battery-percent-tens');
    el.batUnits = document.getElementById('battery-percent-units');
    el.batSymbol = document.getElementById('battery-percent-symbol');
    el.timeFormat = document.getElementById('time-format-layer');
    el.dayName = document.getElementById('day-name-layer');
    el.dateTens = document.getElementById('date-digit-tens');
    el.dateUnits = document.getElementById('date-digit-units');

    // Big time
    el.bigGroup = document.getElementById('big-time-group');
    el.hourTens = document.getElementById('time-digit-hour-tens');
    el.hourUnits = document.getElementById('time-digit-hour-units');
    el.separator = document.getElementById('separator-layer');
    el.minTens = document.getElementById('time-digit-min-tens');
    el.minUnits = document.getElementById('time-digit-min-units');

    // Medium time (Seconds mode)
    el.medGroup = document.getElementById('med-time-group');
    el.medHourTens = document.getElementById('med-digit-hour-tens');
    el.medHourUnits = document.getElementById('med-digit-hour-units');
    el.medSeparator = document.getElementById('med-separator-layer');
    el.medMinTens = document.getElementById('med-digit-min-tens');
    el.medMinUnits = document.getElementById('med-digit-min-units');
    el.secTens = document.getElementById('sec-digit-tens');
    el.secUnits = document.getElementById('sec-digit-units');

    el.einkFlash = document.getElementById('eink-flash');
    el.settingsPanel = document.getElementById('settings-panel');
  }

  var dayNames = ['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat'];

  // Dirty-checking DOM caches so WebKit/EPD only invalidates changed digit rectangles
  var domSrcCache = {};
  var domDisplayCache = {};
  var domVisCache = {};
  var domWidthCache = {};

  function setSprite(node, assetName) {
    if (!node) return;
    var key = node.id;
    if (domSrcCache[key] !== assetName) {
      node.src = getAsset(assetName);
      domSrcCache[key] = assetName;
    }
  }

  function setDisplay(node, val) {
    if (!node) return;
    var key = node.id;
    if (domDisplayCache[key] !== val) {
      node.style.display = val;
      domDisplayCache[key] = val;
    }
  }

  function setVisibility(node, val) {
    if (!node) return;
    var key = node.id;
    if (domVisCache[key] !== val) {
      node.style.visibility = val;
      domVisCache[key] = val;
    }
  }

  function setWidthPx(node, px) {
    if (!node) return;
    var key = node.id;
    if (domWidthCache[key] !== px) {
      node.style.width = px + 'px';
      domWidthCache[key] = px;
    }
  }

  // Apply visual theme & static graphics
  function applyTheme() {
    var bgFile = settings.invert ? 'background-invert.png' : 'background.png';
    var maskFile = settings.invert ? 'branding-mask-invert.png' : 'branding-mask.png';

    setSprite(el.bg, bgFile);
    setSprite(el.brandingMask, maskFile);
    setDisplay(el.brandingMask, settings.brandingMask ? 'block' : 'none');

    setSprite(el.meter, 'icon_meter.png');
    setSprite(el.bluetooth, 'icon_bluetooth.png');
    setSprite(el.batSymbol, 'tiny_percent.png');

    setSprite(el.separator, 'separator.png');
    setSprite(el.medSeparator, 'med-separator.png');

    // icon_battery.png always has a white interior, so fill bar stays black
    el.batteryBar.style.backgroundColor = '#000000';

    if (settings.showSeconds) {
      setDisplay(el.bigGroup, 'none');
      setDisplay(el.medGroup, 'block');
    } else {
      setDisplay(el.bigGroup, 'block');
      setDisplay(el.medGroup, 'none');
    }

    el.screen.style.top = settings.offsetY + 'px';
  }

  // Update battery graphic (dirty-checked for partial E-Ink refresh)
  function updateBatteryUI() {
    if (settings.batteryMode === 'day_countdown') {
      settings.batteryPercent = getDayRemainingPercent();
    }

    var pct = settings.batteryPercent;
    var charging = settings.isCharging;

    setSprite(el.batteryIcon, charging ? 'icon_battery_charge.png' : 'icon_battery.png');

    // Draw battery fill bar (up to 44px wide at 4x scale)
    var barWidth = Math.round((pct / 100.0) * 44);
    if (barWidth > 44) barWidth = 44;
    if (barWidth < 0) barWidth = 0;
    setWidthPx(el.batteryBar, barWidth);

    if (charging) {
      setDisplay(el.batteryBar, 'none'); // Don't cover charging lightning bolt
    } else {
      setDisplay(el.batteryBar, barWidth > 0 ? 'block' : 'none');
    }

    if (pct >= 100) {
      setDisplay(el.batTens, 'none');
      setDisplay(el.batUnits, 'none');
      setDisplay(el.batSymbol, 'none');
    } else {
      setDisplay(el.batTens, 'block');
      setDisplay(el.batUnits, 'block');
      setDisplay(el.batSymbol, 'block');
      setSprite(el.batTens, 'tiny_' + Math.floor(pct / 10) + '.png');
      setSprite(el.batUnits, 'tiny_' + (pct % 10) + '.png');
    }
  }

  // Check battery API if supported
  function initBattery() {
    if (settings.batteryMode === 'hardware') {
      try {
        if (navigator.getBattery) {
          navigator.getBattery().then(function (battery) {
            function onBatChange() {
              settings.batteryPercent = Math.round(battery.level * 100);
              settings.isCharging = battery.charging;
              updateBatteryUI();
            }
            onBatChange();
            battery.addEventListener('levelchange', onBatChange);
            battery.addEventListener('chargingchange', onBatChange);
          });
          return;
        }
      } catch (e) {
        // Fall back
      }
    }
    updateBatteryUI();
  }

  var lastMinute = -1;
  var lastDay = -1;
  var lastHour = -1;

  // Clock Update Routine (dirty-checked so only changed digits trigger EPD redraws)
  function updateClock() {
    var now = new Date();
    var hours = now.getHours();
    var minutes = now.getMinutes();
    var seconds = now.getSeconds();
    var dayOfWeek = now.getDay();
    var dateNum = now.getDate();
    var totalMinutes = hours * 60 + minutes;
    var minuteChanged = (lastMinute !== -1 && minutes !== lastMinute);

    // 1. Update Day and Date if changed
    if (dateNum !== lastDay) {
      setSprite(el.dayName, 'day_' + dayNames[dayOfWeek] + '.png');
      setSprite(el.dateTens, 'datenum_' + Math.floor(dateNum / 10) + '.png');
      setSprite(el.dateUnits, 'datenum_' + (dateNum % 10) + '.png');
      lastDay = dateNum;
    }

    // Update battery countdown if minute changed
    if (minutes !== lastMinute) {
      updateBatteryUI();
    }

    // 2. Format Hour
    var displayHour = hours;
    var isPM = hours >= 12;

    if (!settings.is24h) {
      displayHour = hours % 12;
      if (displayHour === 0) {
        displayHour = 12;
      }
      if (isPM) {
        setSprite(el.timeFormat, 'time_format_PM.png');
        setDisplay(el.timeFormat, 'block');
      } else {
        setDisplay(el.timeFormat, 'none');
      }
    } else {
      setSprite(el.timeFormat, 'time_format_24H.png');
      setDisplay(el.timeFormat, 'block');
    }

    // 3. Render Digits (only mutating DOM nodes whose sprites actually changed)
    var hTens = Math.floor(displayHour / 10);
    var hUnits = displayHour % 10;
    var mTens = Math.floor(minutes / 10);
    var mUnits = minutes % 10;
    var sTens = Math.floor(seconds / 10);
    var sUnits = seconds % 10;

    if (settings.showSeconds) {
      // Medium layout
      if (!settings.is24h && hTens === 0) {
        setDisplay(el.medHourTens, 'none');
      } else {
        setSprite(el.medHourTens, 'med-' + hTens + '.png');
        setDisplay(el.medHourTens, 'block');
      }
      setSprite(el.medHourUnits, 'med-' + hUnits + '.png');
      setSprite(el.medMinTens, 'med-' + mTens + '.png');
      setSprite(el.medMinUnits, 'med-' + mUnits + '.png');

      setSprite(el.secTens, 'sm-' + sTens + '.png');
      setSprite(el.secUnits, 'sm-' + sUnits + '.png');

      if (settings.blinkColon) {
        setVisibility(el.medSeparator, (seconds % 2 === 0) ? 'visible' : 'hidden');
      } else {
        setVisibility(el.medSeparator, 'visible');
      }
    } else {
      // Big bold layout
      if (!settings.is24h && hTens === 0) {
        setDisplay(el.hourTens, 'none');
      } else {
        setSprite(el.hourTens, 'num_' + hTens + '.png');
        setDisplay(el.hourTens, 'block');
      }
      setSprite(el.hourUnits, 'num_' + hUnits + '.png');
      setSprite(el.minTens, 'num_' + mTens + '.png');
      setSprite(el.minUnits, 'num_' + mUnits + '.png');

      if (settings.blinkColon) {
        setVisibility(el.separator, (seconds % 2 === 0) ? 'visible' : 'hidden');
      } else {
        setVisibility(el.separator, 'visible');
      }
    }

    lastMinute = minutes;
    lastHour = hours;

    // Periodic Anti-Ghosting E-Ink Refresh Check (on boundary transitions only)
    if (minuteChanged && settings.ghostClearMinutes > 0 && totalMinutes % settings.ghostClearMinutes === 0) {
      triggerEinkClear();
    }
  }

  // Anti-Ghosting E-Ink Waveform Flasher
  function triggerEinkClear() {
    if (!el.einkFlash) return;
    el.einkFlash.style.backgroundColor = '#000000';
    el.einkFlash.style.display = 'block';

    setTimeout(function () {
      el.einkFlash.style.backgroundColor = '#ffffff';
      setTimeout(function () {
        el.einkFlash.style.display = 'none';
      }, 150);
    }, 150);
  }

  // Timer loop scheduler
  var timerId = null;
  function scheduleNextTick() {
    if (timerId) {
      clearTimeout(timerId);
      timerId = null;
    }

    updateClock();

    if (settings.showSeconds || settings.blinkColon) {
      // 1-second interval
      var now = new Date();
      var delay = 1000 - now.getMilliseconds();
      timerId = setTimeout(scheduleNextTick, delay);
    } else {
      // Minute-aligned interval: wake up right on :00 seconds!
      var now = new Date();
      var msUntilNextMinute = (60 - now.getSeconds()) * 1000 - now.getMilliseconds() + 50;
      timerId = setTimeout(scheduleNextTick, msUntilNextMinute);
    }
  }

  // Responsive scaler: scales 576x672 to fit any screen
  function handleResize() {
    var vw = window.innerWidth || document.documentElement.clientWidth || 600;
    var vh = window.innerHeight || document.documentElement.clientHeight || 800;

    var scaleX = vw / 576;
    var scaleY = vh / 672;
    var scale = Math.min(scaleX, scaleY);

    // If screen is approximately 600x800, use exact 1.0 (4x Pebble pixels)
    if (vw >= 576 && vh >= 672 && Math.abs(scale - 1.0) < 0.2) {
      scale = 1.0;
    }

    var styleStr = 'scale(' + scale + ')';
    el.screen.style.webkitTransform = styleStr;
    el.screen.style.mozTransform = styleStr;
    el.screen.style.msTransform = styleStr;
    el.screen.style.transform = styleStr;
    el.screen.style.webkitTransformOrigin = 'center center';
    el.screen.style.transformOrigin = 'center center';
  }

  // Settings UI Interaction
  function initSettingsUI() {
    var topBar = document.getElementById('top-touch-bar');
    var bottomBar = document.getElementById('bottom-touch-bar');
    var closeBtn = document.getElementById('close-settings-btn');

    if (topBar) {
      topBar.onclick = function () {
        el.settingsPanel.style.display = 'block';
      };
    }

    if (bottomBar) {
      bottomBar.onclick = function () {
        triggerEinkClear();
      };
    }

    if (closeBtn) {
      closeBtn.onclick = function () {
        el.settingsPanel.style.display = 'none';
        saveSettings();
        applyTheme();
        scheduleNextTick();
      };
    }

    // Bind setting toggle buttons
    bindToggle('btn-format-12', 'btn-format-24', function (isA) {
      settings.is24h = !isA;
      updateClock();
    }, !settings.is24h);

    bindToggle('btn-seconds-off', 'btn-seconds-on', function (isA) {
      settings.showSeconds = !isA;
      applyTheme();
      scheduleNextTick();
    }, !settings.showSeconds);

    bindToggle('btn-colon-solid', 'btn-colon-blink', function (isA) {
      settings.blinkColon = !isA;
      scheduleNextTick();
    }, !settings.blinkColon);

    bindToggle('btn-invert-off', 'btn-invert-on', function (isA) {
      settings.invert = !isA;
      applyTheme();
      updateBatteryUI();
    }, !settings.invert);

    bindToggle('btn-brand-show', 'btn-brand-hide', function (isA) {
      settings.brandingMask = !isA;
      applyTheme();
    }, !settings.brandingMask);

    var clearBtn = document.getElementById('btn-force-clear');
    if (clearBtn) {
      clearBtn.onclick = function () {
        triggerEinkClear();
      };
    }

    var upBtn = document.getElementById('btn-offset-up');
    var downBtn = document.getElementById('btn-offset-down');
    var offsetLabel = document.getElementById('offset-y-val');
    function updateOffsetLabel() {
      if (offsetLabel) {
        offsetLabel.innerHTML = settings.offsetY + 'px';
      }
    }
    updateOffsetLabel();
    if (upBtn) {
      upBtn.onclick = function () {
        settings.offsetY -= 4;
        el.screen.style.top = settings.offsetY + 'px';
        updateOffsetLabel();
      };
    }
    if (downBtn) {
      downBtn.onclick = function () {
        settings.offsetY += 4;
        el.screen.style.top = settings.offsetY + 'px';
        updateOffsetLabel();
      };
    }
  }

  function bindToggle(idA, idB, callback, initIsA) {
    var btnA = document.getElementById(idA);
    var btnB = document.getElementById(idB);
    if (!btnA || !btnB) return;

    function setUI(isA) {
      if (isA) {
        btnA.className = 'setting-btn active';
        btnB.className = 'setting-btn';
      } else {
        btnA.className = 'setting-btn';
        btnB.className = 'setting-btn active';
      }
    }

    setUI(initIsA);

    btnA.onclick = function () {
      setUI(true);
      callback(true);
    };
    btnB.onclick = function () {
      setUI(false);
      callback(false);
    };
  }

  // Initialization
  function init() {
    loadSettings();
    cacheElements();
    applyTheme();
    initBattery();
    initSettingsUI();
    handleResize();

    window.onresize = handleResize;

    // Start tick loop
    scheduleNextTick();
  }

  if (document.readyState === 'complete' || document.readyState === 'loaded') {
    init();
  } else {
    window.onload = init;
  }

})();
