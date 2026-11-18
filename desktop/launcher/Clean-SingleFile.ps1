param(
    [Parameter(Mandatory)][string]$RootDirectory,
    [Parameter(Mandatory)][string]$KeepExecutable
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName Microsoft.VisualBasic
$outputRoot = (Resolve-Path -LiteralPath $RootDirectory).Path
$keepFile = (Resolve-Path -LiteralPath $KeepExecutable).Path
$keepDirectory = [IO.Path]::GetDirectoryName($keepFile)
$versionDirectory = [IO.Path]::GetDirectoryName($keepDirectory)
$obsoleteOutputs = @(Get-ChildItem -LiteralPath $outputRoot -Force | Where-Object { $_.FullName -ne $versionDirectory }) +
    @(Get-ChildItem -LiteralPath $versionDirectory -Force | Where-Object { $_.FullName -ne $keepDirectory }) +
    @(Get-ChildItem -LiteralPath $keepDirectory -Force | Where-Object { $_.FullName -ne $keepFile })
foreach ($obsoleteOutput in $obsoleteOutputs) {
    $obsoletePath = (Resolve-Path -LiteralPath $obsoleteOutput.FullName).Path
    if (-not $obsoletePath.StartsWith($outputRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Cleanup target is outside the single-file output directory: $obsoletePath"
    }
    if ($obsoleteOutput.PSIsContainer) {
        [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($obsoletePath,
            [Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,
            [Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin,
            [Microsoft.VisualBasic.FileIO.UICancelOption]::ThrowException)
    } else {
        [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile($obsoletePath,
            [Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,
            [Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin,
            [Microsoft.VisualBasic.FileIO.UICancelOption]::ThrowException)
    }
}
