package com.powerclimb.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimbSessionTrackerTest {

    @Test
    fun `constant power produces time weighted average`() {
        val tracker = ClimbSessionTracker()
        tracker.onRideState(RideStatus.RECORDING, 0)
        tracker.onClimbSignal(100.0, 1_000.0, 1, 0)

        for (second in 0..9) {
            tracker.onPower(200, second * 1_000L)
        }

        val state = tracker.state.value
        assertEquals(ClimbPhase.ACTIVE, state.phase)
        assertEquals(200, state.averageWatts)
        assertEquals(9, state.elapsedSeconds)
        assertEquals(1, state.climbNumber)
    }

    @Test
    fun `climb end finalizes and next climb starts fresh`() {
        val tracker = ClimbSessionTracker()
        tracker.onRideState(RideStatus.RECORDING, 0)
        tracker.onClimbSignal(100.0, 1_000.0, 1, 0)

        for (second in 0..5) {
            tracker.onPower(200, second * 1_000L)
        }

        tracker.onClimbSignal(0.0, 0.0, 1, 10_000)

        assertEquals(ClimbPhase.FINISHED, tracker.state.value.phase)
        assertEquals(200, tracker.state.value.averageWatts)
        assertEquals(10, tracker.state.value.elapsedSeconds)

        tracker.onClimbSignal(100.0, 1_000.0, 2, 20_000)
        tracker.onPower(200, 20_000)
        tracker.onPower(200, 21_000)

        val next = tracker.state.value
        assertEquals(ClimbPhase.ACTIVE, next.phase)
        assertEquals(200, next.averageWatts)
        assertEquals(2, next.climbNumber)
        assertEquals(1, next.elapsedSeconds)
    }

    @Test
    fun `distance signal can start a climb when elevation is zero`() {
        val tracker = ClimbSessionTracker()
        tracker.onRideState(RideStatus.RECORDING, 0)
        tracker.onClimbSignal(0.0, 1_000.0, 1, 0)
        tracker.onPower(200, 0)
        tracker.onPower(200, 1_000)

        val state = tracker.state.value
        assertEquals(ClimbPhase.ACTIVE, state.phase)
        assertEquals(200, state.averageWatts)
    }

    @Test
    fun `brief climb signal loss does not reset`() {
        val tracker = ClimbSessionTracker()
        tracker.onRideState(RideStatus.RECORDING, 0)
        tracker.onClimbSignal(100.0, 1_000.0, 1, 0)

        for (second in 0..3) {
            tracker.onPower(200, second * 1_000L)
        }

        tracker.onClimbSignal(0.0, 0.0, 1, 4_000)
        tracker.onClimbSignal(100.0, 1_000.0, 1, 5_000)
        tracker.onPower(200, 5_000)
        tracker.onPower(200, 6_000)

        val state = tracker.state.value
        assertEquals(ClimbPhase.ACTIVE, state.phase)
        assertEquals(200, state.averageWatts)
        assertEquals(1, state.climbNumber)
    }

    @Test
    fun `pause freezes accumulation and resume continues`() {
        val tracker = ClimbSessionTracker()
        tracker.onRideState(RideStatus.RECORDING, 0)
        tracker.onClimbSignal(100.0, 1_000.0, 1, 0)

        for (second in 0..5) {
            tracker.onPower(200, second * 1_000L)
        }

        tracker.onRideState(RideStatus.PAUSED, 5_500)
        tracker.onPower(200, 6_000)
        tracker.onPower(200, 7_000)

        assertTrue(tracker.state.value.ridePaused)
        assertEquals(200, tracker.state.value.averageWatts)

        tracker.onRideState(RideStatus.RECORDING, 7_500)
        tracker.onPower(200, 7_500)
        tracker.onPower(200, 8_500)

        val state = tracker.state.value
        assertEquals(ClimbPhase.ACTIVE, state.phase)
        assertEquals(200, state.averageWatts)
        assertEquals(6, state.elapsedSeconds)
    }

    @Test
    fun `sensor gap longer than max gap is excluded`() {
        val tracker = ClimbSessionTracker()
        tracker.onRideState(RideStatus.RECORDING, 0)
        tracker.onClimbSignal(100.0, 1_000.0, 1, 0)

        tracker.onPower(200, 0)
        tracker.onPower(200, 1_000)
        tracker.onPower(200, 6_000)
        tracker.onPower(200, 7_000)

        val state = tracker.state.value
        assertEquals(200, state.averageWatts)
        assertEquals(7, state.elapsedSeconds)
    }

    @Test
    fun `explicit summit freezes average before the finish grace`() {
        val tracker = ClimbSessionTracker()
        tracker.onRideState(RideStatus.RECORDING, 0)
        tracker.onClimbSignal(100.0, 1_000.0, 1, 0)

        for (second in 0..5) {
            tracker.onPower(200, second * 1_000L)
        }

        // Native Climber reports the summit at 6s. Power after that point
        // (for example 0 W while descending) must not pollute the average.
        tracker.onClimbSignal(0.0, 0.0, 1, 6_000)
        tracker.onPower(0, 7_000)
        tracker.onTick(8_000)

        val state = tracker.state.value
        assertEquals(ClimbPhase.FINISHED, state.phase)
        assertEquals(200, state.averageWatts)
        assertEquals(6, state.elapsedSeconds)
    }

    @Test
    fun `clock stays frozen during the explicit summit grace`() {
        val tracker = ClimbSessionTracker()
        tracker.onRideState(RideStatus.RECORDING, 0)
        tracker.onClimbSignal(100.0, 1_000.0, 1, 0)

        for (second in 0..5) {
            tracker.onPower(200, second * 1_000L)
        }
        tracker.onClimbSignal(100.0, 1_000.0, 1, 5_000)
        tracker.onClimbSignal(0.0, 0.0, 1, 6_000)
        tracker.onTick(7_000)
        tracker.onTick(8_000)

        val state = tracker.state.value
        assertEquals(ClimbPhase.FINISHED, state.phase)
        assertEquals(200, state.averageWatts)
        assertEquals(6, state.elapsedSeconds)
    }

    @Test
    fun `watchdog ends climb when stream stops`() {
        val tracker = ClimbSessionTracker()
        tracker.onRideState(RideStatus.RECORDING, 0)
        tracker.onClimbSignal(100.0, 1_000.0, 1, 0)

        tracker.onPower(200, 0)
        tracker.onPower(200, 1_000)
        tracker.onPower(200, 2_000)
        tracker.onTick(8_000)

        val state = tracker.state.value
        assertEquals(ClimbPhase.FINISHED, state.phase)
        assertEquals(200, state.averageWatts)
        assertEquals(8, state.elapsedSeconds)
    }

    @Test
    fun `no power means no average`() {
        val tracker = ClimbSessionTracker()
        tracker.onRideState(RideStatus.RECORDING, 0)
        tracker.onClimbSignal(100.0, 1_000.0, 1, 0)
        tracker.onTick(6_000)

        val state = tracker.state.value
        assertEquals(ClimbPhase.FINISHED, state.phase)
        assertNull(state.averageWatts)
    }

    @Test
    fun `ride idle preserves last value and next ride clears it`() {
        val tracker = ClimbSessionTracker()
        tracker.onRideState(RideStatus.RECORDING, 0)
        tracker.onClimbSignal(100.0, 1_000.0, 1, 0)
        tracker.onPower(200, 0)
        tracker.onPower(200, 1_000)

        tracker.onRideState(RideStatus.IDLE, 2_000)

        val finished = tracker.state.value
        assertEquals(ClimbPhase.FINISHED, finished.phase)
        assertEquals(200, finished.averageWatts)
        assertEquals(2, finished.elapsedSeconds)

        tracker.onRideState(RideStatus.RECORDING, 3_000)

        val nextRide = tracker.state.value
        assertEquals(ClimbPhase.IDLE, nextRide.phase)
        assertNull(nextRide.averageWatts)
        assertNull(nextRide.currentWatts)
        assertNull(nextRide.climbNumber)
        assertEquals(0, nextRide.elapsedSeconds)
    }

    @Test
    fun `debug reset clears the last value`() {
        val tracker = ClimbSessionTracker()
        tracker.onRideState(RideStatus.RECORDING, 0)
        tracker.onClimbSignal(100.0, 1_000.0, 1, 0)
        tracker.onPower(200, 0)
        tracker.onPower(200, 1_000)
        tracker.onRideState(RideStatus.IDLE, 2_000)

        tracker.reset()

        val state = tracker.state.value
        assertEquals(ClimbPhase.IDLE, state.phase)
        assertNull(state.averageWatts)
        assertEquals(0, state.elapsedSeconds)
    }

    @Test
    fun `climb number change starts a new session`() {
        val tracker = ClimbSessionTracker()
        tracker.onRideState(RideStatus.RECORDING, 0)
        tracker.onClimbSignal(100.0, 1_000.0, 1, 0)

        tracker.onPower(200, 0)
        tracker.onPower(200, 1_000)
        tracker.onPower(200, 2_000)

        tracker.onClimbSignal(100.0, 1_000.0, 2, 3_000)
        tracker.onPower(300, 3_000)
        tracker.onPower(300, 4_000)

        val state = tracker.state.value
        assertEquals(ClimbPhase.ACTIVE, state.phase)
        assertEquals(2, state.climbNumber)
        assertEquals(300, state.averageWatts)
    }

    @Test
    fun `zero watts is a valid average`() {
        val tracker = ClimbSessionTracker()
        tracker.onRideState(RideStatus.RECORDING, 0)
        tracker.onClimbSignal(100.0, 1_000.0, 1, 0)

        tracker.onPower(0, 0)
        tracker.onPower(0, 1_000)
        tracker.onPower(0, 2_000)

        val state = tracker.state.value
        assertEquals(0, state.averageWatts)
        assertEquals(2, state.elapsedSeconds)
    }
}
