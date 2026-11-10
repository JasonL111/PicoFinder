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
    @Test fun v1RecordsKeepAllFieldsAndDefaultVibrationOn() {
        val old = """{"4294967295":{"name":"旧钥匙","reference":-62.5,"pathLoss":2.7,"timeout":12000}}"""
        val settings = PreferencesDeviceStore.decode(old).getValue(0xffffffffL)
        assertEquals(DeviceSettings("旧钥匙", -62.5, 2.7, 12000, true), settings)
        assertEquals(settings, PreferencesDeviceStore.decode(PreferencesDeviceStore.encode(mapOf(0xffffffffL to settings))).values.single())
    }
    @Test fun independentVibrationChoicesSurviveReloadAndCalibrationUpdates() {
        val first = DeviceSettings("钥匙", -61.0, 2.5, 9000, false)
        val second = DeviceSettings("背包")
        val saved = PreferencesDeviceStore.encode(mapOf(1L to first, 2L to second))
        val restarted = PreferencesDeviceStore.decode(saved)
        assertFalse(restarted.getValue(1).nearAlertVibration)
        assertTrue(restarted.getValue(2).nearAlertVibration)
        val calibrated = restarted.getValue(1).copy(rssiAt1m = -64.0)
        val savedAgain = PreferencesDeviceStore.encode(restarted + (1L to calibrated))
        assertEquals(calibrated, PreferencesDeviceStore.decode(savedAgain).getValue(1))
        val json = org.json.JSONObject(savedAgain).getJSONObject("1")
        assertEquals(setOf("name", "reference", "pathLoss", "timeout", "nearAlertVibration"), json.keys().asSequence().toSet())
    }
}
