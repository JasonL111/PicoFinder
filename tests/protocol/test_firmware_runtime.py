"""Contract checks with mocked BLE/clock. These are not real Pico tests."""
import contextlib
import importlib.util
import io
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import Mock, patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'firmware/pico2w'))
import config
from ble_protocol import encode_advertisement


class FirmwareRuntimeTests(unittest.TestCase):
    def run_main(self, ble, sleeps):
        spec = importlib.util.spec_from_file_location('finder_firmware_main', ROOT / 'firmware/pico2w/main.py')
        module = importlib.util.module_from_spec(spec)
        with patch.dict(sys.modules, {'bluetooth': types.SimpleNamespace(BLE=lambda: ble)}), \
                patch.object(config, 'DEVICE_ID', 0x12345678), \
                patch('time.sleep', side_effect=sleeps) as clock, \
                contextlib.redirect_stdout(io.StringIO()):
            spec.loader.exec_module(module)
        return clock

    def test_boot_uses_microseconds_nonconnectable_and_cleans_up(self):
        ble = Mock()
        ble.active.return_value = True
        self.run_main(ble, [KeyboardInterrupt()])
        first = ble.gap_advertise.call_args_list[0]
        self.assertEqual(first.args, (300000,))
        self.assertEqual(first.kwargs, dict(adv_data=encode_advertisement(0x12345678),
                                           resp_data=b'', connectable=False))
        ble.gap_advertise.assert_any_call(None)
        ble.active.assert_any_call(False)

    def test_radio_failures_back_off_without_reset_loop(self):
        ble = Mock()
        ble.active.return_value = True
        ble.gap_advertise.side_effect = [OSError('radio'), OSError('radio'), None, None]
        clock = self.run_main(ble, [None, None, KeyboardInterrupt()])
        self.assertEqual([call.args[0] for call in clock.call_args_list], [2, 4, 5])


if __name__ == '__main__':
    unittest.main()
