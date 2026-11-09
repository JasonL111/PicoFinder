#!/usr/bin/env python3
"""Run on the computer physically connected to Pico 2 W; does not install UF2."""
import argparse
from pathlib import Path
import shutil
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', default='auto', help='auto, /dev/ttyACM0, or COM3')
    args = parser.parse_args()
    command = shutil.which('mpremote')
    if command is None:
        parser.error('Install mpremote first: python -m pip install mpremote==1.27.0')
    base = [command, 'connect', args.port]
    probe = subprocess.run(base + ['exec',
        "import sys; print(sys.implementation._machine); "
        "assert sys.implementation.name == 'micropython'; "
        "assert sys.implementation.version[:3] >= (1, 27, 0), 'Need MicroPython >=1.27.0'"],
        text=True, capture_output=True, check=True)
    if 'Raspberry Pi Pico 2 W' not in probe.stdout:
        parser.error('Refusing upload: connected board is not Raspberry Pi Pico 2 W')
    print(probe.stdout.strip())
    firmware = Path(__file__).resolve().parents[1] / 'firmware/pico2w'
    subprocess.run(base + ['fs', 'cp'] + [str(firmware / n) for n in
        ('ble_protocol.py', 'identity.py', 'config.py')] + [':'], check=True)
    subprocess.run(base + ['fs', 'cp', str(firmware / 'main.py'), ':main.py'], check=True)
    subprocess.run(base + ['reset'], check=True)
    print('Uploaded and reset. Open mpremote repl, then verify ID with a real Android BLE scan.')


if __name__ == '__main__':
    try:
        main()
    except subprocess.CalledProcessError as exc:
        print('Deployment failed:', exc, file=sys.stderr)
        if exc.stderr:
            print(exc.stderr, file=sys.stderr)
        raise SystemExit(1)
