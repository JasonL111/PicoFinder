"""Fail closed if persistence fails: never silently change an existing identity."""
import os
import struct


def load_or_create_device_id(path, configured_id=None):
    if configured_id is not None:
        if type(configured_id) is not int or not 1 <= configured_id <= 0xffffffff:
            raise ValueError("configured Device ID must be nonzero uint32")
        return configured_id
    try:
        with open(path, "r") as f:
            text = f.read(32).strip()
        value = int(text, 16)
        if len(text) != 8 or not 1 <= value <= 0xffffffff:
            raise ValueError("invalid stored Device ID; fix file or set DEVICE_ID explicitly")
        return value
    except OSError as exc:
        # ENOENT only. Permission/I/O errors must not generate another identity.
        if not exc.args or exc.args[0] != 2:
            raise
    value = 0
    while value == 0:
        value = struct.unpack("<I", os.urandom(4))[0]
    temp = path + ".tmp"
    with open(temp, "w") as f:
        f.write("%08x\n" % value)
        f.flush()
    os.rename(temp, path)
    # Read-back before broadcasting, so a write error cannot create a transient ID.
    with open(path, "r") as f:
        if int(f.read().strip(), 16) != value:
            raise OSError("Device ID persistence verification failed")
    return value
