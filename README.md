# PicoFinder V1.2

**手机直接安装：[下载最新测试 APK](https://github.com/JasonL111/PicoFinder/releases/download/dev-latest/local-ble-finder.apk) · [发布页与校验文件](https://github.com/JasonL111/PicoFinder/releases/tag/dev-latest)。无需 Android Studio 或自己编译。**

一个完全本地运行的 DIY BLE 标签查找器：**Pico 2 W + 官方 MicroPython** 持续广播，
**Android 12+ / Kotlin / Compose / Material 3** 按 RSSI 展示粗略距离与变化趋势。
没有 Wi-Fi 连接、云端账号、服务器、GPS、地图、配对或 Google Find Hub 接入。

> **RSSI 不是精确测距。** 人体、金属、墙壁、朝向和多径会改变读数；观察数秒趋势，
> 不要期待每一步距离都单调变化。本项目不提供方向、远程定位或防盗认证。

```mermaid
flowchart LR
    Pico["Pico 2 W · MicroPython"] -->|"29 字节非连接 BLE 广播"| Scan["Android BluetoothLeScanner"]
    ESP["未来 ESP32-C3 · NimBLE"] -.同一协议.-> Scan
    Spec["protocol/spec.md + 共享向量"] -.约束.-> Pico
    Spec -.约束.-> Scan
    Scan --> Decoder["纯 Kotlin 解码 / 设备库"]
    Decoder --> Estimate["中位数 + EMA / 距离 / 趋势"]
    Estimate --> UI["中文 Compose UI"]
    UI <--> Local["本机名称 / 每设备校准"]
```

## 快速上手

硬件：Raspberry Pi **Pico 2 W**（不是 Pico / Pico W / Pico 2）、USB **数据线**、
Android 12 / API 31 以上且支持 BLE 的手机，以及一台用于刷写固件的电脑。
Pico 由 USB 持续供电；不需要电池、蜂鸣器或其他模块。

**已使用 V1 的用户只需更新 Android App，Pico 固件无需改变。** BLE UUID、v1 广播格式、
Device ID 和无需配对的使用方式保持不变；同协议的未来 ESP32-C3 标签可复用全部查找功能。

1. 按住 Pico 2 W 的 **BOOTSEL** 再接 USB，出现 `RP2350` 启动盘后松开。
2. 在[官方 Pico 2 W 下载页](https://micropython.org/download/RPI_PICO2_W/)选择
   **RPI_PICO2_W 官方稳定版 1.27.0** UF2（默认 ARM 构建）。此项目以该版本 API 和
   官方 RP2/BTstack 实现为基线；不要用其他 Pico 型号、preview 或 RISC-V 镜像替代。
3. 把 UF2 拖进启动盘，等待重启。用 Thonny 连接 MicroPython (Raspberry Pi Pico)，
   或安装 `python -m pip install mpremote==1.27.0`。
4. 参照 [Pico 部署说明](firmware/pico2w/README.md)上传四个 `.py` 文件到设备根目录。
   `main.py` 上电自动运行；默认 300 ms 广播、-59 dBm 参考、未知电量。
5. 在手机打开上方“下载最新测试 APK”，下载 `local-ble-finder.apk` 并安装。
   系统询问时，允许浏览器/文件管理器安装此来源的应用。APK 由 GitHub Actions 在测试、
   lint 和构建通过后自动发布；不需要你安装 Android Studio、JDK 或自行编译。
   这是 Debug 测试版；每次构建的签名可能不同，覆盖安装失败时需卸载旧版后重装，
   卸载会清除名称和校准，请先记录参数。完整流程见 [自动交付说明](docs/delivery.md)。
6. 手机开启蓝牙和系统位置开关，打开 App，**关闭“调试演示”**，点击“开始扫描”。
   授予“附近设备”和“精确位置”权限。未授权、蓝牙关闭或扫描失败均有中文说明。
7. 列表中选择与 USB 日志 `ID=XXXXXXXX` 相同的标签，进入查找。逐渐靠近 / 远离，
   看平滑 RSSI 和数秒趋势，距离仅供参考。默认 5 秒无包隐藏距离，10 秒判为失联。
8. 在“设备设置与 1 米校准”中命名标签；先保存编辑。把手机放在距标签约 1 米处，
   保持固定姿态、无金属或人体遮挡，开始扫描后点击“1 米校准”，等待 8 秒。
   有效样本至少 12 个且跨度至少 6 秒，波动过大不会覆盖原值。n 默认 2.2，可调 1..6。

下载 APK、固件和刷写工具需要联网，**App 和标签运行不需要互联网**。
Debug APK 只用于开发，勿用于正式签名发布；不要将本机签名密钥提交到 Git。

## V1.2：更清楚、更平静的实时查找

- 青绿 Material 3 主题，支持系统浅色 / 深色；列表、查找和设置使用一致的卡片、间距与文字层级。
- 查找页顶部突出相对信号圆环与趋势图标；设备概况保留粗略距离、状态、最后接收时间。
  点“查看信号详情”可展开原始 / 平滑 RSSI、1 米参考、n、电量和协议版本。
- 圆环使用原有平滑 RSSI，以约 **450 ms** 自然缓动过渡；只对绘制进度取 2% 小台阶，
  不改变 RSSI、距离、趋势或极近提醒算法。等级文字淡入，页面约 **240 ms** 过渡。
- “非常强”及柔和光晕只展示 V1.1 已稳定确认的状态；单个 RSSI 峰值或动画进度不能触发它。
  普通扫描不使用无限旋转或呼吸动画，也没有新增声音或振动。
- 停止 / 失联后圆环回到中性状态，不把旧 RSSI 当成实时信号。后台停止视觉动画，
  系统动画比例为零时静态展示；文字和图标始终表达完整状态。
- 列表按原设备顺序和 Device ID 稳定显示，不随 RSSI 重排。各页独立滚动，打开标签时从圆环开始，
  打开设置时可直接看到“极近提醒振动”。页头 **V1.2** 标识与设置底部安装版本用于核对新版。

仅升级 Android 界面，无需重刷 Pico。扫描、2 分钟上限、校准、本机设置和下述固定振动规则均沿用 V1.1。
发布链接保持不变：功能合并到 `main` 并通过原 Actions 后，更新同一 `dev-latest` 发布中的 APK，
不会为每次 Actions 创建新的 Release 条目。设计、动画参数与截图说明见 [V1.2 UI 文档](docs/ui-v1.2.md)。

## V1.1：在住宅内逐步缩小搜索范围

进入一个标签的查找页面，边走动边观察大圆环、信号较弱 / 中等 / 较强的文字及
靠近 / 远离 / 基本不变趋势。原始与平滑 RSSI、设备状态、最后接收时间和约计米数仍在。
距离放在次要位置；强弱分级是信号比较，不能当作精确距离区间。人体、家具、墙壁和
手机朝向都会影响信号；看数秒整体趋势，不能据此判断方向或房间。一直扫描不到时应
考虑物品不在当前接收范围内，也可能存在遮挡或供电问题，不能断言它在室外。

普通寻找仅显示视觉反馈。平滑 RSSI **持续至少 2 秒达到 -45 dBm**，且有至少 6 个
时间分布合理的新鲜样本后，显示“信号非常强 · 仔细找找附近”，并可短振 **120 ms 一次**。
持续强信号不会重复振动；必须降至 **-51 dBm 或更弱持续至少 3 秒**、至少 8 个新鲜样本
才解除单次锁定，两次提醒还需间隔至少 **10 秒**。冷却期间的进入不会延迟补振。
这两个 RSSI 阈值在代码中集中固定，**没有阈值输入框、滑块或预设**，不依赖估算米数。

“设备设置与 1 米校准”新增默认开启的 **极近提醒振动** 开关，切换立即按 Device ID
保存在原本机设备库中；旧名称、参考 RSSI、n 和失联超时保留，旧记录默认开启新开关。
恢复校准保留振动选择；关闭振动后全部视觉反馈仍可用。系统关闭触感、勿扰模式、
无振动硬件或系统限制时安静降级，不保证所有手机都能产生物理振动。

只有前台、正在扫描、当前查找页面的选中设备能够请求振动。设置页、列表、切换设备、
失联、停止、后台和屏幕锁定都会清除相关状态；旧样本和 UI 定时刷新不能推进稳定时长。
Debug 模拟模式仅演示视觉效果，**绝不触发真实振动**。不新增声音、后台提醒、峰值位置、
坐标、户型图、轨迹或房间推断。

## 功能与运行规则

- 自定义 UUID + AD 0x21 Service Data；稳定随机 uint32 ID，支持多标签，不以 MAC/名称识别。
- 瞬时 / 平滑 RSSI、约 1 位小数距离、信号圆环、靠近 / 远离 / 稳定、最后接收时间。
- 中位数 + EMA、异常 RSSI 过滤、时间窗口趋势与滞后阈值、失联时隐藏陈旧距离。
- 名称、1 米 RSSI、n、失联超时和极近振动开关按 ID 保存在本机；无云备份、无 MAC 或历史轨迹存储。
- 仅用户主动前台扫描；转后台即停止，返回后手动重启。每次最多 2 分钟，至少间隔
  6 秒启动，错误不自动快速重试。被系统节流时按提示等待至少 30 秒。
- 调试版有明显“模拟模式”标识的 Fake Scanner；模拟库与真实库分离，不污染真实设备设置。
  第二个虚拟标签会周期性失联。Release 不编译 Fake Scanner。

## 权限选择

本功能从 BLE 推算相对物理距离，采取保守的 Android 权限方案，**不声明
`neverForLocation`**（该断言还可能过滤部分广播）。请求 `BLUETOOTH_SCAN`、
`ACCESS_FINE_LOCATION` 以及 Android 12 精确位置请求所需的 `ACCESS_COARSE_LOCATION`。
`BLUETOOTH_CONNECT` 只用于读取蓝牙开关状态和系统开启蓝牙提示；不建立 GATT 连接。
不请求 `INTERNET`、后台位置、通知或后台服务。详细依据见 [Android README](android/README.md)。
V1.1 增加普通 `VIBRATE` 权限，无需运行时弹窗；不申请勿扰策略访问，不绕过系统设置。

## 目录与开发

| 路径 | 内容 |
|---|---|
| [protocol/spec.md](protocol/spec.md) | 唯一线协议；29 字节封装、大小端、兼容规则 |
| [protocol/test-vectors.json](protocol/test-vectors.json) | 两端共享的有效 / 无效原始十六进制向量 |
| [firmware/pico2w](firmware/pico2w) | 可部署 MicroPython 固件与身份持久化 |
| [android/core](android/core) | 无 Android 依赖的协议、测距、校准、设备状态及测试 |
| [android/app](android/app) | 原生扫描、持久化、ViewModel、三个中文页面、Debug Fake |
| [tests/protocol](tests/protocol) | CPython 编码、配置、身份与模拟 BLE API 测试 |
| [.github/workflows/ci.yml](.github/workflows/ci.yml) | CI + CD：校验、构建、自动发布 APK 到 Releases |
| [docs/delivery.md](docs/delivery.md) | 无需本地编译的安装、发布渠道与下载说明 |
| [firmware/esp32c3/README.md](firmware/esp32c3/README.md) | ESP-IDF + NimBLE 迁移约束；无 ESP 固件 |

用户直接下载 APK；以下命令仅供开发者修改代码后验证。Android Studio 打开 `android/`，
包名 `local.ble.finder` 的修改方式及工具链见 [Android README](android/README.md)。

```bash
# 仓库根目录，无第三方 Python 测试依赖
python -m unittest discover -s tests/protocol -v
python -m unittest discover -s tests/ci -v
# Android 目录，Java 21 + SDK 36
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
# 可选：已启动 API 31+ 模拟器 / 手机上的 Compose 模拟流程测试
./gradlew :app:connectedDebugAndroidTest
```

## 验证与边界

当前执行结果、APK 校验和、报告路径及人工验收清单见 [docs/testing.md](docs/testing.md)。
**代码构建 / 模拟测试与真实硬件端到端测试分开记录**；没有真实 Pico / 手机时，
不能证明射频广播、系统扫描过滤、接收率、实际距离校准或 USB 冷启动已工作。

协议含明文稳定 ID，无认证，可能被观察、伪造、重放，也有 32-bit ID 碰撞可能。
冲突时手动更改一个标签 ID。此 V1 适合本地 DIY，不是商用防盗或安全定位系统。

排错：[troubleshooting.md](docs/troubleshooting.md)；架构：[architecture.md](docs/architecture.md)。
未来 ESP32-C3 只需发送相同 UUID / 数据布局 / 版本 / 身份语义并通过共享向量，
无需更改 Android 核心解码、测距或 UI。

MIT License，见 [LICENSE](LICENSE)。
