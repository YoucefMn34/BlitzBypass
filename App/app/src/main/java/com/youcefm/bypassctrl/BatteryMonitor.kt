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

}
