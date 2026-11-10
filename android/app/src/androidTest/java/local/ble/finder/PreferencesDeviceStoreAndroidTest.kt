package local.ble.finder

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import local.ble.core.DeviceSettings
import org.junit.Assert.*
import org.junit.Test

class PreferencesDeviceStoreAndroidTest {
    @Test fun legacySharedPreferencesUpgradeAndReloadKeepSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("finder-v11-store-test", Context.MODE_PRIVATE)
        try {
            assertTrue(preferences.edit().clear().putString("devices",
                """{"1":{"name":"旧钥匙","reference":-63.5,"pathLoss":2.7,"timeout":12000}}""").commit())
            val store = PreferencesDeviceStore(preferences)
            val old = store.load().getValue(1)
            assertEquals(DeviceSettings("旧钥匙", -63.5, 2.7, 12000), old)
            val changed = old.copy(nearAlertVibration = false)
            store.save(mapOf(1L to changed, 2L to DeviceSettings("背包")))
            // Flush the real SharedPreferences disk writer, then reopen the store.
            assertTrue(preferences.edit().commit())
            val reloaded = PreferencesDeviceStore(context.getSharedPreferences("finder-v11-store-test", Context.MODE_PRIVATE)).load()
            assertEquals(changed, reloaded.getValue(1))
            assertTrue(reloaded.getValue(2).nearAlertVibration)
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
