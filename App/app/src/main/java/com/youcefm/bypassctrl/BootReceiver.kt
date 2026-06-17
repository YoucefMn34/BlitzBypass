package com.youcefm.bypassctrl

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Charger receiver is registered dynamically in MainActivity
            // This receiver ensures the system acknowledges our app is ready
        }
    }
}
