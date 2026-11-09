#!/usr/bin/env bash
set -euo pipefail
: "${RELEASE_TAG:?RELEASE_TAG is required}"
: "${RELEASE_PRERELEASE:?RELEASE_PRERELEASE is required}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
: "${GITHUB_SHA:?GITHUB_SHA is required}"
: "${RUNNER_TEMP:?RUNNER_TEMP is required}"
apk_dir="${APK_DIRECTORY:?APK_DIRECTORY is required}"
python3 tools/ci_release.py validate --directory "$apk_dir"

# Do not let a manually rerun old main build replace a newer development APK.
if [[ "$RELEASE_TAG" == dev-latest ]]; then
  main_sha="$(gh api "repos/$GITHUB_REPOSITORY/git/ref/heads/main" --jq .object.sha)"
  if [[ "$main_sha" != "$GITHUB_SHA" ]]; then
    printf '源码已有更新，保留较新的测试版；本次 APK 仍可在 Actions artifacts 下载。\n' >> "$GITHUB_STEP_SUMMARY"
    exit 0
  fi
fi
notes_file="$RUNNER_TEMP/finder-release-notes.md"
python3 tools/ci_release.py notes --output "$notes_file"
if [[ "$RELEASE_TAG" == dev-latest ]]; then
  title="最新 Android 测试版 · ${GITHUB_SHA:0:7}"
else
  title="Android 测试版 $RELEASE_TAG"
fi

# Distinguish a missing release from denied access or an API outage.
release_error="$RUNNER_TEMP/finder-release-error.txt"
if gh api "repos/$GITHUB_REPOSITORY/releases/tags/$RELEASE_TAG" > "$RUNNER_TEMP/finder-release.json" 2> "$release_error"; then
  is_draft="$(python3 -c 'import json,sys; print(str(json.load(open(sys.argv[1])).get("draft", False)).lower())' "$RUNNER_TEMP/finder-release.json")"
  if [[ "$RELEASE_TAG" != dev-latest && "$is_draft" != true ]]; then
    printf '版本 %s 已发布；保留原有安装包，请创建新版本 tag 发布更新。\n' "$RELEASE_TAG" >> "$GITHUB_STEP_SUMMARY"
    exit 0
  fi
else
  if ! grep -q 'HTTP 404' "$release_error"; then
    cat "$release_error" >&2
    exit 1
  fi
  gh release create "$RELEASE_TAG" --repo "$GITHUB_REPOSITORY" --target "$GITHUB_SHA" \
    --draft --title "$title" --notes-file "$notes_file"
fi

gh release upload "$RELEASE_TAG" "$apk_dir/local-ble-finder.apk" \
  "$apk_dir/SHA256SUMS" "$apk_dir/build-info.json" --repo "$GITHUB_REPOSITORY" --clobber

if [[ "$RELEASE_TAG" == dev-latest ]]; then
  # This one documented development tag is movable. Version tags are never forced.
  if gh api "repos/$GITHUB_REPOSITORY/git/ref/tags/dev-latest" > /dev/null 2> "$release_error"; then
    gh api --method PATCH "repos/$GITHUB_REPOSITORY/git/refs/tags/dev-latest" \
      -f sha="$GITHUB_SHA" -F force=true > /dev/null
  else
    if ! grep -q 'HTTP 404' "$release_error"; then
      cat "$release_error" >&2
      exit 1
    fi
    gh api --method POST "repos/$GITHUB_REPOSITORY/git/refs" \
      -f ref=refs/tags/dev-latest -f sha="$GITHUB_SHA" > /dev/null
  fi
fi
latest=true
if [[ "$RELEASE_PRERELEASE" == true ]]; then latest=false; fi
gh release edit "$RELEASE_TAG" --repo "$GITHUB_REPOSITORY" --draft=false \
  "--prerelease=$RELEASE_PRERELEASE" "--latest=$latest" --title "$title" --notes-file "$notes_file"
release_url="https://github.com/$GITHUB_REPOSITORY/releases/tag/$RELEASE_TAG"
printf '### Android APK 已发布\n\n[下载安装包](https://github.com/%s/releases/download/%s/local-ble-finder.apk) · [发布页](%s)\n' \
  "$GITHUB_REPOSITORY" "$RELEASE_TAG" "$release_url" >> "$GITHUB_STEP_SUMMARY"
