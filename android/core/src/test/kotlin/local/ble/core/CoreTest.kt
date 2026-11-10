package local.ble.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class CoreTest {
    private fun String.bytes() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val vectors get() = JSONObject(File(System.getProperty("protocol.vectors")).readText())

    @Test fun sharedGoldenVectorsDecode() {
        val valid = vectors.getJSONArray("valid")
        for (i in 0 until valid.length()) {
            val v = valid.getJSONObject(i)
            val actual = AdvertisementDecoder.decode(v.getString("advertisement_hex").bytes())
            requireNotNull(actual)
            assertEquals(v.getLong("device_id"), actual.deviceId)
            assertEquals(1, actual.protocolVersion)
            assertEquals(v.getInt("rssi_at_1m"), actual.rssiAt1m)
            assertEquals(if (v.isNull("battery_level")) null else v.getInt("battery_level"), actual.batteryLevel)
            assertEquals(actual, AdvertisementDecoder.decodeServiceData(
                UUID.fromString(vectors.getString("service_uuid")), v.getString("service_data_hex").bytes()))
        }
    }
    @Test fun allSharedInvalidVectorsRejected() {
        val invalid = vectors.getJSONArray("invalid")
        for (i in 0 until invalid.length()) {
            val v = invalid.getJSONObject(i)
            assertNull(v.getString("name"), AdvertisementDecoder.decode(v.getString("advertisement_hex").bytes()))
        }
    }
    @Test fun wrongUuidLengthsAndDuplicateServiceRejected() {
        val v = vectors.getJSONArray("valid").getJSONObject(0)
        val raw = v.getString("advertisement_hex").bytes()
        val data = v.getString("service_data_hex").bytes()
        assertNull(AdvertisementDecoder.decodeServiceData(UUID(0L, 0L), data))
        assertNull(AdvertisementDecoder.decodeServiceData(AdvertisementDecoder.serviceUuid, data + 0))
        assertNull(AdvertisementDecoder.decode(raw + raw))
        assertNotNull(AdvertisementDecoder.decode(raw + byteArrayOf(0, 0)))
        assertNull(AdvertisementDecoder.decode(raw + byteArrayOf(0, 1)))
        assertNull(AdvertisementDecoder.decode(byteArrayOf(0xff.toByte())))
        val reserved = data.clone().apply { this[5] = 0x80.toByte() }
        assertEquals(128, AdvertisementDecoder.decodeServiceData(AdvertisementDecoder.serviceUuid, reserved)?.flags)
    }
    @Test fun everyTruncatedGoldenAdRejected() {
        val raw = vectors.getJSONArray("valid").getJSONObject(0).getString("advertisement_hex").bytes()
        for (length in raw.indices) assertNull(AdvertisementDecoder.decode(raw.copyOf(length)))
    }
    @Test fun medianAndEmaRejectSingleSpike() {
        val f = DistanceEstimator()
        repeat(8) { f.add(-60, it * 300L) }
        assertTrue(f.add(-15, 2400))
        assertEquals(-60.0, f.smoothedRssi!!, 0.01)
        assertFalse(f.add(127, 2500))
        assertFalse(f.add(0, 2500))
        assertFalse(f.add(-127, 2500))
        assertFalse(f.add(-60, 1000))
        assertEquals(-60.0, f.smoothedRssi!!, 0.01)
        f.add(-80, 10000)
        assertEquals(-80.0, f.smoothedRssi!!, 0.01)
        assertEquals(Trend.STABLE, f.trend)
    }
    @Test fun estimatorIsConfigurableAndDistanceValidatesInput() {
        val f = DistanceEstimator(windowSize = 1, alpha = 1.0)
        f.add(-60, 0); f.add(-70, 300)
        assertEquals(-70.0, f.smoothedRssi!!, 0.01)
        assertEquals(1.0, DistanceEstimator.distance(-59.0, -59.0, 2.2)!!, 0.0001)
        assertEquals(10.0, DistanceEstimator.distance(-81.0, -59.0, 2.2)!!, 0.0001)
        assertNull(DistanceEstimator.distance(Double.NaN, -59.0, 2.2))
        assertNull(DistanceEstimator.distance(-59.0, -59.0, 0.0))
        assertNull(DistanceEstimator.distance(-59.0, 0.0, 2.2))
        assertNull(DistanceEstimator.distance(-59.0, -59.0, Double.POSITIVE_INFINITY))
    }
    @Test fun approachingAndAwayTrendsAndNoiseHysteresis() {
        fun trend(direction: Int): Trend {
            val f = DistanceEstimator()
            repeat(31) { i -> f.add(-65 + i * direction, i * 300L) }
            return f.trend
        }
        assertEquals(Trend.APPROACHING, trend(1))
        assertEquals(Trend.AWAY, trend(-1))
        val f = DistanceEstimator()
        repeat(50) { i -> f.add(-60 + (i % 3) - 1, i * 300L) }
        assertEquals(Trend.STABLE, f.trend)
    }
    @Test fun calibrationUsesRobustSamplesAndRejectsInsufficientOrUnstable() {
        val c = CalibrationCollector(0)
        repeat(27) { i -> c.add(if (i == 12) -100 else -59 + i % 3 - 1, i * 300L) }
        assertEquals(-59.0, c.finish(8000).rssiAt1m!!, 1.0)
        assertNotNull(c.finish(7000).error)
        assertNotNull(CalibrationCollector(0).finish(8000).error)
        val burst = CalibrationCollector(0)
        repeat(20) { burst.add(-60, it * 10L) }
        assertNotNull(burst.finish(8000).error)
        val unstable = CalibrationCollector(0)
        repeat(27) { i -> unstable.add(if (i % 2 == 0) -90 else -40, i * 300L) }
        // Equal populations ensure MAD captures motion, rather than a single outlier.
        unstable.add(-40, 7900)
        assertNotNull(unstable.finish(8000).error)
        val moving = CalibrationCollector(0)
        repeat(27) { i -> moving.add(if (i < 15) -90 else -40, i * 300L) }
        // MAD is zero in this uneven two-cluster case; IQR must still reject motion.
        assertNotNull(moving.finish(8000).error)
    }
    private class MemoryStore : DeviceStore {
        var saved: Map<Long, DeviceSettings> = emptyMap()
        override fun load() = saved
        override fun save(devices: Map<Long, DeviceSettings>) { saved = devices.toMap() }
    }
    private fun sample(id: Long = 0xffffffffL, rssi: Int = -59, time: Long = 0) =
        ScanSample(Advertisement(id, 1, 0, -59, null), rssi, time, 100000 + time)

    @Test fun repositorySupportsMultipleIdsTimeoutAndStoppedSignals() {
        val r = DeviceRepository(MemoryStore())
        assertTrue(r.receive(sample()))
        assertTrue(r.receive(sample(1)))
        assertEquals(2, r.snapshot(0, true).size)
        val state = r.snapshot(0, true).last()
        assertEquals(0xffffffffL, state.deviceId)
        assertEquals(DeviceStatus.PRESENT, state.deviceStatus)
        assertEquals(1.0, state.estimatedDistance!!, 0.01)
        assertEquals(DeviceStatus.WEAK, r.snapshot(5000, true).last().deviceStatus)
        assertNull(r.snapshot(5000, true).last().estimatedDistance)
        assertEquals(DeviceStatus.LOST, r.snapshot(10000, true).last().deviceStatus)
        assertNull(r.snapshot(10000, true).last().estimatedDistance)
        assertEquals(DeviceStatus.SCAN_STOPPED, r.snapshot(0, false).last().deviceStatus)
        assertNull(r.snapshot(0, false).last().estimatedDistance)
        r.receive(sample(time = 11000))
        assertEquals(DeviceStatus.PRESENT, r.snapshot(11000, true).last().deviceStatus)
    }
    @Test fun settingsPersistAndResetAndForgetAreLocal() {
        val store = MemoryStore()
        val r = DeviceRepository(store)
        r.receive(sample(1))
        val settings = DeviceSettings("钥匙", -64.0, 3.0, 6000)
        assertTrue(r.update(1, settings))
        val restored = DeviceRepository(store)
        val saved = restored.snapshot(0, true).single()
        assertEquals("钥匙", saved.deviceName)
        assertEquals(DeviceStatus.NEVER_SEEN, saved.deviceStatus)
        assertEquals(settings, saved.calibrationParameters)
        assertFalse(restored.update(1, settings.copy(pathLoss = Double.NaN)))
        assertFalse(restored.update(1, settings.copy(lostTimeoutMs = 4000)))
        assertTrue(restored.update(1, DeviceSettings()))
        restored.forget(1)
        assertTrue(store.saved.isEmpty())
    }
    @Test fun invalidRssiCannotKeepDeviceOnlineAndTimeoutUsesElapsedClock() {
        val r = DeviceRepository(MemoryStore())
        r.receive(sample(time = 100))
        assertFalse(r.receive(sample(rssi = 0, time = 9000)))
        assertFalse(r.receive(sample(time = 50)))
        assertEquals(DeviceStatus.LOST, r.snapshot(10100, true).single().deviceStatus)
    }

    @Test fun duplicateMeasurementsCannotChangeFilteringOrKeepADeviceOnline() {
        val r = DeviceRepository(MemoryStore())
        assertTrue(r.receive(sample(rssi = -60, time = 100)))
        repeat(100) { assertFalse(r.receive(sample(rssi = -10, time = 100))) }
        val state = r.snapshot(100, true).single()
        assertEquals(-60, state.rssi)
        assertEquals(-60.0, state.smoothedRssi!!, 0.0)
        assertEquals(100L, state.lastSeenMs)
        assertEquals(DeviceStatus.LOST, r.snapshot(10100, true).single().deviceStatus)
        assertTrue(r.receive(sample(rssi = -59, time = 10200)))
        assertEquals(DeviceStatus.PRESENT, r.snapshot(10200, true).single().deviceStatus)
    }
}
