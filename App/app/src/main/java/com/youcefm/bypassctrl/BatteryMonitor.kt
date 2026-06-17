package com.youcefm.bypassctrl

data class BatteryInfo(
    val mode: String,
    val status: String,
    val level: String,
    val levelRaw: Int,
    val temp: String,
    val tempRaw: Float,
    val voltage: String,
    val voltageRaw: Float,
    val current: String,
    val currentRaw: Float,
    val power: String,
    val powerRaw: Float,
    val cc: String,
    val ccRaw: Float,
    val isPlugged: Boolean
)

object BatteryMonitor {

    fun readInfo(): BatteryInfo {
        val mode = ShellExecutor.exec("cat /data/bypass/state").trim().ifEmpty { "charge" }
        val status = ShellExecutor.exec("cat /sys/class/power_supply/battery/status").trim()
        val levelRaw = ShellExecutor.exec("cat /sys/class/power_supply/battery/capacity").trim().toIntOrNull() ?: 0
        val tempRawTenths = ShellExecutor.exec("cat /sys/class/power_supply/battery/temp").trim().toFloatOrNull() ?: 0f
        val voltRaw = ShellExecutor.exec("cat /sys/class/power_supply/battery/voltage_now").trim().toFloatOrNull() ?: 0f
        val currRaw = ShellExecutor.exec("cat /sys/class/power_supply/battery/current_now").trim().toFloatOrNull() ?: 0f
        val ccRawMicro = ShellExecutor.exec("cat /sys/class/power_supply/battery/constant_charge_current").trim().toFloatOrNull() ?: 0f
        val usbOnline = ShellExecutor.exec("cat /sys/class/power_supply/usb/online").trim()

        val tempC = tempRawTenths / 10f
        val voltV = voltRaw / 1_000_000f
        val currMa = kotlin.math.abs(currRaw / 1000f)
        val powerW = kotlin.math.abs(voltV * (currRaw / 1_000_000f))
        val ccA = ccRawMicro / 1_000_000f

        return BatteryInfo(
            mode = mode.uppercase(),
            status = status.ifEmpty { "N/A" },
            level = "$levelRaw%",
            levelRaw = levelRaw,
            temp = String.format("%.1f°C", tempC),
            tempRaw = tempC,
            voltage = String.format("%.3fV", voltV),
            voltageRaw = voltV,
            current = String.format("%.0fmA", currMa),
            currentRaw = currMa,
            power = String.format("%.2fW", powerW),
            powerRaw = powerW,
            cc = parseCC(ccRawMicro),
            ccRaw = ccA,
            isPlugged = usbOnline == "1"
        )
    }

    private fun parseCC(rawMicroA: Float): String {
        return when {
            rawMicroA == 0f -> "0"
            rawMicroA >= 1_000_000f -> String.format("%.2f A", rawMicroA / 1_000_000f)
            rawMicroA >= 1_000f -> String.format("%.0f mA", rawMicroA / 1_000f)
            else -> String.format("%.0f µA", rawMicroA)
        }
    }

    fun readLogs(): String {
        return ShellExecutor.exec("tail -n 30 /data/bypass/daemon.log")
    }

    fun setMode(isBypass: Boolean) {
        val value = if (isBypass) "bypass" else "charge"
        ShellExecutor.exec("echo $value > /data/bypass/state")
    }

    /** Write threshold config to daemon. Format: "enabled:value" e.g. "1:80" */
    fun setThreshold(enabled: Boolean, value: Int) {
        val cfg = "${if (enabled) 1 else 0}:$value"
        ShellExecutor.exec("echo $cfg > /data/bypass/config")
    }


// Add these methods to your existing BatteryMonitor object/class

    /**
     * Write temperature threshold config to daemon.
     * Format: enabled:trigger:resume (e.g., "1:40:38")
     */
    fun setTempThreshold(enabled: Boolean, trigger: Int, resume: Int) {
        val config = "${if (enabled) 1 else 0}:$trigger:$resume"
        ShellExecutor.exec("echo $config > /data/bypass/temp_config")
    }

