import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('ci_release', ROOT / 'tools/ci_release.py')
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


def environment(ref='refs/heads/main', event='push'):
    return dict(os.environ, GITHUB_REF=ref, GITHUB_EVENT_NAME=event, GITHUB_SHA='a' * 40,
                GITHUB_REPOSITORY='JasonL111/PicoFinder', GITHUB_RUN_NUMBER='7', GITHUB_RUN_ID='123')


class MetadataTests(unittest.TestCase):
    def test_main_and_manual_main_publish_prerelease(self):
        for event in ('push', 'workflow_dispatch'):
            result = release.metadata(environment(event=event))
            self.assertEqual('true', result['publish'])
            self.assertEqual('dev-latest', result['tag'])
            self.assertEqual('true', result['prerelease'])
            self.assertEqual('dev.7', result['version_name'])
            self.assertEqual('1007', result['version_code'])

    def test_pull_request_and_feature_branch_never_publish(self):
        for ref, event in [('refs/pull/4/merge', 'pull_request'),
                           ('refs/heads/main', 'pull_request'),
                           ('refs/heads/feature', 'push'),
                           ('refs/heads/feature', 'workflow_dispatch')]:
            self.assertEqual('false', release.metadata(environment(ref, event))['publish'])

    def test_version_tags_and_prerelease_tags(self):
        for tag, prerelease in [('v1.2.3', 'false'), ('v1.2.3-rc.1', 'true')]:
            result = release.metadata(environment('refs/tags/' + tag))
            self.assertEqual('true', result['publish'])
            self.assertEqual(tag[1:], result['version_name'])
            self.assertEqual(prerelease, result['prerelease'])
            self.assertEqual(tag, result['tag'])

    def test_bad_tag_context_and_version_overflow_rejected(self):
        for key, value in [('GITHUB_REF', 'refs/tags/v1.2'), ('GITHUB_REF', 'refs/tags/v01.2.3'),
                           ('GITHUB_SHA', 'not-a-sha'), ('GITHUB_REPOSITORY', 'owner/repo\ncommand'),
                           ('GITHUB_RUN_NUMBER', '0'), ('GITHUB_RUN_NUMBER', '2100000000')]:
            env = environment(); env[key] = value
            with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                release.metadata(env)

    def test_checksum_and_commit_binding(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); apk = root / 'input.apk'; apk.write_bytes(b'fixture APK')
            context = release.metadata(environment())
            output = root / 'dist'
            release.package(apk, output, context)
            release.validate(output, context)
            with self.assertRaises(ValueError):
                release.validate(output, dict(context, commit='b' * 40))
            (output / release.APK_NAME).write_bytes(b'tampered')
            with self.assertRaisesRegex(ValueError, 'Checksum mismatch'):
                release.validate(output, context)

    def test_checksum_filenames_cannot_escape_artifact(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); (root / 'SHA256SUMS').write_text('0' * 64 + '  ../secret\n' + '0' * 64 + '  build-info.json\n')
            with self.assertRaisesRegex(ValueError, 'Invalid'):
                release.validate(root, release.metadata(environment()))


class PublicationTests(unittest.TestCase):
    def invoke(self, initial, ref='refs/heads/main', tamper=False):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); binary = root / 'bin'; binary.mkdir()
            executable = binary / 'gh'
            executable.write_text('#!' + sys.executable + '\n' + (ROOT / 'tests/ci/fake_gh.py').read_text())
            executable.chmod(0o755)
            state = root / 'state.json'
            state.write_text(json.dumps((dict(head='a' * 40, calls=[], release=False, tag=None) | initial)))
            env = environment(ref)
            env.update(PATH=str(binary) + os.pathsep + env['PATH'], GH_FAKE_STATE=str(state),
                       RUNNER_TEMP=str(root), GITHUB_STEP_SUMMARY=str(root / 'summary'),
                       RELEASE_TAG='dev-latest' if ref == 'refs/heads/main' else ref.removeprefix('refs/tags/'),
                       RELEASE_PRERELEASE=release.metadata(env)['prerelease'], APK_DIRECTORY=str(root / 'dist'))
            apk = root / 'input.apk'; apk.write_bytes(b'fixture APK')
            release.package(apk, root / 'dist', release.metadata(env))
            if tamper: (root / 'dist/local-ble-finder.apk').write_bytes(b'changed')
            run = subprocess.run(['bash', '.github/scripts/publish-apk.sh'], cwd=ROOT, env=env,
                                 text=True, capture_output=True)
            return run, json.loads(state.read_text()), (root / 'summary').read_text() if (root / 'summary').exists() else ''

    def test_first_publish_uploads_before_leaving_draft_and_creates_tag(self):
        run, state, summary = self.invoke({})
        self.assertEqual(0, run.returncode, run.stderr)
        self.assertEqual('a' * 40, state['tag'])
        self.assertTrue(state['published'])
        self.assertEqual(3, len(state['uploaded']))
        self.assertIn('/releases/download/dev-latest/local-ble-finder.apk', summary)
        calls = [' '.join(c) for c in state['calls']]
        self.assertLess(next(i for i, c in enumerate(calls) if 'release upload' in c),
                        next(i for i, c in enumerate(calls) if 'release edit' in c))

    def test_existing_continuous_release_updates_only_development_tag(self):
        run, state, _ = self.invoke({'release': True, 'tag': 'b' * 40})
        self.assertEqual(0, run.returncode, run.stderr)
        patch = [c for c in state['calls'] if 'PATCH' in c]
        self.assertEqual(1, len(patch))
        self.assertIn('repos/JasonL111/PicoFinder/git/refs/tags/dev-latest', patch[0])
        self.assertIn('force=true', patch[0])

    def test_stale_main_build_cannot_downgrade_release(self):
        run, state, summary = self.invoke({'head': 'b' * 40})
        self.assertEqual(0, run.returncode, run.stderr)
        self.assertEqual(1, len(state['calls']))
        self.assertIn('源码已有更新', summary)

    def test_denied_api_is_not_treated_as_missing_release(self):
        run, state, _ = self.invoke({'denied': True})
        self.assertNotEqual(0, run.returncode)
        self.assertFalse(any(c[0] == 'release' for c in state['calls']))
        self.assertFalse(state.get('published', False))

    def test_existing_version_release_preserves_original_assets(self):
        run, state, summary = self.invoke({'release': True}, ref='refs/tags/v1.2.3')
        self.assertEqual(0, run.returncode, run.stderr)
        self.assertFalse(state.get('published', False))
        self.assertFalse(any(c[:2] == ['release', 'upload'] for c in state['calls']))
        self.assertIn('保留原有安装包', summary)

    def test_interrupted_version_draft_can_finish_on_retry(self):
        run, state, _ = self.invoke({'release': True, 'draft': True}, ref='refs/tags/v1.2.3')
        self.assertEqual(0, run.returncode, run.stderr)
        self.assertTrue(state['published'])
        self.assertEqual(3, len(state['uploaded']))
        self.assertFalse(any('PATCH' in c for c in state['calls']))

    def test_tampered_download_is_rejected_before_api_mutations(self):
        run, state, _ = self.invoke({}, tamper=True)
        self.assertNotEqual(0, run.returncode)
        self.assertEqual([], state['calls'])


if __name__ == '__main__':
    unittest.main()
