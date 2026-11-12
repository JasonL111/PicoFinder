#!/usr/bin/env python3
"""Publish a verified version-tag artifact without replacing published assets or tags."""
import json
import os
from pathlib import Path
import subprocess

from ci_release import digest, metadata, notes, validate


def gh(*arguments, missing_ok=False):
    result = subprocess.run(['gh', *arguments], text=True, capture_output=True)
    if result.returncode:
        if missing_ok and 'HTTP 404' in result.stderr:
            return None
        raise RuntimeError(result.stderr.strip() or 'GitHub CLI failed')
    return result.stdout


def publish():
    context = metadata(os.environ)
    if context['publish'] != 'true' or context['build_type'] != 'release':
        raise ValueError('Only a matching pushed version tag may publish a signed Release APK')
    directory = Path(os.environ['APK_DIRECTORY'])
    validate(directory, context)
    repo, tag, sha = context['repository'], context['tag'], context['commit']
    ref = json.loads(gh('api', f'repos/{repo}/git/ref/tags/{tag}'))['object']
    for _ in range(5):
        if ref['type'] != 'tag':
            break
        ref = json.loads(gh('api', f'repos/{repo}/git/tags/{ref["sha"]}'))['object']
    if ref.get('type') != 'commit' or ref.get('sha') != sha:
        raise ValueError('Version tag moved or does not identify the artifact commit')
    endpoint = f'repos/{repo}/releases/tags/{tag}'
    existing = gh('api', endpoint, missing_ok=True)
    release = json.loads(existing) if existing is not None else None
    if release and release.get('target_commitish') != sha:
        raise ValueError('Existing release has a different source commit; never move a version tag')
    if release and not release.get('draft'):
        print(f'{tag} is already published; existing assets preserved.')
        return
    notes_file = Path(os.environ['RUNNER_TEMP']) / 'picofinder-release-notes.md'
    notes_file.write_text(notes(context))
    title = 'PicoFinder ' + tag
    if release is None:
        gh('release', 'create', tag, '--repo', repo, '--verify-tag', '--target', sha,
           '--draft', '--title', title, '--notes-file', str(notes_file))
        release = {'assets': []}
    files = [directory / name for name in (context['apk_name'], 'build-info.json', 'SHA256SUMS')]
    assets = {asset['name']: asset for asset in release.get('assets', [])}
    missing = []
    for path in files:
        if path.name not in assets:
            missing.append(str(path))
        elif assets[path.name].get('digest') != 'sha256:' + digest(path):
            raise ValueError('Draft asset differs; review it manually instead of overwriting: ' + path.name)
    if missing:
        gh('release', 'upload', tag, *missing, '--repo', repo)
    ready = json.loads(gh('api', endpoint))
    uploaded = {asset['name']: asset.get('digest') for asset in ready.get('assets', [])}
    if any(uploaded.get(path.name) != 'sha256:' + digest(path) for path in files):
        raise ValueError('Uploaded assets failed digest verification; keeping the release draft')
    gh('release', 'edit', tag, '--repo', repo, '--draft=false', '--prerelease=false', '--latest=true',
       '--title', title, '--notes-file', str(notes_file))
    url = f'https://github.com/{repo}/releases/tag/{tag}'
    summary = os.environ.get('GITHUB_STEP_SUMMARY')
    if summary:
        with open(summary, 'a') as stream:
            stream.write(f'### PicoFinder {tag}\n\n[Release]({url}) · [APK](https://github.com/{repo}/releases/download/{tag}/{context["apk_name"]})\n')


if __name__ == '__main__':
    publish()
