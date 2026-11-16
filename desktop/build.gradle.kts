import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.util.Properties
import java.nio.file.Files

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

val windowsVersion = Properties().apply { file("version.properties").inputStream().use(::load) }
    .getProperty("version").also { value ->
        require(value != null && Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)").matches(value)) {
            "desktop/version.properties must contain a stable X.Y.Z version"
        }
        val parts = value.split('.').map { it.toIntOrNull() ?: Int.MAX_VALUE }
        require(parts[0] <= 255 && parts[1] <= 255 && parts[2] <= 65535) { "Windows MSI version is out of range: $value" }
    }
version = windowsVersion
tasks.jar { archiveFileName.set("desktop.jar") }

abstract class GenerateDesktopBuildInfo : DefaultTask() {
    @get:Input abstract val versionName: Property<String>
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @TaskAction fun generate() {
        outputDirectory.get().file("com/mylive/desktop/DesktopBuildInfo.kt").asFile.apply {
            parentFile.mkdirs()
            writeText("package com.mylive.desktop\ninternal object DesktopBuildInfo { const val VERSION = \"${versionName.get()}\" }\n")
        }
    }
}
val generateDesktopBuildInfo by tasks.registering(GenerateDesktopBuildInfo::class) {
    versionName.set(windowsVersion)
    outputDirectory.set(layout.buildDirectory.dir("generated/sources/desktopBuildInfo/main"))
}
kotlin.sourceSets.main { kotlin.srcDir(generateDesktopBuildInfo) }

kotlin {
    jvmToolchain(21)
}

val windowsMpvRuntime by configurations.creating { isTransitive = false }

dependencies {
    implementation(project(":live-core"))
    implementation(project(":shared-ui"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.okhttp)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:${libs.versions.coroutines.get()}")
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jvm.json)
    implementation("com.squareup.okhttp3:okhttp-brotli:${libs.versions.okhttp.get()}")
    implementation(libs.conscrypt.openjdk)
    implementation(libs.jna.platform)
    implementation(libs.zxing.core)
    listOf("base", "graphics", "controls", "media", "web", "swing").forEach { module ->
        implementation("org.openjfx:javafx-$module:${libs.versions.javafx.get()}:win") { isTransitive = false }
    }
    implementation("org.openani.mediamp:mediamp-mpv:${libs.versions.mediamp.get()}") {
        // Upstream publishes its Compose test dependencies in the runtime graph.
        // Keep those on our test classpath through the explicit test dependency above.
        exclude(group = "org.jetbrains.compose.ui", module = "ui-test-junit4")
        exclude(group = "org.jetbrains.compose.ui", module = "ui-test-junit4-desktop")
        exclude(group = "org.jetbrains.compose.ui", module = "ui-test")
        exclude(group = "org.jetbrains.compose.ui", module = "ui-test-desktop")
    }
    windowsMpvRuntime("org.openani.mediamp:mediamp-mpv-runtime-windows-x64:${libs.versions.mediamp.get()}")
    implementation("org.graalvm.polyglot:polyglot:${libs.versions.graalJs.get()}")
    runtimeOnly("org.graalvm.polyglot:js-community:${libs.versions.graalJs.get()}")
    testImplementation(libs.junit)
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation("com.squareup.okhttp3:okhttp-tls:${libs.versions.okhttp.get()}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${libs.versions.coroutines.get()}")
}

tasks.processResources {
    from("../app/src/main/assets/scripts") { into("scripts") }
    from("../app/src/main/res/drawable-nodpi/ic_launcher_foreground_art.png") {
        into("icons"); rename { "mylive.png" }
    }
}

tasks.test {
    exclude("**/NativePlaybackSmokeTest*")
    exclude("**/NativeWebRuntimeTest*")
    exclude("**/NativeDesktopRenderingTest*")
    exclude("**/DesktopUiTest*")
    providers.gradleProperty("desktopLiveRoom").orNull?.let { systemProperty("mylive.liveRoom", it) }
    providers.gradleProperty("desktopCatalogLive").orNull?.let { systemProperty("mylive.catalogLive", it) }
}

tasks.register<Test>("uiTest") {
    description = "Exercise desktop Compose flows without OS input automation"
    group = "verification"
    dependsOn(tasks.testClasses)
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/DesktopUiTest*")
    systemProperty("java.awt.headless", "true")
    systemProperty("skiko.renderApi", "SOFTWARE")
    systemProperty("mylive.uiEvidenceDir", layout.buildDirectory.dir("reports/ui-evidence").get().asFile.absolutePath)
}

