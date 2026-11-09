#!/usr/bin/env python3
"""Reproduce/refresh the Linux x86_64 cloud toolchain without modifying source or lockfiles.

Uses the current checkout. No worktree, application secrets, VPN, or services needed.
Downloads keep inherited HTTPS proxy/CA settings and enforce pinned SHA-256 checksums.
"""
import hashlib
import os
from pathlib import Path
import platform
import shlex
import shutil
import subprocess
import sys
import tarfile
import urllib.parse
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = Path('/workspace/tools')
CACHE = TOOLS / 'downloads'
JDK = TOOLS / 'jdk-21.0.9+10'
SDK = TOOLS / 'android-sdk'
GRADLE_HOME = TOOLS / 'gradle-user-home'
CLI = TOOLS / 'cli19'


def run(args, **kwargs):
    subprocess.run([str(x) for x in args], check=True, **kwargs)


def download(name, url, checksum):
    path = CACHE / name
    def matches():
        if not path.exists():
            return False
        with path.open('rb') as stream:
            return hashlib.file_digest(stream, 'sha256').hexdigest() == checksum
    if not matches():
        temp = path.with_suffix(path.suffix + '.part')
        print('Downloading', name, flush=True)
        with urllib.request.urlopen(url, timeout=90) as response, temp.open('wb') as stream:
            shutil.copyfileobj(response, stream)
        temp.replace(path)
    if not matches():
        raise RuntimeError('Checksum verification failed: ' + name)
    return path


def unzip(archive, target):
    with zipfile.ZipFile(archive) as stream:
        # Only pinned official archives. Restore executable modes from ZIP metadata.
        for item in stream.infolist():
            location = (target / item.filename).resolve()
            if not location.is_relative_to(target.resolve()):
                raise RuntimeError('Unsafe archive path')
            stream.extract(item, target)
            mode = item.external_attr >> 16
            if location.is_file() and mode & 0o111:
                location.chmod(0o755)


