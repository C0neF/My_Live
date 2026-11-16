param(
    [string]$ProjectRoot = (Join-Path $PSScriptRoot '../..'),
    [Parameter(Mandatory)][string]$OutputFile
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$ProjectRoot = [IO.Path]::GetFullPath($ProjectRoot)
$OutputFile = [IO.Path]::GetFullPath($OutputFile)
if (Test-Path -LiteralPath $OutputFile) { throw 'Source archive output already exists.' }
$paths = ((& git -C $ProjectRoot ls-files -z --cached --others --exclude-standard) -join "`n").Split([char]0) |
    Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $ProjectRoot $_) -PathType Leaf) } |
    Sort-Object -Unique
if ($LASTEXITCODE -ne 0) { throw 'Cannot enumerate project sources.' }
$allowedRoots = @('.github','app','desktop','docs','gradle','live-core','macrobenchmark','screenshots','shared-model','shared-ui')
$allowedFiles = @('.gitattributes','.gitignore','build.gradle.kts','gradle.properties','gradlew','gradlew.bat','settings.gradle.kts','LICENSE','NOTICE')
$entries = @()
foreach ($path in $paths) {
    if ($path -match '[\r\n\\]' -or $path.Split('/') -contains '..') { throw 'Unsupported source path.' }
    $rootName = $path.Split('/')[0]
    if ($rootName -notin $allowedRoots -and $path -notin $allowedFiles -and $path -notmatch '^[A-Za-z0-9_-]+\.md$') { throw "Unclassified source path: $path" }
    if ($path -match '(?i)^(app|desktop|live-core|macrobenchmark|shared-model|shared-ui)/build(/|$)|^desktop/(launcher|installer-launcher)/(bin|obj)/|(^|/)(node_modules|\.git|\.gradle)(/|$)|\.(jks|keystore|pfx|p12)$|(^|/)(local\.properties|keystore\.properties|\.env)(\.|$)') {
        throw "Local output or signing material in source list: $path"
    }
    $item = Get-Item -LiteralPath (Join-Path $ProjectRoot $path)
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Source symlink requires explicit review: $path" }
    $entries += [ordered]@{ path = $path; bytes = $item.Length; sha256 = (Get-FileHash -LiteralPath $item.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
}
$baseCommit = (& git -C $ProjectRoot rev-parse HEAD).Trim()
$version = ((Get-Content -LiteralPath (Join-Path $ProjectRoot 'desktop/version.properties') | Where-Object { $_ -match '^version=' }) -replace '^version=','').Trim()
$manifest = [ordered]@{ format = 1; project = 'My Live'; windowsVersion = $version; baseCommit = $baseCommit;
    includesWorkingChanges = [bool](& git -C $ProjectRoot status --porcelain); license = 'GPL-3.0-or-later'; files = $entries }
New-Item -ItemType Directory -Path (Split-Path $OutputFile) -Force | Out-Null
$stream = [IO.File]::Open($OutputFile, [IO.FileMode]::CreateNew)
$zip = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($entry in $entries) {
        $target = $zip.CreateEntry($entry.path, [IO.Compression.CompressionLevel]::Optimal)
        $target.LastWriteTime = [DateTimeOffset]::new(1980,1,1,0,0,0,[TimeSpan]::Zero)
        $inputStream = [IO.File]::OpenRead((Join-Path $ProjectRoot $entry.path))
        $outputStream = $target.Open()
        try { $inputStream.CopyTo($outputStream) } finally { $inputStream.Dispose(); $outputStream.Dispose() }
    }
    $entry = $zip.CreateEntry('SOURCE-MANIFEST.json')
    $entry.LastWriteTime = [DateTimeOffset]::new(1980,1,1,0,0,0,[TimeSpan]::Zero)
    $writer = [IO.StreamWriter]::new($entry.Open(), [Text.UTF8Encoding]::new($false))
    try { $writer.Write(($manifest | ConvertTo-Json -Depth 8)) } finally { $writer.Dispose() }
} finally { $zip.Dispose(); $stream.Dispose() }
@{ path = $OutputFile; files = $entries.Count; sha256 = (Get-FileHash -LiteralPath $OutputFile -Algorithm SHA256).Hash; baseCommit = $baseCommit } | ConvertTo-Json