tasks.register<Test>("liveProbe") {
    group = "verification"
    description = "Opt-in real platform media/chat and catalog probes"
    dependsOn(tasks.testClasses)
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/LiveStreamIntegrationTest*", "**/LiveCatalogIntegrationTest*")
    providers.gradleProperty("desktopLiveRoom").orNull?.let { systemProperty("mylive.liveRoom", it) }
    providers.gradleProperty("desktopCatalogLive").orNull?.let { systemProperty("mylive.catalogLive", it) }
}

val windowsIcon = layout.buildDirectory.file("generated/windowsIcon/mylive.ico")
tasks.register<JavaExec>("generateAndroidProfileFixture") {
    description = "Explicitly regenerate the versioned Android/Windows backup contract fixture"
    dependsOn(tasks.compileTestKotlin)
    mainClass.set("com.mylive.desktop.DesktopProfileContractFixture")
    classpath = sourceSets.test.get().runtimeClasspath
    args(file("src/test/resources/android-compatible-full-profile.json").absolutePath)
}
val generateWindowsIcon by tasks.registering(JavaExec::class) {
    dependsOn(tasks.classes)
    mainClass.set("com.mylive.desktop.DesktopIcon")
    classpath = sourceSets.main.get().runtimeClasspath
    inputs.file("../app/src/main/res/drawable-nodpi/ic_launcher_foreground_art.png")
    outputs.file(windowsIcon)
    args(file("../app/src/main/res/drawable-nodpi/ic_launcher_foreground_art.png").absolutePath, windowsIcon.get().asFile.absolutePath)
}

/** MediaMP's reduced FFmpeg build omits FLV. Keep its mpv/JNI GPU bridge and supply full ABI-compatible FFmpeg DLLs. */
abstract class PrepareWindowsMediaRuntime : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val upstreamRuntimeJar: RegularFileProperty
    @get:Input abstract val ffmpegUrl: Property<String>
    @get:Input abstract val ffmpegSha256: Property<String>
    @get:LocalState abstract val downloadDirectory: DirectoryProperty
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceNotices: DirectoryProperty
    @get:OutputDirectory abstract val resourcesDirectory: DirectoryProperty

    @TaskAction fun prepare() {
        val download = downloadDirectory.file("ffmpeg-lgpl-shared.zip").get().asFile
        download.parentFile.mkdirs()
        fun digest(file: File): String {
            val hash = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) { val size = input.read(buffer); if (size < 0) break; hash.update(buffer, 0, size) }
            }
            return hash.digest().joinToString("") { "%02x".format(it) }
        }
        if (!download.isFile || digest(download) != ffmpegSha256.get()) {
            logger.lifecycle("Downloading pinned FFmpeg LGPL runtime")
            val connection = URI(ffmpegUrl.get()).toURL().openConnection().apply {
                connectTimeout = 30_000; readTimeout = 120_000
            }
            connection.getInputStream().use { input -> download.outputStream().use { input.copyTo(it) } }
        }
        check(digest(download) == ffmpegSha256.get()) { "FFmpeg archive SHA-256 mismatch" }
        val root = resourcesDirectory.dir("windows/mpv").get().asFile.apply { mkdirs() }
        sourceNotices.get().asFile.walkTopDown().filter { it.isFile }.forEach { source ->
            val target = File(root, "notices/" + source.relativeTo(sourceNotices.get().asFile).path)
            target.parentFile.mkdirs()
            source.copyTo(target, overwrite = true)
        }
        ZipFile(upstreamRuntimeJar.get().asFile).use { zip ->
            zip.entries().asSequence().filter { !it.isDirectory && !it.name.contains('/') }.forEach { entry ->
                zip.getInputStream(entry).use { input -> File(root, entry.name).outputStream().use { input.copyTo(it) } }
            }
        }
        val replacements = mutableSetOf<String>()
        ZipFile(download).use { zip ->
            zip.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
                val relative = entry.name.substringAfter('/')
                val target = when {
                    relative.startsWith("bin/") && relative.endsWith(".dll") -> {
                        val name = relative.substringAfterLast('/')
                        replacements.add(name)
                        File(root, name)
                    }
                    relative.startsWith("doc/") || relative.startsWith("LICENSE") -> {
                        val name = relative.removePrefix("doc/")
                        require(name.split('/').none { it == ".." })
                        File(root, "notices/ffmpeg/$name")
                    }
                    else -> null
                }
                target?.let { file ->
                    file.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input -> file.outputStream().use { input.copyTo(it) } }
                }
            }
        }
        check(replacements.containsAll(listOf("avcodec-62.dll", "avformat-62.dll", "avutil-60.dll", "swresample-6.dll", "swscale-9.dll"))) {
            "Unexpected FFmpeg ABI: $replacements"
        }
        File(root, "notices/ffmpeg/SOURCE.txt").writeText(
            "FFmpeg LGPL shared build from BtbN/FFmpeg-Builds\n${ffmpegUrl.get()}\nSHA-256: ${ffmpegSha256.get()}\n" +
                "Source and build recipes: https://github.com/BtbN/FFmpeg-Builds/tree/autobuild-2026-09-30-13-08\n" +
                "FFmpeg source: https://github.com/FFmpeg/FFmpeg/tree/29e619e767\n"
        )
    }
}