def main():
    if platform.system() != 'Linux' or platform.machine() != 'x86_64':
        raise SystemExit('This helper is for Linux x86_64 cloud tasks; use android/README.md elsewhere.')
    CACHE.mkdir(parents=True, exist_ok=True)
    SDK.mkdir(parents=True, exist_ok=True)
    GRADLE_HOME.mkdir(parents=True, exist_ok=True)
    (TOOLS / 'android-user-home').mkdir(exist_ok=True)
    # Standard tools also consult the real user home; never repurpose HOME.
    (Path.home() / '.android').mkdir(exist_ok=True)
    if not (JDK / 'bin/javac').exists():
        archive = download('temurin21.tar.gz',
            'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.9%2B10/'
            'OpenJDK21U-jdk_x64_linux_hotspot_21.0.9_10.tar.gz',
            '810d3773df7e0d6c4394e4e244b264c8b30e0b05a0acf542d065fd78a6b65c2f')
        with tarfile.open(archive) as stream:
            stream.extractall(TOOLS, filter='data')
    if not (CLI / 'cmdline-tools/bin/sdkmanager').exists():
        archive = download('cmdline19.zip',
            'https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip',
            '7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee')
        unzip(archive, CLI)

    trust = TOOLS / 'cloud-cacerts'
    shutil.copyfile(JDK / 'lib/security/cacerts', trust)
    cert = os.environ.get('CODEX_PROXY_CERT')
    if cert:
        # This is the supplied public CA, never a credential or a TLS bypass.
        run([JDK / 'bin/keytool', '-importcert', '-noprompt', '-alias', 'cloud-proxy',
             '-file', cert, '-keystore', trust, '-storepass', 'changeit'])
    properties = {
        'systemProp.javax.net.ssl.trustStore': str(trust),
        'systemProp.javax.net.ssl.trustStorePassword': 'changeit',
        'systemProp.http.nonProxyHosts': 'localhost|127.*|[::1]',
    }
    proxy_args = []
    proxy = os.environ.get('HTTPS_PROXY') or os.environ.get('https_proxy')
    if proxy:
        parsed = urllib.parse.urlsplit(proxy)
        if parsed.username or parsed.password or not parsed.hostname:
            raise RuntimeError('Proxy requires supported platform authentication; no credentials will be copied.')
        port = parsed.port or 80
        for scheme in ('http', 'https'):
            properties['systemProp.' + scheme + '.proxyHost'] = parsed.hostname
            properties['systemProp.' + scheme + '.proxyPort'] = str(port)
        proxy_args = ['--proxy=http', '--proxy_host=' + parsed.hostname, '--proxy_port=' + str(port)]
    config = GRADLE_HOME / 'gradle.properties'
    existing = config.read_text().splitlines() if config.exists() else []
    # Preserve unrelated user configuration in this dedicated Gradle home.
    kept = [line for line in existing if line.partition('=')[0] not in properties]
    config.write_text('\n'.join(kept + [k + '=' + v for k, v in properties.items()]) + '\n')

    env = dict(os.environ, JAVA_HOME=str(JDK), ANDROID_HOME=str(SDK),
               ANDROID_USER_HOME=str(TOOLS / 'android-user-home'), GRADLE_USER_HOME=str(GRADLE_HOME))
    env['PATH'] = str(JDK / 'bin') + os.pathsep + env.get('PATH', '')
    sdk_env = dict(env)
    sdk_env['JAVA_TOOL_OPTIONS'] = (os.environ.get('JAVA_TOOL_OPTIONS', '') +
        ' -Djavax.net.ssl.trustStore=' + str(trust) + ' -Djavax.net.ssl.trustStorePassword=changeit').strip()
    manager = [CLI / 'cmdline-tools/bin/sdkmanager', '--sdk_root=' + str(SDK)] + proxy_args
    expected = [SDK / 'platforms/android-36/android.jar', SDK / 'build-tools/36.0.0/aapt2', SDK / 'platform-tools/adb']
    if not all(p.exists() for p in expected):
        run(manager + ['--licenses'], input='y\n' * 100, text=True, env=sdk_env)
        run(manager + ['platform-tools', 'platforms;android-36', 'build-tools;36.0.0'], env=sdk_env)

    venv = TOOLS / 'mpremote-venv'
    if not (venv / 'bin/python').exists():
        run([sys.executable, '-m', 'venv', venv])
    packages = ['mpremote==1.27.0', 'pyserial==3.5', 'platformdirs==4.14.5']
    if shutil.which('uv'):
        run(['uv', '--cache-dir', TOOLS / 'uv-cache', 'pip', 'install', '--python', venv / 'bin/python'] + packages)
    else:
        run([venv / 'bin/python', '-m', 'pip', 'install', '--disable-pip-version-check'] + packages)
    activation = TOOLS / 'activate-picofinder.sh'
    exports = {
        'JAVA_HOME': str(JDK), 'ANDROID_HOME': str(SDK), 'GRADLE_USER_HOME': str(GRADLE_HOME),
        'ANDROID_USER_HOME': str(TOOLS / 'android-user-home'),
    }
    content = ''.join('export ' + k + '=' + shlex.quote(v) + '\n' for k, v in exports.items())
    paths = [str(venv / 'bin'), str(CLI / 'cmdline-tools/bin'), str(SDK / 'platform-tools'), str(JDK / 'bin')]
    content += 'export PATH=' + shlex.quote(':'.join(paths)) + ':"$PATH"\n'
    activation.write_text(content)
    run([JDK / 'bin/java', '-version'], env=env)
    run([ROOT / 'android/gradlew', '--no-daemon', ':core:test', ':app:testDebugUnitTest',
         ':app:assembleDebug', ':app:lintDebug', ':app:assembleDebugAndroidTest'], cwd=ROOT / 'android', env=env)
    run([sys.executable, '-m', 'unittest', 'discover', '-s', 'tests/protocol', '-v'], cwd=ROOT)
    print('Toolchain and checks complete. Activate: source ' + str(activation))


if __name__ == '__main__':
    main()
