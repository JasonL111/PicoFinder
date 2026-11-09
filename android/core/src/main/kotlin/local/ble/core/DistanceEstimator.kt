package local.ble.core

import kotlin.math.abs
import kotlin.math.pow

fun median(values: List<Double>): Double {
    require(values.isNotEmpty())
    val sorted = values.sorted()
    val m = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[m] else (sorted[m - 1] + sorted[m]) / 2
}

enum class Trend { APPROACHING, AWAY, STABLE }

/** Timestamped median + EMA, then a 6 s trend window with 4/2 dBm hysteresis. */
class DistanceEstimator(private val windowSize: Int = 5, private val alpha: Double = 0.25) {
    init { require(windowSize in 1..31 && alpha > 0.0 && alpha <= 1.0) }
    private val recent = ArrayDeque<Double>()
    private val history = ArrayDeque<Pair<Long, Double>>()
    var smoothedRssi: Double? = null
        private set
    var trend: Trend = Trend.STABLE
        private set
    private var lastTime: Long? = null

    fun add(rssi: Int, timeMs: Long): Boolean {
        if (!isValidRssi(rssi) || timeMs < 0 || (lastTime != null && timeMs < lastTime!!)) return false
        if (lastTime != null && timeMs - lastTime!! >= 5000) reset()
        lastTime = timeMs
        recent.addLast(rssi.toDouble())
        if (recent.size > windowSize) recent.removeFirst()
        val middle = median(recent.toList())
        val smooth = smoothedRssi?.let { alpha * middle + (1 - alpha) * it } ?: middle
        smoothedRssi = smooth
        // One entry per second avoids packet rate controlling the trend decision.
        if (history.isNotEmpty() && timeMs / 1000 == history.last().first / 1000) history.removeLast()
        history.addLast(timeMs to smooth)
        while (history.isNotEmpty() && timeMs - history.first().first > 6000) history.removeFirst()
        if (history.size >= 4 && timeMs - history.first().first >= 3000) {
            val all = history.toList()
            val delta = median(all.takeLast(2).map { it.second }) - median(all.take(2).map { it.second })
            trend = when {
                delta >= 4.0 -> Trend.APPROACHING
                delta <= -4.0 -> Trend.AWAY
                abs(delta) <= 2.0 -> Trend.STABLE
                else -> trend
            }
        } else trend = Trend.STABLE
        return true
    }

    fun reset() {
        recent.clear(); history.clear(); smoothedRssi = null; trend = Trend.STABLE; lastTime = null
    }

    companion object {
        fun isValidRssi(rssi: Int) = rssi in -110..-10
        fun distance(rssi: Double, rssiAt1m: Double, n: Double): Double? {
            if (!rssi.isFinite() || rssi !in -110.0..-10.0 ||
                !rssiAt1m.isFinite() || rssiAt1m !in -100.0..-20.0 ||
                !n.isFinite() || n !in 1.0..6.0) return null
            return 10.0.pow((rssiAt1m - rssi) / (10.0 * n))
        }
    }
}
