# 验证记录与人工验收

## 1.0.0 正式版准备（2026-11-12）

本次基于 main `ad654a5`，完成品牌、双语资源、图标和版本化签名发布流程；
维护者明确尚未配置正式签名 Secrets，故仅准备，不创建 `v1.0.0` tag / Release。
固件、协议、UUID、Device ID、滤波 / 测距 / 趋势、设备存储格式、会话与触感规则保持兼容。
core 唯一改动是将校准错误中文字符串换为 `CalibrationError` 枚举，由 App 资源翻译；
样本筛选、时间、数值校准算法和原有成功 / 失败条件没有变化。

### 实际检查

| 检查 | 结果 | 边界 |
|---|---|---|
| Python 协议 / 固件模拟 | 9 通过 | 非物理 BLE |
| Python 发布机制 | 18 通过 | Fake GitHub CLI；含已发布版本不可覆盖、草稿、移动 tag、篡改和签名缺失 |
| 中英文资源校验 | 3 通过 | 键 / 格式参数、默认英文、Kotlin 无中文 UI 字面量、语言和图标引用 |
| Kotlin core | 23 通过 | 包含固定 −45 / −51 dBm、新鲜样本、冷却与 V1 回归 |
| App 单元测试 | 15 通过 | 会话、Fake 触感、旧设置兼容及持久化 |
| Debug / 未签名 Release / UI test APK | 构建通过 | 1.0.0 / 1000000，真实 APK 版本及签名检查 |
| lintDebug / lintRelease | 均通过，0 问题 | 无新增 baseline，保留 warningsAsErrors |
| 临时密钥 Release 签名冒烟 | 通过 | 实际签名脚本 + SDK 校验 v3；测试私钥和已签测试 APK 已删除，不是正式签名交付 |
| API31 界面 / 本地化 / 原生图标 | 16 个唯一用例均取得通过结果 | 完整轮次 14/16；修正宿主后本地化 4/4，非单轮 16/16 |
| 真机 BLE、马达、厂商启动器、正式签名升级 | 未运行 | 环境无物理手机 / Pico，正式密钥由维护者配置 |

执行命令：

```bash
python -m unittest discover -s tests/protocol -v
python -m unittest discover -s tests/ci -v
python -m unittest discover -s tests/localization -v
python -m compileall -q firmware/pico2w tools tests
git diff --check
cd android
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :app:assembleDebugAndroidTest
# 安装 app-debug.apk 和 app-debug-androidTest.apk 后：
adb shell am instrument -w -r local.ble.finder.test/androidx.test.runner.AndroidJUnitRunner
```

新增本地化测试使用生产页面检查英文深色 / 中文浅色、320 dp / 1.5 倍字体、设备列表 / 查找 /
设置 / 删除弹窗 / 权限文案、保留消息在语言变化后重新解析、自定义名称不翻译、全部校准错误映射。
图标测试直接渲染 Android drawable：圆 / 圆角方 / 方形 × 浅 / 深背景，并保存实际 PNG 预览。
截图均为模拟标签或受控状态，不是生成的演示图，不证明 BLE 精度、帧率、耗电或真机触感。

