$ErrorActionPreference = 'Stop'
Add-Type -Path (Join-Path $PSScriptRoot '../installer-launcher/InstallerArguments.cs')
$cases = @(
    @{ args = @('/i','C:\中文 文件\MyLive.msi','/qn'); expected = '/i "C:\中文 文件\MyLive.msi" /qn' },
    @{ args = @('INSTALLDIR=C:\中文 空格 & 路径'); expected = 'INSTALLDIR="C:\中文 空格 & 路径"' },
    @{ args = @('MESSAGE=hello "world"'); expected = 'MESSAGE="hello ""world"""' },
    @{ args = @('/qn','/norestart','/L*v','C:\QA\setup.log'); expected = '/qn /norestart /L*v C:\QA\setup.log' },
    @{ args = @('EMPTY='); expected = 'EMPTY=""' }
)
foreach ($case in $cases) {
    $actual = [InstallerArguments]::Format([string[]]$case.args)
    if ($actual -cne $case.expected) { throw "Windows Installer command-line contract failed: $actual" }
}
foreach ($invalid in @("bad`nargument", "bad`rargument", "bad`0argument")) {
    $rejected = $false
    try { [void][InstallerArguments]::Format([string[]]@($invalid)) } catch { $rejected = $true }
    if (-not $rejected) { throw 'Invalid command-line control character was accepted.' }
}
@{ passed = $true; cases = $cases.Count + 3 } | ConvertTo-Json
