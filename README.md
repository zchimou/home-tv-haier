# 简居 TV · 海尔适配版

面向海尔 65H5D 的简居 TV 社区适配分支，基于 [Jimu888/home-tv-cn v1.16-home.1](https://github.com/Jimu888/home-tv-cn/releases/tag/v1.16-home.1)；其上游为 [pengchujin/aerial-tv](https://github.com/pengchujin/aerial-tv)。保留原项目 MIT 和字体 OFL 许可，与海尔官方无隶属关系。

**[下载 APK](https://github.com/zchimou/home-tv-haier/releases/latest)** · [改动说明](HAIER.md) · [MIT 许可](LICENSE)

## 功能

- 独立包名 `app.orchard.tv.haier`，可与官方简居 TV 并存。
- 选择首页显示哪些已安装应用，用“前移 / 后移”调整顺序，立即保存；移除图标不会卸载应用。
- “背景”支持内置壁纸、每日壁纸及同一局域网的手机扫码上传照片；选择在重启后保留。“原始背景”恢复初始配色。
- 投屏、硬盘、网络和全部设置连接本机原厂组件；设置通过公开导出的 `SettingsService` 打开完整面板。
- 保留原厂桌面、应用数据和原厂屏保；不启用强制 ADB 保活。
- 普通 LAUNCHER 和电视 LEANBACK_LAUNCHER 入口均保留，便于从原厂应用列表重新打开。
- 首页守护必须显式启用。固件清理应用后，可在另行授权通知使用权的情况下自动恢复守护。

## 已验证范围

| 项目 | 状态 |
| --- | --- |
| 设备 | 海尔 65H5D，内部型号 LRH670_2G_ALI7H_B |
| 系统 | Android 12 / API 31，32 位 ARM；该实机固件 HuanglongV100R002C01SPC004B010 |
| 遥控器、视频应用启动、Home、重启进入首页 | 实机通过，Home 经实体遥控器确认 |
| 首页应用选择、排序、覆盖更新保留配置 | 实机通过 |
| 设置、网络、原厂媒体管理入口 | 实机通过；硬盘与蓝牙由测试者确认正常 |
| 壁纸选择、局域网上传、重启保存 | 实机通过 |
| 原厂 Miracast | 入口已打开；未完成本分支的投屏播放验收 |
| 被强制停止后的恢复 | 另行授权通知恢复通道后，测试中约 6 秒恢复；不保证其他固件相同 |
| 其他电视或系统 | 未验证，请先旁装，不要直接接管首页 |

最低构建版本为 Android 5.0；这不代表所有 Android 5.0+ 固件均兼容。原厂入口按本机组件实现，不适用于所有海尔型号。

## 先旁装试用

准备 Google Android platform-tools，在电视启用 ADB，电脑与电视连同一路由器，并在电视允许电脑调试。以下为 PowerShell 示例，用电视自己的地址替换 `TV_IP`：

```powershell
$tvAddress = 'TV_IP:5555'
adb connect $tvAddress
adb -s $tvAddress install -r '.\home-tv-haier-v1.16-home.1-haier.3.apk'
adb -s $tvAddress shell am start -W -n app.orchard.tv.haier/app.orchard.tv.MainActivity
```

旁装默认不主动接管首页。先验证遥控器、已安装应用和原厂功能。已有海尔版请使用同一签名覆盖安装；不要为了更新先卸载或清除数据。

## 设为首页前先记录恢复目标

记录原默认首页以及原有无障碍、通知监听列表，保存到本地；不要把家庭设备信息上传到公开仓库。

```powershell
adb -s $tvAddress shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s $tvAddress shell settings get secure enabled_accessibility_services
adb -s $tvAddress shell settings get secure enabled_notification_listeners
```

本机原厂首页为 `com.youku.taitan.tv/com.youku.tv.home.activity.HomeActivity`。切换前请核实目标组件仍安装。其他桌面若有按键重映射或首页守护，应先在其自身设置里关闭冲突功能，保持原厂桌面启用。

## 明确接管首页

以下操作授予 WRITE_SECURE_SETTINGS（写系统安全设置）和 SYSTEM_ALERT_WINDOW（悬浮窗），并开启无障碍首页守护。只有在你决定接管首页时执行；守护处理窗口变化与 Home，不读取屏幕内容。

```powershell
adb -s $tvAddress shell pm grant app.orchard.tv.haier android.permission.WRITE_SECURE_SETTINGS
adb -s $tvAddress shell appops set app.orchard.tv.haier SYSTEM_ALERT_WINDOW allow
adb -s $tvAddress shell cmd package set-home-activity --user 0 app.orchard.tv.haier/app.orchard.tv.MainActivity
adb -s $tvAddress shell am broadcast -a app.orchard.tv.haier.CONFIG -n app.orchard.tv.haier/app.orchard.tv.guard.ConfigReceiver --ez take_home true --ez keep_adb false --ez own_screensaver false
adb -s $tvAddress shell am start -W -n app.orchard.tv.haier/app.orchard.tv.MainActivity
```

海尔固件可能绕过默认 HOME，或清理应用并关闭其无障碍服务。仅设置默认首页不能覆盖这些行为。

### 可选：被停止后自动恢复

这是一项独立的**通知使用权**授权：系统允许该服务接收通知，但当前实现只使用 `onListenerConnected` 恢复已授权的首页守护，不读取、保存或修改通知内容。是否授权由使用者决定。

```powershell
adb -s $tvAddress shell cmd notification allow_listener app.orchard.tv.haier/app.orchard.tv.guard.RevivalListener 0
```

只撤销这项授权：

```powershell
adb -s $tvAddress shell cmd notification disallow_listener app.orchard.tv.haier/app.orchard.tv.guard.RevivalListener 0
```

不开启该恢复通道时，应用被强制停止后可能需要从原厂应用列表手动打开。安装不会自动授予这项通知使用权。

## 恢复原厂或之前的首页

`tools/Restore-Home.ps1` 仅支持已验证的内部型号，不卸载、停用或清除任何应用的数据；撤销海尔版首页守护和通知恢复授权，保留其他服务。先只读检查：

```powershell
.\tools\Restore-Home.ps1 -TV $tvAddress -CheckOnly
.\tools\Restore-Home.ps1 -TV $tvAddress
```

恢复之前记录的其他桌面时，添加 `-HomeComponent '之前记录的完整首页组件'`；该桌面必须仍已安装。恢复到其他桌面后，其私有自动启动、按键映射选项需自行确认。

## 构建与签名

使用 JDK 17、Android SDK Platform 36、Build Tools 36.0.0、Gradle 8.14.3：

```powershell
.\gradlew.bat assembleStandardRelease lintStandardRelease
```

输出：`app/build/outputs/apk/standard/release/app-standard-release.apk`。该版本构建和 lint 已通过，报告为 0 错误、62 警告；APK 发布签名已校验。

发布签名证书 SHA256：

```text
78d6d8198cd8aa48042e0f8c103064abbf6dc6349e19e9635140851df848acb9
```

发布私钥不会上传。自行构建默认使用本机 debug key，不能覆盖不同签名的已安装版本。维护自己的发行版时，把签名配置放在忽略版本控制的 `local/`，参见 `app/build.gradle.kts`，并自行妥善备份。

## 来源与许可

- 本分支基线：[Jimu888/home-tv-cn v1.16-home.1](https://github.com/Jimu888/home-tv-cn/releases/tag/v1.16-home.1)。
- 更早上游：[pengchujin/aerial-tv](https://github.com/pengchujin/aerial-tv)，上游 README 记录的基线提交为 `5a91b87852f2a66139d207ec2942f2d6964726f9`。
- 原项目代码：[MIT](LICENSE)；字体：[OFL](FONT-LICENSE-OFL.txt)。保留原作者版权声明。
- 应用名称、图标和商标属于各自权利人。仓库及 Release 不分发视频平台或其他商业应用 APK。
- 公开发布不包含签名私钥、家庭网络地址、ADB 授权文件、电视状态快照或个人配置。