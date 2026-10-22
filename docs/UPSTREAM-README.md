> Historical upstream README; its commands and package names do not apply to this Haier fork.

# 简居 TV · 大陆应用版电视桌面

面向中国大陆常用视频应用的简约 Android TV 首页。基于 [pengchujin/aerial-tv](https://github.com/pengchujin/aerial-tv) 1.16 定制，保留原作者 MIT 许可。非官方分支，与小米及视频平台无隶属关系。

![电视实际运行画面](docs/home-preview.png)

## 功能

- 暗红与深蓝背景、统一玻璃卡片、小尺寸原应用图标、遥控器焦点高光。
- 固定排序：**云视听极光 → 银河奇异果 → CIBN 酷喵 → 芒果 TV → 云视听小电视 → Netflix → YouTube**。
- 只替换首页；点击卡片进入电视上已安装的原应用。不会附带、破解或自动安装这些应用。
- 上方提供信号源、网络、系统设置；下方提供投屏、硬盘、全部应用。
- 左右选择应用，上下进入工具栏，确认打开；返回回到第一张卡片，菜单键打开全部应用。
- 未安装应用显示“待安装”。没有广告位、内容推荐流或重复的选中应用大标题。

## 兼容性与限制

| 项目 | 当前状态 |
| --- | --- |
| 小米 MiTV-MFTR0，Android 14，HyperOS 3.0.106.0 | 已真机验证首页、重启接管、五款国内应用启动及系统入口 |
| 投屏、硬盘、蓝牙 | 保留原厂功能；验证了入口及硬盘识别，未完成全流程媒体播放测试 |
| 其他小米、TCL、海信及其他品牌 | 未验证本定制版；上游兼容经验不等于本版通过验证 |
| 系统版本 | 构建最低 Android 5.0；不代表所有 Android 5.0+ 固件可安装或接管首页 |
| Netflix / YouTube | 仅预留入口；需自行安装兼容版本及准备电视自身可用的网络，Netflix 另受设备认证影响 |

投屏和硬盘快捷入口目前针对小米包名实现，其他品牌可能提示不可用。首页卡片按包名匹配，不同渠道版应用可能需要修改 `HomeScreen.kt` 中的映射。电视必须允许安装应用或开启 ADB；仅连接同一个 Wi-Fi 并不足以完成改造。

## 安装

从本仓库 **Releases** 下载 `home-tv-standard.apk`，不要下载上游包替代。当前包名仍为 `app.orchard.tv`，但签名与上游不同：**不能直接覆盖上游 Aerial**。如果已安装上游版，请先保存其配置，自行决定是否卸载；卸载会删除该应用的数据。

电脑准备 Android platform-tools（`adb`），电视与电脑处于同一局域网，在电视设置中启用 ADB 并允许电脑连接。以下命令适用于 macOS/Linux 的 Bash：

```sh
# 在项目根目录执行，填写电视自己的地址和 APK 路径
APK_FILE='/absolute/path/home-tv-standard.apk' TV='<电视IP>' bash tools/install.sh
```

安装脚本授予桌面接管所需的无障碍、通知监听、悬浮窗及写入系统设置等权限；尝试设置默认桌面，受固件限制时使用守护服务接管。不会卸载系统桌面，也不会自动修改 DNS、代理、广告开关或屏保。第三方固件可能拒绝授权，不能保证每次 Home 切换都完全无闪屏。

`KEEP_ADB=1` 是可选项，会在固件关闭调试后重新开启 ADB；默认不启用。仅在自己信任的局域网中按需使用。

恢复原厂首页、保留本应用和媒体文件：

```sh
TV='<电视IP>' bash tools/restore.sh
```

恢复脚本会撤销本应用的接管权限；支持默认桌面选择的系统可能需要重新选择原厂桌面。不要停用或删除原厂桌面。建议先手动记录原默认桌面及相关授权，便于按设备情况恢复。

## 广告处理的边界

本首页没有广告。应用内的开屏、片头、暂停广告仍由视频平台控制，本项目不是全机广告拦截器。示例小米电视上另行手动关闭了应用商店/小米商城通知和匿名设备标识符，开启了观影免打扰；这些是设备设置，**不会随 APK 自动应用到别人的电视**。关闭广告标识符也不等于减少广告数量。

## 构建

需要 JDK 17、Android SDK 36，并设置 `JAVA_HOME` 与 `ANDROID_HOME`：

```sh
./gradlew assembleStandardRelease lintStandardRelease
```

输出位于 `app/build/outputs/apk/standard/release/`。公开发布版使用维护者私钥签名；私钥不会提交。自行构建默认使用本机 debug key，不能覆盖不同签名的发布版。长期维护请使用自己的签名并妥善备份。

签名配置位于忽略版本控制的 `local/signing.properties`，字段为 `storeFile`（相对于 `local/`）、`storePassword`、`keyAlias`、`keyPassword`。不要分享该文件和密钥。

上游保留的 `guard` 构建供拒绝 HOME 声明的固件研究使用，本次未提供真机验收或二进制发布。安装脚本在 TCL 限制下可能尝试 guard 构建，应先阅读脚本并准备相应构建环境。

## 来源与许可

上游基线：`pengchujin/aerial-tv` 提交 `5a91b87852f2a66139d207ec2942f2d6964726f9`。感谢原作者提供桌面接管、系统入口及 UI 基础。

代码遵循 [MIT License](LICENSE)，字体遵循 [OFL](FONT-LICENSE-OFL.txt)。应用名称、图标及商标归各自权利人所有。截图中的应用图标由电视上已安装应用提供，本仓库不分发对应商业应用 APK。
