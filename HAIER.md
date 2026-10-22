# 简居 TV · 海尔本地适配版

基于 Jimu888/home-tv-cn 发布版 v1.16-home.1，本地适配海尔 65H5D（内部型号 LRH670_2G_ALI7H_B，Android 12 / API 31）。保留原项目 MIT 与字体许可。

## 变化

- 独立包名 `app.orchard.tv.haier`，可与官方 `app.orchard.tv` 并存，不覆盖其签名或数据。
- 同时声明普通应用入口与电视应用入口，便于从原厂应用列表手动恢复启动。未解锁阶段不因无法读取接管偏好而自行停用守护。
- 投屏入口连接海尔原厂 Miracast，硬盘入口连接原厂媒体管理，网络入口连接海尔网络设置；全部设置通过公开的 SettingsService（command=1）打开原厂完整设置面板。
- 首页仅默认显示本机已安装的常用应用，正确识别 `com.ktcp.svideo` 腾讯渠道版本。
- 底部“编辑首页”可以选择已安装应用，并通过“前移 / 后移”调整顺序，设置立即保存。移除首页图标不会卸载应用。
- 底部“背景”可选择内置壁纸、每日壁纸或扫码从同一局域网的手机上传照片；选择立即显示并保存。“原始背景”恢复初始配色。此入口不更改原厂屏保。
- 未选择任何首页应用时仍可从“编辑首页”恢复选择。
- 首次旁装不会接管首页；首页守护必须通过带 WRITE_SECURE_SETTINGS 发送权限的 ConfigReceiver 显式启用。
- 默认首页本身为海尔版时，仍可从应用列表有意打开保留的原厂桌面，并用 Home 回到海尔版。
- 保持原有屏保、媒体应用与网络设置；不启用强制保持 ADB。
- 本机出现正常使用中应用被强制停止、无障碍列表清空。用户另行明确授权通知使用权后，启用原项目 RevivalListener，仅用连接回调恢复首页守护，不读取通知内容。实测强制停止后约6秒自行恢复。

## 构建

JDK 17，Android SDK Platform 36、Build Tools 36.0.0，Gradle 8.14.3。

```powershell
.\gradlew.bat assembleStandardRelease lintStandardRelease
```

使用自己的本地签名更新，以保留本应用首页选择和排序。将私人签名备份复制到忽略版本控制的 `local/`，文件为 `haier-signing.p12` 与 `signing.properties`。签名文件和私钥不能放进源码分发包。

活动类的命名空间仍为 `app.orchard.tv`，因此完整主活动组件为：

```text
app.orchard.tv.haier/app.orchard.tv.MainActivity
```

完成旁装验证、关闭另一桌面的守护后，再设置默认首页和启用此版守护。由 ADB 发送的配置动作为 `app.orchard.tv.haier.CONFIG`，接收组件为 `app.orchard.tv.haier/app.orchard.tv.guard.ConfigReceiver`，参数 `--ez take_home true --ez keep_adb false --ez own_screensaver false`。

兼容性仅以该海尔实机验收为准，不代表其他型号已验证。
