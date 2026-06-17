#!/system/bin/sh
# ============================================================
# Blitz Bypass - Boot Service v3
# ============================================================

BYPASS_DIR="/data/bypass"
CC_NODE="/sys/class/power_supply/battery/constant_charge_current"
APP_PACKAGE="com.youcefm.bypassctrl"
ALERT_ACTIVITY="$APP_PACKAGE/.NotificationActivity"

# Create bypass directory and all required files
mkdir -p "$BYPASS_DIR"

# State file (bypass | charge)
[ -f "$BYPASS_DIR/state" ] || echo "charge" > "$BYPASS_DIR/state"

# Percentage config (enabled:threshold) e.g. 1:80
[ -f "$BYPASS_DIR/config" ] || echo "0:80" > "$BYPASS_DIR/config"

# Temperature config (enabled:trigger:resume) e.g. 1:40:38
[ -f "$BYPASS_DIR/temp_config" ] || echo "0:40:38" > "$BYPASS_DIR/temp_config"

# Auto-open app on charger (1=yes, 0=no)
[ -f "$BYPASS_DIR/auto_open" ] || echo "1" > "$BYPASS_DIR/auto_open"

# Network optimizations (1=enabled, 0=disabled)
[ -f "$BYPASS_DIR/net_opt" ] || echo "0" > "$BYPASS_DIR/net_opt"

# Game mode (1=enabled, 0=disabled)
[ -f "$BYPASS_DIR/games_enabled" ] || echo "0" > "$BYPASS_DIR/games_enabled"

# Game list (newline-separated package names)
[ -f "$BYPASS_DIR/games" ] || touch "$BYPASS_DIR/games"

# Touch optimizations (1=enabled, 0=disabled)
[ -f "$BYPASS_DIR/touch_opt" ] || echo "0" > "$BYPASS_DIR/touch_opt"

# Disable thermals (1=disabled, 0=enabled)
[ -f "$BYPASS_DIR/thermal_off" ] || echo "0" > "$BYPASS_DIR/thermal_off"

# God mode (1=enabled, 0=disabled)
[ -f "$BYPASS_DIR/god_mode" ] || echo "0" > "$BYPASS_DIR/god_mode"

# Log file
[ -f "$BYPASS_DIR/daemon.log" ] || touch "$BYPASS_DIR/daemon.log"

# Permissions (app needs to read/write without root)
chmod 666 "$BYPASS_DIR/state" "$BYPASS_DIR/config" "$BYPASS_DIR/temp_config" "$BYPASS_DIR/daemon.log" "$BYPASS_DIR/auto_open" "$BYPASS_DIR/net_opt" "$BYPASS_DIR/games_enabled" "$BYPASS_DIR/games" "$BYPASS_DIR/touch_opt" "$BYPASS_DIR/thermal_off" "$BYPASS_DIR/god_mode"

# Save original constant_charge_current on first boot
if [ ! -f "$BYPASS_DIR/original_cc" ]; then
    cat "$CC_NODE" > "$BYPASS_DIR/original_cc" 2>/dev/null
    chmod 666 "$BYPASS_DIR/original_cc"
fi

# Clean auto-bypass marker on boot
rm -f "$BYPASS_DIR/auto_bypass"

# Apply network optimizations if enabled
NET_OPT=$(cat "$BYPASS_DIR/net_opt" 2>/dev/null | tr -d ' \n')
if [ "$NET_OPT" = "1" ]; then
    sysctl -w net.ipv4.tcp_congestion_control=bbr >/dev/null 2>&1
    sysctl -w net.core.default_qdisc=fq >/dev/null 2>&1
    sysctl -w net.core.rmem_max=16777216 >/dev/null 2>&1
    sysctl -w net.core.wmem_max=16777216 >/dev/null 2>&1
    sysctl -w net.core.rmem_default=1048576 >/dev/null 2>&1
    sysctl -w net.core.wmem_default=1048576 >/dev/null 2>&1
    sysctl -w net.ipv4.tcp_fastopen=3 >/dev/null 2>&1
    sysctl -w net.ipv4.tcp_low_latency=1 >/dev/null 2>&1
    sysctl -w net.ipv4.tcp_mtu_probing=1 >/dev/null 2>&1
    sysctl -w net.ipv4.tcp_retries2=8 >/dev/null 2>&1
    sysctl -w net.ipv4.tcp_tw_reuse=1 >/dev/null 2>&1
    sysctl -w net.ipv4.tcp_sack=0 >/dev/null 2>&1
    sysctl -w net.ipv4.tcp_no_metrics_save=1 >/dev/null 2>&1
    sysctl -w net.core.busy_read=50 >/dev/null 2>&1
    sysctl -w net.core.busy_poll=50 >/dev/null 2>&1
    sysctl -w net.core.gro_flush_timeout=1000000 >/dev/null 2>&1
    sysctl -w net.core.rps_flow_cnt=32768 >/dev/null 2>&1
    for iface in /sys/class/net/wlan* /sys/class/net/rmnet*; do
        echo ff > "$iface/queues/rx-0/rps_cpus" 2>/dev/null
    done
fi

# Pre-create notification channel by launching the invisible NotificationActivity
# This works even if the app is not running (no broadcast receiver needed)
/system/bin/am start --user 0 \
    -a android.intent.action.MAIN \
    -n "$ALERT_ACTIVITY" \
    --es action "create_channel" \
    -f 0x10000000 \
    >/dev/null 2>&1 || true

# Start daemon
nohup /system/bin/bypass-daemon.sh > /dev/null 2>&1 &

