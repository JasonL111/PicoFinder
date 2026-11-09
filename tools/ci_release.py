#!/usr/bin/env python3
"""Metadata and integrity checks for downloadable GitHub Actions test APKs."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil

APK_NAME = 'local-ble-finder.apk'
INFO_NAME = 'build-info.json'
SEMVER_TAG = re.compile(r'v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z.-]+))?')


def metadata(environment):
    ref = environment['GITHUB_REF']
    event = environment['GITHUB_EVENT_NAME']
    sha = environment['GITHUB_SHA']
    repository = environment['GITHUB_REPOSITORY']
    if not re.fullmatch(r'[0-9a-f]{40}', sha):
        raise ValueError('GITHUB_SHA must be a full commit SHA')
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repository):
        raise ValueError('Invalid repository name')
    run = int(environment['GITHUB_RUN_NUMBER'])
    code = 1000 + run
    if run < 1 or code > 2100000000:
        raise ValueError('Run number cannot produce a valid Android versionCode')
    publish = event in ('push', 'workflow_dispatch') and ref == 'refs/heads/main'
    tag = 'dev-latest' if publish else ''
    version = f'dev.{run}'
    prerelease = True
    if ref.startswith('refs/tags/v'):
        version_tag = ref.removeprefix('refs/tags/')
        match = SEMVER_TAG.fullmatch(version_tag)
        if match is None:
            raise ValueError('Release tag must be vMAJOR.MINOR.PATCH or vMAJOR.MINOR.PATCH-suffix')
        version = version_tag[1:]
        publish = event == 'push'
        tag = version_tag if publish else ''
        prerelease = match.group(4) is not None
    return {
        'publish': str(publish).lower(), 'tag': tag,
        'prerelease': str(prerelease).lower(),
        'version_name': version, 'version_code': str(code),
        'commit': sha, 'repository': repository,
        'run_url': f'https://github.com/{repository}/actions/runs/{int(environment["GITHUB_RUN_ID"])}',
    }


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def package(apk, output, context):
    output.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(apk, output / APK_NAME)
    info = dict(context, build_type='debug', signing='runner-debug-key', apk_sha256=digest(output / APK_NAME))
    (output / INFO_NAME).write_text(json.dumps(info, ensure_ascii=False, indent=2) + '\n')
    (output / 'SHA256SUMS').write_text(''.join(f'{digest(output / name)}  {name}\n' for name in (APK_NAME, INFO_NAME)))


def validate(output, context):
    lines = (output / 'SHA256SUMS').read_text().splitlines()
    if len(lines) != 2:
        raise ValueError('Expected checksums for exactly the APK and build metadata')
    found = set()
    for line in lines:
        match = re.fullmatch(r'([0-9a-f]{64})  (local-ble-finder\.apk|build-info\.json)', line)
        if match is None or match.group(2) in found:
            raise ValueError('Invalid or duplicate checksum filename')
        checksum, name = match.groups()
        found.add(name)
        if digest(output / name) != checksum:
            raise ValueError('Checksum mismatch: ' + name)
    info = json.loads((output / INFO_NAME).read_text())
    for key, value in context.items():
        if info.get(key) != value:
            raise ValueError('Artifact does not belong to this workflow: ' + key)
    if info.get('build_type') != 'debug' or info.get('apk_sha256') != digest(output / APK_NAME):
        raise ValueError('APK metadata mismatch')


def notes(context):
    return f'''# Local BLE Finder Android 测试版

直接下载 **{APK_NAME}**，在 Android 12+ 手机允许该来源安装应用后打开，无需 Android Studio 或自行编译。

- 版本：{context['version_name']}（versionCode {context['version_code']}）
- 源码提交：`{context['commit']}`
- 构建记录：{context['run_url']}
- 交付条件：Python 测试、Kotlin 单元测试、Android lint 和 Debug APK 构建成功。
- 构建类型：Debug，包含明确标注的模拟模式；连接真实标签时关闭模拟开关。

这是无需签名 Secrets 的开发测试 APK，每次 runner 可能使用不同 debug 签名。
如覆盖安装提示签名不兼容，需要卸载旧测试版后重装；卸载会清除本机名称和校准，请先记录参数。
这不是使用长期发布密钥签名的正式安装包。

SHA256SUMS 提供 APK 和 build-info.json 的校验值。BLE RSSI 只是粗略估距；此构建不代表真实 Pico / 手机端到端验收已通过。
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
        print(json.dumps(context, ensure_ascii=False))
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
