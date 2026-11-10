# 架构与关键选择

## 边界

`protocol/spec.md` 是唯一线协议。Python 与 Kotlin 不共享运行时代码，只共享黄金
向量；以后 NimBLE 用完全相同的空中报文接入，不加硬件判别或 Pico 专用字段。

Android：`AndroidBleScanner : BleScanner` 负责权限/硬件状态检查、Service Data filter、
回调生命周期与时间戳；`AdvertisementDecoder` 负责原始 AD 与数据体验证；
`DeviceRepository` 按 uint32 ID（以 Long 保留无符号范围）管理设备、配置和最后接收；
`DistanceEstimator` 与 `CalibrationCollector` 为纯 Kotlin；`DeviceViewModel` 在主线程
整合扫描、每秒 UI tick 和校准；Compose 只消费统一 DeviceState。
`PreferencesDeviceStore` 保存名称、参考值、n、失联超时和极近振动开关，不保存 MAC 或 RSSI 历史。

V1.1 增量加入纯 Kotlin `StrongSignalDetector`，只消费设备库已平滑的新 BLE 测量。
固定常量集中在该文件：-45 dBm 进入，-51 dBm 退出；2 / 3 秒、至少 6 / 8 个样本，
采样计数至少相隔 100 ms、连续间隔最多 1 秒、样本接收年龄最多 1 秒；同毫秒和倒序
样本不计数，UI tick 只会过期证据。强信号单次锁定，持续弱信号解除，10 秒冷却，
冷却中的进入直接消耗而非排队补振；复位保留进程内冷却，防止切页/换设备绕过。
缺包隐藏极强确认但不解除单次锁，失联/会话退出清除证据及锁。

App 层 `FindSessionController` 把判定限定到前台、正在扫描、当前查找页面选中的 ID。
设置/列表不持有查找 ID，切页与 ON_STOP 同步复位并取消振动；扫描 generation 拒绝旧回调。
演示或关闭振动仍运行视觉判定，但不调用 haptic pulse。Android 振动使用可注入的
`HapticFeedbackController` / `HapticDevice`；驱动检查系统设置和勿扰，不使用 bypass。
名称、校准、n、失联检测、滤波与趋势算法未更换，无位置存储或新增数据库。
`DeviceRepository.receive` 仅收紧时间戳入口校验：同一 ID 的相同时间戳与倒序样本一起
丢弃，防止重复回调先污染中位数/EMA，再间接通过强信号判定。不同 ID 可使用相同时间戳。

这里没有以硬件类型路由的分支，也没有 ESP-IDF Android 依赖。只有真实可替换的扫描和
持久化边界使用接口；不建立冗余网络层或泛用插件框架。

## 信号处理

接收有效 RSSI 范围 -110..-10 dBm，0/127/-127 等哨兵丢弃，异常包不会延长在线时间。
最近 5 包中位数送入 alpha=0.25 的 EMA；构造器可替换窗口和 alpha。
超过 5 秒没有包后滤波器重置，避免旧历史拖累重获信号。
趋势使用最近 6 秒、每秒一份平滑值，至少 3 秒跨度；比较最早两点和最新两点的中位数，
变化 ±4 dBm 进入靠近/远离，回到 ±2 dBm 才变为稳定，避免 1–2 dBm 抖动翻转。

`d = 10 ^ ((reference - smoothedRSSI) / (10*n))`，n 默认 2.2；每设备 override 优先，
没有 override 时采用广播内有效参考值。UI 使用约一位小数，超 100 m 仅显示 >100，
弱信号时不要按公式数字推断可靠范围。测距没有物理角度或三角定位含义。

在线阈值默认：0..5 s 正常（RSSI <-85 时信号弱）、5..10 s 可能失联且隐藏距离、
>=10 s 已失联。阈值可设 5..120 s，弱提示为阈值一半。时间运算用 elapsedRealtime
单调时钟；墙上时间只用于展示，不受用户改时间影响超时判断。停止扫描立即隐藏距离，
保留标明“末次”的 RSSI / 接收时间；已保存、尚未接收到的设备也能展示。

校准固定收集 8 秒，仅目标设备的有效原始样本参与，至少 12 包和 6 秒跨度。
中位数 + MAD / IQR 拒绝强波动（MAD>6 dBm 或 IQR>12 dBm）并剔除离群值，
保留的稳定样本也需至少 12 包及 6 秒跨度，参考值需在 -100..-20。
不足或取消不保存；每个 ID 独立；恢复默认清除 override 并重置 n、超时，保留名称。

## 固件与限制

所有纯编码/身份逻辑可在 CPython 验证；bluetooth 硬件接口仅存在于 main.py。
身份持久化在启动广播之前完成；I/O/损坏错误 fail closed，radio 错误有上限退避，
没有 watchdog 复位循环。USB 供电时电量始终 unknown。

v1 使用稳定明文 32-bit ID，碰撞和伪造需用户处理，未设计认证、密钥轮换或隐私轮换。
这些限制不能通过 MAC 地址或标签名称绕开，也不应声称等价于 Find Hub 网络。
