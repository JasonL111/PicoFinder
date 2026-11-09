# GitHub Actions 自动交付 APK

你不需要自己编译 Android。直接打开：

- [最新测试版 APK](https://github.com/JasonL111/PicoFinder/releases/download/dev-latest/local-ble-finder.apk)
- [发布说明、校验和与构建信息](https://github.com/JasonL111/PicoFinder/releases/tag/dev-latest)
- [Actions 构建状态](https://github.com/JasonL111/PicoFinder/actions/workflows/ci.yml)

在 Android 12+ 手机上下载 APK，按系统提示允许浏览器或文件管理器“安装未知来源应用”，
然后安装。若系统阻止下载，先从发布页 Assets 点击 `local-ble-finder.apk`。
App 运行不需要互联网；发布/下载由 GitHub 承担。

## 触发方式

| 操作 | 自动结果 |
|---|---|
| 推送 main | 校验并构建，成功后更新 `dev-latest` 预发布测试版，下载链接保持不变 |
| Actions → Android CI and APK delivery → Run workflow，选择 main | 手动触发同一完整交付流程，无需本地编译 |
| 推送 `v1.2.3` tag | 校验并发布该版本的归档测试 APK；已发布的版本资产不会被覆盖 |
| 推送 `v1.2.3-rc.1` tag | 发布对应版本的预发布测试 APK |
| Pull Request 或其他分支 | 只测试/构建并保存 Actions artifact，不发布 Release |

所有发布依次等待 Python 协议与交付测试、Kotlin 单元测试、Android lint、Debug APK
和 UI 测试 APK 构建成功。CI 构建 UI 测试包，不自动运行实机测试；不能把发布成功当成
真实 BLE 验收通过。main 构建串行发布，重跑旧提交不会覆盖 main 的新版本。

无需配置 Repository Secrets、Android 签名密钥或个人 GitHub token。发布 job 单独声明
`contents: write`，使用 GitHub 自动提供的 `GITHUB_TOKEN`；其他 job 只有读取权限。
PR 不进入发布 job。当前发布的是 **Debug 测试版**，保留有明显标识的模拟模式。

## 文件与版本

每次发布包含：

- `local-ble-finder.apk`：可安装的测试版。
- `SHA256SUMS`：APK 和元数据的 SHA-256。
- `build-info.json`：源码提交、构建 URL、版本、构建类型和 APK 哈希。

主分支版本名为 `dev.<Actions run number>`；版本 tag 使用去掉 `v` 的版本名。
versionCode 为 `1000 + Actions run number`。源码中的本地默认版本不会因 CI 改写。
下载后的包与元数据在发布前再次校验，并绑定当前 workflow 的提交与版本，防止上传错包。
可用 `sha256sum --check SHA256SUMS` 检查下载；Windows 可用
`Get-FileHash .\local-ble-finder.apk -Algorithm SHA256` 与文件中的值比较。

## 测试版签名与升级

首次安装无需任何签名配置。GitHub runner 会生成普通 Android debug 签名，**不同构建
可能使用不同签名**。若覆盖安装报签名不兼容，先记录名称、RSSI 参考值、n 和超时参数，
卸载旧测试版后重装；卸载会清空本机设置。

不把私钥提交到仓库，也不把签名密钥放进可由 PR 读取的共享缓存。需要长期直接覆盖
升级、保留设置或正式分发时，再配置由 GitHub Secrets 保管的固定 release 签名；当前
无需配置的测试版流程不声称具备正式版签名稳定性。

## 开发者发版本

一般只需推送 main。需要保留一个版本时：

```bash
git tag v1.0.0
git push origin v1.0.0
```

不要删除重建已发布版本 tag；修改后使用新 tag。`dev-latest` 是唯一有意移动的测试
标签，不用于固定源码版本。失败时到 Actions 查看失败步骤；失败流程不会被标记为
已成功发布的新版本。

此前 CI 的失败点是 `android-actions/setup-android@v3` 初始化 SDK，尚未进入 Gradle。
当前流程固定 Ubuntu 24.04、JDK21、校验后的 Android CLI19、SDK36 / Build Tools36，
不再请求已废弃的 `tools` 包。所有下载保留 TLS 和文件校验。
