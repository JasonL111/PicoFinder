package local.ble.finder

/** A small injectable boundary; callers never need Context or a platform Vibrator. */
interface HapticDevice {
    fun isAvailableAndAllowed(): Boolean
    fun pulse()
    fun cancel()
}

class HapticFeedbackController(private val device: HapticDevice) {
    fun pulse() {
        try {
            if (device.isAvailableAndAllowed()) device.pulse()
        } catch (_: RuntimeException) { /* Optional feedback must never break finding. */ }
    }

    fun cancel() {
        try { device.cancel() }
        catch (_: RuntimeException) { /* Hardware/services/permissions may disappear. */ }
    }
}
