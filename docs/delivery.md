# 下载、安装与版本核对

正式下载入口为 [Releases](https://github.com/JasonL111/PicoFinder/releases)。
**1.0.0 当前已完成发布准备，尚未发布**：维护者需要配置固定签名 Secrets，合并准备工作，
再推送 `v1.0.0`。旧 `dev-latest` 保留为历史开发产物，不代表本次正式版本。

## 普通用户

1. 在 [最新正式版](https://github.com/JasonL111/PicoFinder/releases/latest)选择所需版本；
   [v1.0.0](https://github.com/JasonL111/PicoFinder/releases/tag/v1.0.0) 将在签名工作流成功后出现。
2. 从 Assets 下载 `picofinder-1.0.0.apk`。Android 12+ 才能安装，无需 Android Studio。
3. 按系统提示允许浏览器或文件管理器安装此 APK。应用显示名为 **PicoFinder**。
4. 为 App 授予附近设备和定位权限，打开蓝牙；标签无需与手机配对。

每个正式版本包含版本化 APK、`SHA256SUMS`、`build-info.json`。
元数据记录完整提交、构建地址、Android 版本、构建类型、APK 和签名证书 SHA-256。
下载全部文件后执行 `sha256sum --check SHA256SUMS`；Windows 可使用
`Get-FileHash .\picofinder-1.0.0.apk -Algorithm SHA256` 对照校验和。
校验和确认下载完整性，签名证书决定是否能覆盖升级；两者用途不同。

## 开发预览与正式版的区别

| 触发 | 结果 |
|---|---|
| 普通分支 / main push、PR、手动 Run workflow | 测试、双变体构建、lint；Actions 中的 `picofinder-debug` artifact |
| 推送与 `version.properties` 一致的稳定 tag，如 `v1.0.0` | 所有检查通过后，使用维护者密钥签名并发布一个新 Release |
| 不匹配的版本 tag | 校验失败，不发布 |
| 手动运行，即使选择 tag | 只产生 Debug artifact，不发布 |
| 重跑已发布版本 | 不覆盖既有 Release、资产或 tag |

Actions artifact 必须打开**对应提交**的成功 run，下载并解压 `picofinder-debug`。
其中 APK 为 `picofinder-1.0.0-debug.apk`；它与正式版版本名相同，但元数据明确标为 Debug，
仅 Debug 提供模拟模式。Actions 成功不等于真机 BLE 或振动已通过验收。

## 升级与本机数据

应用 ID 继续为 `local.ble.finder`，已有存储格式、Device ID、名称、校准值及振动开关保留。
Android 只有在应用签名兼容且版本号允许时才能直接覆盖安装并保留数据。

旧 CI 每次生成 Debug 密钥，新正式版将使用长期固定的 Release 密钥，两者通常不兼容。
如提示签名冲突，先手工记录各标签名称、参考 RSSI、n、超时和振动开关，卸载旧 Debug 版再安装。
**卸载会删除本机设置，App 不提供导出或云端恢复。** 今后正式版应始终使用同一密钥和递增 versionCode。

## 发布失败

缺少签名 Secrets 会在签名步骤立即失败，不会降级为 Debug 并发布。普通 CI 无需这些 Secrets。
发布先创建草稿，上传并核对三个文件的服务端 digest，成功后才公开。
同一 run 重试可以补齐草稿中缺失且不冲突的文件；若草稿已有不同内容，应人工检查，不能覆盖。
HTTP 403、tag 移动、版本或哈希不一致都会停止发布。维护者操作见 [releasing.md](releasing.md)。

App 本身没有 Internet 权限、不使用后端；下载和发布由 GitHub 提供。
