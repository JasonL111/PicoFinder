package local.ble.finder

import android.content.SharedPreferences
import androidx.core.content.edit
import local.ble.core.DeviceSettings
import local.ble.core.DeviceStore
import org.json.JSONObject

/** Persists settings and known IDs, never MAC addresses or RSSI history. */
class PreferencesDeviceStore(private val preferences: SharedPreferences) : DeviceStore {
    override fun load(): Map<Long, DeviceSettings> = decode(preferences.getString("devices", null))
    override fun save(devices: Map<Long, DeviceSettings>) {
        preferences.edit { putString("devices", encode(devices)) }
    }

    companion object {
        fun encode(devices: Map<Long, DeviceSettings>): String {
            val root = JSONObject()
            devices.forEach { (id, settings) ->
                root.put(id.toString(), JSONObject().apply {
                    put("name", settings.name)
                    put("reference", settings.rssiAt1m ?: JSONObject.NULL)
                    put("pathLoss", settings.pathLoss)
                    put("timeout", settings.lostTimeoutMs)
                    put("nearAlertVibration", settings.nearAlertVibration)
                })
            }
            return root.toString()
        }
        fun decode(text: String?): Map<Long, DeviceSettings> {
            if (text == null) return emptyMap()
            return try {
                val root = JSONObject(text)
                root.keys().asSequence().mapNotNull { key ->
                    val id = key.toLongOrNull()?.takeIf { it in 1..0xffffffffL } ?: return@mapNotNull null
                    try {
                        val obj = root.getJSONObject(key)
                        val settings = DeviceSettings(obj.getString("name"),
                            if (obj.isNull("reference")) null else obj.getDouble("reference"),
                            obj.getDouble("pathLoss"), obj.getLong("timeout"),
                            obj.optBoolean("nearAlertVibration", true))
                        if (settings.isValid()) id to settings else null
                    } catch (_: org.json.JSONException) { null }
                }.toMap()
            } catch (_: org.json.JSONException) { emptyMap() }
        }
    }
}
