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
V1.1 保留每秒一次的 RSSI 数值刷新，避免广播频率主导绘制；极强确认变化即时发布。
新 BLE 测量逐个推进判定，定时刷新只处理过期和超时，不能增加稳定时长或请求振动。
Activity ON_STOP（包括切后台、锁屏和 Activity 重建）停止；返回后不自动扫描。
蓝牙关闭广播即时停止；权限撤销及超时也停止。系统扫描错误用中文解释。

## Debug 演示与测试

只有 debug 源集包含 FakeBleScanner。切换“调试演示（虚拟数据）”，点击开始扫描，
无需 BLE 权限即可看到两标签；第二个标签每 30 秒中仅前 12 秒广播。
第一个标签周期性达到极强信号，可预览 V1.1 圆环确认与普通强弱变化。
UI 显示醒目的模拟提示；真实/模拟库分开，模拟设置仅在本次进程中保留。
release 源集无假扫描器及演示开关。模拟模式不能证明真实权限或射频功能。
模拟数据即使达到极强阈值也只显示视觉提示，不调用真实振动。

## V1.1 极近振动

普通权限 `VIBRATE` 只在 manifest 声明，不弹出危险权限请求。Android 12 使用
`VibratorManager.defaultVibrator`、一次 120 ms `VibrationEffect` 和触感用途属性；
Android 13+ 使用 `VibrationAttributes.USAGE_TOUCH`，API31–32 使用 sonification
AudioAttributes。每次请求前检查硬件和勿扰状态；API31–32 读取系统触感开关，API33+
由系统对 USAGE_TOUCH 应用触感开关/强度设置，避免依赖已废弃的旧设置。系统限制/异常安静降级。
不申请通知策略访问，不设置绕过标记。不同厂商对触感设置的处理仍需真机验收。

`StrongSignalDetector` 为纯 Kotlin，固定 -45 / -51 dBm，2 s 进入、3 s 退出、10 s 冷却；
至少 6 / 8 个计数样本，计数间隔至少 100 ms，相邻有效测量间隔不超过 1 s，接收时年龄
不超过 1 s，拒绝同毫秒/倒序/未来测量。短缺包隐藏极强确认并清空连续证据，但不解除
单次锁；真正失联或会话结束才复位锁。冷却在页面/设备/扫描切换之间保留（进程内）。
`FindSessionController` 约束选中设备、页面、扫描、前台、演示及用户开关；
`HapticFeedbackController` 和 `HapticDevice` 支持注入 Fake，无需 Android 服务即可测试。
Compose 不直接调用振动；导航与 Activity ON_STOP 同步关闭会话。

持久化沿用 `finder-devices-v1` / `devices`，仅新增默认 true 的 `nearAlertVibration` 字段。
旧记录原字段不变；阈值不写入用户设置。振动开关立即保存，不需点击“保存设置”；
校准/改名/恢复默认校准都保留该开关。

## V1.2 视觉层

`MainActivity.kt` 保留原导航 / 会话边界，使用 `AnimatedContent` 做 240 ms 页面过渡；
各页独立滚动。`FinderScreens.kt` 实现设备卡片、信号主视觉、概况与折叠详情；
`FinderTheme.kt` 集中色彩、排版、圆角和动画时长，`FinderIcons.kt` 提供统一线形图标。
`FinderMotion.kt` 只观察前台生命周期与系统动画设置，不持有扫描器或触感控制器。

圆环使用 `animateFloatAsState` 和 `FastOutSlowInEasing`，450 ms 插值原有平滑 RSSI 的
显示进度，2% 取整仅用于绘制。`animateColorAsState` 与有限淡入淡出表达状态，不创建普通
扫描的无限循环。极强视觉只消费已有 `veryStrongSignal`；不根据动画或原始峰值判定。
后台或系统 `animator_duration_scale=0` 时移除视觉补间，静态内容可读、可操作。
无新增生产 UI 库；`ui-test-manifest` 仅在 Debug 为独立 Compose 测试提供宿主 Activity。

`MotionUiTest` 用受控模拟状态检查分级、失联掩蔽、动画中间值与静态降级、RSSI 不变、
设备顺序、深浅主题及 1.8 倍字体；`DemoUiTest` 保留真实 Activity + Fake Scanner 的导航、
停止 / 后台与持久化交互。截图来自实际 Compose 渲染，不代表物理 BLE 或振动验收。
设计说明见 [ui-v1.2.md](../docs/ui-v1.2.md)，实际执行结果见 [testing.md](../docs/testing.md)。

`core` 是纯 JVM 模块，解码器、可替换的 BleScanner 边界、滤波/测距/趋势/校准和
DeviceRepository 均不依赖 Android。app 单元测试覆盖实际持久化 JSON 格式；Compose
instrumentation 测试使用真正的 MainActivity + Fake Scanner 检查页面、重命名、停止和
后台生命周期。需要已启动的 API 31+ 模拟器，执行结果见 docs/testing.md。

APK：`app/build/outputs/apk/debug/app-debug.apk`；单元测试报告：
`core/build/reports/tests/test/` 与 `app/build/reports/tests/testDebugUnitTest/`；
lint：`app/build/reports/lint-results-debug.html`。CI 上传 APK 和报告；main / 版本 tag 验证
通过后，CD 会发布可直接安装的 Debug APK、SHA256SUMS 和 build-info.json 到 GitHub Releases。
流水线通过 finderVersionName / finderVersionCode Gradle 属性写入版本，本地默认为 1.2.0 / 3。
release 可通过 `./gradlew :app:assembleRelease` 构建未签名包；V1 不配置发布密钥。
