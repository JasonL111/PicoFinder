# Local BLE Finder Advertising Protocol · v1

本文件是 Pico、Android 和未来 ESP32-C3 的唯一线协议规范。测试向量见
[test-vectors.json](test-vectors.json)。不使用 GATT、配对、Wi-Fi、云端或芯片型号。

## 传输与 AD 封装

- Legacy、非连接式广播（允许 ADV_NONCONN_IND 或 ADV_SCAN_IND），默认间隔 300 ms；允许 100–10240 ms。
- Service UUID：`26a8a030-0a21-4e62-a373-9d127b7ce808`，项目固定私有 128-bit UUID。
- UUID 在 AD 中为 **整个 16 字节倒序**：`08e87c7b129d73a3624e210a30a0a826`；不是把 UUID 的几个文本段分别倒序。
- 广播共 **29 字节**，不超过 31 字节。无名称、无 Scan Response、无额外 UUID List。
- 每个 AD 的 length 包含 type 和 value，不包含 length 自身。

| 广播绝对偏移 | 长度 | 内容 |
|---|---:|---|
| 0 | 3 | `02 01 04`：Flags AD，BR/EDR not supported；不声称可连接或 GAP discoverable |
| 3 | 1 | `19`：Service Data AD 长度 25 |
| 4 | 1 | `21`：Service Data - 128-bit UUID |
| 5 | 16 | UUID 的 BLE little-endian 字节 |
| 21 | 8 | 下表的数据体 |

Android `ScanRecord.getServiceData(uuid)` 返回的只是以下 **8 字节数据体**，不含
UUID 或 AD header。扫描器必须使用 **Service Data filter**；`setServiceUuid` 过滤的是
UUID List，不能假定它能匹配只有 AD 0x21 的广播。

官方 MicroPython 1.27.0 的 RP2/BTstack 在 `connectable=False` 时实际使用
**ADV_SCAN_IND (0x02)**，仍不可连接，但可被主动扫描；本实现传空扫描响应。
不能将此 API 误称为强制 ADV_NONCONN_IND。未来 NimBLE 可选 ADV_NONCONN_IND；
Android 对两者处理一致。

## 数据体

所有多字节整数为 little-endian；int8 为二进制补码。偏移相对数据体。

| 偏移 | 长度 | 字段 | 合法值 / 语义 |
|---|---:|---|---|
| 0 | 1 | Protocol Version / uint8 | `1` |
| 1 | 4 | Device ID / uint32 | `1..4294967295`，0 无效；随机生成并持久保存，不使用 MAC |
| 5 | 1 | Flags / uint8 | v1 全部保留，发送端必须为 0；接收端忽略未知位 |
| 6 | 1 | RSSI at 1m / int8 | `-100..-20` dBm；必须提供有效参考值；默认 -59，不代表已实测 |
| 7 | 1 | Battery Level / uint8 | `0..100` 或 `255` 未知；101..254 无效 |

Pico V1 必须发送电量 255。USB 供电不能推断电量。Flags 不是 BLE Flags AD。

v1 数据体必须恰为 8 字节。无效长度、版本、ID、参考 RSSI、电量或 UUID 均拒绝。
接收端可以跳过无关 AD，允许末尾零填充，但不得越界读取；同一报文出现多个本协议
Service Data 时拒绝以避免歧义。未来追加字段必须分配新协议版本；旧接收端忽略不支持
版本，不能按 v1 偏移强行解释。保留 flag 可在未来定义不改变布局的可选提示。

## 黄金向量

Device ID `0x12345678`，RSSI -59，未知电量：

```text
020104 1921 08e87c7b129d73a3624e210a30a0a826 01 78563412 00 c5 ff
```

Python 编码器与 Kotlin 解码器必须读取同一 JSON 文件。ESP-IDF/NimBLE 的 UUID
构造宏和原始数组可能使用不同顺序，移植时以最终空中 AD 字节和黄金向量为准。

## 身份、显示名称与边界

名称仅由手机按 Device ID 本地保存；固件 DEVICE_NAME 只用于 USB 日志。29 字节
广播剩余 2 字节不足以携带有意义的名称，故不强塞名称或依赖扫描响应。
Device ID 是稳定随机 32-bit 值，不是加密身份：存在碰撞、重放、伪造和被跟踪风险。
多个标签必须使用不同 ID；冲突时手动更改一个标签，并在手机删除旧记录。
应用不提供防盗认证或跨城查找；MAC 随机化不改变协议身份。
USB 正常重启不得换 ID；删除 identity 文件或刷空文件系统会换 ID。手动 ID 优先。
RSSI 是粗略测距参考，不能承诺距离精度或方向。协议不包含任何硬件必需字段。
