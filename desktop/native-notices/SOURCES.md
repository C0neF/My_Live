# Windows native source materials

`sources.lock.json` identifies the actual redistributed DLLs by SHA-256, source archive URLs and
checksums, matched MSYS2 binary/source packages, and upstream FFmpeg dependency repositories/revisions.
`Collect-NativeSources.ps1` verifies the DLL inventory before downloading and bundling the locked sources.

The bundle contains MediaMP, its patched-mpv build recipe, mpv, FFmpeg, the matching BtbN build
recipes and 25 MSYS2 source packages. The MSYS2 packages contain PKGBUILD files, patches and upstream
sources. The DLLs were matched byte-for-byte against their MSYS2 binary packages; PACKAGE-INFO.txt
and their original licenses are also shipped with the application under notices/msys2/.

## mpv / MediaMP

MediaMP: d7d4d9e81979cb0549e55805adb64f3cb1c1e689 (v0.3.2).
mpv: 41f6a645068483470267271e1d09966ca3b9f413.
The MediaMP archive includes the JNI renderer, render_d3d11.patch and Gradle/MSYS2 native build
tasks. Follow its README and buildSrc/src/main/kotlin/mpv/ recipes for WindowsX64. Its .gitmodules
lists the original FFmpeg/dav1d build inputs; their exact Git revisions are in the source lock.
The upstream build enables GPL mpv features. My Live replaces the reduced FFmpeg DLL set with
the full ABI-compatible build below; it does not alter the native source files.

## Full FFmpeg DLLs

FFmpeg source revision: 29e619e767cde9045a75c29bc9a8278ae7b3a98b.
Build recipes: BtbN/FFmpeg-Builds 6c9aec5fc9a72ec3abedd1fa84db141fa18cf52b,
also identified by its autobuild-2026-09-30-13-08 tag. Variant: win64 lgpl-shared 8.1.
Actual configuration: ffmpeg-buildconf.txt (from the matching upstream executable).

To obtain the exact FFmpeg source without a large Git archive download, extract the official
ffmpeg-8.1.3.tar.xz, initialize an empty Git repository inside the extracted source directory,
then apply the included patch with line-ending conversion disabled:

```powershell
tar -xf ffmpeg-8.1.3.tar.xz
git -C ffmpeg-8.1.3 init
git -C ffmpeg-8.1.3 -c core.autocrlf=false apply ../ffmpeg-n8.1.3-to-29e619e767.patch
```

All nine modified files were checked against the upstream Git blob hashes at the target commit.
The patch represents upstream changes between n8.1.3 and that commit, not My Live modifications.
The upstream build recipe's `makeimage.sh win64 lgpl-shared 8.1` and `build.sh win64 lgpl-shared 8.1`
describe the complete toolchain, patches, configuration and static dependency build. Pin the
FFmpeg checkout to the source revision above instead of a moving branch when rebuilding it.
The FFmpeg archive's .version can be set to n8.1.3-9-g29e619e767 for the original version string.

Static dependencies have their exact repository/revision and recipe path enumerated in
ffmpegDependencySources in the source lock; these are direct upstream source locations, not
binary downloads. The bundled recipe's download.sh retrieves their sources and any submodules;
Cargo dependencies are recorded in the respective upstream lockfiles. The build README documents
Docker, target variants and prerequisites. These dependency sources remain under their own licenses.

## JVM and launcher runtime

The app's notices/DEPENDENCIES.txt identifies the Maven dependencies and licenses/ contains their
upstream notices, with LICENSE-INDEX.txt mapping compact filenames to original JAR entries.
runtime/legal/ retains OpenJDK notices and the Classpath Exception. Source and build revisions for
the bundled Temurin runtime are in runtimeSourceReferences. JavaFX source is available from
OpenJDK's jfx21u repository and the matching org.openjfx:21.0.13 source artifacts on Maven Central.
The single-file executable additionally retains the .NET runtime's LICENSE and THIRD-PARTY-NOTICES.

My Live project source and its build scripts are provided as the separate MyLive-Sources archive.
Publish it alongside the binaries and this source bundle. Preserve upstream notices, the source
lock, patches, build instructions, source access and all applicable license terms when redistributing.
