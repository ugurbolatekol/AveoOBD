package com.aveoobd.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            val prefs = Prefs(context)
            if (prefs.autoStart && prefs.btAddress.isNotBlank() && Settings.canDrawOverlays(context)) {
                try {
                    val serviceIntent = Intent(context, OverlayService::class.java)
                    context.startForegroundService(serviceIntent)
                } catch (_: Exception) {
                }
            }
        }
    }
}
