package local.ble.core

import java.util.UUID

/** No Android or hardware dependency. All offsets and validation follow protocol/spec.md. */
data class Advertisement(
    val deviceId: Long,
    val protocolVersion: Int,
    val flags: Int,
    val rssiAt1m: Int,
    val batteryLevel: Int?,
)

object AdvertisementDecoder {
    val serviceUuid: UUID = UUID.fromString("26a8a030-0a21-4e62-a373-9d127b7ce808")
    private val uuidLe = "08e87c7b129d73a3624e210a30a0a826".chunked(2)
        .map { it.toInt(16).toByte() }.toByteArray()

    fun decodeServiceData(uuid: UUID, data: ByteArray): Advertisement? {
        if (uuid != serviceUuid || data.size != 8 || data[0].u() != 1) return null
        val id = (1..4).fold(0L) { value, i -> value or (data[i].u().toLong() shl (8 * (i - 1))) }
        val reference = data[6].toInt()
        val battery = data[7].u()
        if (id == 0L || reference !in -100..-20 || (battery !in 0..100 && battery != 255)) return null
        return Advertisement(id, 1, data[5].u(), reference, battery.takeUnless { it == 255 })
    }

    /** Raw AD parser is shared by real scans, fake scans and golden-vector tests. */
    fun decode(raw: ByteArray): Advertisement? {
        var offset = 0
        var found: Advertisement? = null
        var matches = 0
        while (offset < raw.size) {
            val length = raw[offset].u()
            if (length == 0) {
                if (raw.copyOfRange(offset, raw.size).any { it.toInt() != 0 }) return null
                break
            }
            val end = offset + 1 + length
            if (end > raw.size) return null
            if (raw[offset + 1].u() == 0x21 && length >= 17 &&
                raw.copyOfRange(offset + 2, offset + 18).contentEquals(uuidLe)) {
                matches++
                found = decodeServiceData(serviceUuid, raw.copyOfRange(offset + 18, end))
                if (found == null) return null
            }
            offset = end
        }
        return found.takeIf { matches == 1 }
    }

    private fun Byte.u() = toInt() and 0xff
}
