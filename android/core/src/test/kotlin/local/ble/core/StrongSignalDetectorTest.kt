package local.ble.core

import org.junit.Assert.*
import org.junit.Test

class StrongSignalDetectorTest {
    private fun feed(d: StrongSignalDetector, from: Long, to: Long, rssi: Double = -45.0, step: Long = 250) =
        (from..to step step).count { d.onSample(rssi, it, it) }

    @Test fun ordinarySignalsAndSingleSpikeDoNotConfirm() {
        val d = StrongSignalDetector()
        assertEquals(0, feed(d, 0, 10000, -60.0))
        assertFalse(d.onSample(-40.0, 10250, 10250))
        assertFalse(d.isVeryStrong)
        assertEquals(0, feed(d, 10500, 15000, -46.0))
        assertFalse(d.isVeryStrong)
    }

    @Test fun fixedInclusiveEnterThresholdRequiresTwoSecondsAndFreshSamples() {
        val d = StrongSignalDetector()
        assertEquals(0, feed(d, 0, 1750))
        assertFalse(d.isVeryStrong)
        assertFalse(d.onSample(-45.0, 1999, 1999))
        assertTrue(d.onSample(-45.0, 2000, 2000))
        assertTrue(d.isVeryStrong)
        assertEquals(0, feed(d, 2250, 30000))
    }

    @Test fun jitterDoesNotEnterOrRepeatedlyAlert() {
        val d = StrongSignalDetector()
        repeat(25) { assertFalse(d.onSample(if (it % 2 == 0) -44.0 else -46.0, it * 250L, it * 250L)) }
        assertFalse(d.isVeryStrong)
        assertEquals(1, feed(d, 6250, 8250, -44.0))
        repeat(100) {
            val time = 8500 + it * 250L
            assertFalse(d.onSample(if (it % 2 == 0) -44.0 else -46.0, time, time))
        }
        assertTrue(d.isVeryStrong)
    }

    @Test fun inclusiveExitNeedsThreeSecondsBeforeReentry() {
        val d = StrongSignalDetector()
        assertEquals(1, feed(d, 0, 2000))
        assertEquals(0, feed(d, 2250, 12250, -50.9))
        assertEquals(0, feed(d, 12500, 14500))
        // A short dip, or an intervening stronger sample, cannot rearm.
        feed(d, 14750, 17500, -51.0)
        assertTrue(d.isVeryStrong)
        assertFalse(d.onSample(-50.0, 17750, 17750))
        feed(d, 18000, 20750, -51.0)
        assertTrue(d.isVeryStrong)
        assertFalse(d.onSample(-51.0, 21000, 21000))
        assertFalse(d.isVeryStrong)
        assertEquals(1, feed(d, 21250, 23250))
    }

    @Test fun cooldownConsumesAnEntryWithoutDelayedOrPeriodicAlert() {
        val d = StrongSignalDetector()
        assertEquals(1, feed(d, 0, 2000))
        feed(d, 2250, 5250, -51.0)
        assertFalse(d.isVeryStrong)
        assertEquals(0, feed(d, 5500, 7500)) // Entry during the 10-second cooldown.
        assertTrue(d.isVeryStrong)
        assertEquals(0, feed(d, 7750, 15000)) // No deferred pulse when cooldown elapses.
        feed(d, 15250, 18250, -51.0)
        assertEquals(1, feed(d, 18500, 20500))
    }

    @Test fun tooFewSamplesAndSparseMeasurementsCannotProveContinuity() {
        val d = StrongSignalDetector()
        assertEquals(0, feed(d, 0, 2000, step = 1000)) // Only three samples.
        assertFalse(d.isVeryStrong)
        assertEquals(0, feed(d, 10000, 20000, step = 2000)) // Every gap breaks evidence.
        assertFalse(d.isVeryStrong)
    }

    @Test fun duplicatesOutOfOrderAndMillisecondBurstsCannotCreateDuration() {
        val d = StrongSignalDetector()
        repeat(5000) { assertFalse(d.onSample(-40.0, 0, 0)) }
        assertFalse(d.onSample(-40.0, 1000, 1000))
        repeat(5000) { assertFalse(d.onSample(-40.0, 1000, 1000)) }
        assertFalse(d.onSample(-40.0, 500, 1000))
        assertFalse(d.onSample(-40.0, 2000, 2000))
        assertFalse(d.isVeryStrong)
        d.reset()
        assertEquals(0, feed(d, 3000, 3099, -40.0, step = 1))
        assertFalse(d.onSample(-40.0, 5000, 5000))
        assertFalse(d.isVeryStrong)
    }

    @Test fun staleFutureInvalidSamplesAndUiTicksCannotTrigger() {
        val d = StrongSignalDetector()
        assertFalse(d.onSample(-40.0, 0, 0))
        repeat(100) { d.expire(it * 1000L) }
        assertFalse(d.isVeryStrong)
        for (time in 250L..3000L step 250) {
            assertFalse(d.onSample(-40.0, time, time + 1001))
            assertFalse(d.onSample(-40.0, time + 1, time))
            assertFalse(d.onSample(Double.NaN, time, time))
            assertFalse(d.onSample(0.0, time, time))
        }
        assertFalse(d.isVeryStrong)
        assertEquals(1, feed(d, 4000, 6000))
    }

    @Test fun gapsHideConfirmationButDoNotUnlockRepeatedStrongEntry() {
        val d = StrongSignalDetector()
        assertEquals(1, feed(d, 0, 2000))
        d.expire(3001)
        assertFalse(d.isVeryStrong)
        assertEquals(0, feed(d, 15000, 20000))
        assertTrue(d.isVeryStrong)
        // The weak rearm interval must also have enough samples and no gaps.
        feed(d, 22000, 28000, -60.0, step = 2000)
        assertEquals(0, feed(d, 28250, 30250))
    }

    @Test fun sessionResetClearsEvidenceAndLockButKeepsGlobalCooldown() {
        val d = StrongSignalDetector()
        feed(d, 0, 1750)
        d.reset() // Lost / stopped / left the session.
        assertFalse(d.onSample(-45.0, 2000, 2000))
        assertFalse(d.isVeryStrong)
        assertEquals(1, feed(d, 2250, 4000))
        d.reset()
        assertEquals(0, feed(d, 4250, 6250))
        assertTrue(d.isVeryStrong)
        d.reset()
        assertFalse(d.isVeryStrong)
        assertEquals(1, feed(d, 14000, 16000))
    }

    @Test fun calibrationAndDeviceSettingsCannotChangeTheFixedThreshold() {
        listOf(DeviceSettings("钥匙", -20.0, 1.0), DeviceSettings("背包", -100.0, 6.0, 120000, false)).forEach { settings ->
            assertTrue(settings.isValid())
            val d = StrongSignalDetector()
            assertEquals(0, feed(d, 0, 3000, -45.01))
            assertEquals(1, feed(d, 3250, 5250, -45.0))
        }
    }
}
