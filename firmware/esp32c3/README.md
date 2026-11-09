# V2 ESP32-C3 迁移约束（尚未实现固件）

未来使用 ESP-IDF + NimBLE，实现与 [唯一协议规范](../../protocol/spec.md) **相同**的广播：

1. 同一 Service UUID `26a8a030-0a21-4e62-a373-9d127b7ce808`，同一完整 UUID BLE 字节序。
2. 同一 Legacy AD：Flags + type 0x21 Service Data，v1 8 字节布局，总长 29，不能超 31。
   推荐 ADV_NONCONN_IND；ADV_SCAN_IND 也兼容。默认 300 ms，无连接、配对或必需名称。
3. 同一 little-endian uint32 非零随机 ID，首次生成后存入 NVS；正常复位不得重新生成，
   不用 MAC/芯片序列号作为协议身份。提供手动 override 和冲突处理。
4. 同一 v1 版本/保留位/参考 RSSI/电量规则。USB 原型电量为 255；增加字段分配新版本，
   不悄悄延长 v1 包，不要求 Android 判断硬件类型。
5. NimBLE UUID 宏与原始数据数组的顺序必须以实际 AD 字节核对；不能凭字符串直觉填入。
6. 用同一 `protocol/test-vectors.json` 对编码器做逐字节测试，再用真实手机与 Pico 并存
   测试多设备识别、独立校准、断电重启 ID、失联/恢复。
7. 需要量测实际 RF 功率、广播间隔、稳定性；不能直接把 Pico 的 -59 参考值称为实测值。

协议一致时 Android 无需重写解码、RSSI、Repository 或 UI。不要为 ESP32 添加专有
Android 服务、ESP-IDF 依赖、Wi-Fi 后端或强制 hardwareType。
