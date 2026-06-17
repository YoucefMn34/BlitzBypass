# Blitz Bypass

A root-powered battery bypass charging controller for Android with game mode, network optimizations, and auto-open on charger.

## Requirements
- Rooted device (KernelSU / Magisk)
- Android 9+ (API 28)

---

## App Features

### Dashboard
- Live battery status (mode, level, temp, voltage, current, power, charge current)
- Animated battery icon and power flow visualization
- One-tap bypass/charge toggle with colored pill button

### Bypass Controls
- **Auto-Stop Threshold** — percentage-based auto-bypass (e.g., stop at 85%)
- **Temperature Bypass** — trigger/resume at custom temps with hysteresis
- **Game Mode** — auto-bypass when a selected game is launched, stays active until killed from recents

### Config
- **Open on Charger** — auto-launch app when charger is connected
- **Game List** — pick which games trigger Game Mode (with search, icons, predefined games)
- **Notification Permission** — manage bypass alert permissions
- **Daemon Status** — view and restart the daemon

### Network Optimizations
- BBR congestion control
- Large TCP buffers (16MB)
- Low latency tweaks (tcp_fastopen, tcp_low_latency, mtu_probing)
- Busy poll for reduced socket latency
- RPS/XPS for multi-core packet processing
- Persisted across reboots via daemon

### Log
- Live daemon log viewer with clear button

---

## Module Features (Daemon)

### Charging Control
- Kernel-level bypass charging via `constant_charge_current` node
- Manual bypass/charge via CLI: `su -c 'bypass -b'` / `bypass -c'` / `bypass -t'`

### Auto-Stop
- Percentage threshold with daemon-side enforcement
- Temperature threshold with trigger/resume hysteresis

### Game Mode
- Detects active game processes using `dumpsys activity` + `oom_score_adj`
- Ignores cached/background-only processes (no false triggers)
- Requires 2 consecutive detections (6s) before triggering
- Notifications with game name on launch/exit

### Auto-Open
- Detects charger connection and launches app automatically

### Network Optimizations
- Applies sysctl tuning on boot when enabled
- Persists via `/data/bypass/net_opt` config file

### Notifications
- BroadcastReceiver-based (no UI intrusion)
- Works when app is killed

---

## Installation

### App
```bash
adb install -r -t app-debug.apk
```

### Module (KernelSU)
```bash
adb push bypass-charging.zip /data/local/tmp/
adb shell su -c 'ksud module install /data/local/tmp/bypass-charging.zip'
adb reboot
```

### Module (Magisk)
Flash `bypass-charging.zip` via Magisk Manager.

---

## CLI Usage

```bash
# Toggle bypass/charge
su -c 'bypass -t'

# Enable bypass
su -c 'bypass -b'

# Enable charge
su -c 'bypass -c'

# Show status
su -c 'bypass -s'

# View logs
su -c 'bypass -l'

# Set temp config (enabled:trigger:resume)
su -c 'bypass --temp-config 1:40:38'

# Set percent config (enabled:threshold)
su -c 'bypass --percent-config 1:85'
```

---

## Config Files

All stored in `/data/bypass/`:

| File | Format | Description |
|------|--------|-------------|
| `state` | `charge` / `bypass` | Current charging mode |
| `config` | `enabled:threshold` | Percentage threshold (e.g., `1:85`) |
| `temp_config` | `enabled:trigger:resume` | Temperature threshold (e.g., `1:40:38`) |
| `auto_open` | `1` / `0` | Auto-open app on charger |
| `net_opt` | `1` / `0` | Network optimizations |
| `games_enabled` | `1` / `0` | Game mode toggle |
| `games` | newline-separated | Game package names |
| `daemon.log` | text | Daemon log (last 150 lines) |
| `original_cc` | number | Original constant_charge_current value |

---

## License

MIT
