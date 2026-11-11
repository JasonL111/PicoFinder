# 验证记录与人工验收

## V1.1 验证（2026-11-10）

本次仅修改 Android 查找体验；固件、UUID、v1 数据格式和 Device ID 语义未改动。
V1 的中位数 + EMA、趋势、距离公式、校准和失联算法未修改。
唯一收紧的接收规则是 `DeviceRepository.receive` 拒绝同一 ID 的相同时间戳（V1 仅拒绝
倒序），防止重复包先改变平滑值。新增设备库与会话组合回归，原有 V1 测试完整保留。

### 用户提供的 V1 真机反馈

用户已反馈 Raspberry Pi Pico 2 W / MicroPython 1.27.0 持续 USB 供电广播、Android
扫描发现成功；在 0.5 m、1 m、3 m 观察到基本合理的 RSSI / 距离趋势；拔掉 USB 可检测
失联；重启 Pico 后 ID 保持不变。该反馈证明已试过的链路可用，**不代表所有软硬件、
精确测距或 V1.1 振动已经验收**。本次无需改固件或重刷 MicroPython。

### 本次软件执行记录

| 检查 | 结果 | 范围 |
|---|---|---|
| Python 协议/固件模拟 | 9 通过 | `python -m unittest discover -s tests/protocol -v` |
| Python 发布流程 | 13 通过 | `python -m unittest discover -s tests/ci -v`；包括 PR 不发布与主分支发布保护 |
| Kotlin core JVM | **23 通过，0 失败/跳过** | 原有 11 项 + 1 项入库去重 + 11 项极强判定；未使用 Thread.sleep |
| Android app JVM | **15 通过，0 失败/跳过** | 11 项会话/触感策略 + 4 项持久化格式测试 |
| Debug APK / UI test APK | **构建通过** | JDK21 / Gradle8.13 / SDK36 / min31，本地 1.1.0 / versionCode 2 |
| Android lintDebug | **通过，0 问题** | 保留 warningsAsErrors；无新增豁免/baseline |
| APK 签名 / manifest | **通过** | v2 Debug 签名；新增普通 VIBRATE，无 INTERNET / 后台位置 / 后台服务 |
| API31 Compose / SharedPreferences | **4 通过，0 失败/跳过** | API31 AOSP 软件模拟器；完整 runner 203.421 s，`OK (4 tests)` |
| V1.1 物理手机触感及 BLE | **未运行** | 本环境未连接物理手机或 Pico；需按下方清单验收 |

首次工具安装同时解压模拟器镜像，临时磁盘不足，导致 Gradle 下载缓存无法写入；该次
失败未算通过。清理本次已解压的下载包，先完成构建，再准备模拟器。
官方 API31 r05 镜像按 SDK 元数据 SHA-1 校验，按原字节稀疏解压节省零填充空间；
模拟器使用独立小数据分区，未改 APK 或测试断言。最终完整 Gradle 命令成功，随后对
Android 13+ 系统设置兼容和 Debug 视觉预览调整再完整验证，最后一轮 **BUILD SUCCESSFUL**。
API31 软件模拟器首次初始化及安装时出现 SystemServer 看门狗重启，日志明确阻塞于
`LooperStats.messageDispatchStarting`，安装返回 `Broken pipe`。这些尝试运行 **0 案例**，
没有计作 UI 测试通过。已按 Android 12 官方运行时开关关闭模拟器的可选 LooperStats
并重启测试框架，继续验证；此调整不进入 App，也不更改测试断言。
首次实际 UI 流程又遇到 Compose 空闲超时，截图确认存在 System UI 无响应弹窗，
该轮未算通过。关闭系统异常弹窗后，数值界面恢复 V1 每秒刷新、极强确认即时发布，
空闲页面不再每次刷新调用硬件取消。重新安装最终 APK 并预编译，保持全部断言，
完整四项直接 runner 最终 **203.421 s，OK (4 tests)**。失败输出保留在
`/workspace/artifacts/v11-compose-ui-attempt1.txt`，成功输出在
`/workspace/artifacts/v11-compose-ui-tests.txt`；它们均为软件模拟结果。

本次成功的模拟器命令（两份 APK 预先构建）：

```bash
adb install --no-streaming -r -t app/build/outputs/apk/debug/app-debug.apk
adb install --no-streaming -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell cmd package compile -m speed local.ble.finder
adb shell cmd package compile -m speed local.ble.finder.test
adb shell am instrument -w -r local.ble.finder.test/androidx.test.runner.AndroidJUnitRunner
```

