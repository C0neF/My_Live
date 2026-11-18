# MyLive

Windows x64 客户端，当前版本 3.0.0，使用 KMP 共享模型、JVM 共享平台协议和 CMP 界面。
目标为 Windows 10 22H2 / Windows 11 x64；实际验收环境为 Windows 11，记录见 [VERIFICATION.md](VERIFICATION.md)。

## 安装与功能

提供单文件免安装 EXE、MSI/EXE 安装包及便携 ZIP。单文件版双击即可运行，首次自动展开运行库，之后复用缓存；不需要手动解压或安装 Java/.NET。
ZIP 版完整解压后运行 `MyLive/MyLive.exe`。各版本共用原有账号、关注与历史资料。
构建输出位于 `desktop/build/compose/binaries/main/` 的 `single-file/3.0.0/`、`msi/`、`exe/`、`zip/`。
最新单文件位于 [single-file/3.0.0/MyLive/MyLive.exe](build/compose/binaries/main/single-file/3.0.0/MyLive/MyLive.exe)。窗口、托盘、侧栏和可执行文件统一使用 MyLive 名称，版本信息保留在关于页面和文件属性中。

- 四个平台推荐、分类、分类直播间、直播间/主播搜索、房间号与网页链接进入。
- mpv 原生 GPU 播放，支持 FLV、质量/线路切换、硬件解码、缩放、音量、静音、定时停止、全屏、小窗和置顶。
- 实时聊天、滚动/顶部/底部弹幕、表情图片和付费留言；支持字号、速度、透明度、区域、行数、关键词/用户屏蔽与去重。
- 关注、最近 200 条历史、平台/开播/分组筛选、搜索、置顶、多分组和备注；手动或自动刷新开播状态。
- 冷灰蓝色的浅色/深色主题、跟随系统、强调色、文字大小、平台顺序、关闭到托盘及隐藏后继续播放/暂停。
- 哔哩哔哩扫码登录、内置网页登录、Cookie 导入、登录态检查和斗鱼续期。可仅本次会话使用，也可由 Windows DPAPI 保护后保存。
- Android/Windows 资料迁移、WebDAV、局域网同步和远程同步房间。
- 播放指标、脱敏诊断导出、版本检查和许可入口。更新检查只提供本仓库发布的 Windows 文件。

工具栏按钮有悬停提示。快捷键：空格播放/暂停，M 静音，↑/↓ 调整应用音量，F11/Esc 全屏/退出，P 小窗，T 置顶。
播放器增加独立的“全区域”按钮（F10）：窗口内仅显示播放器，保留系统窗口边框和当前窗口尺寸。鼠标移到窗口右边缘时展开实时聊天，停留在聊天栏内保持显示，移出聊天栏或窗口后收起；聊天以浮层覆盖右侧，视频尺寸保持不变。再次点击全区域按钮或按 Esc 恢复正常布局；F11 和双击画面仍控制全屏。全区域同样支持本地视频，本地视频没有实时聊天浮层。

Skia 可回收图形资源的缓存预算设置为 64 MB，原生解码器、帧缓冲等占用另计。JVM 堆以 32 MB 起步，上限 512 MB；播放器在首次播放时创建，首页修改播放偏好不会提前初始化原生播放器。直播前向缓存上限 32 MB，预读 4 秒，不保留已播放数据回看缓存。固定位置弹幕按到期时间更新，滚动弹幕继续共享显示器帧时钟。运行库包含按需开启的 JFR 性能诊断模块；实际直播 CPU、总内存及 GPU 占用仍取决于视频规格和设备。
平台账号与设置弹窗分别保持固定宽度，统一采用 660 dp 高度并按主窗口空间缩小；切换平台、二维码/手动登录和设置分类时外框保持稳定。标题、标签和底部操作固定，长内容在内部滚动，滚动条位置预留。小窗中打开设置会暂时恢复普通窗口，关闭后返回小窗。
Ctrl+L 打开房间号/链接入口，处于全屏或小窗时先返回普通窗口；直播间、搜索和视频链接输入均支持回车提交。
启动时附加 `--background` 会保持窗口隐藏，并可从托盘恢复。后台启动是否继续播放沿用播放设置。
侧边栏“搜索”提供直播间/主播搜索及房间号/链接入口。房间入口可选择数字房间号所属平台，输入错误时保留弹窗。
搜索与首页使用独立状态，返回时保留搜索词、结果与滚动位置。
平台、分类和搜索结果在当前会话内保留最近 12 个列表，切回时恢复已加载的页和位置；刷新按钮、F5 或 Ctrl+R 获取新内容。
接近列表末尾时自动加载下一页，仍可手动加载；搜索类型切换会重新查询，输入框支持一键清除。
文字输入或弹出设置时不触发播放快捷键。隐藏时自动暂停的内容会在恢复窗口后继续；用户主动暂停的内容保持暂停。
手机旋转、触摸亮度、Android 系统 PiP 等交互由桌面窗口、音量、全屏、小窗和托盘操作承接。

