param(
    [string]$ProjectRoot = (Join-Path $PSScriptRoot '../..'),
    [Parameter(Mandatory)][string]$CacheDirectory,
    [Parameter(Mandatory)][string]$OutputFile
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$ProjectRoot = [IO.Path]::GetFullPath($ProjectRoot)
$CacheDirectory = [IO.Path]::GetFullPath($CacheDirectory)
$OutputFile = [IO.Path]::GetFullPath($OutputFile)
if (Test-Path -LiteralPath $OutputFile) { throw 'Native source archive output already exists.' }
$notices = Join-Path $ProjectRoot 'desktop/native-notices'
$lockFile = Join-Path $notices 'sources.lock.json'
$lock = Get-Content -LiteralPath $lockFile -Raw | ConvertFrom-Json
$runtime = Join-Path $ProjectRoot 'desktop/build/generated/mediaResources/windows/mpv'
$dlls = @(Get-ChildItem -LiteralPath $runtime -Filter '*.dll' -File)
if ($dlls.Count -ne @($lock.runtimeDlls.PSObject.Properties).Count) { throw 'Native DLL inventory changed; review and update the source lock.' }
foreach ($dll in $dlls) {
    $expected = $lock.runtimeDlls.PSObject.Properties[$dll.Name]
    if (-not $expected -or (Get-FileHash -LiteralPath $dll.FullName -Algorithm SHA256).Hash -ne $expected.Value) { throw "Native DLL changed: $($dll.Name)" }
}
$videoRuntime = Join-Path $ProjectRoot 'desktop/build/generated/videoResources/windows/mpv'
if (Test-Path -LiteralPath $videoRuntime -PathType Container) {
    $videoDlls = @(Get-ChildItem -LiteralPath $videoRuntime -Filter '*.dll' -File | Where-Object { $_.Name -ne 'mediampv.dll' })
    $expectedUpstreamCount = @($lock.runtimeDlls.PSObject.Properties | Where-Object { $_.Name -ne 'mediampv.dll' }).Count
    if ($videoDlls.Count -ne $expectedUpstreamCount) { throw 'Video runtime DLL inventory changed; review and update the source lock.' }
    foreach ($dll in $videoDlls) {
        $expected = $lock.runtimeDlls.PSObject.Properties[$dll.Name]
        if (-not $expected -or (Get-FileHash -LiteralPath $dll.FullName -Algorithm SHA256).Hash -ne $expected.Value) { throw "Video runtime DLL changed: $($dll.Name)" }
    }
    $videoBridgeDll = Join-Path $videoRuntime 'mediampv.dll'
    $builtBridgeDll = Join-Path $ProjectRoot 'desktop/build/native/video-bridge/mediampv.dll'
    if (-not (Test-Path -LiteralPath $videoBridgeDll -PathType Leaf) -or -not (Test-Path -LiteralPath $builtBridgeDll -PathType Leaf)) {
        throw 'Custom video bridge mediampv.dll is missing from video runtime.'
    }
    if ((Get-FileHash -LiteralPath $videoBridgeDll -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath $builtBridgeDll -Algorithm SHA256).Hash) {
        throw 'Video runtime mediampv.dll does not match built native bridge.'
    }
}
New-Item -ItemType Directory -Path $CacheDirectory,(Split-Path $OutputFile) -Force | Out-Null
foreach ($archive in $lock.archives) {
    if ($archive.name -notmatch '^[A-Za-z0-9][A-Za-z0-9._~+-]+$' -or $archive.sha256 -notmatch '^[a-f0-9]{64}$') { throw 'Invalid native source lock entry.' }
    $uri = [Uri]$archive.url
    if ($uri.Scheme -ne 'https' -or $uri.Host -notin @('codeload.github.com','repo.msys2.org','ffmpeg.org')) { throw 'Unexpected native source host.' }
    $path = Join-Path $CacheDirectory $archive.name
    if (-not (Test-Path -LiteralPath $path)) {
        $temporary = $path + '.download-' + [Guid]::NewGuid().ToString('N')
        try {
            Write-Host "Downloading source: $($archive.name)"
            Invoke-WebRequest -Uri $uri -OutFile $temporary -TimeoutSec 600
            if ((Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash -ne $archive.sha256) { throw "Source checksum mismatch: $($archive.name)" }
            Move-Item -LiteralPath $temporary -Destination $path
        } finally { if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary } }
    }
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $archive.sha256) { throw "Cached source checksum mismatch: $($archive.name)" }
}
$stream = [IO.File]::Open($OutputFile, [IO.FileMode]::CreateNew)
$zip = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create)
function Add-ArchiveFile([string]$Name, [string]$Path) {
    $entry = $zip.CreateEntry($Name, [IO.Compression.CompressionLevel]::NoCompression)
    $entry.LastWriteTime = [DateTimeOffset]::new(1980,1,1,0,0,0,[TimeSpan]::Zero)
    $inputStream = [IO.File]::OpenRead($Path)
    $outputStream = $entry.Open()
    try { $inputStream.CopyTo($outputStream) } finally { $inputStream.Dispose(); $outputStream.Dispose() }
}
try {
    foreach ($archive in $lock.archives) { Add-ArchiveFile ('archives/' + $archive.name) (Join-Path $CacheDirectory $archive.name) }
    foreach ($name in @('sources.lock.json','ffmpeg-n8.1.3-to-29e619e767.patch','ffmpeg-buildconf.txt')) { Add-ArchiveFile $name (Join-Path $notices $name) }
    Add-ArchiveFile 'README.md' (Join-Path $notices 'SOURCES.md')
    Add-ArchiveFile 'LICENSE' (Join-Path $ProjectRoot 'LICENSE')
    Add-ArchiveFile 'NOTICE' (Join-Path $ProjectRoot 'NOTICE')
} finally { $zip.Dispose(); $stream.Dispose() }
@{ passed = $true; archives = $lock.archives.Count; matchedRuntimeDlls = $dlls.Count;
    path = $OutputFile; sha256 = (Get-FileHash -LiteralPath $OutputFile -Algorithm SHA256).Hash } | ConvertTo-Json
