package com.powerclimb.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

enum class RideStatus {
    IDLE,
    RECORDING,
    PAUSED,
}

enum class ClimbPhase {
    IDLE,
    ACTIVE,
    FINISHED,
}

/**
 * UI-facing state of the current (or last finished) native Climber climb.
 *
 * [averageWatts] is the time-weighted average power of the active climb while
 * [phase] is [ClimbPhase.ACTIVE], and the final average while it is
 * [ClimbPhase.FINISHED].
 */
data class ClimbPowerState(
    val phase: ClimbPhase = ClimbPhase.IDLE,
    val averageWatts: Int? = null,
    val currentWatts: Int? = null,
    val elapsedSeconds: Int = 0,
    val climbNumber: Int? = null,
    val ridePaused: Boolean = false,
) {
    companion object {
        val PREVIEW = ClimbPowerState(
            phase = ClimbPhase.ACTIVE,
            averageWatts = 247,
            currentWatts = 251,
            elapsedSeconds = 222,
            climbNumber = 2,
        )
    }
}

/**
 * Pure Kotlin state machine that keeps a time-weighted average of power during
 * a native Karoo Climber climb.
 *
 * Design rules:
 * - A climb starts on the first active climb packet.
 * - A brief loss of the climb signal (<= [endGraceMs]) does not end the climb.
 * - A climb ends after [endGraceMs] without an active packet, when the climb
 *   number changes, or when the ride stops.
 * - Power is integrated only while the ride is recording and not paused.
 * - Sensor gaps longer than [maxPowerGapMs] are not added to the average.
 * - The first power sample after a climb start is the reference point; no
 *   invented power is added for the time before it.
 */
