#!/usr/bin/env bash
set -euo pipefail
: "${APK_VERSION_NAME:?APK_VERSION_NAME is required}"
: "${APK_VERSION_CODE:?APK_VERSION_CODE is required}"
: "${RUNNER_TEMP:?RUNNER_TEMP is required}"
log_file="$RUNNER_TEMP/android-build.log"

# Bound memory on hosted runners and avoid an additional Kotlin compiler JVM.
set +e
./gradlew --no-daemon --max-workers=2 \
  '-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8' \
  -Pkotlin.compiler.execution.strategy=in-process \
  "-PfinderVersionName=$APK_VERSION_NAME" "-PfinderVersionCode=$APK_VERSION_CODE" \
  :core:test :app:testDebugUnitTest :app:lintDebug :app:lintRelease \
  :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest \
  2>&1 | tee "$log_file"
build_status=${PIPESTATUS[0]}
set -e
if [[ "$build_status" -ne 0 ]]; then
  python3 - "$log_file" <<'PY'
from pathlib import Path
import os
import sys
log = Path(sys.argv[1]).read_text(errors='replace')
tail = '\n'.join(log.splitlines()[-70:])[-9000:]
# Workflow commands must escape content, rather than interpret build output.
escaped = tail.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
print('::error title=Android build failed::' + escaped)
summary = os.environ.get('GITHUB_STEP_SUMMARY')
if summary:
    with open(summary, 'a') as stream:
        stream.write('### Android build failure\n\n```text\n' + tail.replace('```', "'''") + '\n```\n')
PY
fi
exit "$build_status"
