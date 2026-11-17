# Windows 发布候选版

Windows 版本独立于 Android，由 `desktop/version.properties` 控制。稳定版标签使用
`windows-vX.Y.Z`。程序的更新检查只选择带 Windows 文件的正式版本，忽略草稿和预发布。

本地准备、验证和生成发布材料不会创建 Git 标签、合并分支、推送或发布 Release。
这些操作由维护者决定。Windows desktop workflow 只有读取仓库和上传构建产物的权限。

## 构建与验收

需要 Windows x64、完整 JDK 21、Android SDK、.NET SDK（见 launcher/global.json）及 PowerShell 7。
CI 使用 Temurin 21（`java-version: '21'`）。`setup-java` 不接受 `21.0.12.1+1` 这种补丁钉死。本地可通过 `-PdesktopJdk=绝对路径` 指定 JDK 21。

```powershell
.\gradlew.bat :desktop:verifyWindows :desktop:packageWindows :desktop:singleFileTest -PdesktopJdk=C:/Jdk21
.\desktop\release\Test-PackagePaths.ps1 -ImageDirectory desktop/build/compose/binaries/main/app/MyLive
.\desktop\release\Start-InstallerSandbox.ps1 `
  -Msi desktop/build/compose/binaries/main/msi/MyLive-3.0.0.msi `
  -SetupExe desktop/build/compose/binaries/main/exe/MyLive-3.0.0.exe `
  -Media desktop/src/test/resources/sample.flv `
  -AppJar desktop/build/libs/desktop.jar `
  -ReportDirectory build/installer-acceptance -ExpectedVersion 3.0.0
```

Sandbox 启动器立即返回结果目录。在该目录查看 `installer-verification.json`，只有
`passed: true` 才表示完成。测试在断网的独立 Windows 环境执行，输入只读，结果目录可写。
安装有 `wsb` CLI 时使用显式创建和连接，返回 sandboxId；验收后可用 `wsb stop --id 返回的ID` 释放该测试环境。
没有启用 Sandbox 的机器可在一次性 CI runner 运行 `Verify-Installer.ps1`；脚本拒绝在普通宿主机运行。

验收包含当前用户安装、中文/空格/符号路径、安装后原生解码/WebKit/DPAPI/同步、升级替换旧产品、
拒绝降级、卸载和保留资料。升级基线是同一载荷的 MSI 副本，降低安装器版本并生成新产品标识；
它证明 Windows Installer 的升级行为。应用资料格式迁移另由 Android/Windows 兼容性测试覆盖。

`Test-PackagePaths.ps1` 为安装路径预留 139 个字符。JVM 许可使用紧凑文件名，
`app/resources/notices/LICENSE-INDEX.txt` 保留完整的原始 JAR 和许可路径映射。
不要恢复过深的许可文件目录：MSI 文件操作仍会触及 Windows 路径长度限制。

`desktop/installer/main.wxs` 使用 Windows Installer 标准的三段版本比较和降级保护。
固定 JDK 的自定义升级辅助函数存在临时字符串生命周期问题，实测会清掉正确的旧版本检测结果；
安装模板移除了该辅助调用。`packageMsi` 使用显式 jpackage 任务，`packageExe` 用自包含启动器包装该 MSI，
共用应用镜像、图标、升级 UUID 和模板；它们仍是可用的打包入口。详情见 [installer/README.md](installer/README.md)。

Android 回归命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleQa :app:compileDebugAndroidTestKotlin
```

## 源码与交付目录

My Live 使用 GPL-3.0-or-later，第三方组件保留各自许可。根目录 LICENSE/NOTICE 随包保留。
`desktop/native-notices/sources.lock.json` 固定原生源码下载及校验值，并记录实际 DLL 的 SHA-256。
MSYS2 源码包包含 PKGBUILD、补丁和上游源文件；FFmpeg/MediaMP 源码及构建脚本也一并保存。
FFmpeg 静态依赖的仓库和修订列在源码索引中，下载与构建由随附的原版构建脚本控制。
JVM 依赖目录、许可、JDK 和 .NET 运行库许可均保留在应用镜像内。

```powershell
.\desktop\release\Collect-NativeSources.ps1 -CacheDirectory build/native-sources -OutputFile build/MyLive-Native-Sources-3.0.0.zip
.\desktop\release\New-ReleaseBundle.ps1 -OutputDirectory build/windows-candidate `
  -InstallerReceipt build/installer-acceptance/本次运行/results/installer-verification.json `
  -NativeSources build/MyLive-Native-Sources-3.0.0.zip
```

交付脚本拒绝混用已有输出目录，校验验收记录对应的 EXE、MSI 和应用 JAR，随后生成源码快照、
文件清单与 SHA256SUMS.txt。源码快照包含已跟踪及未忽略的项目文件、每个文件的校验值和基础提交；
排除构建缓存、本机配置、账号资料和签名密钥。未提交源码会明确标记，不冒充已经存在的 Git 提交。

一次完整交付包含单文件 EXE、便携 ZIP、MSI、安装 EXE、项目源码、原生源码、许可、发布说明、
验收收据、release-manifest.json 和 SHA256SUMS.txt。保留这些文件作为同一批次。

## 维护者决定发布时

1. 审查当前分支变更和候选版验收记录，按自己的流程提交、合并并推送。
2. 在确定的提交运行 Windows desktop workflow，确认所有步骤通过，下载该次完整产物。
3. 在同一提交创建匹配版本文件的 `windows-vX.Y.Z` 标签，创建 Release 草稿，上传完整产物。
4. 核对文件、SHA-256、版本及源码可访问性后再发布。Windows Release 设为不替代 Android 的 Latest。

本项目不会自动下载或执行更新。关于页面提供本仓库 Windows 文件及 SHA-256 的入口。
Windows 标签不会触发 Build release APK，Android 版本仍使用自己的 `vX.Y.Z` 标签。
当前构建没有 Authenticode 签名，清单如实记录签名状态；如加入签名，须对签名后的最终文件重新验收并生成校验值。

Windows 11 x64 为本地实际验收环境；Windows 10 22H2 x64 是支持基线，兼容性需在该系统上单独记录，不能用当前环境的结果代替。
