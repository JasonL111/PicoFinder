package local.ble.finder

import local.ble.core.DeviceSettings
import org.junit.Assert.*
import org.junit.Test

class PreferencesDeviceStoreTest {
    @Test fun roundTripPersistsUnsignedIdsUnicodeAndIndependentCalibration() {
        val original = mapOf(0xffffffffL to DeviceSettings("钥匙🔑", -62.5, 2.7, 12000),
            1L to DeviceSettings("标签二"))
        assertEquals(original, PreferencesDeviceStore.decode(PreferencesDeviceStore.encode(original)))
    }
    @Test fun corruptAndInvalidRecordsCannotInjectInvalidSettings() {
        assertTrue(PreferencesDeviceStore.decode("broken").isEmpty())
        assertTrue(PreferencesDeviceStore.decode(null).isEmpty())
        val mixed = """{"0":{"name":"x","reference":null,"pathLoss":2.2,"timeout":10000},
            "1":{"name":"好","reference":null,"pathLoss":2.2,"timeout":10000},
            "2":{"name":"坏","reference":null,"pathLoss":0,"timeout":10000}}"""
        assertEquals(setOf(1L), PreferencesDeviceStore.decode(mixed).keys)
    }
}