GitHub Actions 本任务首轮 [run 42919359855](https://github.com/JasonL111/PicoFinder/actions/runs/42919359855)
（`ecadbee0`）的协议/发布测试、Android 测试/lint/构建/签名/制品打包均通过，publish 按
PR 规则跳过。之后提交的最终 CI 以 [PR #1 Checks](https://github.com/JasonL111/PicoFinder/pull/1/checks)
为准；每次运行的结果与其源码 SHA 对应。

本次本地导出：`/workspace/artifacts/local-ble-finder-v1.1-debug.apk`（29,821,054 字节），SHA-256：
`e4f6e637558db63d34e86bff4ee09653bb7052adf725451fe9fdd3b8fef1dccc`。
报告位于 core/app 的标准 `build/reports/`；完整构建日志另存 `/workspace/artifacts/v11-build.log`。
APK 不提交进 Git；PR/分支由原 Actions 生成 artifact，合并 main 后才更新 `dev-latest`，
保留原下载链接及既有 Debug 签名限制，发布成功不能代替真机验收。

开发者完整命令（`android/` 目录）：

```bash
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
# API31+ 模拟器或手机已连接时
./gradlew :app:connectedDebugAndroidTest
```

`StrongSignalDetectorTest` 用时间参数，无 Thread.sleep，覆盖普通信号、异常峰值、
-45 dBm 持续 2 s、单次锁、-44/-46 抖动、-51 dBm 持续 3 s、10 s 冷却、稀疏/突发/
同毫秒/倒序/陈旧/未来样本、UI tick、退出复位及不同校准不改变固定阈值。
进入至少 6 个、退出至少 8 个计数样本；计数间隔至少 100 ms，连续间隔及接收年龄最多 1 s。
短缺包清空连续证据并隐藏极强确认，不自行解除单次锁；真正失联或会话退出才复位。
冷却期内的进入直接消耗，不到期补振，换设备/重进页面也不能绕过进程内冷却。
`CoreTest` 额外验证重复包不能改写末次 RSSI / 平滑 RSSI / 时间，也不能保持在线，随后
新测量仍可重连；会话组合测试验证重复原始峰值无法通过污染滤波制造极强提醒。

`FindSessionControllerTest` 注入 Fake HapticDevice，验证只有选中设备的真实前台扫描
会话可请求 pulse；关闭振动仍可视觉确认，列表/设置/后台/停止/失联/演示/其他设备不
触发，取消调用立即发生，无硬件/系统关闭/驱动抛异常安静降级。与真实 DeviceRepository
组合验证单个原始强峰值不会穿过现有滤波误触发。它们验证软件策略，不能证明物理马达行为。
保留 V1 的 WEAK 状态语义：原始 RSSI 很低但仍新鲜的广播也参与退出判定，不会因为
状态标签为“弱信号”而阻止 -51 dBm 以下的持续弱信号解除单次提醒锁。

`PreferencesDeviceStoreTest` 验证 V1 JSON 默认开启、旧字段保留、每 ID 独立开关、多次
保存/加载与校准 copy 保留开关，设置中只有旧字段和新开关，不存极强阈值。
`PreferencesDeviceStoreAndroidTest` 使用真实 Android SharedPreferences 检查旧数据升级及
重新打开 Store。Compose 测试检查开关立即保存、页面往返/Activity 重建、恢复校准保留
开关和仅有原来四个文本输入（不增加极强阈值入口），并保留 V1 模拟发现/改名/后台测试。

### V1.1 真机验收清单（待用户执行）

- [ ] 记录手机型号、Android 版本与系统触感/勿扰设置；安装后关闭调试演示，原 Pico 固件可继续扫描。
- [ ] 原名称、1 m 校准、n、失联超时保留；旧设备开关默认开启；关闭后杀进程重开仍关闭。
- [ ] 在约 30 m² 住宅走动，圆环与靠近/远离趋势易读；普通强弱变化全程无振动。
- [ ] 缓慢接近，平滑 RSSI 到 -45 dBm 或更强持续约 2 s 后短振一次；短暂峰值不振，持续强信号不重复。
- [ ] 观察 -44/-46 dBm 附近波动不重复；降至 -51 dBm 或更弱约 3 s 后再进入，且距上次至少 10 s 才可能再次短振。
- [ ] 关闭极近振动：仍有完整视觉提示，全程不振；勿扰/关闭系统触感/无马达设备安静降级。
- [ ] 两标签选择隔离；返回列表、进入设置、停止扫描、拔掉标签 USB、锁屏/后台后均不振；回来需手动扫描并重新积累样本。
- [ ] 演示模式无真实振动；2 分钟自动停止仍有效；1 m 校准、失联后重连与多个设备仍正常。
- [ ] 在第二种 Android 版本/厂商手机上重复触感测试；如有同协议 ESP32-C3 再验兼容（本次未验证该硬件）。

固定 -45/-51 dBm 与粗略距离没有一一对应关系，部分手机/标签组合可能难以达到 -45；
不能为了触发而在 UI 修改阈值，也不能用未振动断言物品不在附近。系统/厂商可能抑制
触感；无真实手机时不能宣称系统设置与物理振动体验已全部测试通过。

## V1 历史软件验证记录（此前会话）

下列数字与 artifact 路径为 V1 的历史记录，不视为本次 V1.1 已重跑的结果。

| 检查 | 结果 | 说明 |
|---|---|---|
| CPython unittest | **9 通过，0 失败/跳过** | 编码、UUID、29 字节 AD、共享向量、配置、身份、模拟 BLE API 与退避 |
| Kotlin core JVM | **11 通过，0 失败/跳过** | 共享正/负向量、所有截断、无符号 ID、滤波/趋势、测距、失联、校准、设备库 |
| Android app JVM | **2 通过，0 失败/跳过** | 持久化 JSON 往返、独立设备校准、损坏/无效记录 |
| Debug APK + test APK | **构建通过** | JDK21 / Gradle8.13 / AGP8.13.2 / SDK36 / min31 |
| Android lintDebug | **通过，0 问题** | warningsAsErrors=true；未使用 baseline 隐藏问题 |
| APK 签名与 manifest | **通过** | v2 debug 签名有效；min31 / target36；无 INTERNET / 后台位置权限 |
| Cloud setup 刷新 | **通过** | tools/cloud_setup.py 实际运行，复用工具/缓存后再次验证构建与测试 |
| Compose instrumentation | **2 通过，0 失败/跳过** | API31 AOSP 软件模拟器，真 MainActivity + Debug Fake Scanner；不测试射频 |
| 真实 Pico 2 W UF2 / 上传 / 冷启动 | **未运行** | 当前无连接的 USB Pico |
| 真实 Android 权限 / BLE 接收 / 距离 | **未运行** | 当前无连接的物理手机 |
| GitHub Actions 远端运行 | 以仓库实际 run 为准 | 不能用本地构建代替远端状态 |

Debug APK 常规位置：`android/app/build/outputs/apk/debug/app-debug.apk`。
当前会话另导出 `/workspace/artifacts/local-ble-finder-v1-debug.apk`，SHA-256 见同目录
`SHA256SUMS`；Git 不包含构建 APK，CI 成功后在 artifact 中获取。

复现命令见根 README。JUnit XML / HTML 报告分别位于：

- `android/core/build/test-results/test/`、`core/build/reports/tests/test/`。
- `android/app/build/test-results/testDebugUnitTest/`、`app/build/reports/tests/testDebugUnitTest/`。
- `android/app/build/reports/lint-results-debug.html`。
- 本次成功的设备测试原始结果：`/workspace/artifacts/compose-ui-tests.txt`（`OK (2 tests)`）。
- Gradle 设备报告 `android/app/build/reports/androidTests/connected/debug/` 保留初次安装失败的零测试记录，**不是成功结果**。

首次 lint 暴露的 API31 接收器兼容、备份规则、图标与 KTX 问题已修正；移动资源目录后
清理了 app 生成缓存并重新构建，最终 lint 通过。软件测试包含假 BLE，不代表 MicroPython
二进制、USB 或真实无线堆栈已经执行。模拟模式也不代表真实权限请求通过。

## 模拟器执行经过与可复现步骤

主机实际检查无 `/dev/kvm`，使用 `-accel off` 的 Android 12 / API31 AOSP x86_64。
初次 Gradle UTP 安装在系统启动/分包写入时失败，运行 **0 测试**；首次直接 runner
2 项中 1 项通过，另一项遇到 System UI ANR / Compose 空闲超时，单项重试又遇到
输入焦点超时。这些都没有算通过。

等系统启动就绪、清理系统无响应弹窗后，改用非流式安装，预编译同一 App / test APK。
测试代码将仅用于观察数据的 ViewModel 引用在扫描前取得，减少持续更新时不必要的空闲
等待；断言完整保留。设置页加入 IME 避让，保证键盘显示时可滚动到保存按钮。最终完整
两项直接 runner 用例执行 **143.456 秒，OK (2 tests)**，原始输出另存 artifact。

```bash
# 已启动并解锁 API31+ 模拟器；先构建最终 App 和 test APK
cd android
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install --no-streaming -r -t app/build/outputs/apk/debug/app-debug.apk
adb install --no-streaming -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# 软件模拟器可选的 Android 官方运行时预编译；不修改 APK 或断言
adb shell cmd package compile -m speed -f local.ble.finder
adb shell cmd package compile -m speed -f local.ble.finder.test
adb shell am instrument -w -r local.ble.finder.test/androidx.test.runner.AndroidJUnitRunner
```

必须看 runner 的 `OK (2 tests)` 和失败计数；`adb shell am instrument` 自身退出码 0
并不等于用例全通过。优先在硬件加速模拟器上使用正常的 `connectedDebugAndroidTest`。
这些结果验证导航、模拟发现、重命名、停止后隐藏距离、后台生命周期；不验证射频、
Android 真实权限授予或物理测距。

## 人工实机验收清单

记录手机型号 / 系统版本、MicroPython 版本、标签 ID、广播间隔及环境；不要只看 UI 数字。

### 固件与协议

- [ ] 正确的 Pico **2 W**，刷官方 RPI_PICO2_W 1.27.0 稳定 ARM UF2。
- [ ] 四个源码文件都在设备根目录；不靠电脑 REPL 手动执行，断电重插后自动启动。
- [ ] USB 日志出现非零 ID、29 字节 AD、BLE active；无 Wi-Fi 连接行为。
- [ ] 连续断电/重启三次 ID 不变；升级四个源码文件后 ID 仍不变。
- [ ] 用原始扫描工具核对 UUID 的完整倒序字节、版本1、ID、-59 初始参考与255未知电量。
- [ ] 约300ms广播；无需配对/GATT；尝试连接不应建立连接。

### 手机前台扫描

- [ ] 安装 Debug APK，关闭模拟开关；拒绝权限、只给大致位置、永久拒绝时有明确提示且不崩溃。
- [ ] 授予附近设备+精确位置并开启位置开关，蓝牙关闭时提示；使用“开启蓝牙”后可扫描。
- [ ] 正常开始/停止；ID 与 USB 日志一致；错误码有提示；连续快速点击不无限启动。
- [ ] 不支持 BLE 的设备得到明确说明；有条件时在不同厂商 Android12 和较新系统上重复。
- [ ] 真实名称可改，杀进程/重启 App 后名称、n、校准值和阈值仍在。
- [ ] 有两标签时两个 ID 分开显示、选择、设置；同一设备 MAC 改变不应生成新身份。

### 距离、校准、失联

- [ ] 约1米固定8秒做校准，成功写入；不足样本/拔电/移动剧烈时不覆盖原校准。
- [ ] n 可修改，参考 RSSI 可手动覆盖/清除，恢复默认不清除名称；每个ID独立。
- [ ] 从5米缓慢靠近到1米，观察数秒平滑趋势总体靠近；反向走动总体远离，不要求逐包单调。
- [ ] 原始RSSI与平滑RSSI都显示dBm；估算距离注明约，信号条与最后接收时间合理。
- [ ] 标签拔电后默认5秒隐藏距离并提示弱信号/可能失联，10秒已失联；改阈值后正确生效。
- [ ] 重接标签后重获信号，不沿用失联前的陈旧滤波值；电量显示未知。
- [ ] 点击停止后距离立即清空；保留RSSI明确标为末次。
- [ ] 转后台、锁屏、旋转或重建 Activity 后停止；返回时手动开始；扫描2分钟自动停止。
- [ ] 开启飞行模式再单独开蓝牙、关闭Wi-Fi/移动数据后仍可查找，证明运行不依赖互联网。
- [ ] 深色模式、小屏滚动、中文显示、模拟/真实模式醒目标记都正确。

任何实机失败都应记录原始报文和步骤再修复；完成上述清单后才能声称硬件端到端通过。