    /**
     * Read current temperature threshold config from daemon.
     */
    fun readTempThreshold(): TempThresholdConfig {
        val result = ShellExecutor.exec("cat /data/bypass/temp_config 2>/dev/null || echo 0:40:38")
        val parts = result.trim().split(":")
        return TempThresholdConfig(
            enabled = parts.getOrNull(0)?.toIntOrNull() == 1,
            trigger = parts.getOrNull(1)?.toIntOrNull() ?: 40,
            resume = parts.getOrNull(2)?.toIntOrNull() ?: 38
        )
    }

    data class TempThresholdConfig(
        val enabled: Boolean,
        val trigger: Int,
        val resume: Int
    )

    fun setAutoOpen(enabled: Boolean) {
        val value = if (enabled) "1" else "0"
        ShellExecutor.exec("echo $value > /data/bypass/auto_open")
    }

    fun readAutoOpen(): Boolean {
        val result = ShellExecutor.exec("cat /data/bypass/auto_open 2>/dev/null || echo 1")
        return result.trim() == "1"
    }

    fun applyNetworkOptimizations() {
        ShellExecutor.exec("echo 1 > /data/bypass/net_opt")
        val cmd = """sysctl -w net.ipv4.tcp_congestion_control=bbr && sysctl -w net.core.default_qdisc=fq && sysctl -w net.core.rmem_max=16777216 && sysctl -w net.core.wmem_max=16777216 && sysctl -w net.core.rmem_default=1048576 && sysctl -w net.core.wmem_default=1048576 && sysctl -w net.ipv4.tcp_fastopen=3 && sysctl -w net.ipv4.tcp_low_latency=1 && sysctl -w net.ipv4.tcp_mtu_probing=1 && sysctl -w net.ipv4.tcp_retries2=8 && sysctl -w net.ipv4.tcp_tw_reuse=1 && sysctl -w net.ipv4.tcp_sack=0 && sysctl -w net.ipv4.tcp_no_metrics_save=1 && sysctl -w net.core.busy_read=50 && sysctl -w net.core.busy_poll=50 && sysctl -w net.core.gro_flush_timeout=1000000 && sysctl -w net.core.rps_flow_cnt=32768"""
        ShellExecutor.exec(cmd)
        ShellExecutor.exec("for iface in /sys/class/net/wlan* /sys/class/net/rmnet*; do echo ff > \$iface/queues/rx-0/rps_cpus 2>/dev/null; done")
    }

    fun revertNetworkOptimizations() {
        ShellExecutor.exec("echo 0 > /data/bypass/net_opt")
        val cmd = """sysctl -w net.ipv4.tcp_congestion_control=cubic && sysctl -w net.core.default_qdisc=fq_codel && sysctl -w net.core.rmem_max=212992 && sysctl -w net.core.wmem_max=212992 && sysctl -w net.core.rmem_default=212992 && sysctl -w net.core.wmem_default=212992 && sysctl -w net.ipv4.tcp_fastopen=1 && sysctl -w net.ipv4.tcp_low_latency=0 && sysctl -w net.ipv4.tcp_mtu_probing=0 && sysctl -w net.ipv4.tcp_retries2=15 && sysctl -w net.ipv4.tcp_tw_reuse=0 && sysctl -w net.ipv4.tcp_sack=3 && sysctl -w net.ipv4.tcp_no_metrics_save=0 && sysctl -w net.core.busy_read=0 && sysctl -w net.core.busy_poll=0 && sysctl -w net.core.gro_flush_timeout=0 && sysctl -w net.core.rps_flow_cnt=0"""
        ShellExecutor.exec(cmd)
        ShellExecutor.exec("for iface in /sys/class/net/wlan* /sys/class/net/rmnet*; do echo 0 > \$iface/queues/rx-0/rps_cpus 2>/dev/null; done")
    }

    fun setGameMode(enabled: Boolean) {
        val value = if (enabled) "1" else "0"
        ShellExecutor.exec("echo $value > /data/bypass/games_enabled")
    }

    fun readGameMode(): Boolean {
        val result = ShellExecutor.exec("cat /data/bypass/games_enabled 2>/dev/null || echo 0")
        return result.trim() == "1"
    }

    fun saveGameList(packages: List<String>) {
        val content = packages.joinToString("\n")
        ShellExecutor.exec("echo '${content.replace("'", "'\\''")}' > /data/bypass/games")
    }

