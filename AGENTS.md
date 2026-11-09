# Project guidance

- The wire contract is protocol/spec.md. Keep Python and Kotlin on the same shared JSON vectors.
- Keep Android decoding, signal processing and device state in the pure JVM core module.
- No Wi-Fi, Internet permission, backend, background scanning or hardware-based decoder branches in V1.
- Firmware targets official MicroPython 1.27.0 on RPI_PICO2_W, not a different Pico board.
- Run Python: python -m unittest discover -s tests/protocol -v
- Run Android from android/: ./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
- Run optional Compose instrumentation with an API31+ device: ./gradlew :app:connectedDebugAndroidTest
- Distinguish mock/software results from physical BLE and USB deployment. Never claim unrun checks passed.
- Each cloud task is already isolated. Use this checkout; do not create a worktree unless requested.
