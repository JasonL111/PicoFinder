# Versioned releases

The first formal version is **1.0.0 / 1000000**, tag **v1.0.0**. This change prepares publication;
the maintainer has not configured signing Secrets yet, so no formal tag or release is created by this task.
The previous `dev-latest` remains historical output. New pushes never move it or replace its assets.

## One-time signing setup

Keep a long-lived Android release key, an encrypted offline backup and its passwords under maintainer
control. Losing this key prevents compatible future updates. Do not paste private material into issues,
chat, source files or build logs. Use a separate key from any disposable Debug signing key.

For example, generate a JKS interactively on a trusted machine (the command prompts for passwords):

```bash
keytool -genkeypair -v -keystore picofinder-release.jks -storetype JKS \
  -alias picofinder -keyalg RSA -keysize 3072 -validity 10000
```

In repository Settings → Secrets and variables → Actions, configure these **repository Secrets**:

| Secret | Value |
|---|---|
| `PICO_RELEASE_KEYSTORE_BASE64` | Base64 of the complete keystore file; e.g. `base64 -w0 picofinder-release.jks` on Linux |
| `PICO_RELEASE_STORE_PASSWORD` | Keystore password |
| `PICO_RELEASE_KEY_ALIAS` | Key alias, e.g. `picofinder` |
| `PICO_RELEASE_KEY_PASSWORD` | Key password |

The tag-only signing job decodes the key into runner temporary storage with restrictive permissions and
removes it on exit. It checks the alias, then Gradle signs the **Release** variant using v2/v3 signatures.
Missing or incomplete values fail rather than publishing Debug output. PRs do not receive these Secrets;
keys are never stored in Gradle caches or artifacts. Use repository rules to limit who can push version tags.

For a local signing check, export `PICO_RELEASE_KEYSTORE_PATH`, `PICO_RELEASE_STORE_PASSWORD`,
`PICO_RELEASE_KEY_ALIAS` and `PICO_RELEASE_KEY_PASSWORD` through your secure local environment, then run
`:app:assembleRelease :app:lintRelease`. With no variables, Gradle deliberately builds unsigned Release output.

## Publish v1.0.0

1. Review and merge the preparation PR. Ensure the intended commit passes protocol, release/resource,
   JVM, Debug/Release build and lint checks. Complete the physical-device checks in [testing.md](testing.md).
2. Configure and back up the signing key as above. Confirm `version.properties` is 1.0.0 / 1000000 and
   review [the release notes](releases/v1.0.0.md). Do not generate a new key for each release.
3. Tag the reviewed commit on the up-to-date main branch:

   ```bash
   git switch main
   git pull --ff-only
   git tag -a v1.0.0 -m "PicoFinder v1.0.0"
   git push origin v1.0.0
   ```

4. Follow [PicoFinder CI and versioned release](https://github.com/JasonL111/PicoFinder/actions/workflows/ci.yml).
   A pushed tag must exactly match `v` + `versionName`. The signed binary is inspected for version,
   application ID, min/target SDK, non-debuggable Release variant and valid v2 signature before packaging.
5. Publication first creates a draft, verifies the tag still points to the artifact commit, uploads all
   three files and compares their server-side SHA-256 digests. Only then is the release made public.

The resulting [v1.0.0 page](https://github.com/JasonL111/PicoFinder/releases/tag/v1.0.0) contains:

- `picofinder-1.0.0.apk` — signed Release APK.
- `SHA256SUMS` — checksums for APK and metadata.
- `build-info.json` — exact commit, workflow URL, version, build type and signing certificate fingerprint.

Download/install that exact public asset on a phone and compare checksums and visible version.
Existing Debug installs may require uninstalling because their signing key differs; see [delivery.md](delivery.md).

## Future versions and reruns

Update `version.properties`, `CHANGELOG.md`, version references and `docs/releases/vX.Y.Z.md` together.
The code allocation is `major × 1000000 + minor × 10000 + patch × 100`: 1.0.1 → 1000100,
1.1.0 → 1010000. Minor/patch are 0–99, major 0–2099 and code must be 1–2100000000.
This is above prior dev codes and increases across supported stable versions. Pre-release tag syntax
is deliberately rejected; add a reviewed policy before introducing it.

Published releases/assets and version tags are never overwritten by this workflow. If a published version
needs a fix, issue a new version. Do not delete/recreate a tag. A same-run retry can fill missing files in
an unpublished draft only when existing file digests match; conflicting drafts stop for manual review.
An already-public version returns without mutations. A moved tag or source mismatch also stops.

Branch pushes, PRs and manual dispatches only upload `picofinder-debug` Actions artifacts; selecting a tag
in Run workflow does not publish. The publish job alone receives `contents: write` and uses `GITHUB_TOKEN`,
so no personal access token is needed. Missing signing Secrets cannot affect ordinary CI preview builds.

The Python release tests use a Fake GitHub CLI; they verify policy, provenance, immutable published assets,
draft completion, checksum failures and HTTP errors. They do not constitute a real GitHub release upload.