    fun readGameList(): List<String> {
        val result = ShellExecutor.exec("cat /data/bypass/games 2>/dev/null")
        return result.lines().filter { it.isNotBlank() }
    }

    fun applyTouchOptimizations() {
        ShellExecutor.exec("echo 1 > /data/bypass/touch_opt")
        ShellExecutor.exec("echo 1 > /sys/devices/platform/goodix_ts.0/switch_report_rate")
        ShellExecutor.exec("echo 1 > /sys/class/touch/touch_dev/enable_touch_raw")
        ShellExecutor.exec("echo close > /sys/class/touch/touch_dev/scp_tp_mistouch_enable")
        ShellExecutor.exec("echo 0 > /sys/class/touch/touch_dev/palm_sensor")
        ShellExecutor.exec("echo 500 > /proc/touch_boost/boost_duration")
        ShellExecutor.exec("echo 21 > /proc/touch_boost/boost_opp_cluster_0")
        ShellExecutor.exec("echo 27 > /proc/touch_boost/boost_opp_cluster_1")
        ShellExecutor.exec("echo 32 > /proc/touch_boost/boost_opp_cluster_2")
        ShellExecutor.exec("echo 1 > /proc/touch_boost/boost_up")
        ShellExecutor.exec("echo 1 > /proc/touch_boost/boost_down")
    }

    fun revertTouchOptimizations() {
        ShellExecutor.exec("echo 0 > /data/bypass/touch_opt")
        ShellExecutor.exec("echo 0 > /sys/devices/platform/goodix_ts.0/switch_report_rate")
        // Keep raw touch enabled — device requires it
        ShellExecutor.exec("echo open > /sys/class/touch/touch_dev/scp_tp_mistouch_enable")
        ShellExecutor.exec("echo 0 > /sys/class/touch/touch_dev/palm_sensor")
        ShellExecutor.exec("echo 100 > /proc/touch_boost/boost_duration")
        ShellExecutor.exec("echo -1 > /proc/touch_boost/boost_opp_cluster_0")
        ShellExecutor.exec("echo -1 > /proc/touch_boost/boost_opp_cluster_1")
        ShellExecutor.exec("echo -1 > /proc/touch_boost/boost_opp_cluster_2")
        ShellExecutor.exec("echo 0 > /proc/touch_boost/boost_up")
        ShellExecutor.exec("echo 1 > /proc/touch_boost/boost_down")
    }

    fun applyDisableThermals() {
        ShellExecutor.exec("echo 1 > /data/bypass/thermal_off")
        ShellExecutor.exec("echo 0 > /sys/module/metis/parameters/thermal_break_enable")
        ShellExecutor.exec("echo 999999 > /sys/module/mtk_perf_ioctl_magt/parameters/thermal_aware_threshold")
        ShellExecutor.exec("echo 0 > /sys/kernel/fpsgo/fbt/enable_switch_down_throttle")
    }

    fun revertDisableThermals() {
        ShellExecutor.exec("echo 0 > /data/bypass/thermal_off")
        ShellExecutor.exec("echo 1 > /sys/module/metis/parameters/thermal_break_enable")
        ShellExecutor.exec("echo -1 > /sys/module/mtk_perf_ioctl_magt/parameters/thermal_aware_threshold")
        ShellExecutor.exec("echo 1 > /sys/kernel/fpsgo/fbt/enable_switch_down_throttle")
    }

