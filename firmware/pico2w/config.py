"""Edit before uploading; no Wi-Fi credentials or Internet required."""
DEVICE_NAME = "我的蓝牙标签"  # USB log label; the phone stores its own per-ID name.
DEVICE_ID = None  # None generates a persistent random nonzero uint32; or e.g. 0x12345678.
DEVICE_ID_FILE = "device_id.txt"
ADVERTISING_INTERVAL_MS = 300
RSSI_AT_1M = -59  # Initial reference, calibrate with your own phone/environment.
