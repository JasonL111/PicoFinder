# Changelog

This log starts with the first formal version. Earlier V1/V1.1/V1.2 names referred to development
iterations, not a claimed history of published semantic versions. Publication status is shown in GitHub Releases.

## 1.0.0

- Consolidates Pico 2 W / MicroPython BLE discovery, stable Device IDs, multiple tags and local settings.
- Preserves median + EMA filtering, rough distance, trends, offline detection and 1 m calibration.
- Preserves fixed −45/−51 dBm very-strong alerts, one brief pulse and per-tag opt-out.
- Includes the refined signal-ring interface, light/dark themes and reduced-motion behavior.
- Adds complete English/default and Simplified Chinese Android resources and bilingual READMEs.
- Adapts the user-supplied concentric-circle icon to scalable adaptive launcher resources.
- Introduces a shared version source and tag-only, maintainer-signed Release publication.
- Stops publishing ordinary branch/PR builds to the moving `dev-latest` entry; old entries remain historical.

Known limits: approximate RSSI only, foreground two-minute scans, no direction/room inference, Find Hub,
GPS, cloud tracking or tested ESP32-C3 firmware. Formal signing Secrets must be configured before the first publication.
