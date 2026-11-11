package local.ble.core

// Product constants, shared by every device. These are deliberately not settings.
private const val STRONG_SIGNAL_ENTER_RSSI_DBM = -45.0
private const val STRONG_SIGNAL_EXIT_RSSI_DBM = -51.0
private const val ENTER_DURATION_MS = 2000L
private const val EXIT_DURATION_MS = 3000L
private const val ALERT_COOLDOWN_MS = 10000L
private const val MAX_SAMPLE_GAP_MS = 1000L
private const val MIN_COUNTED_SAMPLE_GAP_MS = 100L
private const val MIN_ENTER_SAMPLES = 6
private const val MIN_EXIT_SAMPLES = 8

/** Consumes the repository's median + EMA output, never raw RSSI or UI ticks.
 *
 * Measurement and receipt times use the same monotonic clock. A fresh, strictly newer
 * measurement is required on every call; bursts cannot manufacture a stable interval.
 * Missing packets hide the visual confirmation, but only a sustained weak interval (or
 * a session reset) unlocks another entry. Resets retain the cooldown across sessions.
 */
class StrongSignalDetector {
    var isVeryStrong: Boolean = false
        private set
    private var entryLocked = false
    private var lastSampleMs: Long? = null
    private var lastAlertMs: Long? = null
    private var enterStartMs: Long? = null
    private var enterCount = 0
    private var enterCountedMs = 0L
    private var exitStartMs: Long? = null
    private var exitCount = 0
    private var exitCountedMs = 0L

    /** Returns a one-shot alert request. The caller applies foreground/device/user policy. */
    fun onSample(smoothedRssi: Double, sampleMs: Long, nowMs: Long): Boolean {
        if (!smoothedRssi.isFinite() || smoothedRssi !in -110.0..-10.0 || sampleMs < 0 ||
            sampleMs > nowMs || nowMs - sampleMs > MAX_SAMPLE_GAP_MS ||
            lastSampleMs?.let { sampleMs <= it } == true) return false

        if (lastSampleMs?.let { sampleMs - it > MAX_SAMPLE_GAP_MS } == true) clearEvidence()
        lastSampleMs = sampleMs

        if (smoothedRssi <= STRONG_SIGNAL_EXIT_RSSI_DBM) {
            enterStartMs = null; enterCount = 0
            if (exitStartMs == null) {
                exitStartMs = sampleMs; exitCount = 1; exitCountedMs = sampleMs
            } else if (sampleMs - exitCountedMs >= MIN_COUNTED_SAMPLE_GAP_MS) {
                exitCount++; exitCountedMs = sampleMs
            }
            if (sampleMs - exitStartMs!! >= EXIT_DURATION_MS && exitCount >= MIN_EXIT_SAMPLES) {
                entryLocked = false
                isVeryStrong = false
            }
        } else {
            exitStartMs = null; exitCount = 0
            if (smoothedRssi >= STRONG_SIGNAL_ENTER_RSSI_DBM) {
                if (enterStartMs == null) {
                    enterStartMs = sampleMs; enterCount = 1; enterCountedMs = sampleMs
                } else if (sampleMs - enterCountedMs >= MIN_COUNTED_SAMPLE_GAP_MS) {
                    enterCount++; enterCountedMs = sampleMs
                }
                if (sampleMs - enterStartMs!! >= ENTER_DURATION_MS && enterCount >= MIN_ENTER_SAMPLES) {
                    isVeryStrong = true
                    if (!entryLocked) {
                        // Consume this entry even during cooldown; no delayed periodic alert.
                        entryLocked = true
                        if (lastAlertMs?.let { nowMs - it < ALERT_COOLDOWN_MS } != true) {
                            lastAlertMs = nowMs
                            return true
                        }
                    }
                }
            } else {
                enterStartMs = null; enterCount = 0
            }
        }
        return false
    }

    /** Timer calls can only remove evidence, never confirm a signal or request an alert. */
    fun expire(nowMs: Long) {
        if (lastSampleMs?.let { nowMs - it > MAX_SAMPLE_GAP_MS } == true) clearEvidence()
    }

    fun reset() {
        clearEvidence()
        entryLocked = false
        lastSampleMs = null
    }

    private fun clearEvidence() {
        isVeryStrong = false
        enterStartMs = null; enterCount = 0
        exitStartMs = null; exitCount = 0
    }
}
