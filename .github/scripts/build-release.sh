#!/usr/bin/env bash
set -euo pipefail
: "${RUNNER_TEMP:?RUNNER_TEMP is required}"
: "${PICO_RELEASE_KEYSTORE_BASE64:?Configure the release keystore Secret; see docs/releasing.md}"
: "${PICO_RELEASE_STORE_PASSWORD:?Configure PICO_RELEASE_STORE_PASSWORD}"
: "${PICO_RELEASE_KEY_ALIAS:?Configure PICO_RELEASE_KEY_ALIAS}"
: "${PICO_RELEASE_KEY_PASSWORD:?Configure PICO_RELEASE_KEY_PASSWORD}"
export PICO_RELEASE_KEYSTORE_PATH="$RUNNER_TEMP/picofinder-release.keystore"
trap 'rm -f "$PICO_RELEASE_KEYSTORE_PATH"' EXIT
umask 077
printf '%s' "$PICO_RELEASE_KEYSTORE_BASE64" | base64 --decode > "$PICO_RELEASE_KEYSTORE_PATH"
keytool -list -keystore "$PICO_RELEASE_KEYSTORE_PATH" -storepass:env PICO_RELEASE_STORE_PASSWORD \
  -alias "$PICO_RELEASE_KEY_ALIAS" > /dev/null
cd android
./gradlew --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8' \
  -Pkotlin.compiler.execution.strategy=in-process \
  "-PfinderVersionName=$APK_VERSION_NAME" "-PfinderVersionCode=$APK_VERSION_CODE" \
  :app:assembleRelease :app:lintRelease
