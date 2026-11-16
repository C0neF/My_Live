# My Live Windows 2.1.1

首个 Windows x64 版本，支持 Windows 10 22H2 / Windows 11。

- 哔哩哔哩、斗鱼、虎牙、抖音的浏览、搜索、播放与实时聊天。
- 原生硬件解码、清晰度和线路切换、弹幕、全屏、小窗、置顶与托盘。
- 账号登录和 Windows 加密保存，关注、历史、分组与 Android 资料迁移。
- WebDAV、局域网及远程资料同步。
- 自适应导航、播放器控件自动隐藏、紧凑窗口布局和聊天阅读位置保留。
- 首页仅保留平台导航与直播间卡片，搜索位于侧边栏；应用图标与安卓端统一。
- 独立的 Windows 更新识别、脱敏诊断与完整许可/源码信息。

单文件版：下载 `MyLive-SingleFile-2.1.1.exe` 后直接运行，首次启动自动展开运行库。
便携版：完整解压 `MyLive-Windows-x64-2.1.1.zip`，运行 `MyLive/MyLive.exe`。
安装版：选择 MSI 或 Setup EXE，按当前用户安装；升级安装包即可更新。
从此前自行构建的“所有用户安装”迁移时，先卸载该安装，再安装当前用户版本。

以上版本均不要求预装 Java 或 .NET，共用 `%LOCALAPPDATA%/MyLive/` 资料。
更新、卸载和替换免安装程序会保留资料；备份可在资料/同步界面导出。
单文件运行库缓存位于 `%TEMP%/.net/`，退出所有相关进程后可清理对应版本缓存。

下载后可使用 PowerShell 核对 `SHA256SUMS.txt`：

```powershell
Get-FileHash .\MyLive-SingleFile-2.1.1.exe -Algorithm SHA256
```

当前文件没有 Authenticode 签名。版本、文件大小和 SHA-256 见 release-manifest.json。
My Live 使用 GPL-3.0-or-later；项目源码、原生源码和许可证随本批文件提供。
平台可提供的清晰度、登录与搜索能力取决于平台接口和账号权限。
