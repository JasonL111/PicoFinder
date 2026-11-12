<img src="docs/assets/picofinder.svg" width="96" alt="PicoFinder 标识">

# PicoFinder

**[English](README.md) | 简体中文**

[![CI](https://github.com/JasonL111/PicoFinder/actions/workflows/ci.yml/badge.svg)](https://github.com/JasonL111/PicoFinder/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/JasonL111/PicoFinder)](https://github.com/JasonL111/PicoFinder/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

一个本地运行的 BLE 近距离寻物工具：USB 供电的 Raspberry Pi Pico 2 W 持续广播标签 ID，
Android 手机通过相对信号强弱帮助逐步缩小搜索范围。适合在住宅附近寻找钥匙、背包等物品，
不提供精确室内定位或远程追踪网络。

**当前版本：1.0.0。** 正式 APK 由版本 tag 触发构建，并使用维护者长期保存的密钥签名。
Actions 提供 Debug 预览制品；原 `dev-latest` 是历史开发产物。正式下载请核对对应版本的 Release 附件。

## 下载

- [已发布版本](https://github.com/JasonL111/PicoFinder/releases) · [最新正式版](https://github.com/JasonL111/PicoFinder/releases/latest)
- [v1.0.0 发布页](https://github.com/JasonL111/PicoFinder/releases/tag/v1.0.0)：签名 tag 工作流完成后可用。
- 正式附件为 **`picofinder-1.0.0.apk`**，同时提供 `SHA256SUMS` 和 `build-info.json`。

普通用户无需 Android Studio 或自行编译。安装及分发说明见 [docs/delivery.md](docs/delivery.md)。

## 功能与界面

- 多标签、稳定 Device ID、本地名称及每设备独立设置。
- 原始与平滑 RSSI、动态相对信号圆环、粗略距离和靠近／远离趋势。
- 失联检测、最后收到广播时间及 8 秒的 1 米校准。
- 只有持续稳定极强信号才短振一次。进入阈值固定 **−45 dBm**，退出阈值固定 **−51 dBm**；
  用户可以关闭振动，不能修改阈值。普通寻找仅提供视觉反馈。
- 根据系统语言显示英文或简体中文，支持深浅主题及减少动画。
- 仅前台扫描，每次最多 2 分钟；无账号、服务器、互联网连接、配对或 GATT 服务要求。

查找页优先显示信号等级和趋势，下方保留距离／状态，技术参数可展开查看。
圆环对现有平滑测量值做短暂的显示补间，不改变 RSSI、距离、扫描或振动判断。
界面设计见 [UI 说明](docs/ui-v1.2.md)。

## 硬件与软件要求

| 部分 | 要求 |
|---|---|
| 标签 | Raspberry Pi **Pico 2 W**、USB 电源，首次部署需 USB 数据线 |
| 固件环境 | 官方 **RPI_PICO2_W MicroPython 1.27.0** 稳定 ARM 构建 |
| 手机 | **Android 12 / API 31+**、支持 BLE、开启蓝牙和系统位置开关 |
| 仅开发需要 | JDK 21、Android SDK / target 36、仓库 Gradle Wrapper，Python 测试使用 CPython |

Pico、Pico W、Pico 2 是不同硬件。ESP32-C3 目录仅提供未来同协议移植约束，本仓库尚无经过验证的 ESP32-C3 固件。

## 架构与协议

```mermaid
flowchart LR
  Pico["Pico 2 W / MicroPython"] -->|"非连接 BLE 广播"| Scanner["Android 前台扫描"]
  Scanner --> Core["纯 Kotlin 解码 / 设备状态 / 中位数 + EMA"]
  Core --> UI["Compose / Material 3"]
  UI <--> Settings["按 ID 保存的本机设置"]
```

v1 协议通过自定义 128-bit Service Data，在 29 字节广播中携带稳定 uint32 Device ID、
参考 RSSI、flags 和电量信息。身份不依赖蓝牙 MAC 地址。
[protocol/spec.md](protocol/spec.md) 与[共享测试向量](protocol/test-vectors.json)是唯一线协议依据。
1.0.0 主要整理界面与发布；UUID、数据格式和固件行为保持兼容。

## 快速开始

1. 按住 **BOOTSEL** 连接 Pico 2 W 的 USB，在出现 `RP2350` 启动盘后松开。
2. 安装官方 [RPI_PICO2_W MicroPython 1.27.0](https://micropython.org/download/RPI_PICO2_W/)。
3. 按 [Pico 部署说明](firmware/pico2w/README.md)，把四个 `.py` 文件上传到设备根目录。
   `main.py` 上电自动运行；默认 300 ms 广播、−59 dBm 参考值、电量未知。
4. 下载并安装对应版本 APK；系统询问时允许浏览器／文件管理器安装此来源应用。
5. 开启蓝牙和系统位置，授予附近设备与精确位置权限，点击“开始扫描”。
6. 选择与 USB 日志 ID 一致的标签，缓慢走动，观察数秒信号趋势。

已有 PicoFinder 标签升级至 1.0.0 **无需重刷固件**。下载工具和固件需要联网，App 与标签运行不需要。
Debug 预览具有明确标记的模拟模式，不触发真实振动；正式 Release 不包含 Fake Scanner。

## 校准与近距离寻找

进入“设备设置与 1 米校准”，先保存手动编辑。让手机与标签相距约 1 米，避开金属和人体遮挡，
开始扫描后固定姿态，执行 8 秒校准。至少需要 12 个有效样本、时间跨度至少 6 秒；
不足或波动过大时保留原值。环境衰减系数 n 默认 2.2，可设为 1–6。
名称、校准、失联超时和振动开关仅保存在当前手机。

走动时以圆环和趋势为主，估算米数仅供参考。稳定确认极强后显示“信号非常强 · 仔细找找附近”，
并可短振 120 ms 一次。重新进入前需持续降到 −51 dBm 或更弱，且两次提醒至少间隔 10 秒。
系统触感、勿扰模式和硬件条件可能抑制振动。停止、失联、离开查找页和进入后台都会清除当前提醒会话。

## 版本化发布

`version.properties` 统一版本来源：**1.0.0 / versionCode 1000000**。推送与它一致的版本 tag 后，
构建由维护者固定密钥签名的 Release APK，创建 **PicoFinder v1.0.0**。
普通分支、PR、手动工作流仅测试、lint、构建并上传 Debug Artifact；不再覆盖正式 Release 或移动版本 tag。

签名 Secrets、维护者步骤和 `v1.0.0` 发布命令见 [docs/releasing.md](docs/releasing.md)。
版本内容记录在 [CHANGELOG.md](CHANGELOG.md)。

## 开发与测试

普通用户直接安装 APK。开发者可在 Android Studio 打开 `android/`，或使用仓库 Wrapper：

```bash
python -m unittest discover -s tests/protocol -v
python -m unittest discover -s tests/ci -v
python -m unittest discover -s tests/localization -v
cd android
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
# 已连接 API31+ 模拟器或设备时，可选运行：
./gradlew :app:connectedDebugAndroidTest
```

工具链与代码结构见 [Android 开发说明](android/README.md)。实际执行结果、失败经过及待人工验证场景
分别记录在 [docs/testing.md](docs/testing.md)。模拟数据和界面截图不能证明真实 BLE、测距精度、
马达振动、帧率或功耗。

## 项目结构

| 路径 | 内容 |
|---|---|
| `android/core` | 纯 Kotlin 解码、滤波、距离、校准和设备状态 |
| `android/app` | 扫描、Compose UI、中英文资源、本地设置与触感 |
| `firmware/pico2w` | MicroPython 固件与部署说明 |
| `protocol` | 线协议与共享向量 |
| `tests` | Python 协议、本地化及发布回归测试 |
| `.github` / `tools` | CI、制品核验和不可覆盖的版本发布 |
| `docs` | 安装、发布、界面设计与验证记录 |

## 限制、隐私与适用范围

人体、墙壁、家具、朝向和反射都会影响 RSSI。它不能识别房间或方向，扫描不到也不能断言物品在室外。
本项目不是厘米级测距、UWB、GPS、防盗系统或 Google Find Hub 设备；不提供地图、最强位置记录、室内轨迹或后台提醒。

v1 广播使用未经认证的明文稳定 ID，可能被观察、伪造或重放，32-bit ID 也可能碰撞。
本地名称／设置没有云备份。用户反馈已验证 Pico／Android 扫描、0.5／1／3 m 趋势、拔电失联与重启 ID 稳定，
该反馈不代表所有手机或环境均经过验证。

## 后续方向、贡献与许可证

计划扩大手机验证、根据实测改进室内使用指引，并为 ESP32-C3 移植相同协议。
这些是规划，不是已交付功能。贡献需保持线协议兼容并通过共享测试，见 [CONTRIBUTING.md](CONTRIBUTING.md)。

MIT License，见 [LICENSE](LICENSE)。
