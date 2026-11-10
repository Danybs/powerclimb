package com.powerclimb

import android.util.Log
import com.powerclimb.data.ClimbMonitor
import com.powerclimb.debug.DebugClimbBridge
import com.powerclimb.ui.ClimbFieldMode
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension

class PowerClimbExtension : KarooExtension(EXTENSION_ID, BuildConfig.VERSION_NAME) {
    companion object {
        const val EXTENSION_ID = "powerclimb"
        private const val TAG = "PowerClimb"
    }

    private lateinit var karooSystem: KarooSystemService
    lateinit var monitor: ClimbMonitor
        private set

    override fun onCreate() {
        super.onCreate()

        karooSystem = KarooSystemService(this)
        monitor = ClimbMonitor(karooSystem)
        DebugClimbBridge.attach(monitor)

        karooSystem.connect { connected ->
            Log.i(TAG, "KarooSystem connected=$connected")
        }

        // Start immediately. KarooSystemService keeps consumers registered across
        // reconnects, and the monitor lives in the service, not in the view.
        monitor.start()
    }

    override fun onDestroy() {
        Log.i(TAG, "Extension destroyed")
        if (::monitor.isInitialized) {
            DebugClimbBridge.detach(monitor)
            monitor.stop()
        }
        if (::karooSystem.isInitialized) {
            karooSystem.disconnect()
        }
        super.onDestroy()
    }

    override val types by lazy {
        listOf(
            ClimbAvgPowerDataType(this, ClimbFieldMode.NUMERIC),
            ClimbAvgPowerDataType(this, ClimbFieldMode.VISUAL),
            ClimbAvgPowerDataType(this, ClimbFieldMode.ZONE),
        )
    }
}
