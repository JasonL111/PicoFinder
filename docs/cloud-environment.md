# Cloud 环境复用

本次环境在 `/workspace/PicoFinder` 使用已有 checkout 初始化工程；任务本来已隔离，不需要
创建 worktree。构建工具和缓存放在 `/workspace/tools`，不写入依赖声明或固件设备状态。

Python 3.12+，Linux x86_64：

```bash
cd /workspace/PicoFinder
python tools/cloud_setup.py
source /workspace/tools/activate-picofinder.sh
```

脚本按需安装并校验 Temurin JDK 21.0.9+10、Android command-line tools 19、SDK 36 /
Build Tools 36.0.0 / platform-tools；Gradle Wrapper 固定 8.13 及 SHA-256。
mpremote 固定 1.27.0；pyserial 3.5、platformdirs 4.14.5。
保留系统代理和 TLS 验证，用提供的**公共 CA**构造 Java truststore，不写凭据。
支持重复执行、刷新依赖并运行项目测试、APK 构建和 lint。
Android 工具也要求标准 `$HOME/.android` 目录存在；若沙箱禁止首次创建，用正常权限
升级仅创建该目录，不更改 HOME 或沙箱配置。

平时从 android/ 执行：

```bash
source /workspace/tools/activate-picofinder.sh
cd /workspace/PicoFinder/android
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

没有必需后台服务、应用环境变量、API Key 或服务器。首次依赖下载需要 GitHub /
release-assets.githubusercontent.com、dl.google.com、services.gradle.org /
downloads.gradle.org、repo.maven.apache.org、plugins.gradle.org 和 PyPI 等官方源，
当前环境 package_managers 预设覆盖构建所需来源。运行 App 不需要这些联网能力。

本次官方文档站 micropython.org、docs.micropython.org、developer.android.com、
www.raspberrypi.com 受到环境出站策略限制；MicroPython API 和板型行为已改用可访问的
**官方 GitHub v1.27.0 源码**核对，没有绕过出站策略。若要在 Cloud 下载 UF2 或直接阅读
这些网页，需要在环境设置中加入对应域名。用户本地电脑可使用文档中的官方链接。
GitHub API (`api.github.com`) 同样被禁止，因此 gh 无法创建 PR 或查询远端 Actions；
原生 Git 的 HTTPS 代理已实际验证可读、可提交推送，不需要另行请求 GitHub token。

可选 UI 测试：已有的 AVD `finder-api31`、API31 AOSP x86_64 image 和 emulator 位于
`/workspace/tools`。无 KVM 时可使用软件模拟，首次启动较慢；运行：

```bash
source /workspace/tools/activate-picofinder.sh
export ANDROID_AVD_HOME=/workspace/tools/avd
"$ANDROID_HOME/emulator/emulator" -avd finder-api31 -port 5554 -no-window -no-audio \
  -no-boot-anim -no-snapshot -accel off -gpu swiftshader_indirect
# 另一终端：确认 adb shell getprop sys.boot_completed 返回 1 后
cd /workspace/PicoFinder/android
./gradlew :app:connectedDebugAndroidTest
```

本 host 没有 KVM；初次软件启动与安装曾发生 ANR / 超时。最终通过非流式安装、
运行时预编译执行了两项完整 UI 用例，具体步骤和原始结果见 [testing.md](testing.md)。

这些进程不会随快照恢复而继续运行。只有需要 UI 测试时再启动模拟器；正常编辑、单元
测试和 APK 构建不依赖它。真实 Pico / 手机仍需在物理连接它们的电脑上部署和验收。

配置工具的 draft 保存结果会在本次交付报告中单独说明。源码中的脚本可复用不等于
Cloud 设置已经持久化、发布或在新任务恢复验证成功。
