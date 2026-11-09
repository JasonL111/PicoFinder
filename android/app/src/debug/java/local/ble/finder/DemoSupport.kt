package local.ble.finder

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import local.ble.core.AdvertisementDecoder
import local.ble.core.BleScanner
import local.ble.core.ScanSample
import kotlin.math.sin

/** Debug source set only. The release APK cannot enable or instantiate a fake scanner. */
object DemoSupport {
    const val available = true
    fun create(): BleScanner = FakeBleScanner()
}

private class FakeBleScanner : BleScanner {
    private val handler = Handler(Looper.getMainLooper())
    private var job: Runnable? = null
    override fun start(onSample: (ScanSample) -> Unit, onError: (String) -> Unit): String? {
        stop()
        val start = SystemClock.elapsedRealtime()
        val golden = "020104192108e87c7b129d73a3624e210a30a0a826017856341200c5ff"
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val runnable = object : Runnable {
            override fun run() {
                if (job !== this) return
                val now = SystemClock.elapsedRealtime()
                val phase = (now - start) / 18000.0 * Math.PI * 2
                val rssi = (-67 + 12 * sin(phase)).toInt()
                // The second tag intentionally disappears after 12 s in every 30 s cycle.
                listOf(0x12345678L, 0x87654321L).forEachIndexed { i, id ->
                    if (i == 0 || (now - start) % 30000 < 12000) {
                        val raw = golden.clone()
                        repeat(4) { j -> raw[22 + j] = (id shr (8 * j)).toByte() }
                        val ad = AdvertisementDecoder.decode(raw)!!
                        onSample(ScanSample(ad, rssi - i * 10, now, System.currentTimeMillis()))
                    }
                }
                handler.postDelayed(this, 300)
            }
        }
        job = runnable; handler.post(runnable)
        return null
    }
    override fun stop() { job?.let(handler::removeCallbacks); job = null }
}