窗口采用左侧导航，首页提供标题、平台切换和直播间卡片。常见桌面窗宽展开导航名称，较窄窗口保留图标、文字及数量标记，次要工具收进“更多工具”。侧栏顶部提供“打开直播间”入口，仍可使用 Ctrl+L。
中英文界面使用 Segoe UI 与微软雅黑等系统字体，正文、辅助信息和按钮分别使用统一字号与行高；选中、悬停及键盘焦点有明确反馈。
“设置 → 外观 → 文字大小”提供紧凑 90%、标准 100%、舒适 110% 和大号 120%，即时生效并随偏好、资料备份保存。强调色预设分别适配浅色和深色主题，外观页提供文字预览。
Windows 窗口、托盘、侧边栏、快捷方式、安装包和单文件 EXE 共用安卓现有的图标原图；ICO 包含 16–256 像素的七个尺寸。
Windows x64 界面默认使用 GPU OpenGL 兼容渲染，以处理 Direct3D 按需呈现与 VRR 场景中的鼠标卡顿。该策略不区分显卡品牌；AMD、Intel 与 NVIDIA 均使用各自驱动的 OpenGL 实现。Windows OpenGL 播放器使用 `d3d11va-copy,auto-safe` 保留硬件解码；这条兼容路径每帧需要回读和上传，高分辨率、高帧率视频的开销会高于 Direct3D 共享纹理路径。开发者仍可通过 `SKIKO_RENDER_API` 或 JVM 参数 `-Dskiko.renderApi` 显式选择后端，环境变量优先。

卡片内容缓存、悬停绘制和鼠标事件优化作用于虎牙、斗鱼、抖音、哔哩哔哩的共用界面。原生 GPU 检查目前仅在 RTX 5060 完成；AMD、Intel 的实机表现尚未验证。其他系统和架构保留 Skiko 平台默认方式（macOS 为 Metal、Linux 为 OpenGL），Windows 专用的解码方式也仅用于 Windows OpenGL。当前发布包仍只支持 Windows x64；macOS、Linux、Windows ARM64 尚未打包验证。
列表带滚动条、封面缺失占位、加载骨架、错误重试和可清除筛选的空状态。搜索和过滤工具随窗口宽度换行。
播放器闲置 3 秒隐藏控件，移动鼠标后恢复；暂停、缓冲、打开菜单或查看播放信息时保持可见。双击画面可切换全屏。
小窗口将次要播放操作收进“更多”菜单，聊天按需以侧边浮层展开；宽窗口使用并排聊天，本地视频使用完整播放宽度。
小窗的“更多”菜单也支持清晰度、弹幕和播放信息，无需恢复普通窗口。
聊天支持复制文本，上翻阅读时暂停跟随新消息，可点击“回到最新”恢复位置。
宽窗口中可拖动视频与聊天之间的分隔线调整聊天栏宽度，双击恢复默认；聚焦分隔线后也可用左右方向键调整。窄窗口的聊天浮层支持 Esc 关闭。

滚动弹幕支持同轨连续显示，前一条尾部让出间距后即可接续；行数设置限制实际轨道数量，总可见消息上限为 120 条。
同轨长短消息等速移动，长文本从右侧完整进入。播放信息仅在展开查看或显式开启诊断日志时持续采样。
换画质和线路时保留主动暂停状态，加载期间的新暂停或继续操作也会生效。滚动弹幕共用一个帧时钟，位置更新留在绘制层。

窗口先显示加载界面，播放运行库、账号和资料并行初始化；直接打开直播或本地视频时，首页列表在返回浏览后再加载。
封面和表情复用网络连接，图片内存缓存上限 32 MB、磁盘缓存上限 256 MB，磁盘缓存位于资料目录下的 `cache/images/`。

## 数据与同步

资料默认保存在 `%LOCALAPPDATA%/MyLive/library.json`，升级程序不会覆盖。后台写入使用文件锁与原子替换，多实例读取磁盘最新状态后更新。
损坏或不兼容的文件会保留，并提示重试。开发可用 `-Dmylive.dataDir=绝对目录` 隔离资料。

导入支持 Android v1–v3 配置包、旧关注数组和 Windows 导出包。完整备份包含关注、历史、置顶、分组、备注、屏蔽及支持的偏好；房间合并去重，设置恢复可在预览时选择。JSON 上限 5 MB，导出不覆盖同名文件。
账号、WebDAV 密码和同步服务器配置单独保存，不进入资料备份。完整导出契约由桌面测试和 Android 真实 Room 恢复测试共同验证。

WebDAV 使用 HTTPS，可填写目录或具体 JSON 地址；与 Android 共用 `mylive_profile_backup.json`，支持坚果云目录规则和创建缺失的父目录。下载先预览再确认恢复，上传前确认更新远端备份。

局域网接收手动开启并选择本机地址，每次生成新配对码。支持 Android 扫码或填写地址/配对码，沿用 MLSYNC2、AES-GCM、请求签名和防重放，支持配置包及关注/分组/历史/屏蔽词分项同步。停止接收或退出后关闭监听。
远程房间支持创建、加入，手动发送关注、分组、历史和屏蔽词；关闭同步窗口会断开房间。各平台决定登录权限，例如抖音搜索需要完整登录态。

