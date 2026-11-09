# Pico 2 W 固件部署

## 选择官方固件

基线是 **MicroPython 1.27.0，RPI_PICO2_W，默认 ARM 稳定构建**。
从 https://micropython.org/download/RPI_PICO2_W/ 下载对应 UF2，按官网提供的校验信息
核对下载。不要选择 RPI_PICO_W、RPI_PICO2、普通 Pico、daily preview 或 RISC-V variant。

官方该板定义列出 BLE：
https://github.com/micropython/micropython/blob/v1.27.0/ports/rp2/boards/RPI_PICO2_W/board.json

官方 API `BLE.gap_advertise(interval_us, adv_data=..., resp_data=..., connectable=False)`
使用**微秒**，广播间隔按 625 μs 取整；300000 μs 恰好可表示。
[API 文档](https://docs.micropython.org/en/v1.27.0/library/bluetooth.html)；
[已核对的官方源码](https://github.com/micropython/micropython/blob/v1.27.0/extmod/btstack/modbluetooth_btstack.c)。
RP2 的 BTstack 在 `connectable=False` 下使用 ADV_SCAN_IND：不可连接但可扫描，不是
ADV_NONCONN_IND。项目不需要扫描响应，传 `resp_data=b""`；Android 两种都能处理。
自定义 Service Data 直接通过 `adv_data` 传入完整 29 字节 AD，无需注册服务。

## BOOTSEL 与 UF2

1. 断开 USB，按住 BOOTSEL，再插入**数据线**，见到 `RP2350` 启动盘后松开。
2. 拖入正确 UF2；磁盘自动弹出并重启。不要把 `.py` 文件复制到 BOOTSEL 磁盘。
3. 用 Thonny 选择解释器 “MicroPython (Raspberry Pi Pico)” 和串口。USB REPL
   执行 `import sys; print(sys.implementation)` 确认板名包含 Raspberry Pi Pico 2 W。
   执行 `import bluetooth; b=bluetooth.BLE(); b.active(True); print(b.active()); b.active(False)`
   检查模块和初始化。该步骤需真实设备，本云环境未执行。

## 上传

修改电脑上的 `config.py`：

| 参数 | 默认 / 范围 |
|---|---|
| DEVICE_NAME | USB 日志标签，1..64 UTF-8 字节；Android 名称由手机本地保存 |
| DEVICE_ID | None 自动创建并保存；或手动设 1..0xffffffff，多个设备不得相同 |
| DEVICE_ID_FILE | 根目录 device_id.txt；不要在正常升级时覆盖或删除 |
| ADVERTISING_INTERVAL_MS | 300；允许 100..10240 ms |
| RSSI_AT_1M | -59；允许 -100..-20 dBm，未测量的默认参考值 |

Thonny：按 Ctrl-C 停止旧程序，把 `ble_protocol.py`、`identity.py`、`config.py`、
`main.py` 分别另存到 **MicroPython 设备根目录**，最后上传 main.py，断电重插。

mpremote（电脑命令行，仓库根目录）：

```bash
python -m pip install mpremote==1.27.0
mpremote connect list
# 自动检测一块设备；有多个设备时把 auto 换为 /dev/ttyACM0 或 COM3
mpremote connect auto fs cp firmware/pico2w/ble_protocol.py firmware/pico2w/identity.py firmware/pico2w/config.py :
mpremote connect auto fs cp firmware/pico2w/main.py :main.py
mpremote connect auto reset
mpremote connect auto repl
```

也可运行 `python tools/deploy_pico.py --port auto`，它会先核对板名和最低版本，再上传
四个文件，最后复位。不会写 UF2、擦除身份或修改 Android；需要电脑已安装 mpremote。
`mpremote` 会中断运行中的固件。完成部署后重新上电，可确认真正自动启动。

## USB 日志与身份

启动日志显示名称、八位十六进制 ID、29 字节原始报文和广播启动消息。
`main.py` 运行时使用 `mpremote repl` 连接（不是 exec/fs，会中断程序）；Ctrl-C 停止，
Ctrl-D 软重启，mpremote 常用 Ctrl-] 退出 REPL。需要日志时最好先打开 REPL 再按 Ctrl-D。
不要同时让 Thonny 和 mpremote 占用同一串口。

自动 ID 使用内置 `os.urandom(4)`，写临时文件后 rename、读回校验，再开始广播。
正常复位沿用文件；损坏或写入失败时停止并打印异常，**不会静默换 ID**。
显式 DEVICE_ID 优先于文件。恢复自动 ID 时重新使用原文件；刷空文件系统会丢 ID。

若把广播间隔增大到数秒，手机失联阈值应至少设为广播间隔的三倍，避免误报。
升级其他 MicroPython 版本后须重新核对 BLE API 并执行实机清单，不能假定行为完全一致。

无线暂时错误按 2/4/8…最多 60 秒退避，不快速复位。配置错误留在 REPL 修复。
没有 Wi-Fi 导入、网络凭据、GATT 服务或配对流程。电池字段始终 255（未知）。

## 确认广播

日志只能说明 API 成功返回，不能证明射频链路。用真实手机打开 App、关闭模拟模式，
开始扫描，核对 ID 和数据。也可用能显示 Service Data 0x21 的 BLE 扫描工具核对
[黄金报文](../../protocol/test-vectors.json)。不必在系统“可配对设备”中找到它。

扫描不到时检查：板型和版本、USB 文件位置、Ctrl-C 后是否忘记重启、权限/位置开关、
手机蓝牙、ID、Service Data filter、是否误用 UUID List filter。详见
[排错文档](../../docs/troubleshooting.md)。
