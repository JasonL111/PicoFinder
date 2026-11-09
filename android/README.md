# Android 开发与运行

只想使用 App：直接[下载自动构建测试 APK](https://github.com/JasonL111/PicoFinder/releases/download/dev-latest/local-ble-finder.apk)，
不需要执行本页构建命令。发布流程和签名限制见 [自动交付说明](../docs/delivery.md)。

工具链固定在 version catalog：AGP 8.13.2、Gradle 8.13（Wrapper + SHA-256）、Kotlin
2.2.21 + 同版本 Compose compiler plugin、Compose BOM 2025.12.01、JDK 21、SDK / target
36、Build Tools 36.0.0、minSdk 31。编译字节码目标 Java 17。使用支持 AGP 8.13 的当前
稳定 Android Studio，Gradle JDK 选择 JDK 21，不要单独更换某个组件后假定兼容。

打开本目录；SDK 路径由 `ANDROID_HOME` 或 **未提交的** `local.properties` 提供。

```bash
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
# 可选：真实设备/模拟器可用时
./gradlew :app:connectedDebugAndroidTest
```

App 包名 `local.ble.finder` 是占位 ID，修改 `app/build.gradle.kts` 的 namespace /
applicationId，并统一调整 Kotlin package、目录及测试包名。应用名在 strings.xml。
普通构建会从 Google Maven / Maven Central / Gradle 下载依赖，App 本身不联网。

## 权限及扫描约束

Android 12+ 不需要旧的 BLUETOOTH / BLUETOOTH_ADMIN 权限。项目声明并运行时申请：

- BLUETOOTH_SCAN：原生前台扫描。
- BLUETOOTH_CONNECT：`BluetoothAdapter.isEnabled`、系统 ACTION_REQUEST_ENABLE；
  **不是用于建立连接**，代码不调用 GATT 或读取设备名称/MAC。
- ACCESS_FINE_LOCATION + ACCESS_COARSE_LOCATION：相对物理距离由扫描推导，采用保守
  解释，不做 `neverForLocation` 强断言。Android 12 精确位置必须与 coarse 一起请求；
  只允许大致位置时明确提示未完整授权。未做该断言时系统位置开关也需开启。

不声明 neverForLocation 还避免官方文档所提示的部分信标被过滤问题。没有 GPS /
经纬度获取 API，无后台位置、INTERNET、前台服务、通知权限或开机接收器。用户可拒绝，
再次永久拒绝时提供应用权限设置入口。请求中断或权限撤销后停止扫描，不崩溃。

官方规则参考：
https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
https://developer.android.com/develop/connectivity/bluetooth/ble/find-ble-devices

使用 Service Data UUID + 版本字节 filter，而非 UUID List filter；原始 AD 还会由
纯 Kotlin 解码器完整校验。SCAN_MODE_LOW_LATENCY 只在用户主动前台查找期间运行。
主界面每秒更新一次；每次扫描最多 120 秒，6 秒内禁止重复启动，失败不自动无限重试。
Activity ON_STOP（包括切后台、锁屏和 Activity 重建）停止；返回后不自动扫描。
蓝牙关闭广播即时停止；权限撤销及超时也停止。系统扫描错误用中文解释。

## Debug 演示与测试

只有 debug 源集包含 FakeBleScanner。切换“调试演示（虚拟数据）”，点击开始扫描，
无需 BLE 权限即可看到两标签；第二个标签每 30 秒中仅前 12 秒广播。
UI 显示醒目的模拟提示；真实/模拟库分开，模拟设置仅在本次进程中保留。
release 源集无假扫描器及演示开关。模拟模式不能证明真实权限或射频功能。

`core` 是纯 JVM 模块，解码器、可替换的 BleScanner 边界、滤波/测距/趋势/校准和
DeviceRepository 均不依赖 Android。app 单元测试覆盖实际持久化 JSON 格式；Compose
instrumentation 测试使用真正的 MainActivity + Fake Scanner 检查页面、重命名、停止和
后台生命周期。需要已启动的 API 31+ 模拟器，执行结果见 docs/testing.md。

APK：`app/build/outputs/apk/debug/app-debug.apk`；单元测试报告：
`core/build/reports/tests/test/` 与 `app/build/reports/tests/testDebugUnitTest/`；
lint：`app/build/reports/lint-results-debug.html`。CI 上传 APK 和报告；main / 版本 tag 验证
通过后，CD 会发布可直接安装的 Debug APK、SHA256SUMS 和 build-info.json 到 GitHub Releases。
流水线通过 finderVersionName / finderVersionCode Gradle 属性写入版本，本地默认仍为 1.0.0 / 1。
release 可通过 `./gradlew :app:assembleRelease` 构建未签名包；V1 不配置发布密钥。
