# 验证记录与人工验收

## 本次会话实际结果

| 检查 | 结果 | 说明 |
|---|---|---|
| CPython unittest | **9 通过，0 失败/跳过** | 编码、UUID、29 字节 AD、共享向量、配置、身份、模拟 BLE API 与退避 |
| Kotlin core JVM | **11 通过，0 失败/跳过** | 共享正/负向量、所有截断、无符号 ID、滤波/趋势、测距、失联、校准、设备库 |
| Android app JVM | **2 通过，0 失败/跳过** | 持久化 JSON 往返、独立设备校准、损坏/无效记录 |
| Debug APK + test APK | **构建通过** | JDK21 / Gradle8.13 / AGP8.13.2 / SDK36 / min31 |
| Android lintDebug | **通过，0 问题** | warningsAsErrors=true；未使用 baseline 隐藏问题 |
| APK 签名与 manifest | **通过** | v2 debug 签名有效；min31 / target36；无 INTERNET / 后台位置权限 |
| Cloud setup 刷新 | **通过** | tools/cloud_setup.py 实际运行，复用工具/缓存后再次验证构建与测试 |
| Compose instrumentation | 结果待本轮模拟器执行完成后更新 | 真 MainActivity + Debug Fake Scanner；不测试射频 |
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
- 设备测试：`android/app/build/reports/androidTests/connected/debug/`。

首次 lint 暴露的 API31 接收器兼容、备份规则、图标与 KTX 问题已修正；移动资源目录后
清理了 app 生成缓存并重新构建，最终 lint 通过。软件测试包含假 BLE，不代表 MicroPython
二进制、USB 或真实无线堆栈已经执行。模拟模式也不代表真实权限请求通过。

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
