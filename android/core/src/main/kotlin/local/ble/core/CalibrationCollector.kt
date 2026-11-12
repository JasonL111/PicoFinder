package local.ble.core

import kotlin.math.abs

/** Presentation-independent outcomes; Android supplies localized descriptions. */
enum class CalibrationError {
    NOT_FINISHED, INSUFFICIENT_SAMPLES, UNSTABLE_SIGNAL, INSUFFICIENT_STABLE_SAMPLES, REFERENCE_OUT_OF_RANGE
}

data class CalibrationResult(val rssiAt1m: Double? = null, val error: CalibrationError? = null)

class CalibrationCollector(val startedMs: Long, val durationMs: Long = 8000) {
    private val samples = mutableListOf<Pair<Long, Double>>()
    fun add(rssi: Int, nowMs: Long) {
        if (DistanceEstimator.isValidRssi(rssi) && nowMs in startedMs..(startedMs + durationMs)) {
            samples.add(nowMs to rssi.toDouble())
        }
    }
    fun finish(nowMs: Long): CalibrationResult {
        if (nowMs - startedMs < durationMs) return CalibrationResult(error = CalibrationError.NOT_FINISHED)
        if (samples.size < 12 || samples.last().first - samples.first().first < 6000) {
            return CalibrationResult(error = CalibrationError.INSUFFICIENT_SAMPLES)
        }
        val middle = median(samples.map { it.second })
        val mad = median(samples.map { abs(it.second - middle) })
        val sorted = samples.map { it.second }.sorted()
        val interquartileRange = sorted[sorted.size * 3 / 4] - sorted[sorted.size / 4]
        if (mad > 6.0 || interquartileRange > 12.0) return CalibrationResult(error = CalibrationError.UNSTABLE_SIGNAL)
        val retained = samples.filter { abs(it.second - middle) <= maxOf(3.0, mad * 3) }
        if (retained.size < 12 || retained.last().first - retained.first().first < 6000) return CalibrationResult(error = CalibrationError.INSUFFICIENT_STABLE_SAMPLES)
        val reference = median(retained.map { it.second })
        if (reference !in -100.0..-20.0) return CalibrationResult(error = CalibrationError.REFERENCE_OUT_OF_RANGE)
        return CalibrationResult(rssiAt1m = reference)
    }
}
