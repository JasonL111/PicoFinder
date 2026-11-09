"""Copy alongside config.py, identity.py and ble_protocol.py as /main.py on Pico 2 W."""
import sys
import time
import bluetooth
import config
from ble_protocol import encode_advertisement, validate_settings
from identity import load_or_create_device_id


def run():
    ble = None
    try:
        validate_settings(config.ADVERTISING_INTERVAL_MS, config.DEVICE_ID,
                          config.RSSI_AT_1M, config.DEVICE_NAME)
        device_id = load_or_create_device_id(config.DEVICE_ID_FILE, config.DEVICE_ID)
        payload = encode_advertisement(device_id, config.RSSI_AT_1M)
        print("Local BLE Finder v1 | %s | ID=%08X" % (config.DEVICE_NAME, device_id))
        print("AD (%d bytes): %s" % (len(payload), payload.hex()))
        delay = 2
        while True:
            try:
                if ble is None:
                    ble = bluetooth.BLE()
                ble.active(True)
                # Firmware API uses microseconds, not milliseconds. No GATT services.
                ble.gap_advertise(config.ADVERTISING_INTERVAL_MS * 1000,
                                  adv_data=payload, resp_data=b"", connectable=False)
                print("BLE advertising active, interval=%d ms, non-connectable" %
                      config.ADVERTISING_INTERVAL_MS)
                delay = 2
                while True:
                    time.sleep(5)
                    if not ble.active():
                        raise OSError("BLE became inactive")
            except (OSError, RuntimeError) as exc:
                print("BLE error; retry in %d seconds:" % delay, exc)
                try:
                    ble.active(False)
                except Exception:
                    pass
                time.sleep(delay)
                delay = min(delay * 2, 60)
    except KeyboardInterrupt:
        print("Stopped by USB REPL")
    except Exception as exc:
        # Configuration or flash errors: remain stopped and let USB REPL recover.
        print("Startup failed; correct configuration/files, then reset. No reboot loop.")
        sys.print_exception(exc)
    finally:
        if ble is not None:
            try:
                ble.gap_advertise(None)
                ble.active(False)
            except Exception:
                pass


run()
