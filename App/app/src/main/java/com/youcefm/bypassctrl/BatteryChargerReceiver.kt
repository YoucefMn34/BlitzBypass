package com.youcefm.bypassctrl

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BatteryChargerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_POWER_CONNECTED) return

        val prefs = context.getSharedPreferences("blitz_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_open_enabled", true)) return

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(launchIntent)
    }
}