val prepareWindowsMediaRuntime by tasks.registering(PrepareWindowsMediaRuntime::class) {
    upstreamRuntimeJar.set(layout.file(windowsMpvRuntime.elements.map { it.single().asFile }))
    ffmpegUrl.set("https://github.com/BtbN/FFmpeg-Builds/releases/download/autobuild-2026-09-30-13-08/ffmpeg-n8.1.3-9-g29e619e767-win64-lgpl-shared-8.1.zip")
    ffmpegSha256.set("3e47bda1607740550141e37c0e49d1e5182b34699f15adfd137ee266d346811a")
    downloadDirectory.set(layout.buildDirectory.dir("downloads/ffmpeg-8.1.3"))
    sourceNotices.set(layout.projectDirectory.dir("native-notices"))
    resourcesDirectory.set(layout.buildDirectory.dir("generated/mediaResources"))
}

val windowsVideoBridge = layout.buildDirectory.file("native/video-bridge/mediampv.dll")
val buildWindowsVideoBridge by tasks.registering(Exec::class) {
    group = "build"
    description = "Build the OpenGL GPU video transport against the bundled mpv ABI"
    dependsOn(prepareWindowsMediaRuntime)
    inputs.dir(layout.projectDirectory.dir("native"))
    inputs.file(prepareWindowsMediaRuntime.flatMap { it.resourcesDirectory.file("windows/mpv/libmpv-2.dll") })
    outputs.file(windowsVideoBridge)
    val nativeJdk = providers.gradleProperty("desktopJdk").orElse(providers.environmentVariable("JAVA_HOME"))
        .getOrElse(System.getProperty("java.home"))
    commandLine("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File",
        file("native/Build-WindowsVideoBridge.ps1").absolutePath,
        "-RuntimeDirectory", layout.buildDirectory.dir("generated/mediaResources/windows/mpv").get().asFile.absolutePath,
        "-OutputDirectory", layout.buildDirectory.dir("native/video-bridge").get().asFile.absolutePath,
        "-JavaHome", nativeJdk)
}
val windowsVideoResources = layout.buildDirectory.dir("generated/videoResources")
val prepareWindowsVideoRuntime by tasks.registering(Sync::class) {
    dependsOn(buildWindowsVideoBridge)
    from(prepareWindowsMediaRuntime.flatMap { it.resourcesDirectory }) { exclude("windows/mpv/mediampv.dll") }
    from(windowsVideoBridge) { into("windows/mpv") }
    from("native/README.md") { into("windows/mpv/notices/mylive-video") }
    from("native/mediamp/LICENSE") { into("windows/mpv/notices/mylive-video") }
    into(windowsVideoResources)
}

tasks.register<Test>("nativeSmokeTest") {
    description = "Exercise actual Windows media and embedded web native runtimes"
    group = "verification"
    dependsOn(prepareWindowsVideoRuntime, tasks.testClasses)
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/NativePlaybackSmokeTest*")
    include("**/NativeWebRuntimeTest*")
    include("**/NativeDesktopRenderingTest*")
    systemProperty("skiko.renderApi", "OPENGL")
    systemProperty("skiko.gpu.resourceCacheLimit", 64L * 1024 * 1024)
    systemProperty("mylive.nativeRenderingReport", layout.buildDirectory.dir("reports/native-rendering").get().asFile.absolutePath)
    systemProperty("prism.order", "sw")
    systemProperty("java.awt.headless", "false")
    systemProperty("mylive.nativeRuntime", windowsVideoResources.get().dir("windows/mpv").asFile.absolutePath)
}

