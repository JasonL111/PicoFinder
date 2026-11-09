"""Local BLE Finder v1. Pure Python; also runs on official MicroPython 1.27.0."""
import struct

SERVICE_UUID = "26a8a030-0a21-4e62-a373-9d127b7ce808"
# BLE 128-bit UUID is transmitted least-significant octet first (all 16 reversed).
SERVICE_UUID_LE = bytes.fromhex("08e87c7b129d73a3624e210a30a0a826")
PROTOCOL_VERSION = 1
UNKNOWN_BATTERY = 255


def _integer(value, low, high, name):
    if type(value) is not int or not low <= value <= high:
        raise ValueError("invalid " + name)


def validate_settings(interval_ms, device_id, rssi_at_1m, device_name):
    # Non-connectable legacy advertising minimum is 100 ms. Upper bound 10.24 s.
    _integer(interval_ms, 100, 10240, "interval_ms")
    if device_id is not None:
        _integer(device_id, 1, 0xffffffff, "device_id")
    _integer(rssi_at_1m, -100, -20, "rssi_at_1m")
    if not isinstance(device_name, str) or not 1 <= len(device_name.encode("utf-8")) <= 64:
        raise ValueError("device_name must be 1..64 UTF-8 bytes (USB display only)")


def encode_service_data(device_id, rssi_at_1m=-59, battery=UNKNOWN_BATTERY, flags=0):
    _integer(device_id, 1, 0xffffffff, "device_id")
    _integer(rssi_at_1m, -100, -20, "rssi_at_1m")
    _integer(battery, 0, 255, "battery")
    if battery > 100 and battery != UNKNOWN_BATTERY:
        raise ValueError("battery must be 0..100 or 255")
    # v1 transmitters must leave all reserved bits clear.
    _integer(flags, 0, 0, "flags")
    return struct.pack("<BIBbB", PROTOCOL_VERSION, device_id, flags, rssi_at_1m, battery)


def encode_advertisement(device_id, rssi_at_1m=-59, battery=UNKNOWN_BATTERY, flags=0):
    service = SERVICE_UUID_LE + encode_service_data(device_id, rssi_at_1m, battery, flags)
    # Flags: BR/EDR not supported. Not claiming GAP discoverability/connectability.
    payload = b"\x02\x01\x04" + bytes((len(service) + 1, 0x21)) + service
    if len(payload) > 31:
        raise ValueError("legacy advertising exceeds 31 bytes")
    return payload
