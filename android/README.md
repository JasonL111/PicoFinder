# Android development

To use PicoFinder, download its APK from [Releases](https://github.com/JasonL111/PicoFinder/releases).
See [delivery and upgrade notes](../docs/delivery.md); signing is required before the first formal release.
The application ID remains `local.ble.finder`, with the visible name **PicoFinder**.

## Toolchain and build

The version catalog pins AGP 8.13.2, Gradle 8.13 (Wrapper with checksum), Kotlin and Compose compiler
2.2.21, Compose BOM 2025.12.01, JDK 21, SDK/target 36 and Build Tools 36.0.0. Minimum Android API is 31;
bytecode targets Java 17. Use Android Studio compatible with this AGP, with Gradle JDK set to 21.

Set `ANDROID_HOME`, or an uncommitted `local.properties` with `sdk.dir`. Then:

```bash
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Optional, with an API31+ device/emulator:
./gradlew :app:connectedDebugAndroidTest
```

Versions come from the root [version.properties](../version.properties): 1.0.0 / 1000000.
CI passes the same values through `finderVersionName` and `finderVersionCode`. Changing a local Gradle
override does not authorize a release; tag metadata and APK inspection must also match.

Without signing variables `assembleRelease` creates an **unsigned, non-installable preparation APK**.
Formal signing uses four `PICO_RELEASE_*` environment variables; see [release instructions](../docs/releasing.md).
Keep keys and passwords outside the repository. Never use a disposable CI Debug key for formal releases.

## Modules and runtime boundaries

`core` is a pure JVM module: decoding, RSSI median/EMA, distance, trends, calibration, device state and
strong-signal detection. It has no Android Context or Vibrator dependency. App code handles BLE, lifecycle,
Compose, SharedPreferences, permission prompts, localized messages and the injectable haptic boundary.

Android 12+ scanning uses Nearby devices plus precise location, because physical distance is derived.
There is no `neverForLocation` assertion, GPS reading, Internet permission, pairing, GATT, background
service or background scan. Each foreground scan ends after two minutes. Background stops scanning;
returning requires an explicit restart. Existing settings are keyed by protocol Device ID, not BLE MAC.

The −45/−51 dBm thresholds stay fixed in core constants. Only selected-tag, live, fresh measurements in
a real find session can request a short vibration. UI animation and recomposition cannot advance detection.
Debug simulation uses isolated data/settings and does not enable real vibration. Release has no demo entry.

## UI, resources and icon

Default English strings are in `app/src/main/res/values/strings.xml`; Simplified Chinese uses
`values-zh-rCN/strings.xml`. Format arguments must match; Chinese plurals only use `other`.
ViewModel messages retain resource IDs through `UiText` so locale changes can resolve them again.
Custom device names stay unchanged. API33+ advertises English/Chinese in system per-app language settings;
API31/32 follow the system language normally.

Both launcher references use an adaptive icon with vector foreground, gradient background and API33+
monochrome layer. Since minSdk is 31, obsolete pre-adaptive bitmap fallbacks are unnecessary.
See [icon adaptation](../docs/icon.md) and the historical [UI design notes](../docs/ui-v1.2.md).

## Verification and outputs

JVM tests cover protocol/state/filtering/calibration, strong-signal rules, session isolation, Fake haptics
and settings migration/persistence. Instrumentation covers the real Activity with Fake BLE, SharedPreferences,
animation semantics, large fonts, both translations and native icon masks. It does not test physical BLE.
Python release tests use a Fake GitHub CLI; resource tests need no SDK.

| Output | Path |
|---|---|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Unsigned Release (no key) | `app/build/outputs/apk/release/app-release-unsigned.apk` |
| Signed Release (key configured) | `app/build/outputs/apk/release/app-release.apk` |
| JVM reports | `core/build/reports/tests/test/`, `app/build/reports/tests/testDebugUnitTest/` |
| Lint | `app/build/reports/lint-results-debug.html`, `lint-results-release.html` |

Direct `adb shell am instrument -w -r local.ble.finder.test/androidx.test.runner.AndroidJUnitRunner`
is also supported after installing both APKs; save its output when using it instead of Gradle.
Tests save rendered previews under external app files `formal-screens` and `formal-icons`.
See [testing records](../docs/testing.md) for actual runs and unverified hardware scenarios.
