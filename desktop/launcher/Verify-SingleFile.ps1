param(
    [Parameter(Mandatory)][string]$Executable,
    [Parameter(Mandatory)][string]$AppJar,
    [Parameter(Mandatory)][string]$Media,
    [Parameter(Mandatory)][string]$ReportDirectory,
    [Parameter(Mandatory)][ValidatePattern('^\d+\.\d+\.\d+$')][string]$ExpectedVersion,
    [switch]$KeepRuntimeArtifacts
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$run = Join-Path ([IO.Path]::GetFullPath($ReportDirectory)) ([Guid]::NewGuid().ToString('N'))
$case = Join-Path $run '中文 空格 & 路径'
$localData = Join-Path $run 'isolated-user'
New-Item -ItemType Directory -Path $case,(Join-Path $localData 'MyLive') -Force | Out-Null
$binary = Join-Path $case 'My Live 单文件.exe'
$profileFixture = Join-Path $localData 'MyLive/library.json'
$cache = Join-Path $run 'cache'
$concurrentCache = Join-Path $run 'concurrent-cache'
$runningProcesses = [Collections.Generic.List[Diagnostics.Process]]::new()
$verificationFile = Join-Path $ReportDirectory 'verification.json'
@{ passed = $false; status = 'running'; evidenceDirectory = $run } | ConvertTo-Json | Set-Content -Encoding utf8 -LiteralPath $verificationFile

function Start-Bundle([string]$Name, [string]$Cache) {
    $output = Join-Path $run $Name
    $info = [Diagnostics.ProcessStartInfo]::new($binary)
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
    $info.WorkingDirectory = $case
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.ArgumentList.Add("--self-check=$output")
    $info.ArgumentList.Add('--self-check-media=测试 素材.flv')
    $info.Environment['DOTNET_BUNDLE_EXTRACT_BASE_DIR'] = $Cache
    $info.Environment['LOCALAPPDATA'] = $localData
    # Prove that installed Java/.NET locations and command-line tools are unnecessary.
    $info.Environment['PATH'] = "$env:SystemRoot/System32;$env:SystemRoot"
    $info.Environment['DOTNET_ROOT'] = Join-Path $run 'not-installed'
    $info.Environment['DOTNET_ROOT_X64'] = Join-Path $run 'not-installed'
    $info.Environment['DOTNET_MULTILEVEL_LOOKUP'] = '0'
    [void]$info.Environment.Remove('JAVA_HOME')
    [void]$info.Environment.Remove('JRE_HOME')
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $process = [Diagnostics.Process]::Start($info)
    $runningProcesses.Add($process)
    return [pscustomobject]@{
        Name = $Name; Output = $output; Process = $process; Watch = $watch
        Stdout = $process.StandardOutput.ReadToEndAsync(); Stderr = $process.StandardError.ReadToEndAsync()
    }
}

function Complete-Bundle($Job) {
    try {
        if (-not $Job.Process.WaitForExit(90000)) {
            $Job.Process.Kill($true)
            if (-not $Job.Process.WaitForExit(10000)) { throw "Unable to stop timed out check: $($Job.Name)" }
            throw "Single-file check timed out: $($Job.Name)"
        }
        $Job.Watch.Stop()
        [IO.File]::WriteAllText((Join-Path $run "$($Job.Name).stdout.log"), $Job.Stdout.GetAwaiter().GetResult())
        [IO.File]::WriteAllText((Join-Path $run "$($Job.Name).stderr.log"), $Job.Stderr.GetAwaiter().GetResult())
        if ($Job.Process.ExitCode -ne 0) { throw "Single-file check failed: $($Job.Name), exit $($Job.Process.ExitCode)" }
        $result = Get-Content -LiteralPath (Join-Path $Job.Output 'self-check.json') -Raw | ConvertFrom-Json
        if ($result.version -ne $ExpectedVersion) { throw "Wrong embedded application version: $($result.version)" }
        if (-not $result.passed -or -not $result.checks.native_media.decoded -or $result.checks.native_media.width -ne 320) {
            throw "Native runtime check failed: $($Job.Name)"
        }
        return [pscustomobject]@{ name = $Job.Name; passed = $true; milliseconds = $Job.Watch.ElapsedMilliseconds }
    } finally {
        if ($Job.Process.HasExited) {
            [void]$runningProcesses.Remove($Job.Process)
            $Job.Process.Dispose()
        }
    }
}

function Read-CachedApp([string]$Cache) {
    $apps = @(Get-ChildItem -LiteralPath $Cache -Recurse -File -Filter 'MyLive.exe')
    if ($apps.Count -ne 1) { throw 'Expected one complete cached app image' }
    $app = $apps[0].Directory.FullName
    $jars = @(Get-ChildItem -LiteralPath (Join-Path $app 'app') -File | Where-Object Name -Match '^desktop-[a-f0-9]+\.jar$')
    if ($jars.Count -ne 1 -or (Get-FileHash -LiteralPath $jars[0].FullName -Algorithm SHA256).Hash -ne $expectedJarHash) {
        throw 'Embedded app differs from the tested app JAR'
    }
    foreach ($relative in @('runtime/bin/server/jvm.dll', 'app/resources/mpv/libmpv-2.dll',
        'app/resources/notices/dotnet/LICENSE.TXT', 'app/resources/notices/dotnet/THIRD-PARTY-NOTICES.TXT')) {
        if (-not (Test-Path -LiteralPath (Join-Path $app $relative))) { throw "Missing bundled runtime file: $relative" }
    }
    if ((Get-FileHash -LiteralPath (Join-Path $app 'app/resources/mpv/notices/sources.lock.json') -Algorithm SHA256).Hash -ne $expectedSourceLockHash) {
        throw 'Embedded native source information differs from the candidate'
    }
    return $apps[0]
}

function Remove-OwnedRuntimeArtifacts {
    $ownedRoot = (Resolve-Path -LiteralPath $run).ProviderPath.TrimEnd('\')
    if ([IO.Path]::GetFileName($ownedRoot) -notmatch '^[a-f0-9]{32}$' -or
        [IO.Path]::GetDirectoryName($ownedRoot) -ne [IO.Path]::GetFullPath($ReportDirectory).TrimEnd('\')) {
        throw 'Refusing cleanup outside the owned verification run'
    }
    [long]$removedBytes = 0
    foreach ($target in @($cache, $concurrentCache, $case)) {
        if (-not (Test-Path -LiteralPath $target)) { continue }
        $resolved = (Resolve-Path -LiteralPath $target).ProviderPath.TrimEnd('\')
        if (-not $resolved.StartsWith($ownedRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing cleanup outside the owned run: $resolved"
        }
        $cursor = $resolved
        while ($cursor.Length -ge $ownedRoot.Length) {
            if (((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw "Refusing cleanup through a reparse point: $cursor"
            }
            $cursor = [IO.Path]::GetDirectoryName($cursor)
        }
        if (@(Get-ChildItem -LiteralPath $resolved -Recurse -Force -Attributes ReparsePoint).Count) {
            throw "Refusing recursive cleanup of linked content: $resolved"
        }
        $removedBytes += [long](Get-ChildItem -LiteralPath $resolved -Recurse -Force -File | Measure-Object Length -Sum).Sum
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
    return $removedBytes
}

try {
Copy-Item -LiteralPath $Executable -Destination $binary
Copy-Item -LiteralPath $Media -Destination (Join-Path $case '测试 素材.flv')
[IO.File]::WriteAllText($profileFixture, '{"version":1,"follows":[],"history":[],"marker":"preserve-existing-user-data"}')
$profileHash = (Get-FileHash -LiteralPath $profileFixture -Algorithm SHA256).Hash
$expectedJarHash = (Get-FileHash -LiteralPath $AppJar -Algorithm SHA256).Hash
$expectedSourceLockHash = (Get-FileHash -LiteralPath (Join-Path $PSScriptRoot '../native-notices/sources.lock.json') -Algorithm SHA256).Hash
$checks = @()
$checks += Complete-Bundle (Start-Bundle '首次 启动' $cache)
$cachedApp = Read-CachedApp $cache
$timestamp = $cachedApp.LastWriteTimeUtc
$checks += Complete-Bundle (Start-Bundle '缓存 重启' $cache)
$after = Read-CachedApp $cache
if ($after.FullName -ne $cachedApp.FullName -or $after.LastWriteTimeUtc -ne $timestamp) { throw 'Warm launch did not reuse the complete cache' }

# Both launches start before waiting: exercise the host's first-run extraction lock.
$first = Start-Bundle '并发 A' $concurrentCache
$second = Start-Bundle '并发 B' $concurrentCache
$checks += Complete-Bundle $first
$checks += Complete-Bundle $second
[void](Read-CachedApp $concurrentCache)
if ((Get-FileHash -LiteralPath $profileFixture -Algorithm SHA256).Hash -ne $profileHash) { throw 'Existing user data was changed' }
} catch {
    @{ passed = $false; status = 'failed'; evidenceDirectory = $run; errorType = $_.Exception.GetType().Name } |
        ConvertTo-Json | Set-Content -Encoding utf8 -LiteralPath $verificationFile
    throw
} finally {
    foreach ($process in $runningProcesses.ToArray()) {
        try {
            if (-not $process.HasExited) {
                $process.Kill($true)
                if (-not $process.WaitForExit(10000)) { throw 'Unable to stop owned verification process' }
            }
        } finally { $process.Dispose() }
    }
    $removedBytes = if ($KeepRuntimeArtifacts) { 0L } else { Remove-OwnedRuntimeArtifacts }
    @{ runtimeArtifactsRemoved = -not $KeepRuntimeArtifacts; removedBytes = $removedBytes } |
        ConvertTo-Json | Set-Content -Encoding utf8 -LiteralPath (Join-Path $run 'cleanup.json')
}

$receipt = [ordered]@{
    passed = $true; version = $ExpectedVersion; checks = $checks; cacheReused = $true
    concurrentColdStart = $true; unicodeAndSpacePaths = $true; relativeMediaPath = $true
    existingProfilePreserved = $true; noInstalledJavaOrDotnetOnPath = $true
    applicationJarSha256 = $expectedJarHash
    nativeSourceLockSha256 = $expectedSourceLockHash
    executableSha256 = (Get-FileHash -LiteralPath $Executable -Algorithm SHA256).Hash
    executableBytes = (Get-Item -LiteralPath $Executable).Length; evidenceDirectory = $run
    runtimeArtifactsRemoved = -not $KeepRuntimeArtifacts; removedRuntimeBytes = $removedBytes
}
$receipt | ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 -LiteralPath $verificationFile
$receipt | ConvertTo-Json -Depth 6
