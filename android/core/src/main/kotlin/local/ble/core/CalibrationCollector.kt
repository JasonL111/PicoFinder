package local.ble.core

import kotlin.math.abs

data class CalibrationResult(val rssiAt1m: Double? = null, val error: String? = null)

class CalibrationCollector(val startedMs: Long, val durationMs: Long = 8000) {
    private val samples = mutableListOf<Pair<Long, Double>>()
    fun add(rssi: Int, nowMs: Long) {
        if (DistanceEstimator.isValidRssi(rssi) && nowMs in startedMs..(startedMs + durationMs)) {
            samples.add(nowMs to rssi.toDouble())
        }
    }
    fun finish(nowMs: Long): CalibrationResult {
        if (nowMs - startedMs < durationMs) return CalibrationResult(error = "校准尚未完成")
        if (samples.size < 12 || samples.last().first - samples.first().first < 6000) {
            return CalibrationResult(error = "有效样本不足：保持前台扫描并稳定放置，再重试")
        }
        val middle = median(samples.map { it.second })
        val mad = median(samples.map { abs(it.second - middle) })
        val sorted = samples.map { it.second }.sorted()
        val interquartileRange = sorted[sorted.size * 3 / 4] - sorted[sorted.size / 4]
        if (mad > 6.0 || interquartileRange > 12.0) return CalibrationResult(error = "信号波动过大，请固定手机与标签并重试")
        val retained = samples.filter { abs(it.second - middle) <= maxOf(3.0, mad * 3) }
        if (retained.size < 12 || retained.last().first - retained.first().first < 6000) return CalibrationResult(error = "稳定样本不足，请重试")
        val reference = median(retained.map { it.second })
        if (reference !in -100.0..-20.0) return CalibrationResult(error = "参考 RSSI 超出有效范围")
        return CalibrationResult(rssiAt1m = reference)
    }
}
