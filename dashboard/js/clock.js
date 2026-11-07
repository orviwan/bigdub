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
    ghostClearMinutes: 30, // Periodic anti-ghosting waveform refresh
    batteryPercent: 90,
    isCharging: false
  };

  // Asset path resolver (overridden if ASSETS_MAP is provided)
  function getAsset(name) {
    if (window.EMBEDDED_ASSETS && window.EMBEDDED_ASSETS[name]) {
      return window.EMBEDDED_ASSETS[name];
    }
    return 'assets/images/' + name;
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
              settings[key] = parsed[key];
            }
          }
        }
      }
    } catch (e) {
      // Ignore localStorage errors in restricted webviews
    }

    // URL parameters (e.g., ?battery=90&charging=0)
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
              var b = parseInt(v, 10);
              if (!isNaN(b)) settings.batteryPercent = b;
            } else if (k === 'charging') {
              settings.isCharging = (v === '1' || v === 'true');
            } else if (k === 'invert') {
              settings.invert = (v === '1' || v === 'true');
            } else if (k === '24h') {
              settings.is24h = (v === '1' || v === 'true');
            } else if (k === 'seconds') {
              settings.showSeconds = (v === '1' || v === 'true');
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

  // Apply visual theme & static graphics
  function applyTheme() {
    var bgFile = settings.invert ? 'background-invert.png' : 'background.png';
    var maskFile = settings.invert ? 'branding-mask-invert.png' : 'branding-mask.png';

    el.bg.src = getAsset(bgFile);
    el.brandingMask.src = getAsset(maskFile);
    el.brandingMask.style.display = settings.brandingMask ? 'block' : 'none';

    el.meter.src = getAsset('icon_meter.png');
    el.bluetooth.src = getAsset('icon_bluetooth.png');
    el.batSymbol.src = getAsset('tiny_percent.png');

    el.separator.src = getAsset('separator.png');
    el.medSeparator.src = getAsset('med-separator.png');

    if (settings.invert) {
      el.batteryBar.style.backgroundColor = '#ffffff';
    } else {
      el.batteryBar.style.backgroundColor = '#000000';
    }

    if (settings.showSeconds) {
      el.bigGroup.style.display = 'none';
      el.medGroup.style.display = 'block';
    } else {
      el.bigGroup.style.display = 'block';
      el.medGroup.style.display = 'none';
    }
  }

  // Update battery graphic
  function updateBatteryUI() {
    var pct = settings.batteryPercent;
    var charging = settings.isCharging;

    el.batteryIcon.src = getAsset(charging ? 'icon_battery_charge.png' : 'icon_battery.png');

    // Draw battery fill bar (up to 44px wide at 4x scale)
    var barWidth = Math.round((pct / 100.0) * 44);
    el.batteryBar.style.width = barWidth + 'px';

    if (pct >= 100) {
      el.batTens.style.display = 'none';
      el.batUnits.style.display = 'none';
      el.batSymbol.style.display = 'none';
      el.batteryBar.style.display = 'block';
    } else {
      el.batTens.style.display = 'block';
      el.batUnits.style.display = 'block';
      el.batSymbol.style.display = 'block';
      el.batTens.src = getAsset('tiny_' + Math.floor(pct / 10) + '.png');
      el.batUnits.src = getAsset('tiny_' + (pct % 10) + '.png');
      el.batteryBar.style.display = 'block';
    }
  }

  // Check battery API if supported
  function initBattery() {
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
      } else {
        updateBatteryUI();
      }
    } catch (e) {
      updateBatteryUI();
    }
  }

  var lastMinute = -1;
  var lastDay = -1;
  var lastHour = -1;

  // Clock Update Routine
  function updateClock() {
    var now = new Date();
    var hours = now.getHours();
    var minutes = now.getMinutes();
    var seconds = now.getSeconds();
    var dayOfWeek = now.getDay();
    var dateNum = now.getDate();

    // 1. Update Day and Date if changed
    if (dateNum !== lastDay) {
      el.dayName.src = getAsset('day_' + dayNames[dayOfWeek] + '.png');
      el.dateTens.src = getAsset('datenum_' + Math.floor(dateNum / 10) + '.png');
      el.dateUnits.src = getAsset('datenum_' + (dateNum % 10) + '.png');
      lastDay = dateNum;
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
        el.timeFormat.src = getAsset('time_format_PM.png');
        el.timeFormat.style.display = 'block';
      } else {
        el.timeFormat.style.display = 'none';
      }
    } else {
      el.timeFormat.src = getAsset('time_format_24H.png');
      el.timeFormat.style.display = 'block';
    }

    // 3. Render Digits
    var hTens = Math.floor(displayHour / 10);
    var hUnits = displayHour % 10;
    var mTens = Math.floor(minutes / 10);
    var mUnits = minutes % 10;
    var sTens = Math.floor(seconds / 10);
    var sUnits = seconds % 10;

    if (settings.showSeconds) {
      // Medium layout
      if (!settings.is24h && hTens === 0) {
        el.medHourTens.style.display = 'none';
      } else {
        el.medHourTens.src = getAsset('med-' + hTens + '.png');
        el.medHourTens.style.display = 'block';
      }
      el.medHourUnits.src = getAsset('med-' + hUnits + '.png');
      el.medMinTens.src = getAsset('med-' + mTens + '.png');
      el.medMinUnits.src = getAsset('med-' + mUnits + '.png');

      el.secTens.src = getAsset('sm-' + sTens + '.png');
      el.secUnits.src = getAsset('sm-' + sUnits + '.png');

      if (settings.blinkColon) {
        el.medSeparator.style.visibility = (seconds % 2 === 0) ? 'visible' : 'hidden';
      } else {
        el.medSeparator.style.visibility = 'visible';
      }
    } else {
      // Big bold layout
      if (!settings.is24h && hTens === 0) {
        el.hourTens.style.display = 'none';
      } else {
        el.hourTens.src = getAsset('num_' + hTens + '.png');
        el.hourTens.style.display = 'block';
      }
      el.hourUnits.src = getAsset('num_' + hUnits + '.png');
      el.minTens.src = getAsset('num_' + mTens + '.png');
      el.minUnits.src = getAsset('num_' + mUnits + '.png');

      if (settings.blinkColon) {
        el.separator.style.visibility = (seconds % 2 === 0) ? 'visible' : 'hidden';
      } else {
        el.separator.style.visibility = 'visible';
      }
    }

    lastMinute = minutes;
    lastHour = hours;

    // Periodic Anti-Ghosting E-Ink Refresh Check
    if (settings.ghostClearMinutes > 0 && minutes % settings.ghostClearMinutes === 0 && seconds === 0) {
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
