import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('ci_release', ROOT / 'tools/ci_release.py')
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


def environment(ref='refs/tags/v1.0.0', event='push'):
    return dict(os.environ, GITHUB_REF=ref, GITHUB_EVENT_NAME=event, GITHUB_SHA='a' * 40,
                GITHUB_REPOSITORY='JasonL111/PicoFinder', GITHUB_RUN_NUMBER='17', GITHUB_RUN_ID='123')


def fixture_package(apk, directory, context):
    # Publication tests exercise provenance/immutability, not APK cryptography.
    with patch.object(release, 'inspect_apk', return_value='c' * 64):
        release.package(apk, directory, context)


class MetadataTests(unittest.TestCase):
    def test_only_matching_pushed_version_tag_publishes(self):
        m = release.metadata(environment())
        self.assertEqual(('true', '1.0.0', '1000000', 'release'),
                         tuple(m[k] for k in ('publish', 'version_name', 'version_code', 'build_type')))
        self.assertEqual('picofinder-1.0.0.apk', m['apk_name'])

    def test_main_pr_branch_and_dispatch_are_debug_artifacts_only(self):
        for ref, event in [('refs/heads/main', 'push'), ('refs/heads/main', 'workflow_dispatch'),
                           ('refs/pull/3/merge', 'pull_request'), ('refs/heads/main', 'pull_request'),
                           ('refs/heads/feature', 'push'), ('refs/tags/v1.0.0', 'workflow_dispatch'),
                           ('refs/tags/v1.0.0', 'pull_request')]:
            with self.subTest(ref=ref, event=event):
                m = release.metadata(environment(ref, event))
                self.assertEqual('false', m['publish'])
                self.assertEqual('', m['tag'])
                self.assertEqual('debug', m['build_type'])
                self.assertEqual('picofinder-1.0.0-debug.apk', m['apk_name'])

    def test_version_and_code_have_one_authority_and_increase(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / 'version.properties'
            for version, code in [('1.0.0', 1000000), ('1.0.1', 1000100), ('1.1.0', 1010000), ('2.0.0', 2000000)]:
                p.write_text(f'versionName={version}\nversionCode={code}\n')
                self.assertEqual((version, str(code)), release.project_version(p))
            p.write_text('versionName=1.0.0\nversionCode=3\n')
            with self.assertRaises(ValueError):
                release.project_version(p)

    def test_wrong_tags_and_unsafe_context_rejected(self):
        for key, value in [('GITHUB_REF', 'refs/tags/v1.0.1'), ('GITHUB_REF', 'refs/tags/v01.0.0'),
                           ('GITHUB_REF', 'refs/tags/v1.0.0-rc.1'), ('GITHUB_REF', 'refs/tags/dev-latest'),
                           ('GITHUB_SHA', 'invalid'), ('GITHUB_REPOSITORY', 'owner/repo\ncommand'),
                           ('GITHUB_RUN_NUMBER', '0'), ('GITHUB_RUN_ID', '-1')]:
            env = environment(); env[key] = value
            with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                release.metadata(env)

    def test_manifest_variant_version_and_signature_are_verified(self):
        context = release.metadata(environment())
        signature = 'Verified using v2 scheme (APK Signature Scheme v2): true\nSigner #1 certificate SHA-256 digest: ' + 'c' * 64
        v3_signature = ('Verified using v2 scheme (APK Signature Scheme v2): false\n'
                        'Verified using v3 scheme (APK Signature Scheme v3): true\n'
                        'Signer #1 certificate SHA-256 digest: ' + 'c' * 64)
        manifest = "package: name='local.ble.finder' versionCode='1000000' versionName='1.0.0'\nsdkVersion:'31'\ntargetSdkVersion:'36'\n"
        with patch.dict(os.environ, ANDROID_HOME='/sdk'):
            for signing, body, valid in [(signature, manifest, True),
                                          (v3_signature, manifest, True),
                                          (signature, manifest + 'application-debuggable\n', False),
                                          (signature, manifest.replace('1.0.0', '1.2.0'), False),
                                          (signature.replace(': true', ': false'), manifest, False)]:
                with patch.object(subprocess, 'check_output', side_effect=[signing, body]):
                    if valid:
                        self.assertEqual('c' * 64, release.inspect_apk(Path('app.apk'), context))
                    else:
                        with self.assertRaises(ValueError):
                            release.inspect_apk(Path('app.apk'), context)

    def test_checksums_and_context_binding(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); apk = root / 'input.apk'; apk.write_bytes(b'fixture APK')
            context = release.metadata(environment()); output = root / 'dist'
            fixture_package(apk, output, context)
            release.validate(output, context)
            for key, value in [('commit', 'b' * 40), ('build_type', 'debug'), ('version_code', '3')]:
                with self.assertRaises(ValueError):
                    release.validate(output, dict(context, **{key: value}))
            (output / context['apk_name']).write_bytes(b'tampered')
            with self.assertRaisesRegex(ValueError, 'Checksum mismatch'):
                release.validate(output, context)

    def test_checksum_paths_cannot_escape_artifact(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            (root / 'SHA256SUMS').write_text('0' * 64 + '  ../secret\n' + '0' * 64 + '  build-info.json\n')
            with self.assertRaisesRegex(ValueError, 'Invalid'):
                release.validate(root, release.metadata(environment()))


class PublicationTests(unittest.TestCase):
    def invoke(self, initial=None, ref='refs/tags/v1.0.0', event='push', tamper=False):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); binary = root / 'bin'; binary.mkdir()
            executable = binary / 'gh'
            executable.write_text('#!' + sys.executable + '\n' + (ROOT / 'tests/ci/fake_gh.py').read_text())
            executable.chmod(0o755)
            state = root / 'state.json'
            state.write_text(json.dumps(dict(calls=[], tag='a' * 40, release=False) | (initial or {})))
            env = environment(ref, event)
            env.update(PATH=str(binary) + os.pathsep + env['PATH'], GH_FAKE_STATE=str(state),
                       RUNNER_TEMP=str(root), GITHUB_STEP_SUMMARY=str(root / 'summary'), APK_DIRECTORY=str(root / 'dist'))
            apk = root / 'input.apk'; apk.write_bytes(b'fixture APK')
            fixture_package(apk, root / 'dist', release.metadata(env))
            if tamper:
                (root / 'dist' / release.metadata(env)['apk_name']).write_bytes(b'changed')
            run = subprocess.run(['bash', '.github/scripts/publish-apk.sh'], cwd=ROOT, env=env, text=True, capture_output=True)
            return run, json.loads(state.read_text()), (root / 'summary').read_text() if (root / 'summary').exists() else ''

    def test_first_publish_checks_tag_uploads_then_leaves_draft(self):
        run, state, summary = self.invoke()
        self.assertEqual(0, run.returncode, run.stderr)
        self.assertTrue(state['published'])
        self.assertEqual(3, len(state['assets']))
        calls = [' '.join(c) for c in state['calls']]
        self.assertLess(next(i for i,c in enumerate(calls) if 'release upload' in c), next(i for i,c in enumerate(calls) if 'release edit' in c))
        self.assertIn('/v1.0.0/picofinder-1.0.0.apk', summary)
        self.assertFalse(any('--clobber' in c or '--method' in c for c in state['calls']))

    def test_published_version_is_never_changed(self):
        run, state, _ = self.invoke({'release': True})
        self.assertEqual(0, run.returncode, run.stderr)
        self.assertFalse(any(c[0] == 'release' for c in state['calls']))
        self.assertEqual('a' * 40, state['tag'])

    def test_empty_interrupted_draft_can_resume(self):
        run, state, _ = self.invoke({'release': True, 'draft': True})
        self.assertEqual(0, run.returncode, run.stderr)
        self.assertTrue(state['published'])

    def test_conflicting_draft_asset_cannot_be_overwritten(self):
        run, state, _ = self.invoke({'release': True, 'draft': True,
                                   'assets': [{'name': 'picofinder-1.0.0.apk', 'digest': 'sha256:' + 'b' * 64}]})
        self.assertNotEqual(0, run.returncode)
        self.assertFalse(any(c[0] == 'release' for c in state['calls']))

    def test_annotated_tags_resolve_to_the_same_commit(self):
        run, state, _ = self.invoke({'tag_type': 'tag', 'tag': 'b' * 40, 'peeled': 'a' * 40})
        self.assertEqual(0, run.returncode, run.stderr)
        self.assertTrue(state['published'])

    def test_moved_tag_or_conflicting_release_commit_fails_closed(self):
        for initial in ({'tag': 'b' * 40}, {'release': True, 'target': 'b' * 40}):
            run, state, _ = self.invoke(initial)
            self.assertNotEqual(0, run.returncode)
            self.assertFalse(any(c[0] == 'release' for c in state['calls']))

    def test_upload_digest_failure_keeps_draft(self):
        run, state, _ = self.invoke({'corrupt_upload': True})
        self.assertNotEqual(0, run.returncode)
        self.assertTrue(state['draft'])
        self.assertFalse(state.get('published', False))

    def test_denied_api_not_treated_as_a_missing_release(self):
        run, state, _ = self.invoke({'denied': True})
        self.assertNotEqual(0, run.returncode)
        self.assertFalse(any(c[0] == 'release' for c in state['calls']))

    def test_branch_and_dispatch_cannot_mutate_github(self):
        for ref,event in [('refs/heads/main','push'), ('refs/pull/3/merge','pull_request'), ('refs/tags/v1.0.0','workflow_dispatch')]:
            run, state, _ = self.invoke(ref=ref,event=event)
            self.assertNotEqual(0, run.returncode)
            self.assertEqual([],state['calls'])

    def test_tampered_artifact_rejected_before_api_calls(self):
        run, state, _ = self.invoke(tamper=True)
        self.assertNotEqual(0, run.returncode)
        self.assertEqual([], state['calls'])

    def test_missing_signing_secrets_cannot_fall_back_to_debug(self):
        env = dict(os.environ, RUNNER_TEMP=tempfile.gettempdir())
        for key in list(env):
            if key.startswith('PICO_RELEASE_'):
                del env[key]
        run = subprocess.run(['bash','.github/scripts/build-release.sh'],cwd=ROOT,env=env,text=True,capture_output=True)
        self.assertNotEqual(0,run.returncode)
        self.assertIn('PICO_RELEASE_KEYSTORE_BASE64',run.stderr)


if __name__ == '__main__':
    unittest.main()