    fun applyGodMode() {
        ShellExecutor.exec("echo 1 > /data/bypass/god_mode")
        // Network optimizations
        applyNetworkOptimizations()
        // Touch optimizations
        applyTouchOptimizations()
        // Disable thermals
        applyDisableThermals()
        // CPU performance governor
        ShellExecutor.exec("echo performance > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
        ShellExecutor.exec("echo performance > /sys/devices/system/cpu/cpu4/cpufreq/scaling_governor")
        ShellExecutor.exec("echo performance > /sys/devices/system/cpu/cpu7/cpufreq/scaling_governor")
        // GPU max frequency
        ShellExecutor.exec("echo performance > /sys/class/devfreq/13000000.mali/governor")
        ShellExecutor.exec("echo 1300000000 > /sys/class/devfreq/13000000.mali/min_freq")
        // DDR max frequency
        ShellExecutor.exec("echo userspace > /sys/class/devfreq/mtk-dvfsrc-devfreq/governor")
        ShellExecutor.exec("echo 8533000000 > /sys/class/devfreq/mtk-dvfsrc-devfreq/min_freq")
        // CPU core control boost
        ShellExecutor.exec("echo 4 > /sys/devices/system/cpu/cpu7/core_ctl/min_cpus")
        ShellExecutor.exec("echo 1 > /sys/devices/system/cpu/cpu7/core_ctl/core_ctl_boost")
        // Scheduler boost
        ShellExecutor.exec("echo 1 > /proc/sys/kernel/sched_boost")
        // CPUSETS
        ShellExecutor.exec("echo 0-7 > /dev/cpuset/top-app/cpus")
        ShellExecutor.exec("echo 0-7 > /dev/cpuset/foreground/cpus")
        ShellExecutor.exec("echo 0-3 > /dev/cpuset/background/cpus")
        ShellExecutor.exec("echo 0-3 > /dev/cpuset/system-background/cpus")
        // FPSGO boost
        ShellExecutor.exec("echo 1 > /sys/kernel/fpsgo/fbt/boost_ta")
        ShellExecutor.exec("echo 1 > /sys/kernel/fpsgo/fbt/boost_VIP")
        ShellExecutor.exec("echo 1 > /sys/kernel/fpsgo/fbt/blc_boost")
        ShellExecutor.exec("echo 1 > /sys/kernel/fpsgo/fbt/enable_uclamp_boost")
        // IO optimization
        ShellExecutor.exec("echo kyber > /sys/block/sda/queue/scheduler")
        ShellExecutor.exec("echo 0 > /proc/sys/vm/swappiness")
        ShellExecutor.exec("echo 5 > /proc/sys/vm/dirty_ratio")
        ShellExecutor.exec("echo 1 > /proc/sys/vm/dirty_background_ratio")
        ShellExecutor.exec("echo 50 > /proc/sys/vm/vfs_cache_pressure")
    }

    fun revertGodMode() {
        ShellExecutor.exec("echo 0 > /data/bypass/god_mode")
        revertNetworkOptimizations()
        revertTouchOptimizations()
        revertDisableThermals()
        // CPU governor back
        ShellExecutor.exec("echo schedutil > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
        ShellExecutor.exec("echo schedutil > /sys/devices/system/cpu/cpu4/cpufreq/scaling_governor")
        ShellExecutor.exec("echo schedutil > /sys/devices/system/cpu/cpu7/cpufreq/scaling_governor")
        // GPU back
        ShellExecutor.exec("echo simple_ondemand > /sys/class/devfreq/13000000.mali/governor")
        // DDR back
        ShellExecutor.exec("echo simple_ondemand > /sys/class/devfreq/mtk-dvfsrc-devfreq/governor")
        // CPUSETS back
        ShellExecutor.exec("echo 0-7 > /dev/cpuset/top-app/cpus")
        ShellExecutor.exec("echo 0-6 > /dev/cpuset/foreground/cpus")
        ShellExecutor.exec("echo 0-3 > /dev/cpuset/background/cpus")
        ShellExecutor.exec("echo 0-3 > /dev/cpuset/system-background/cpus")
        // Scheduler boost off
        ShellExecutor.exec("echo 0 > /proc/sys/kernel/sched_boost")
        // FPSGO boost off
        ShellExecutor.exec("echo 0 > /sys/kernel/fpsgo/fbt/boost_ta")
        ShellExecutor.exec("echo 0 > /sys/kernel/fpsgo/fbt/boost_VIP")
        ShellExecutor.exec("echo 0 > /sys/kernel/fpsgo/fbt/blc_boost")
        ShellExecutor.exec("echo 0 > /sys/kernel/fpsgo/fbt/enable_uclamp_boost")
        // IO back
        ShellExecutor.exec("echo mq-deadline > /sys/block/sda/queue/scheduler")
        ShellExecutor.exec("echo 50 > /proc/sys/vm/swappiness")
    }

}
