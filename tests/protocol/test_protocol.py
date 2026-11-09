import importlib.util
import json
from pathlib import Path
import struct
import sys
import tempfile
import unittest
import uuid
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'firmware/pico2w'))
import ble_protocol as protocol
from identity import load_or_create_device_id


class ProtocolTests(unittest.TestCase):
    def test_shared_golden_vectors(self):
        vectors = json.loads((ROOT / 'protocol/test-vectors.json').read_text())
        self.assertEqual(protocol.SERVICE_UUID, vectors['service_uuid'])
        self.assertEqual(protocol.SERVICE_UUID_LE.hex(), vectors['uuid_le_hex'])
        self.assertEqual(protocol.SERVICE_UUID_LE, uuid.UUID(protocol.SERVICE_UUID).bytes[::-1])
        for v in vectors['valid']:
            with self.subTest(v['name']):
                battery = 255 if v['battery_level'] is None else v['battery_level']
                data = protocol.encode_service_data(v['device_id'], v['rssi_at_1m'], battery)
                ad = protocol.encode_advertisement(v['device_id'], v['rssi_at_1m'], battery)
                self.assertEqual(data.hex(), v['service_data_hex'])
                self.assertEqual(ad.hex(), v['advertisement_hex'])
                self.assertEqual(len(ad), 29)
                self.assertLessEqual(len(ad), 31)
                self.assertEqual(ad[3], len(ad) - 4)
                self.assertEqual(ad[4], 0x21)
                self.assertEqual(ad[5:21], protocol.SERVICE_UUID_LE)
                self.assertEqual(struct.unpack('<BIBbB', ad[21:]),
                                 (1, v['device_id'], 0, v['rssi_at_1m'], battery))

    def test_encoding_rejects_invalid_fields(self):
        for kwargs in ({'device_id': 0}, {'device_id': -1}, {'device_id': 2**32},
                       {'device_id': True}, {'rssi_at_1m': -101}, {'rssi_at_1m': -19},
                       {'battery': 101}, {'battery': 254}, {'battery': -1}, {'flags': 1}):
            with self.subTest(kwargs), self.assertRaises(ValueError):
                protocol.encode_advertisement(**({'device_id': 1} | kwargs))

    def test_config_validation(self):
        protocol.validate_settings(300, None, -59, '标签')
        protocol.validate_settings(100, 1, -100, 'x')
        protocol.validate_settings(10240, 0xffffffff, -20, 'x')
        for args in ((99, None, -59, 'x'), (10241, None, -59, 'x'),
                     (300.0, None, -59, 'x'), (300, 0, -59, 'x'),
                     (300, None, 0, 'x'), (300, None, -59, ''),
                     (300, None, -59, '中' * 22)):
            with self.subTest(args), self.assertRaises(ValueError):
                protocol.validate_settings(*args)

    def test_device_id_survives_restart_and_manual_override(self):
        with tempfile.TemporaryDirectory() as d:
            path = str(Path(d) / 'device_id.txt')
            first = load_or_create_device_id(path)
            self.assertGreater(first, 0)
            self.assertEqual(first, load_or_create_device_id(path))
            self.assertEqual(123, load_or_create_device_id(path, 123))
            self.assertEqual(first, load_or_create_device_id(path))

    def test_corrupt_identity_is_not_silently_replaced(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / 'id'
            p.write_text('00000000')
            with self.assertRaises(ValueError):
                load_or_create_device_id(str(p))
            self.assertEqual(p.read_text(), '00000000')

    def test_persistence_error_prevents_identity(self):
        with patch('builtins.open', side_effect=PermissionError(13, 'denied')):
            with self.assertRaises(PermissionError):
                load_or_create_device_id('id')

    def test_random_zero_is_retried(self):
        with tempfile.TemporaryDirectory() as d, patch('identity.os.urandom',
                                                     side_effect=[b'\0'*4, b'\x01\0\0\0']):
            self.assertEqual(1, load_or_create_device_id(str(Path(d) / 'id')))


if __name__ == '__main__':
    unittest.main()
