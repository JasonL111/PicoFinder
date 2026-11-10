package local.ble.finder

import local.ble.core.*
import org.junit.Assert.*
import org.junit.Test

class FindSessionControllerTest {
    private class FakeHapticDevice : HapticDevice {
        var available = true
        var allowed = true
        var pulses = 0
        var cancels = 0
        var fail = false
        override fun isAvailableAndAllowed(): Boolean {
            if (fail) throw SecurityException("System restricted vibration")
            return available && allowed
        }
        override fun pulse() { pulses++ }
        override fun cancel() { cancels++ }
    }
    private class Fixture {
        val hardware = FakeHapticDevice()
        val session = FindSessionController(HapticFeedbackController(hardware))
        fun context(id: Long? = 1, scanning: Boolean = true, foreground: Boolean = true, demo: Boolean = false, now: Long = 0) =
            session.updateContext(id, scanning, foreground, demo, now)
        fun state(time: Long, id: Long = 1, enabled: Boolean = true, rssi: Double = -45.0, status: DeviceStatus = DeviceStatus.PRESENT) =
            DeviceState(id, 1, "钥匙", time, time, rssi.toInt(), rssi, 0.1, null, status, Trend.STABLE,
                DeviceSettings(nearAlertVibration = enabled), -59, 4)
        fun feed(from: Long = 0, to: Long = 2000, id: Long = 1, enabled: Boolean = true, rssi: Double = -45.0) {
            for (time in from..to step 250) session.onMeasurement(state(time, id, enabled, rssi), time, time)
        }
    }

    @Test fun onePulseForRealForegroundFindAndNoRepeat() {
        val f = Fixture(); f.context(); f.feed()
        assertEquals(1, f.hardware.pulses)
        assertTrue(f.session.isVeryStrong)
        f.feed(2250, 20000)
        assertEquals(1, f.hardware.pulses)
    }

    @Test fun disabledVibrationKeepsVisualConfirmationAndCancelsImmediately() {
        val f = Fixture(); f.context(); f.feed(enabled = false)
        assertEquals(0, f.hardware.pulses)
        assertTrue(f.session.isVeryStrong)
        f.session.updateVibrationEnabled(true)
        f.feed(2250, 15000) // Enabling does not alert from an old entry.
        assertEquals(0, f.hardware.pulses)
        val cancels = f.hardware.cancels
        f.session.updateVibrationEnabled(false)
        assertEquals(cancels + 1, f.hardware.cancels)
    }

    @Test fun switchingDevicesDiscardsEvidenceAndIgnoresOtherDevices() {
        val f = Fixture(); f.context(); f.feed(to = 1750)
        f.context(id = 2, now = 2000)
        f.feed(2000, 5000, id = 1)
        assertEquals(0, f.hardware.pulses)
        f.feed(5250, 7000, id = 2)
        assertEquals(0, f.hardware.pulses)
        f.feed(7250, 7250, id = 2)
        assertEquals(1, f.hardware.pulses)
    }

    @Test fun listSettingsStoppedAndBackgroundCannotAlertOrReuseOldEvidence() {
        val transitions: List<(Fixture) -> Unit> = listOf(
            { it.context(id = null, now = 2000) }, // List/settings have no finding selection.
            { it.context(scanning = false, now = 2000) },
            { it.context(foreground = false, now = 2000) },
        )
        transitions.forEach { transition ->
            val f = Fixture(); f.context(); f.feed(to = 1750)
            transition(f)
            f.feed(2000, 15000)
            assertEquals(0, f.hardware.pulses)
            assertFalse(f.session.isVeryStrong)
            f.context(now = 16000)
            f.feed(14000, 15750) // Queued measurements from before this session are ignored.
            f.feed(16000, 17750)
            assertEquals(0, f.hardware.pulses)
            f.feed(18000, 18000)
            assertEquals(1, f.hardware.pulses)
        }
    }