## 开发、验证与打包

源码构建需要 JDK 21 和项目 Android SDK。Android Studio JBR 可能没有 `jpackage`，打包可指定完整 JDK：
构建单文件版另需 .NET 10 SDK（最低 10.0.202；仅开发机器需要）。

```powershell
.\gradlew.bat :desktop:verifyWindows
.\gradlew.bat :desktop:run
.\gradlew.bat :desktop:run --args="--room=douyu:9999"
.\gradlew.bat :desktop:run --args="--video=D:/Videos/sample.mp4"
.\gradlew.bat :desktop:packageWindows -PdesktopJdk=C:/Path/To/jdk-21
.\gradlew.bat :desktop:packageSingleFile -PdesktopJdk=C:/Path/To/jdk-21
.\gradlew.bat :desktop:singleFileTest -PdesktopJdk=C:/Path/To/jdk-21
```

`verifyWindows` 包含共享模型/协议、桌面单元测试、Compose 界面流程和原生媒体/网页登录检查。
`packageWindows` 生成统一图标的 ZIP、单文件 EXE、MSI、安装 EXE，包含运行库、依赖目录、许可和源码入口。
单文件版复用同一应用镜像，额外携带启动器运行库；缓存默认位于 `%TEMP%/.net/`，用户资料仍在 `%LOCALAPPDATA%/MyLive/`。
`singleFileTest` 用独立缓存和资料目录检查首次启动、缓存重启、并发启动、中文/空格路径、相对视频路径以及无需已安装 Java/.NET 的运行。
检查结束后自动删除该次复制的 EXE 和独立解压缓存（失败时也清理），保留日志、自检 JSON 和资料样本。
需要保留解压内容排查时，可直接调用 `desktop/launcher/Verify-SingleFile.ps1` 并传入 `-KeepRuntimeArtifacts`。
首次准备媒体库下载固定 SHA-256 的 FFmpeg LGPL 共享构建（约 81 MB），之后使用缓存。
GitHub **Windows desktop** workflow 已配置相同测试、四类打包、安装升级/卸载、源码包和校验清单；提交/触发后才会在远端运行。
Windows 版本由 `version.properties` 统一控制。完整候选版制作和维护者发布流程见 [RELEASING.md](RELEASING.md)。

联网检查独立执行，避免停播和第三方网络波动影响普通测试：

```powershell
.\gradlew.bat :desktop:liveProbe -PdesktopCatalogLive=true
.\gradlew.bat :desktop:liveProbe -PdesktopLiveRoom=huya:998
.\gradlew.bat :desktop:liveProbe -PdesktopLiveRoom=douyu:auto
```

打包后的程序可显式自检，写入仅限给定 QA 目录：

```powershell
MyLive.exe --self-check=D:/QA/runtime --self-check-media=D:/QA/sample.flv
MyLive.exe --window-smoke=D:/QA/window --video=D:/QA/sample.mp4
```

前者检查资料、DPAPI、加密同步、签名、WebKit 和解码并写入 `self-check.json`。
`--self-check` 不创建主窗口；需要验证正常程序的隐藏初始化，可组合 `--background --window-smoke=QA目录`，该组合只记录后台启动状态。
后者短暂显示测试窗口，验证全屏/恢复、小窗/恢复、置顶、托盘和暂停策略后退出；使用隔离资料及静音，生成 `window-smoke.json`。
Compose 流程直接使用测试接口，不需要操作系统键鼠自动化；图像在 `desktop/build/reports/ui-evidence/`。

## 模块与运行库

| 模块 | 职责 |
| --- | --- |
| shared-model | KMP commonMain 直播模型，Android/JVM 目标 |
| live-core | 共用平台 API、签名、弹幕、同步协议和远程同步网络实现 |
| shared-ui | Android 端主题颜色与平台选项卡组件 |
| app | Android、Media3、QuickJS、Room、Hilt、系统服务 |
| desktop | Windows、mpv、GraalJS、DPAPI、资料存储、同步和生命周期 |

固定 Kotlin 2.4.21、Compose 插件 1.10.3、MediaMP 0.3.2，兼容项目 AGP 9.2.1。脚本从 Android assets 打包，保持单一来源。
斗鱼弹幕单独使用 Conscrypt，并保留证书和域名校验。MediaMP 原精简 FFmpeg 不含 FLV，故保留其 mpv/JNI GPU 桥接，补入固定版本和校验值的完整兼容 FFmpeg DLL。
播放信息中的源帧率和 mpv 丢帧计数不等于显示器整条合成链路的实际呈现帧率。

JVM 依赖目录和上游 JAR 许可在 `app/resources/notices/`，原生媒体许可、精确源码修订和构建入口在 `app/resources/mpv/notices/`。
当前 mpv 二进制启用 GPL；My Live 项目使用 GPL-3.0-or-later，见根目录 LICENSE/NOTICE。
第三方组件保留各自许可。Windows 候选版包含项目源码、原生源码及其下载/构建索引。
