package com.powerclimb.data

import android.os.SystemClock
import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Subscribes to the native Karoo Climber streams and feeds [ClimbSessionTracker].
 *
 * IMPORTANT: this monitor is owned by the extension service, not by the data
 * field view. That is what allows the average to keep accumulating while the
 * rider changes to another page.
 */
class ClimbMonitor(
    private val karooSystem: KarooSystemService,
) {
    companion object {
        private const val TAG = "PowerClimb"
        private const val TICK_MS = 1_000L

        private const val DEBUG_ELEVATION_TO_TOP = 350.0
        private const val DEBUG_DISTANCE_TO_TOP = 3_500.0
        private const val DEBUG_CLIMB_NUMBER = 1
    }

    private val tracker = ClimbSessionTracker()
    val state = tracker.state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val consumerIds = mutableListOf<String>()
    private var tickerJob: Job? = null
    private var started = false

    @Volatile
    private var debugActive = false
    private var debugJob: Job? = null
    private var debugCleanupJob: Job? = null

    @Volatile
    private var lastNativeRideStatus = RideStatus.IDLE

    @Volatile
    private var latestElevationToTop: Double? = null

    @Volatile
    private var latestDistanceToTop: Double? = null

    @Volatile
    private var latestClimbNumber: Int? = null

    @Volatile
    private var lastOnClimb = false

    @Volatile
    private var lastPhase: ClimbPhase? = null

    @Volatile
    private var lastSignalLogAtMs = 0L

    @Synchronized
    fun start() {
        if (started) return
        started = true

        latestElevationToTop = null
        latestDistanceToTop = null
        latestClimbNumber = null
        lastOnClimb = false
        lastPhase = null
        lastSignalLogAtMs = 0L

        consumerIds += karooSystem.addConsumer<OnStreamState>(
            OnStreamState.StartStreaming(DataType.Type.POWER),
        ) { event ->
            handlePower(event)
        }

        consumerIds += karooSystem.addConsumer<OnStreamState>(
            OnStreamState.StartStreaming(DataType.Type.ELEVATION_TO_TOP),
        ) { event ->
            handleElevationToTop(event)
        }

        consumerIds += karooSystem.addConsumer<OnStreamState>(
            OnStreamState.StartStreaming(DataType.Type.DISTANCE_TO_TOP),
        ) { event ->
            handleDistanceToTop(event)
        }

        consumerIds += karooSystem.addConsumer<OnStreamState>(
            OnStreamState.StartStreaming(DataType.Type.CLIMB_NUMBER),
        ) { event ->
            handleClimbNumber(event)
        }

        consumerIds += karooSystem.addConsumer<RideState> { event ->
            handleRideState(event)
        }

        // Navigation is only used for diagnostics: the native Climber streams
        // are the source of truth for this data field.
        consumerIds += karooSystem.addConsumer<OnNavigationState> { event ->
            logNavigation(event.state)
        }

        tickerJob = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                tracker.onTick(nowMs())
                logPhaseIfChanged()
            }
        }

        Log.i(TAG, "Climb monitor started")
    }

    @Synchronized
    fun stop() {
        if (!started) return
        started = false

        consumerIds.forEach { karooSystem.removeConsumer(it) }
        consumerIds.clear()

        tickerJob?.cancel()
        tickerJob = null

        debugJob?.cancel()
        debugJob = null
        debugCleanupJob?.cancel()
        debugCleanupJob = null
        debugActive = false

        Log.i(TAG, "Climb monitor stopped")
    }

    /**
     * Debug-only simulation. It feeds the same [ClimbSessionTracker] used by
     * the real native streams, so the whole service -> data type -> view path
     * can be exercised without a route or a real climb.
     */
    @Synchronized
    fun startDebugClimb() {
        debugCleanupJob?.cancel()
        debugCleanupJob = null
        debugJob?.cancel()

        debugActive = true
        val now = nowMs()
        tracker.onRideState(RideStatus.IDLE, now)
        tracker.onRideState(RideStatus.RECORDING, now)
        tracker.onClimbSignal(
            DEBUG_ELEVATION_TO_TOP,
            DEBUG_DISTANCE_TO_TOP,
            DEBUG_CLIMB_NUMBER,
            now,
        )

        debugJob = scope.launch {
            var tick = 0
            while (isActive) {
                val tickNow = nowMs()
                val watts = 200 + (tick % 20) * 3
                tracker.onPower(watts, tickNow)
                tracker.onClimbSignal(
                    DEBUG_ELEVATION_TO_TOP,
                    DEBUG_DISTANCE_TO_TOP,
                    DEBUG_CLIMB_NUMBER,
                    tickNow,
                )
                logPhaseIfChanged()

                if (tick % 5 == 0) {
                    val state = tracker.state.value
                    Log.i(
                        TAG,
                        "DEBUG tick=$tick watts=$watts avg=${state.averageWatts} " +
                            "elapsed=${state.elapsedSeconds}s phase=${state.phase}",
                    )
                }

                tick++
                delay(TICK_MS)
            }
        }

        Log.i(TAG, "DEBUG climb started")
    }

    @Synchronized
    fun pauseDebugRide() {
        tracker.onRideState(RideStatus.PAUSED, nowMs())
        logPhaseIfChanged()
        Log.i(TAG, "DEBUG ride paused")
    }

    @Synchronized
    fun resumeDebugRide() {
        tracker.onRideState(RideStatus.RECORDING, nowMs())
        logPhaseIfChanged()
        Log.i(TAG, "DEBUG ride resumed")
    }

    @Synchronized
    fun finishDebugClimb() {
        val now = nowMs()
        tracker.onRideState(RideStatus.RECORDING, now)
        tracker.onClimbSignal(0.0, 0.0, DEBUG_CLIMB_NUMBER, now)
        debugJob?.cancel()
        debugJob = null
        logPhaseIfChanged()
        Log.i(TAG, "DEBUG climb finish requested")

        debugCleanupJob?.cancel()
        debugCleanupJob = scope.launch {
            delay(3_000L)
            debugActive = false
            Log.i(TAG, "DEBUG simulation released to native streams")
        }
    }

    @Synchronized
    fun resetDebugSimulation() {
        debugJob?.cancel()
        debugJob = null
        debugCleanupJob?.cancel()
        debugCleanupJob = null
        debugActive = false
        val now = nowMs()
        tracker.onRideState(RideStatus.IDLE, now)
        if (lastNativeRideStatus != RideStatus.IDLE) {
            tracker.onRideState(lastNativeRideStatus, now)
        }
        logPhaseIfChanged()
        Log.i(TAG, "DEBUG simulation reset")
    }

    private fun handlePower(event: OnStreamState) {
        if (debugActive) return
        val streaming = event.state as? StreamState.Streaming
        if (streaming == null) {
            tracker.onPowerUnavailable(nowMs())
            logPhaseIfChanged()
            return
        }

        val values = streaming.dataPoint.values
        val watts = values[DataType.Field.POWER] ?: streaming.dataPoint.singleValue
        if (watts == null) {
            tracker.onPowerUnavailable(nowMs())
        } else {
            tracker.onPower(watts.roundToInt(), nowMs())
        }
        logPhaseIfChanged()
    }

    private fun handleElevationToTop(event: OnStreamState) {
        if (debugActive) return
        val values = (event.state as? StreamState.Streaming)?.dataPoint?.values ?: return
        val elevation = values[DataType.Field.ELEVATION_TO_TOP] ?: values[DataType.Field.SINGLE]
        if (elevation != null) {
            latestElevationToTop = elevation
        }
        updateClimbSignal()
    }

    private fun handleDistanceToTop(event: OnStreamState) {
        if (debugActive) return
        val values = (event.state as? StreamState.Streaming)?.dataPoint?.values ?: return
        val distance = values[DataType.Field.DISTANCE_TO_TOP] ?: values[DataType.Field.SINGLE]
        if (distance != null) {
            latestDistanceToTop = distance
        }
        updateClimbSignal()
    }

    private fun handleClimbNumber(event: OnStreamState) {
        if (debugActive) return
        val values = (event.state as? StreamState.Streaming)?.dataPoint?.values ?: return
        val number = values[DataType.Field.CLIMB_NUMBER] ?: values[DataType.Field.SINGLE]
        if (number != null) {
            latestClimbNumber = number.toInt()
        }
        updateClimbSignal()
    }

    private fun updateClimbSignal() {
        val now = nowMs()
        tracker.onClimbSignal(
            elevationToTop = latestElevationToTop,
            distanceToTop = latestDistanceToTop,
            number = latestClimbNumber,
            nowMs = now,
        )
        logClimbTransitionIfChanged()
        logPhaseIfChanged()

        // A low-rate heartbeat is useful for the on-device probe: it shows
        // whether the native Climber signal survives page changes.
        val onClimb = isOnClimb()
        if (onClimb && now - lastSignalLogAtMs >= 5_000L) {
            lastSignalLogAtMs = now
            Log.i(
                TAG,
                "climb signal elevToTop=$latestElevationToTop " +
                    "distToTop=$latestDistanceToTop climbNumber=$latestClimbNumber " +
                    "power=${tracker.state.value.currentWatts}",
            )
        }
    }

    private fun handleRideState(event: RideState) {
        val status = when (event) {
            is RideState.Idle -> RideStatus.IDLE
            is RideState.Recording -> RideStatus.RECORDING
            is RideState.Paused -> RideStatus.PAUSED
        }
        lastNativeRideStatus = status
        if (debugActive) return
        tracker.onRideState(status, nowMs())
        Log.i(TAG, "ride state=$status")
        logPhaseIfChanged()
    }

    private fun isOnClimb(): Boolean = when {
        latestElevationToTop != null -> latestElevationToTop!! > 0.0
        latestDistanceToTop != null -> latestDistanceToTop!! > 0.0
        else -> false
    }

    private fun logClimbTransitionIfChanged() {
        val onClimb = isOnClimb()
        if (onClimb != lastOnClimb) {
            lastOnClimb = onClimb
            Log.i(
                TAG,
                "climb active=$onClimb elevToTop=$latestElevationToTop " +
                    "distToTop=$latestDistanceToTop climbNumber=$latestClimbNumber",
            )
        }
    }

    private fun logPhaseIfChanged() {
        val current = tracker.state.value
        if (current.phase != lastPhase) {
            lastPhase = current.phase
            Log.i(
                TAG,
                "phase=${current.phase} avg=${current.averageWatts} " +
                    "current=${current.currentWatts} elapsed=${current.elapsedSeconds}s " +
                    "climb=${current.climbNumber} paused=${current.ridePaused}",
            )
        }
    }

    private fun logNavigation(state: OnNavigationState.NavigationState) {
        when (state) {
            is OnNavigationState.NavigationState.NavigatingRoute -> {
                Log.i(
                    TAG,
                    "navigation route='${state.name}' distance=${state.routeDistance} " +
                        "climbs=${state.climbs.size}",
                )
            }

            is OnNavigationState.NavigationState.NavigatingToDestination -> {
                Log.i(
                    TAG,
                    "navigation destination='${state.destination.name ?: state.destination.type}' " +
                        "climbs=${state.climbs.size}",
                )
            }

            is OnNavigationState.NavigationState.Idle -> {
                Log.i(TAG, "navigation idle")
            }
        }
    }

    private fun nowMs(): Long = SystemClock.elapsedRealtime()
}
