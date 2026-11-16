# Single-file Windows launcher

`packageSingleFile` embeds the existing jpackage app image inside a self-contained .NET bundle.
It produces `MyLive.exe` under `single-file/<version>/MyLive/`, using `desktop/version.properties`; end users do not install Java, .NET or the app.

The SDK is constrained to .NET 10 by `global.json`. The shipped runtime **and apphost** are pinned
to 10.0.13 with `RuntimeFrameworkVersion`, independently of the developer's installed SDK patch.
This version was checked against [Microsoft's .NET 10 release metadata](https://builds.dotnet.microsoft.com/dotnet/release-metadata/10.0/releases.json).

The JVM, mpv and its native libraries need filesystem paths, so this wrapper deliberately uses
`IncludeAllContentForSelfExtract`. This is the compatibility mode documented by Microsoft;
check its availability before migrating to a new SDK major version. The .NET host performs cache
creation and first-launch synchronization, rather than adding a second custom archive extractor.
See [single-file deployment](https://learn.microsoft.com/en-us/dotnet/core/deploying/single-file/overview).

The launcher passes each argument through `ProcessStartInfo.ArgumentList`, preserves the caller's
working directory, waits for the JVM app and propagates its exit code. It does not invoke a shell,
register an installed product, create shortcuts, or move the user's existing profile.

The .NET host extracts the image under a directory named after the executable. The Windows JVM
launcher then converts that path, the working directory, and process arguments with the ANSI code
page (JDK-8241461). Characters the code page cannot represent become `?`, so startup fails with
`could not find java.dll` or `InvalidPathException`. The wrapper starts the image, working
directory, and `--self-check` / `--window-smoke` / `--self-check-media` values through ASCII
junctions or an ASCII hard link. The caller's files are not moved.

The bundle's default cache is `%TEMP%/.net/<executable>/<bundle-id>/`; the native app continues to
use `%LOCALAPPDATA%/MyLive/` for user data. .NET runtime notices are included in the extracted app's
`app/resources/notices/dotnet/`, alongside the existing JVM/native notices.

Build and verify from the repository root:

```powershell
.\gradlew.bat :desktop:packageSingleFile -PdesktopJdk=C:/Path/To/jdk-21
.\gradlew.bat :desktop:singleFileTest -PdesktopJdk=C:/Path/To/jdk-21
```

Use `-PdesktopDotnet=C:/Path/To/dotnet.exe` when the .NET SDK is not on PATH.
The verifier uses fresh cache directories, removes Java/.NET locations from the child's PATH,
sets DOTNET_ROOT to a nonexistent directory, and exercises actual JVM, FLV, WebKit, DPAPI,
signing and sync operations. It also checks cache reuse, concurrent cold start, Unicode/space/
ampersand paths, relative video paths, application JAR identity and preservation of a profile fixture.
Its result is `desktop/build/reports/single-file/verification.json`.

The verifier removes its own copied EXE and extraction caches after both successful and failed
runs, keeping the small logs, self-check results and isolated profile fixture. It only stops
processes it started and checks deletion paths against the unique run directory. Sibling files,
the input executable and the user's normal profile remain outside cleanup.
Invoke the script directly with `-KeepRuntimeArtifacts` to retain the runtime images for debugging.
