#!/usr/bin/env bash
set -euo pipefail

# Pin the SDK CLI we verified, rather than installing the obsolete "tools" package.
cli_version=13114758
cli_sha256=7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee
: "${RUNNER_TEMP:?RUNNER_TEMP is required}"
: "${GITHUB_ENV:?GITHUB_ENV is required}"
: "${GITHUB_PATH:?GITHUB_PATH is required}"
android_sdk_dir="${ANDROID_HOME:-$RUNNER_TEMP/android-sdk}"
cli_dir="$RUNNER_TEMP/finder-android-cli"
mkdir -p "$android_sdk_dir" "$cli_dir"
curl --fail --location --retry 3 \
  "https://dl.google.com/android/repository/commandlinetools-linux-${cli_version}_latest.zip" \
  --output "$cli_dir/cli.zip"
printf '%s  %s\n' "$cli_sha256" "$cli_dir/cli.zip" | sha256sum --check
unzip -q "$cli_dir/cli.zip" -d "$cli_dir"
manager="$cli_dir/cmdline-tools/bin/sdkmanager"

# Avoid hiding sdkmanager failures behind yes's expected SIGPIPE exit status.
python3 - "$manager" "$android_sdk_dir" <<'PY'
import subprocess
import sys
manager, sdk = sys.argv[1:]
base = [manager, '--sdk_root=' + sdk]
subprocess.run(base + ['--licenses'], input='y\n' * 100, text=True, check=True)
subprocess.run(base + ['platform-tools', 'platforms;android-36', 'build-tools;36.0.0'], check=True)
PY
printf 'ANDROID_HOME=%s\nANDROID_SDK_ROOT=%s\n' "$android_sdk_dir" "$android_sdk_dir" >> "$GITHUB_ENV"
printf '%s\n%s\n' "$cli_dir/cmdline-tools/bin" "$android_sdk_dir/platform-tools" >> "$GITHUB_PATH"