abstract class PrepareThirdPartyNotices : DefaultTask() {
    @get:InputFiles @get:Classpath abstract val libraries: ConfigurableFileCollection
    @get:Input abstract val components: ListProperty<String>
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val projectLicense: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val projectNotice: RegularFileProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @TaskAction fun write() {
        val root = outputDirectory.get().asFile.apply { mkdirs() }
        projectLicense.get().asFile.copyTo(File(root, "MyLive-LICENSE.txt"), overwrite = true)
        projectNotice.get().asFile.copyTo(File(root, "MyLive-NOTICE.txt"), overwrite = true)
        File(root, "DEPENDENCIES.txt").writeText("My Live Windows bundled JVM dependencies\n\n" + components.get().sorted().joinToString("\n") + "\n")
        File(root, "README.txt").writeText(
            "Bundled third-party notices are extracted from the unmodified upstream JARs in licenses/.\n" +
            "LICENSE-INDEX.txt maps compact file names to the original JAR and license entry.\n" +
            "Native media licenses, build recipes and source revisions: ../mpv/notices/\n" +
            "Project source: https://github.com/C0neF/My_Live\n" +
            "My Live is licensed under GPL-3.0-or-later; see MyLive-LICENSE.txt and MyLive-NOTICE.txt.\n" +
            "Third-party components retain their own licenses. Runtime-image JDK legal notices are in ../../runtime/legal/.\n"
        )
        val licenseIndex = mutableListOf<String>()
        fun shortId(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .take(8).joinToString("") { "%02x".format(it) }
        libraries.files.filter { it.isFile && it.extension == "jar" }.sortedBy { it.name }.forEach { jar ->
            ZipFile(jar).use { zip ->
                zip.entries().asSequence().filter { entry -> !entry.isDirectory && entry.size in 0..2_000_000 &&
                    Regex("(?i)(^|/)(license[^/]*|notice[^/]*|copyright[^/]*|legal/.*)$").containsMatchIn(entry.name)
                }.sortedBy { it.name }.forEach { entry ->
                    // Windows Installer file actions have a path budget even with LongPathsEnabled.
                    val name = "licenses/${shortId(jar.name)}-${shortId(entry.name)}.txt"
                    licenseIndex.add("$name : ${jar.name} !/${entry.name}")
                    val output = File(root, name)
                    output.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input -> output.outputStream().use { input.copyTo(it) } }
                }
            }
        }
        File(root, "LICENSE-INDEX.txt").writeText(licenseIndex.joinToString("\n", postfix = "\n"))
    }
}

val prepareThirdPartyNotices by tasks.registering(PrepareThirdPartyNotices::class) {
    projectLicense.set(rootProject.layout.projectDirectory.file("LICENSE"))
    projectNotice.set(rootProject.layout.projectDirectory.file("NOTICE"))
    libraries.from(configurations.runtimeClasspath)
    components.set(configurations.runtimeClasspath.map { configuration ->
        configuration.resolvedConfiguration.resolvedArtifacts.map { "${it.moduleVersion.id} : ${it.file.name}" }
    })
    outputDirectory.set(layout.buildDirectory.dir("generated/distributionNotices"))
}
val desktopResources = layout.buildDirectory.dir("generated/desktopResources")
val prepareDesktopResources by tasks.registering(Sync::class) {
    dependsOn(prepareWindowsVideoRuntime)
    from(windowsVideoResources)
    from(prepareThirdPartyNotices.flatMap { it.outputDirectory }) { into("windows/notices") }
    into(desktopResources)
}
tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(prepareDesktopResources, generateWindowsIcon) }
// jpackage must be invalidated by resource-only changes, including removed license files.
tasks.matching { it.name in setOf("createDistributable", "packageMsi", "packageExe") }.configureEach {
    dependsOn(prepareDesktopResources)
    inputs.dir(desktopResources).withPathSensitivity(PathSensitivity.RELATIVE)
}

