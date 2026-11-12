#!/usr/bin/env python3
"""Version metadata and verified APK artifacts for PicoFinder's tag-only releases."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
INFO_NAME = 'build-info.json'
SEMVER = re.compile(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)')


def project_version(path=None):
    values = {}
    for line in (path or ROOT / 'version.properties').read_text().splitlines():
        if not line.strip() or line.lstrip().startswith('#'):
            continue
        key, separator, value = line.partition('=')
        if not separator or key in values:
            raise ValueError('Invalid or duplicate version property')
        values[key] = value
    match = SEMVER.fullmatch(values.get('versionName', ''))
    if match is None or set(values) != {'versionName', 'versionCode'}:
        raise ValueError('version.properties needs a stable MAJOR.MINOR.PATCH and versionCode')
    major, minor, patch = map(int, match.groups())
    code = major * 1_000_000 + minor * 10_000 + patch * 100
    if major > 2099 or minor > 99 or patch > 99 or not 1 <= code <= 2_100_000_000:
        raise ValueError('Version exceeds the documented Android versionCode allocation')
    if str(code) != values['versionCode']:
        raise ValueError('versionCode must equal major*1000000 + minor*10000 + patch*100')
    return values['versionName'], str(code)


def metadata(environment):
    version, code = project_version()
    ref, event = environment['GITHUB_REF'], environment['GITHUB_EVENT_NAME']
    sha, repository = environment['GITHUB_SHA'], environment['GITHUB_REPOSITORY']
    if not re.fullmatch(r'[0-9a-f]{40}', sha):
        raise ValueError('GITHUB_SHA must be a full commit SHA')
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repository):
        raise ValueError('Invalid repository name')
    if int(environment['GITHUB_RUN_NUMBER']) < 1 or int(environment['GITHUB_RUN_ID']) < 1:
        raise ValueError('Invalid workflow run')
    version_tag = ref.removeprefix('refs/tags/') if ref.startswith('refs/tags/') else ''
    if version_tag and (not version_tag.startswith('v') or
                        SEMVER.fullmatch(version_tag[1:]) is None or version_tag[1:] != version):
        raise ValueError('Release tag must match version.properties: v' + version)
    publish = event == 'push' and version_tag == 'v' + version
    kind = 'release' if publish else 'debug'
    return {
        'publish': str(publish).lower(), 'tag': version_tag if publish else '',
        'prerelease': 'false', 'version_name': version, 'version_code': code,
        'build_type': kind, 'signing': 'maintainer-release-key' if publish else 'runner-debug-key',
        'apk_name': f'picofinder-{version}{"" if publish else "-debug"}.apk',
        'commit': sha, 'repository': repository,
        'run_url': f'https://github.com/{repository}/actions/runs/{int(environment["GITHUB_RUN_ID"])}',
    }


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def inspect_apk(apk, context):
    """Verify actual bytes, version, variant and signature before labeling an APK."""
    sdk = Path(os.environ['ANDROID_HOME']) / 'build-tools' / '36.0.0'
    signature = subprocess.check_output([str(sdk / 'apksigner'), 'verify', '--verbose',
                                         '--print-certs', str(apk)], text=True)
    # apksigner reports the scheme selected for the APK's minSdk. With v3 signing
    # and minSdk 31, v3 is verified while the v2 output can legitimately be false.
    if not any(f'Verified using v{scheme} scheme (APK Signature Scheme v{scheme}): true' in signature
               for scheme in (2, 3)):
        raise ValueError('APK must have a valid v2 or v3 signature')
    certificate = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})', signature)
    if certificate is None:
        raise ValueError('Missing signing certificate fingerprint')
    badging = subprocess.check_output([str(sdk / 'aapt'), 'dump', 'badging', str(apk)], text=True)
    package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    if package is None or package.groups() != ('local.ble.finder', context['version_code'], context['version_name']):
        raise ValueError('APK package/version does not match workflow metadata')
    if "sdkVersion:'31'" not in badging or "targetSdkVersion:'36'" not in badging:
        raise ValueError('Unexpected Android SDK requirements')
    if ('application-debuggable' in badging) != (context['build_type'] == 'debug'):
        raise ValueError('APK build variant does not match workflow metadata')
    return certificate.group(1)


def package(apk, output, context):
    certificate = inspect_apk(apk, context)
    output.mkdir(parents=True, exist_ok=True)
    name = context['apk_name']
    shutil.copyfile(apk, output / name)
    info = dict(context, certificate_sha256=certificate, apk_sha256=digest(output / name))
    (output / INFO_NAME).write_text(json.dumps(info, ensure_ascii=False, indent=2) + '\n')
    (output / 'SHA256SUMS').write_text(''.join(f'{digest(output / item)}  {item}\n' for item in (name, INFO_NAME)))


def validate(output, context):
    expected = {context['apk_name'], INFO_NAME}
    lines = (output / 'SHA256SUMS').read_text().splitlines()
    if len(lines) != len(expected):
        raise ValueError('Expected checksums for exactly the APK and build metadata')
    seen = set()
    for line in lines:
        checksum, separator, name = line.partition('  ')
        if not separator or not re.fullmatch(r'[0-9a-f]{64}', checksum) or name not in expected or name in seen:
            raise ValueError('Invalid or duplicate checksum filename')
        seen.add(name)
        if digest(output / name) != checksum:
            raise ValueError('Checksum mismatch: ' + name)
    info = json.loads((output / INFO_NAME).read_text())
    for key, value in context.items():
        if info.get(key) != value:
            raise ValueError('Artifact does not belong to this workflow: ' + key)
    if info.get('apk_sha256') != digest(output / context['apk_name']) or not re.fullmatch(r'[0-9a-f]{64}', info.get('certificate_sha256', '')):
        raise ValueError('APK metadata mismatch')


def notes(context):
    if context['publish'] != 'true':
        raise ValueError('Release notes require a matching pushed version tag')
    source = ROOT / 'docs' / 'releases' / (context['tag'] + '.md')
    if not source.is_file():
        raise ValueError('Missing reviewed release notes: ' + str(source.relative_to(ROOT)))
    return source.read_text() + f'''\n\n---
Build provenance: [{context['commit']}]({context['run_url']}).
Android version: {context['version_name']} ({context['version_code']}).
Download **{context['apk_name']}**. SHA256SUMS and build-info.json identify the exact signed artifact.
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('metadata', 'package', 'validate', 'notes'))
    parser.add_argument('--apk', type=Path)
    parser.add_argument('--directory', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    context = metadata(os.environ)
    if args.command == 'metadata':
        with open(os.environ['GITHUB_OUTPUT'], 'a') as stream:
            for key, value in context.items():
                stream.write(f'{key}={value}\n')
        print(json.dumps(context))
    elif args.command == 'package':
        if args.apk is None or args.directory is None:
            parser.error('package needs --apk and --directory')
        package(args.apk, args.directory, context)
    elif args.command == 'validate':
        if args.directory is None:
            parser.error('validate needs --directory')
        validate(args.directory, context)
    else:
        if args.output is None:
            parser.error('notes needs --output')
        args.output.write_text(notes(context))


if __name__ == '__main__':
    main()
