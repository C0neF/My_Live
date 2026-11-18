param(
    [string]$ProjectRoot = (Join-Path $PSScriptRoot '../..'),
    [Parameter(Mandatory)][string]$OutputDirectory,
    [Parameter(Mandatory)][string]$InstallerReceipt,
    [Parameter(Mandatory)][string]$NativeSources
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$ProjectRoot = [IO.Path]::GetFullPath($ProjectRoot)
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Use a new release directory to avoid mixing builds.' }
$version = ((Get-Content -LiteralPath (Join-Path $ProjectRoot 'desktop/version.properties') | Where-Object { $_ -match '^version=' }) -replace '^version=','').Trim()
$base = Join-Path $ProjectRoot 'desktop/build/compose/binaries/main'
$singleCheck = Get-Content -LiteralPath (Join-Path $ProjectRoot 'desktop/build/reports/single-file/verification.json') -Raw | ConvertFrom-Json
$installerCheck = Get-Content -LiteralPath $InstallerReceipt -Raw | ConvertFrom-Json
if (-not $singleCheck.passed -or -not $installerCheck.passed -or $singleCheck.version -ne $version -or $installerCheck.version -ne $version) { throw 'Acceptance receipts are missing, failed or from a different version.' }
$files = [ordered]@{
    "MyLive.exe" = (Join-Path $base "single-file/$version/MyLive/MyLive.exe")
    "MyLive-Windows-x64-$version.zip" = (Join-Path $base "zip/MyLive-Windows-x64-$version.zip")
    "MyLive-Windows-x64-$version.msi" = (Join-Path $base "msi/MyLive-$version.msi")
    "MyLive-Setup-Windows-x64-$version.exe" = (Join-Path $base "exe/MyLive-$version.exe")
    "MyLive-Native-Sources-$version.zip" = [IO.Path]::GetFullPath($NativeSources)
    'LICENSE.txt' = (Join-Path $ProjectRoot 'LICENSE')
    'NOTICE.txt' = (Join-Path $ProjectRoot 'NOTICE')
    'RELEASE-NOTES.md' = (Join-Path $ProjectRoot 'desktop/release/NOTES.md')
}
foreach ($file in $files.Values) { if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing release input: $file" } }
$notesHeading = ((Get-Content -LiteralPath $files['RELEASE-NOTES.md'] -TotalCount 1) ?? '').Trim()
if ($notesHeading -ne "# My Live Windows $version") { throw "Release notes heading '$notesHeading' does not match version $version." }
if ((Get-FileHash -LiteralPath $files["MyLive.exe"] -Algorithm SHA256).Hash -ne $singleCheck.executableSha256 -or
    (Get-FileHash -LiteralPath $files["MyLive-Windows-x64-$version.msi"] -Algorithm SHA256).Hash -ne $installerCheck.msiSha256) { throw 'Packages changed after acceptance.' }
if ((Get-FileHash -LiteralPath $files["MyLive-Setup-Windows-x64-$version.exe"] -Algorithm SHA256).Hash -ne $installerCheck.setupExeSha256 -or
    $installerCheck.applicationJarSha256 -ne $singleCheck.applicationJarSha256) { throw 'Installer acceptance does not match this code and Setup EXE.' }
if ((Get-FileHash -LiteralPath (Join-Path $ProjectRoot 'desktop/build/libs/desktop.jar') -Algorithm SHA256).Hash -ne $singleCheck.applicationJarSha256) { throw 'Application code changed after single-file acceptance.' }
$sourceLock = Join-Path $ProjectRoot 'desktop/native-notices/sources.lock.json'
$sourceLockHash = (Get-FileHash -LiteralPath $sourceLock -Algorithm SHA256).Hash
if ($sourceLockHash -ne $installerCheck.nativeSourceLockSha256 -or $sourceLockHash -ne $singleCheck.nativeSourceLockSha256) { throw 'Native source information changed after acceptance.' }
function Get-ZipEntryHash($Zip, [string]$Name) {
    $entry = $Zip.GetEntry($Name)
    if (-not $entry) { throw "Required package entry is missing: $Name" }
    $stream = $entry.Open()
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { [Convert]::ToHexString($algorithm.ComputeHash($stream)) } finally { $stream.Dispose(); $algorithm.Dispose() }
}
$portable = [IO.Compression.ZipFile]::OpenRead($files["MyLive-Windows-x64-$version.zip"])
try {
    $jars = @($portable.Entries | Where-Object { $_.FullName -match '^MyLive/app/desktop-[a-f0-9]+\.jar$' })
    if ($jars.Count -ne 1 -or (Get-ZipEntryHash $portable $jars[0].FullName) -ne $singleCheck.applicationJarSha256) { throw 'Portable ZIP contains different application code.' }
    if ((Get-ZipEntryHash $portable 'MyLive/app/resources/mpv/notices/sources.lock.json') -ne $sourceLockHash) { throw 'Portable native source information differs.' }
    if (@($portable.Entries | Where-Object { $_.FullName.StartsWith('MyLive/') -and $_.Length -gt 0 -and $_.FullName.Substring(7).Length -gt 120 }).Count) { throw 'Portable ZIP exceeds the installer path budget.' }
} finally { $portable.Dispose() }
$native = [IO.Compression.ZipFile]::OpenRead($files["MyLive-Native-Sources-$version.zip"])
try {
    if ((Get-ZipEntryHash $native 'sources.lock.json') -ne $sourceLockHash) { throw 'Native source bundle is from a different runtime.' }
    $lock = Get-Content -LiteralPath $sourceLock -Raw | ConvertFrom-Json
    foreach ($archive in $lock.archives) { if ((Get-ZipEntryHash $native ('archives/' + $archive.name)) -ne $archive.sha256) { throw "Native source archive mismatch: $($archive.name)" } }
    foreach ($name in @('ffmpeg-n8.1.3-to-29e619e767.patch','ffmpeg-buildconf.txt')) {
        if ((Get-ZipEntryHash $native $name) -ne (Get-FileHash -LiteralPath (Join-Path $ProjectRoot "desktop/native-notices/$name") -Algorithm SHA256).Hash) { throw "Native source material changed: $name" }
    }
} finally { $native.Dispose() }
New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
foreach ($name in $files.Keys) { Copy-Item -LiteralPath $files[$name] -Destination (Join-Path $OutputDirectory $name) }
& (Join-Path $PSScriptRoot 'New-SourceArchive.ps1') -ProjectRoot $ProjectRoot -OutputFile (Join-Path $OutputDirectory "MyLive-Sources-$version.zip") | Out-Null
Copy-Item -LiteralPath $InstallerReceipt -Destination (Join-Path $OutputDirectory 'installer-verification.json')
Copy-Item -LiteralPath (Join-Path $ProjectRoot 'desktop/build/reports/single-file/verification.json') -Destination (Join-Path $OutputDirectory 'single-file-verification.json')
$assets = @(Get-ChildItem -LiteralPath $OutputDirectory -File | Sort-Object Name | ForEach-Object {
    [ordered]@{ name = $_.Name; bytes = $_.Length; sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
})
$manifest = [ordered]@{ format = 1; project = 'My Live'; platform = 'windows-x64'; version = $version;
    releaseTag = "windows-v$version"; license = 'GPL-3.0-or-later'; minimumWindows = 'Windows 10 22H2 x64';
    baseCommit = (& git -C $ProjectRoot rev-parse HEAD).Trim(); workingChanges = [bool](& git -C $ProjectRoot status --porcelain);
    signed = ((Get-AuthenticodeSignature -LiteralPath $files["MyLive.exe"]).Status -eq 'Valid'); assets = $assets }
$manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'release-manifest.json') -Encoding utf8
$sums = Get-ChildItem -LiteralPath $OutputDirectory -File | Sort-Object Name | ForEach-Object { (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() + '  ' + $_.Name }
$sums | Set-Content -LiteralPath (Join-Path $OutputDirectory 'SHA256SUMS.txt') -Encoding utf8
@{ passed = $true; version = $version; directory = $OutputDirectory; assets = $assets.Count + 2 } | ConvertTo-Json