compose.desktop {
    application {
        providers.gradleProperty("desktopJdk").orNull?.let { javaHome = it }
        mainClass = "com.mylive.desktop.MainKt"
        jvmArgs += listOf("-Xms32m", "-Xmx512m", "-Dfile.encoding=UTF-8", "-Dpolyglot.engine.WarnInterpreterOnly=false")
        nativeDistributions {
            appResourcesRootDir.set(desktopResources)
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "MyLive"
            packageVersion = windowsVersion
            description = "MyLive"
            vendor = "MyLive"
            modules("jdk.unsupported", "java.net.http", "java.logging", "java.sql", "java.management", "jdk.jsobject", "jdk.xml.dom", "java.scripting", "jdk.httpserver", "jdk.jfr")
            windows {
                iconFile.set(windowsIcon)
                menuGroup = "MyLive"
                shortcut = true
                menu = true
                perUserInstall = true
                dirChooser = true
                upgradeUuid = "16cc1334-ce7a-42b6-b29c-daf4707aaf03"
            }
        }
    }
}

tasks.register("verifyWindows") {
    group = "verification"
    description = "Run shared, desktop UI and native runtime acceptance checks"
    dependsOn(":shared-model:jvmTest", ":live-core:test", tasks.test, "uiTest", "nativeSmokeTest")
}

// Use a reviewed WiX override: the JDK 21 custom upgrade helper loses its UUID string.
// The standard MSI action supports our strictly validated three-field versions.
listOf("Msi" to "msi").forEach { (suffix, format) ->
    val installerTask = tasks.register<Exec>("packageWindows$suffix") {
        group = "distribution"
        description = "Build the $format installer with standard MSI upgrade detection"
        dependsOn("createDistributable", ":unzipWix", generateWindowsIcon)
        val image = layout.buildDirectory.dir("compose/binaries/main/app/MyLive").get().asFile
        val destination = layout.buildDirectory.dir("compose/binaries/main/$format").get().asFile
        val installer = File(destination, "MyLive-$windowsVersion.$format")
        val resources = layout.projectDirectory.dir("installer").asFile
        val wix = rootProject.layout.buildDirectory.dir("wix311").get().asFile
        val jdk = providers.gradleProperty("desktopJdk").orElse(providers.environmentVariable("JAVA_HOME"))
            .getOrElse(System.getProperty("java.home"))
        inputs.dir(image).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.dir(resources).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.file(windowsIcon)
        inputs.property("version", windowsVersion)
        inputs.property("jdk", jdk)
        outputs.file(installer)
        environment("PATH", wix.absolutePath + File.pathSeparator + System.getenv("PATH"))
        commandLine(File(jdk, "bin/jpackage.exe").absolutePath,
            "--type", format, "--name", "MyLive", "--app-version", windowsVersion,
            "--app-image", image.absolutePath, "--dest", destination.absolutePath,
            "--resource-dir", resources.absolutePath, "--icon", windowsIcon.get().asFile.absolutePath,
            "--vendor", "MyLive", "--description", "MyLive",
            "--win-upgrade-uuid", "16cc1334-ce7a-42b6-b29c-daf4707aaf03", "--win-per-user-install",
            "--win-dir-chooser", "--win-shortcut", "--win-menu", "--win-menu-group", "MyLive")
        doFirst {
            destination.mkdirs()
            if (installer.exists()) {
                check(!Files.isSymbolicLink(installer.toPath())) { "Installer output must be a regular file" }
                check(installer.delete()) { "Cannot replace old installer output: $installer" }
            }
        }
    }
    tasks.matching { it.name == "package$suffix" }.configureEach {
        onlyIf("The explicit jpackage task produces this installer") { false }
        dependsOn(installerTask)
    }
}

