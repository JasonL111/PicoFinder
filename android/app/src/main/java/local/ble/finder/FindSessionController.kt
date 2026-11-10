package local.ble.finder

import local.ble.core.DeviceState
import local.ble.core.DeviceStatus
import local.ble.core.StrongSignalDetector

/** Main-thread session policy. Only the scanner callback may call onMeasurement. */
class FindSessionController(private val haptics: HapticFeedbackController) {
    private val detector = StrongSignalDetector()
    private var selectedId: Long? = null
    private var scanning = false
    private var foreground = false
    private var demo = false
    private var validAfterMs = 0L
    private var vibrationEnabled = true
    val isVeryStrong get() = detector.isVeryStrong
    private val active get() = scanning && foreground && selectedId != null

    fun updateContext(deviceId: Long?, scanning: Boolean, foreground: Boolean, demo: Boolean, nowMs: Long) {
        if (selectedId != deviceId || this.scanning != scanning || this.foreground != foreground || this.demo != demo) {
            detector.reset()
            haptics.cancel()
            validAfterMs = nowMs
        }
        selectedId = deviceId
        this.scanning = scanning
        this.foreground = foreground
        this.demo = demo
    }

    fun updateVibrationEnabled(enabled: Boolean) {
        if (!enabled && vibrationEnabled) haptics.cancel()
        vibrationEnabled = enabled
    }

    fun onMeasurement(device: DeviceState, sampleMs: Long, nowMs: Long) {
        if (!active || device.deviceId != selectedId || sampleMs < validAfterMs ||
            device.lastSeenMs != sampleMs ||
            device.deviceStatus != DeviceStatus.PRESENT && device.deviceStatus != DeviceStatus.WEAK) return
        updateVibrationEnabled(device.calibrationParameters.nearAlertVibration)
        val smooth = device.smoothedRssi ?: return
        if (detector.onSample(smooth, sampleMs, nowMs) && vibrationEnabled && !demo) haptics.pulse()
    }

    /** Expiry can revoke confirmation, but it can never request feedback. */
    fun refresh(device: DeviceState?, nowMs: Long) {
        if (!active || device == null || device.deviceId != selectedId || device.deviceStatus in
            setOf(DeviceStatus.LOST, DeviceStatus.SCAN_STOPPED, DeviceStatus.NEVER_SEEN)) {
            detector.reset()
            haptics.cancel()
        } else {
            updateVibrationEnabled(device.calibrationParameters.nearAlertVibration)
            detector.expire(nowMs)
        }
    }
}