class ClimbSessionTracker(
    private val endGraceMs: Long = 5_000L,
    private val maxPowerGapMs: Long = 3_000L,
    private val minSessionMs: Long = 1_000L,
) {
    private val _state = MutableStateFlow(ClimbPowerState())
    val state: StateFlow<ClimbPowerState> = _state.asStateFlow()

    private var phase = ClimbPhase.IDLE
    private var rideRecording = false
    private var ridePaused = false
    private var climbNumber: Int? = null
    private var lastActiveAtMs: Long? = null
    private var lastPowerAtMs: Long? = null
    private var lastPowerWatts: Int? = null
    private var accumulatedWattMs = 0.0
    private var accumulatedMs = 0L
    private var currentWatts: Int? = null
    private var finalAverageWatts: Int? = null

    @Synchronized
    fun onRideState(status: RideStatus, nowMs: Long) {
        when (status) {
            RideStatus.IDLE -> {
                rideRecording = false
                ridePaused = false
                resetSession()
                publish()
            }

            RideStatus.RECORDING -> {
                rideRecording = true
                if (ridePaused) {
                    ridePaused = false
                    lastPowerAtMs = null
                    lastPowerWatts = null
                }
                publish()
            }

            RideStatus.PAUSED -> {
                rideRecording = true
                if (!ridePaused) {
                    flushPower(nowMs)
                    lastPowerAtMs = null
                    lastPowerWatts = null
                }
                ridePaused = true
                publish()
            }
        }
    }

    @Synchronized
    fun onClimbSignal(
        elevationToTop: Double?,
        distanceToTop: Double?,
        number: Int?,
        nowMs: Long,
    ) {
        val onClimb = when {
            elevationToTop != null -> elevationToTop > 0.0
            distanceToTop != null -> distanceToTop > 0.0
            else -> false
        }

        if (!rideRecording) {
            if (phase == ClimbPhase.ACTIVE) finish(nowMs)
            publish()
            return
        }

        if (onClimb) {
            val newNumber = number?.takeIf { it > 0 }
            if (phase == ClimbPhase.ACTIVE) {
                if (newNumber != null && climbNumber != null && newNumber != climbNumber) {
                    finish(nowMs)
                    startSession(nowMs, newNumber)
                } else {
                    if (newNumber != null) climbNumber = newNumber
                    lastActiveAtMs = nowMs
                }
            } else {
                startSession(nowMs, newNumber)
            }
        } else if (
            phase == ClimbPhase.ACTIVE &&
            lastActiveAtMs != null &&
            nowMs - lastActiveAtMs!! >= endGraceMs
        ) {
            finish(nowMs)
        }

        publish()
    }

    @Synchronized
    fun onPower(watts: Int?, nowMs: Long) {
        if (watts == null) {
            onPowerUnavailable(nowMs)
            return
        }

        currentWatts = watts

        if (phase == ClimbPhase.ACTIVE && rideRecording && !ridePaused) {
            val lastAt = lastPowerAtMs
            val lastW = lastPowerWatts
            if (lastAt == null || lastW == null) {
                lastPowerAtMs = nowMs
                lastPowerWatts = watts
            } else {
                val dt = nowMs - lastAt
                if (dt > 0 && dt <= maxPowerGapMs) {
                    accumulatedWattMs += lastW.toDouble() * dt
                    accumulatedMs += dt
                }
                lastPowerAtMs = nowMs
                lastPowerWatts = watts
            }
        }

        publish()
    }

    @Synchronized
    fun onPowerUnavailable(nowMs: Long) {
        if (phase == ClimbPhase.ACTIVE && rideRecording && !ridePaused) {
            flushPower(nowMs)
        }
        lastPowerAtMs = null
        lastPowerWatts = null
        currentWatts = null
        publish()
    }

    /**
     * Periodic watchdog. Call once per second so a climb also ends when the
     * native stream simply stops emitting instead of sending an explicit zero.
     */
    @Synchronized
    fun onTick(nowMs: Long) {
        val lastActive = lastActiveAtMs
        if (
            phase == ClimbPhase.ACTIVE &&
            !ridePaused &&
            lastActive != null &&
            nowMs - lastActive >= endGraceMs
        ) {
            finish(nowMs)
            publish()
        }
    }

    private fun startSession(nowMs: Long, number: Int?) {
        phase = ClimbPhase.ACTIVE
        climbNumber = number
        lastActiveAtMs = nowMs
        lastPowerAtMs = null
        lastPowerWatts = null
        accumulatedWattMs = 0.0
        accumulatedMs = 0L
        finalAverageWatts = null
    }

    private fun finish(nowMs: Long) {
        if (phase != ClimbPhase.ACTIVE) return

        flushPower(nowMs)
        finalAverageWatts = averageOrNull()
        phase = ClimbPhase.FINISHED
        lastActiveAtMs = null
        lastPowerAtMs = null
        lastPowerWatts = null
    }

    private fun flushPower(nowMs: Long) {
        val lastAt = lastPowerAtMs ?: return
        val lastW = lastPowerWatts ?: return
        val dt = nowMs - lastAt
        if (dt > 0 && dt <= maxPowerGapMs) {
            accumulatedWattMs += lastW.toDouble() * dt
            accumulatedMs += dt
        }
        lastPowerAtMs = null
        lastPowerWatts = null
    }

    private fun resetSession() {
        phase = ClimbPhase.IDLE
        climbNumber = null
        lastActiveAtMs = null
        lastPowerAtMs = null
        lastPowerWatts = null
        accumulatedWattMs = 0.0
        accumulatedMs = 0L
        currentWatts = null
        finalAverageWatts = null
    }

    private fun averageOrNull(): Int? {
        if (accumulatedMs < minSessionMs) return null
        return (accumulatedWattMs / accumulatedMs).roundToInt()
    }

    private fun publish() {
        val average = when (phase) {
            ClimbPhase.ACTIVE -> averageOrNull()
            ClimbPhase.FINISHED -> finalAverageWatts
            ClimbPhase.IDLE -> null
        }

        _state.value = ClimbPowerState(
            phase = phase,
            averageWatts = average,
            currentWatts = currentWatts,
            elapsedSeconds = (accumulatedMs / 1_000L).toInt(),
            climbNumber = climbNumber,
            ridePaused = ridePaused,
        )
    }
}