    @Test fun lossAndTicksResetConfirmationWithoutProducingAlerts() {
        val f = Fixture(); f.context(); f.feed(to = 1750)
        f.session.refresh(f.state(1750, status = DeviceStatus.LOST), 12000)
        f.feed(12250, 14000)
        assertEquals(0, f.hardware.pulses)
        f.feed(14250, 14250)
        assertEquals(1, f.hardware.pulses)
        f.session.refresh(f.state(14250), 15251)
        assertFalse(f.session.isVeryStrong)
        repeat(30) { f.session.refresh(f.state(14250), 16000 + it * 1000L) }
        assertEquals(1, f.hardware.pulses)
    }

    @Test fun demoCanConfirmVisuallyButNeverRequestsRealVibration() {
        val f = Fixture(); f.context(demo = true); f.feed(to = 20000)
        assertTrue(f.session.isVeryStrong)
        assertEquals(0, f.hardware.pulses)
    }

    @Test fun cooldownSurvivesSwitchingStoppingAndRestarting() {
        val f = Fixture(); f.context(); f.feed()
        f.context(id = null, now = 2250)
        f.context(id = 2, now = 2500)
        f.feed(2500, 4500, id = 2)
        assertTrue(f.session.isVeryStrong)
        assertEquals(1, f.hardware.pulses)
        f.context(scanning = false, now = 5000)
        f.context(now = 15000)
        f.feed(15000, 17000)
        assertEquals(2, f.hardware.pulses)
    }

    @Test fun freshWeakStatusSamplesStillCountTowardsTheExitThreshold() {
        val f = Fixture(); f.context(); f.feed()
        // V1 labels RSSI below -85 as WEAK even when new packets keep arriving.
        for (time in 2250L..12250L step 250) {
            f.session.onMeasurement(f.state(time, rssi = -90.0, status = DeviceStatus.WEAK), time, time)
        }
        assertFalse(f.session.isVeryStrong)
        f.feed(12500, 14500)
        assertEquals(2, f.hardware.pulses)
    }

    @Test fun noHardwareSystemOptOutAndRestrictedApiDegradeQuietly() {
        repeat(3) { mode ->
            val f = Fixture()
            if (mode == 0) f.hardware.available = false
            if (mode == 1) f.hardware.allowed = false
            if (mode == 2) f.hardware.fail = true
            f.context(); f.feed()
            assertTrue(f.session.isVeryStrong)
            assertEquals(0, f.hardware.pulses)
        }
        val throwingDriver = object : HapticDevice {
            override fun isAvailableAndAllowed() = true
            override fun pulse(): Unit = throw IllegalStateException("Unavailable service")
            override fun cancel(): Unit = throw SecurityException("Revoked permission")
        }
        HapticFeedbackController(throwingDriver).apply { pulse(); cancel() }
    }

    @Test fun acceptedRepositoryMeasurementsUseExistingSmoothing() {
        val store = object : DeviceStore {
            override fun load() = emptyMap<Long, DeviceSettings>()
            override fun save(devices: Map<Long, DeviceSettings>) = Unit
        }
        val repository = DeviceRepository(store)
        val f = Fixture(); f.context()
        for (time in 0L..10000L step 250) {
            val rssi = if (time == 3000L) -40 else -60
            assertTrue(repository.receive(ScanSample(Advertisement(1, 1, 0, -59, null), rssi, time, time)))
            f.session.onMeasurement(repository.snapshot(time, true).single(), time, time)
        }
        assertFalse(f.session.isVeryStrong)
        assertEquals(0, f.hardware.pulses)
    }

    @Test fun duplicateRawPeaksCannotPumpTheFilterIntoAFalseStrongEntry() {
        val repository = DeviceRepository(object : DeviceStore {
            override fun load() = emptyMap<Long, DeviceSettings>()
            override fun save(devices: Map<Long, DeviceSettings>) = Unit
        })
        val f = Fixture(); f.context()
        fun receive(rssi: Int, time: Long) {
            if (repository.receive(ScanSample(Advertisement(1, 1, 0, -59, null), rssi, time, time))) {
                f.session.onMeasurement(repository.snapshot(time, true).single(), time, time)
            }
        }
        receive(-60, 0)
        repeat(100) { receive(-10, 0) }
        for (time in 500L..15000L step 500) receive(-60, time)
        assertEquals(-60.0, repository.snapshot(15000, true).single().smoothedRssi!!, 0.0)
        assertFalse(f.session.isVeryStrong)
        assertEquals(0, f.hardware.pulses)
    }
}
