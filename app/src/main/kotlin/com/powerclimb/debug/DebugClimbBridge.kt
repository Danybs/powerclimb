package com.powerclimb.debug

import com.powerclimb.BuildConfig
import com.powerclimb.data.ClimbMonitor

/**
 * Debug-only bridge between the exported debug broadcast receiver and the
 * extension service's [ClimbMonitor].
 *
 * Everything is gated by [BuildConfig.DEBUG], and the receiver itself is only
 * declared in the debug manifest. Release builds keep the class but expose no
 * way to reach it.
 */
object DebugClimbBridge {
    @Volatile
    private var monitor: ClimbMonitor? = null

    fun attach(instance: ClimbMonitor) {
        if (!BuildConfig.DEBUG) return
        monitor = instance
    }

    fun detach(instance: ClimbMonitor) {
        if (monitor === instance) {
            monitor = null
        }
    }

    fun handle(action: String?): Boolean {
        if (!BuildConfig.DEBUG) return false
        val current = monitor ?: return false
        return when (action) {
            "start" -> {
                current.startDebugClimb()
                true
            }

            "pause" -> {
                current.pauseDebugRide()
                true
            }

            "resume" -> {
                current.resumeDebugRide()
                true
            }

            "finish" -> {
                current.finishDebugClimb()
                true
            }

            "reset" -> {
                current.resetDebugSimulation()
                true
            }

            else -> false
        }
    }
}
