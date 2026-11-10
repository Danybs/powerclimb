package com.powerclimb.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.powerclimb.BuildConfig

/**
 * Debug-only entry point:
 *
 * adb shell am broadcast -a com.powerclimb.DEBUG_CLIMB -p com.powerclimb --es action start
 *
 * This receiver is declared exclusively in src/debug/AndroidManifest.xml.
 */
class DebugClimbReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG) return
        val action = intent.getStringExtra("action") ?: "start"
        val handled = DebugClimbBridge.handle(action)
        Log.i("PowerClimb", "DEBUG broadcast action=$action handled=$handled")
    }
}
