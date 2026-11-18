param([Parameter(Mandatory)][string]$ImageDirectory)
$ErrorActionPreference = 'Stop'
$ImageDirectory = [IO.Path]::GetFullPath($ImageDirectory)
# Reserve 139 characters for a user's chosen install location plus the separator.
# MSI file actions still encounter MAX_PATH even when the application is long-path-aware.
$violations = @(Get-ChildItem -LiteralPath $ImageDirectory -File -Recurse | Where-Object {
    [IO.Path]::GetRelativePath($ImageDirectory, $_.FullName).Length -gt 120
})
if ($violations.Count) { throw "Installer path budget exceeded by $($violations.Count) files. First: $([IO.Path]::GetRelativePath($ImageDirectory, $violations[0].FullName))" }
@{ passed = $true; maximumRelativePath = (Get-ChildItem -LiteralPath $ImageDirectory -File -Recurse |
    ForEach-Object { [IO.Path]::GetRelativePath($ImageDirectory, $_.FullName).Length } | Measure-Object -Maximum).Maximum } | ConvertTo-Json
