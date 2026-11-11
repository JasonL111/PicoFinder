package local.ble.core

data class DeviceSettings(
    val name: String = "",
    val rssiAt1m: Double? = null,
    val pathLoss: Double = 2.2,
    val lostTimeoutMs: Long = 10000,
    val nearAlertVibration: Boolean = true,
) {
    fun isValid() = name.length <= 40 && (rssiAt1m == null || rssiAt1m.isFinite() && rssiAt1m in -100.0..-20.0) &&
        pathLoss.isFinite() && pathLoss in 1.0..6.0 && lostTimeoutMs in 5000..120000
}

interface DeviceStore {
    fun load(): Map<Long, DeviceSettings>
    fun save(devices: Map<Long, DeviceSettings>)
}

enum class DeviceStatus { NEVER_SEEN, PRESENT, WEAK, LOST, SCAN_STOPPED }
data class DeviceState(
    val deviceId: Long,
    val protocolVersion: Int,
    val deviceName: String,
    val lastSeenMs: Long?,
    val lastSeenWallMs: Long?,
    val rssi: Int?,
    val smoothedRssi: Double?,
    val estimatedDistance: Double?,
    val batteryLevel: Int?,
    val deviceStatus: DeviceStatus,
    val trend: Trend,
    val calibrationParameters: DeviceSettings,
    val advertisedReference: Int,
    val signalBars: Int,
)

data class ScanSample(val advertisement: Advertisement, val rssi: Int, val timeMs: Long, val wallMs: Long)

/** Single-thread confined by the ViewModel. Timeouts use elapsed time, never wall clock. */
class DeviceRepository(private val store: DeviceStore) {
    private data class Entry(
        var settings: DeviceSettings,
        var sample: ScanSample? = null,
        val estimator: DistanceEstimator = DistanceEstimator(),
    )
    private val devices = linkedMapOf<Long, Entry>()
    init {
        store.load().filter { (id, settings) -> id in 1..0xffffffffL && settings.isValid() }
            .forEach { (id, settings) -> devices[id] = Entry(settings) }
    }
    fun receive(sample: ScanSample): Boolean {
        if (sample.timeMs < 0 || !DistanceEstimator.isValidRssi(sample.rssi)) return false
        val id = sample.advertisement.deviceId
        if (id !in 1..0xffffffffL) return false
        val entry = devices[id]
        // Re-delivery of one measurement must not pump the median/EMA or refresh identity data.
        if (entry?.sample != null && sample.timeMs <= entry.sample!!.timeMs) return false
        val target = entry ?: Entry(DeviceSettings()).also { devices[id] = it; persist() }
        if (!target.estimator.add(sample.rssi, sample.timeMs)) return false
        target.sample = sample
        return true
    }
    fun update(id: Long, settings: DeviceSettings): Boolean {
        if (!settings.isValid()) return false
        val entry = devices[id] ?: return false
        entry.settings = settings
        entry.estimator.reset()
        persist()
        return true
    }
    fun forget(id: Long) { devices.remove(id); persist() }
    fun resetSignals() { devices.values.forEach { it.estimator.reset() } }
    private fun persist() = store.save(devices.mapValues { it.value.settings })

    fun snapshot(nowMs: Long, scanning: Boolean): List<DeviceState> = devices.map { (id, entry) ->
        val sample = entry.sample
        val age = sample?.let { (nowMs - it.timeMs).coerceAtLeast(0) }
        val weakAfter = entry.settings.lostTimeoutMs / 2
        val fresh = scanning && age != null && age < weakAfter
        val smooth = entry.estimator.smoothedRssi
        val reference = sample?.advertisement?.rssiAt1m ?: -59
        val status = when {
            !scanning -> DeviceStatus.SCAN_STOPPED
            sample == null -> DeviceStatus.NEVER_SEEN
            age!! >= entry.settings.lostTimeoutMs -> DeviceStatus.LOST
            age >= weakAfter || sample.rssi < -85 -> DeviceStatus.WEAK
            else -> DeviceStatus.PRESENT
        }
        DeviceState(id, sample?.advertisement?.protocolVersion ?: 1,
            entry.settings.name.ifBlank { "标签 %08X".format(id) }, sample?.timeMs, sample?.wallMs,
            sample?.rssi, smooth.takeIf { fresh },
            smooth?.takeIf { fresh }?.let {
                DistanceEstimator.distance(it, entry.settings.rssiAt1m ?: reference.toDouble(), entry.settings.pathLoss)
            }, sample?.advertisement?.batteryLevel, status,
            entry.estimator.trend.takeIf { fresh } ?: Trend.STABLE, entry.settings, reference,
            if (!fresh || smooth == null) 0 else when { smooth >= -55 -> 4; smooth >= -65 -> 3; smooth >= -80 -> 2; else -> 1 })
    }.sortedBy { it.deviceId }
}