首轮 lint 发现 API33 语言声明、中文无用复数、Compose Configuration 读取和图标资源问题；
均修正资源或测试实现，不添加 baseline，不关闭 `warningsAsErrors`。图标移动目录后的增量
资源缓存还曾导致资源未找到，随后清理 App 构建目录重建。失败轮次不计为通过。
Release lint 另发现两条仅用于模拟截图的文案，已移入 Debug 源集。
实际临时签名检查发现 apksigner 在 minSdk31 / v3 包中输出 v2=false、v3=true；
制品检查现接受有效 v2 或 v3，仍拒绝无现代签名的包，并增加对应回归断言。
签名冒烟仅使用一次性测试私钥，正式 Secrets 仍未配置；未创建实际 Release。
输入框标签已缩短，范围放在提示文字中，便于大字体阅读；参数校验和输入项数量不变。
最终排版调整后的全量构建 `BUILD SUCCESSFUL in 4m 33s`，日志 `build-ui-final.log`；临时签名构建 44 s。
PR 初次资源修复后的 [Actions run 43172306768](https://github.com/JasonL111/PicoFinder/actions/runs/43172306768)
实际通过并上传 Debug artifact；最终提交以 PR #3 Checks 为准。
原始日志、截图和兼容性审计保存在工作环境 `/workspace/artifacts/formal-1.0/`。

API31 首次新增用例轮次为 2 通过 / 3 失败。诊断复现发现 Compose 的段落宽度包含空白：
按钮实际尺寸 110×20 px，段落却保留 264×20 px；实际截图中 Enable Bluetooth 完整显示。
测试改为核对每行文字边界、完整字符和省略状态，避免把段落空白误认为裁切；失败时仍保存
截图及尺寸。没有移除可读性断言。首次日志 `ui-localization.log`、诊断 `ui-diagnostic.log`。

完整回归为 **14 通过 / 2 失败，550.620 s**，原始日志 `ui-full.log`。14 个通过用例含
真实 Activity + Fake Scanner 的发现、导航、停止、后台、开关保存、版本、系统动画设置，
既有动画 / 状态 / 大字体 / 顺序回归、SharedPreferences 兼容，以及权限 / 校准错误翻译与
保留消息切换语言。失败均为新增双语弹窗用例：英文截图未区分主页面和 Dialog 两个 root；
中文测试只覆盖 Compose 局部上下文，原生 Dialog 宿主仍是英文，诊断树实际显示
`Remove this saved tag?`，因此无法找到中文标题。增加等待没有解决语言配置错误。
测试现同时设置宿主 Resources 的语言 / 字体并在退出时恢复，确保 API31 原生窗口也使用
测试配置；该兼容桥仅在测试代码中使用，未修改生产语言逻辑。另增强完整按钮滚动、原生窗口
等待和 `isDialog()` 截图选择；保留标题可见、全文字符 / 行边界、取消按钮等断言。
中间复测及诊断失败日志保留，不计作通过。运行环境中断后模拟器被关闭，最后一次仅完成
test APK 构建、尚未安装 / 执行的命令也没有计作通过；恢复后以 `ui-native-locale.log` 为准。

恢复后完整重跑 **4 个本地化用例，189.735 s，OK (4 tests)**，包含中文浅色 / 英文深色、
320 dp / 1.5 倍字体页面与原生弹窗、权限 / 校准错误以及消息 / 名称的语言切换。
12 个未改动用例沿用完整轮次的通过结果，最终覆盖 16 个不同用例；没有把中间失败算作通过。
原始完整轮次为 `ui-full.log`，最终本地化轮次为 `ui-native-locale.log`，逐用例来源为
`ui-validation.json`。最终命令：

```bash
adb shell am instrument -w -r -e class local.ble.finder.LocalizationUiTest local.ble.finder.test/androidx.test.runner.AndroidJUnitRunner
```

发布测试使用独立的 1.0.0 夹具，不依赖仓库当前版本。另在临时 1.0.1 工程中实际重跑同一
18 项发布测试并验证 tag 元数据为 1.0.1 / 1000100，避免正常升级版本号后 CI 被旧测试夹具卡住。
这不是实际发布 1.0.1，也未创建该 tag。

模拟器使用 API31 AOSP x86_64，无 KVM，320 dp 宽；测试内设置 1.5 倍字体并检查浅 / 深主题。
冷启动曾出现 System UI / 启动器无响应和图形缓冲区异常，重启本地框架后才执行测试。
`ro.hw_timeout_multiplier=5` 仅用于该软件环境，不打入 APK；关闭系统动画，专门动画用例
仍使用可控 Compose 时钟验证过渡。它不能用于真机启动时延、帧率或耗电结论。
18 个冻结生产 / 协议 / 固件文件与 main 基线逐字节相同；校准类归一化错误文案后主体
逐字节相同，审计 `compatibility-audit.json`。Manifest 权限集合也保持原样。
本地最终 Debug APK SHA-256：`e5b2038d2d560f145eadcb846acca36d6146ec6a3867a049d4162f4377b2b4ad`。
CI 使用临时 Debug 密钥，与本地包签名 / 哈希不同，应以对应 run 的元数据为准。

### 发布前人工验收

- [ ] 在手机系统语言英语 / 简体中文之间切换；检查列表、查找、设置、删除弹窗、权限和校准错误。
- [ ] 浅 / 深主题和大字体下可读、可滚动；在实际启动器与应用抽屉检查图标及主题图标。
- [ ] 原 Pico 固件直接扫描；名称、校准、振动开关在同签名覆盖安装 / 重启后保留。
- [ ] 普通信号无振动；极强且稳定后仅短振一次；退出 / 冷却后才可再次提醒。
- [ ] 切换标签、返回列表、后台、失联、停止扫描及模拟模式均不误振动；关闭开关后视觉仍完整。
- [ ] 配置并备份正式签名密钥，tag 发布后核对 Assets、校验和、安装版本和签名证书。

以下 V1.2 / V1.1 / V1 记录为历史验证，使用当时的版本号和交付流程；当前发布规则以
[releasing.md](releasing.md) 为准，不再更新 `dev-latest`。

## V1.2 验证（2026-11-11）

本次为 Android 视觉层增量升级。对比 V1.1 主分支 `3689d3b`，`android/core/src/main`、
DeviceViewModel、AndroidBleScanner、PreferencesDeviceStore、FindSessionController、
HapticFeedbackController、Debug / Release 扫描源集、固件、协议和 `.github` 均没有修改。
现有 -45 / -51 dBm、稳定样本、短振和冷却策略保留，UI 只展示已有确认状态。

### 本次实际执行

| 检查 | 结果 | 范围 |
|---|---|---|
| Python 协议 / 固件模拟 | **9 通过，0 失败 / 跳过** | `python -m unittest discover -s tests/protocol -v` |
| Python 发布机制 | **13 通过，0 失败 / 跳过** | `python -m unittest discover -s tests/ci -v` |
| Kotlin core JVM | **23 通过，0 失败 / 跳过** | 清除 test 输出后重跑，含 V1.1 固定阈值 / 新鲜样本 / 冷却回归 |
| Android app JVM | **15 通过，0 失败 / 跳过** | 清除 test 输出后重跑，含会话隔离、Fake 触感及设置兼容 |
| Debug APK / UI test APK | **构建通过** | JDK21 / Gradle8.13 / SDK36，本地 1.2.0 / versionCode 3 |
| Android lintDebug | **通过，0 问题** | 保留 warningsAsErrors，无新增 baseline / 抑制 |
| APK 签名 / manifest | **通过** | v2 Debug 签名；min31 / target36；权限与 V1.1 相同 |
| API31 Compose / SharedPreferences | **11 个唯一用例均有通过结果** | 完整轮次 10/11，修正后重跑 2 个受影响用例 2/2（重叠 1 项）；非单轮 11/11 |
| 真实手机 / Pico / 耗电或帧率测量 | **未运行** | 本环境无物理手机 / Pico，不将软件结果视为真机体验 |

完整开发命令（Android 目录，额外 cleanTest 确保测试实际重跑）：

```bash
./gradlew :core:cleanTest :core:test :app:cleanTestDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
adb install --no-streaming -r -t app/build/outputs/apk/debug/app-debug.apk
adb install --no-streaming -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r local.ble.finder.test/androidx.test.runner.AndroidJUnitRunner
```

完整构建 `BUILD SUCCESSFUL`，最终视觉修正构建日志 `/workspace/artifacts/v1.2/build-review.log`，JUnit 与
lint 报告位于标准 `android/*/build` 目录。首轮测试 APK 编译发现测试类的无效扩展导入，
已删除并完整重跑；该轮失败没有计作通过。没有修改生产业务代码来绕过测试。

API31 AOSP x86_64 使用无 KVM 的软件模拟器。启动阶段曾发生 SystemServer 看门狗重启，
设备 runner 在进入用例前中断；另一次启动未就绪的尝试无法连接 ActivityManager，这些
均没有算作 UI 通过。按 Android 12 官方 `Build.HW_TIMEOUT_MULTIPLIER` 调试机制设置
本地模拟器 `ro.hw_timeout_multiplier=5` 并重启 zygote，预处理 APK、关闭截图已确认的
System UI 无响应弹窗后，才实际完成首轮 11 项测试。该参数仅用于软件测试环境，不进入
APK；该环境不能用于推断真机启动速度、帧率或耗电。

首轮实际执行 **8 通过 / 3 失败**：首次模拟发现等待 10 s 超时、趋势断言误选文字的容器、
大字体状态换行使下一张卡片上移 33 px。后两项分别修正为选择实际文字节点和使用
`Text.minLines` 预留状态行；保留原卡片位置断言和 0.5 px 容差。模拟发现改为 30 s 的
功能等待，并断言演示开关已开启，不改变 Fake 的 300 ms 样本节奏或扫描算法。
首轮失败输出保留在 `/workspace/artifacts/v1.2/compose-attempt4.txt`，不得当作全通过结果。

下一完整轮次 **10 通过 / 1 失败，646.662 s**，分级、动画、大字体卡片、数据保持及偏好
均通过。剩余长流程在编辑设置后已没有“停止扫描”按钮，耗时跨过原 120 s 扫描上限。
把停止及隐藏距离的验证提前到编辑名称之前，保留发现、圆环、改名、保存、返回断言；
同时让顺序测试直接渲染生产 `DeviceListPage`，检查 320 dp / 1.8 倍字体下的真实 LazyColumn。
仅重跑这两个修改过的用例，最终 **177.326 s，OK (2 tests)**。其余 9 个未改动用例沿用
上述完整轮次的通过结果，最终覆盖 11 个不同用例。应用 APK 在这次测试调整前后相同，
没有延长扫描时限、放宽卡片位置断言或改变业务参数。

完整轮次原始日志：`/workspace/artifacts/v1.2/compose-attempt5.txt`；最终受影响用例日志：
`/workspace/artifacts/v1.2/compose-affected-final.txt`；逐用例来源及最终状态：
`/workspace/artifacts/v1.2/ui-validation.json`。最终重跑命令：

```bash
adb shell am instrument -w -r -e class local.ble.finder.DemoUiTest#demoDiscoveryRenameAndStopHideDistance,local.ble.finder.MotionUiTest#listIdentityAndOrderStayStableWhenSignalsCross local.ble.finder.test/androidx.test.runner.AndroidJUnitRunner
```

`MotionUiTest` 验证普通分级、原始峰值不能代替稳定确认、确认后极强文案、停止 / 失联
掩蔽陈旧状态、450 ms 补间的中间值及结束值、静态降级、原 RSSI / 校准值保持、稳定列表、
浅色 / 深色与 1.8 倍字体。`DemoUiTest` 使用真实 Activity + Fake Scanner 验证发现、
查找 / 设置 / 列表往返、改名、停止、后台、开关保存 / Activity 重建及系统动画开关；
原 SharedPreferences Android 兼容测试保留。测试动画使用可控 Compose 时钟。

与 V1.1 主分支逐字节核对的 **32 个冻结文件无变化**（核心、扫描 / 会话 / 触感 / 存储、
固件、协议、Fake / Release 源集、manifest、CI / 工具）；MainActivity 的权限与生命周期
代码除替换主题块外完全一致。审计输出 `/workspace/artifacts/v1.2/compatibility-audit.json`。

最终截图：`/workspace/artifacts/v1.2/screens-final/`，包含真实 Activity + Fake Scanner
的查找 / 设置，以及明确标注受控模拟数据的浅色、深色、1.8 倍字体状态。新旧默认列表为
`/workspace/artifacts/v1.2/v11-list.png`、`v12-list.png`，均由模拟器实际截图取得。
截图没有生成或修饰；它们验证视觉呈现，不代表真实 BLE 或振动验收。

首轮远端 [Actions run 43106394582](https://github.com/JasonL111/PicoFinder/actions/runs/43106394582)
（`fbc3217`）已通过；最终提交检查以 [PR #2 Checks](https://github.com/JasonL111/PicoFinder/pull/2/checks)
为准。原 CI 执行 Python / JVM / lint / APK 构建及签名打包，设备 UI 测试来自上述本地模拟器，
没有把编译 test APK 等同于执行 UI 用例。

本地 APK SHA-256：
`f3141ba252f4db37022ca46362279eb61015d2f4a91e244cf98000bd67e72667`。
APK 不提交进 Git。CI 仍按 run 写入安装版本并上传制品，PR 不发布；合并 main 通过后，
才更新现有 `dev-latest` 中的 APK，原下载 URL 不变。CI APK 的签名 / 版本和本地包不同，
应使用该次构建的 `build-info.json` 与 `SHA256SUMS` 核对，不能混用本地哈希。

### 已发布版本核对

本次实际下载的旧 `dev-latest` 为 `dev.10` / 1010，源提交 `3689d3b`，APK SHA-256
`636624c6d1291f74298b3dd0dc3c17137f12f7bdaa6b4e4ad99d653fd70e2e3c`，与其发布元数据一致，
包内包含 V1.1 极强提示和振动开关文字。这不能证明用户手机当时安装的是哪一个 APK。
V1.2 页头标识和安装版本信息、每页独立滚动用于改善识别与可见性；不能据此把未证实的
“缓存”或“安装错版本”当作已确定原因。Releases 固定条目的创建日期不代表最新 APK 构建日期。

### V1.2 真机验收清单（待用户执行）

- [ ] 确认页头 V1.2；设置“关于”的安装版本与下载构建元数据相符，原名称 / 校准 / 开关仍在。
- [ ] 关闭演示、使用原 Pico 固件扫描；进入查找可直接看到圆环，设置页顶部可见振动开关。
- [ ] 室内缓慢走动，圆环平滑变化，图标与文字表达靠近 / 远离 / 稳定，展开可查看原 RSSI。
- [ ] 普通信号和短峰值不振；稳定极强才一次短振，持续强信号不重复；关闭开关仍可完整查找。
- [ ] 停止扫描、拔 USB、返回列表、后台 / 锁屏后没有陈旧实时强信号或新触感；返回仍需手动扫描。
- [ ] 深浅主题、小屏、系统大字体与关闭动画时内容可读、可滚动，页面返回和校准可操作。
- [ ] 两个标签不因强弱变化重排；实际观察页面卡顿、发热和耗电，如异常记录手机 / 系统 / 步骤。

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
