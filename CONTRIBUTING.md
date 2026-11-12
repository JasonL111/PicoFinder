# Contributing

Please describe the concrete problem and a small, reviewable change. Keep protocol, signal processing
and device state in the pure Kotlin core. Changes to wire behavior must update the shared Python/Kotlin
vectors and `protocol/spec.md` together; a UI or documentation change should not alter that contract.

- Run the Python protocol, CI and localization suites listed in the README.
- Run core/app JVM tests, Debug build and lint. Also compile the Release variant when touching shared app code.
- Add both English and Simplified Chinese resources for user-visible copy; keep user-saved names unchanged.
- Use controlled clocks for signal/alert tests. Keep Android haptics out of composable recomposition.
- State exactly which checks ran. Identify simulated data and keep physical phone/Pico results separate.
- Do not add background scanning, GPS, direction/room inference or configurable very-strong thresholds as incidental work.
- Never commit signing keys, passwords or generated APKs. See [releasing.md](docs/releasing.md) for maintainer-only publication.

Pull requests should explain the resulting behavior and validation. Report hardware issues with the phone/OS,
Pico/MicroPython version, tag ID and reproducible steps, without uploading private keys or personal location data.