val windowsSetup = tasks.register<Exec>("packageWindowsExe") {
    group = "distribution"
    description = "Wrap the exact verified MSI in a self-contained installer launcher"
    dependsOn("packageWindowsMsi", generateWindowsIcon)
    val msi = layout.buildDirectory.file("compose/binaries/main/msi/MyLive-$windowsVersion.msi").get().asFile
    val output = layout.buildDirectory.dir("compose/binaries/main/exe").get().asFile
    val executable = File(output, "MyLive-$windowsVersion.exe")
    inputs.files(fileTree("installer-launcher") { exclude("bin/**", "obj/**") })
    inputs.file("launcher/app.manifest")
    inputs.file(msi); inputs.file(windowsIcon); inputs.property("version", windowsVersion)
    outputs.file(executable)
    workingDir(layout.projectDirectory.dir("launcher"))
    environment("DOTNET_CLI_TELEMETRY_OPTOUT", "1"); environment("DOTNET_NOLOGO", "1")
    commandLine(providers.gradleProperty("desktopDotnet").getOrElse("dotnet"), "publish", "../installer-launcher/MyLive.Setup.csproj",
        "-c", "Release", "-r", "win-x64", "--self-contained", "true", "-p:DesktopVersion=$windowsVersion",
        "-p:MsiFile=${msi.absolutePath}", "-p:LauncherIcon=${windowsIcon.get().asFile.absolutePath}", "-o", output.absolutePath)
}
tasks.matching { it.name == "packageExe" }.configureEach {
    onlyIf("The self-contained installer launcher produces this executable") { false }
    dependsOn(windowsSetup)
}
val portableZip by tasks.registering(Zip::class) {
    group = "distribution"
    dependsOn("createDistributable")
    from(layout.buildDirectory.dir("compose/binaries/main/app"))
    archiveFileName.set("MyLive-Windows-x64-$windowsVersion.zip")
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main/zip"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
tasks.register("packageWindows") {
    group = "distribution"
    description = "Build portable ZIP, single-file EXE, MSI and EXE installers"
    dependsOn(portableZip, "packageSingleFile", "packageWindowsMsi", "packageWindowsExe")
}

val singleFileDirectory = layout.buildDirectory.dir("compose/binaries/main/single-file/$windowsVersion/MyLive")
val singleFileExecutable = singleFileDirectory.map { it.file("MyLive.exe") }
tasks.register<Exec>("packageSingleFile") {
    group = "distribution"
    description = "Build a self-contained, self-extracting Windows EXE requiring no installation"
    dependsOn("createDistributable", generateWindowsIcon)
    val image = layout.buildDirectory.dir("compose/binaries/main/app/MyLive").get().asFile
    val icon = windowsIcon.get().asFile
    val output = singleFileDirectory.get().asFile
    val expectedName = singleFileExecutable.get().asFile.name
    workingDir(layout.projectDirectory.dir("launcher"))
    inputs.files(fileTree("launcher") { exclude("bin/**", "obj/**") })
    inputs.dir(image)
    inputs.file(icon)
    inputs.property("version", windowsVersion)
    outputs.file(singleFileExecutable)
    environment("DOTNET_CLI_TELEMETRY_OPTOUT", "1")
    environment("DOTNET_NOLOGO", "1")
    commandLine(providers.gradleProperty("desktopDotnet").getOrElse("dotnet"), "publish", "MyLive.SingleFile.csproj",
        "-c", "Release", "-r", "win-x64", "--self-contained", "true",
        "-p:DesktopVersion=$windowsVersion", "-p:AppImageDir=${image.absolutePath}", "-p:LauncherIcon=${icon.absolutePath}",
        "-p:SingleFileOutputRoot=${output.parentFile.parentFile.absolutePath}", "-o", output.absolutePath)
    doLast {
        check(output.walkTopDown().filter { it.isFile }.all { it.name == expectedName }) {
            "Single-file publishing left loose runtime files"
        }
    }
}

tasks.register<Exec>("singleFileTest") {
    group = "verification"
    description = "Verify the single EXE in fresh caches without installed Java or .NET on PATH"
    dependsOn("packageSingleFile")
    val report = layout.buildDirectory.dir("reports/single-file").get().asFile
    inputs.file(singleFileExecutable)
    inputs.file("launcher/Verify-SingleFile.ps1")
    // Always exercise a fresh extraction rather than reuse a previous successful report.
    outputs.upToDateWhen { false }
    commandLine("pwsh", "-NoProfile", "-NonInteractive", "-File", file("launcher/Verify-SingleFile.ps1").absolutePath,
        "-Executable", singleFileExecutable.get().asFile.absolutePath,
        "-AppJar", tasks.jar.get().archiveFile.get().asFile.absolutePath,
        "-Media", file("src/test/resources/sample.flv").absolutePath,
        "-ExpectedVersion", windowsVersion,
        "-ReportDirectory", report.absolutePath)
}
