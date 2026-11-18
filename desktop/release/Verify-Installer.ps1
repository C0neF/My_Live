param(
    [Parameter(Mandatory)][string]$Msi,
    [Parameter(Mandatory)][string]$SetupExe,
    [Parameter(Mandatory)][string]$Media,
    [Parameter(Mandatory)][string]$ReportDirectory,
    [Parameter(Mandatory)][ValidatePattern('^\d+\.\d+\.\d+$')][string]$ExpectedVersion,
    [Parameter(Mandatory)][ValidatePattern('^[A-Fa-f0-9]{64}$')][string]$ExpectedApplicationJarSha256,
    [Parameter(Mandatory)][ValidatePattern('^[A-Fa-f0-9]{64}$')][string]$ExpectedNativeSourceLockSha256
)

# Compatible with Windows PowerShell 5.1 in Windows Sandbox, as well as CI pwsh.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ($env:USERNAME -ne 'WDAGUtilityAccount' -and $env:GITHUB_ACTIONS -ne 'true') {
    throw 'Installer acceptance must run in Windows Sandbox or a disposable GitHub Actions runner.'
}
New-Item -ItemType Directory -Path $ReportDirectory -Force | Out-Null
$report = Join-Path $ReportDirectory 'installer-verification.json'
@{ passed = $false; status = 'running' } | ConvertTo-Json | Set-Content -LiteralPath $report -Encoding UTF8
$run = Join-Path $env:TEMP ('MyLive-Installer-QA-' + [Guid]::NewGuid().ToString('N'))
# The installed executable is the JVM launcher. On an ANSI code page that cannot
# represent Chinese, that launcher exits with "could not find java.dll" (JDK-8241461).
# Keep the Chinese directory when the code page can round-trip it; otherwise keep the
# space and ampersand, which are the other characters this path is here to exercise.
$unicodeInstallName = '中文 空格 & 路径'
$ansi = [Text.Encoding]::GetEncoding(0)
$unicodeInstallPath = $ansi.GetString($ansi.GetBytes($unicodeInstallName)) -ceq $unicodeInstallName
$installDirectory = Join-Path $run $(if ($unicodeInstallPath) { $unicodeInstallName } else { 'space & path' })
New-Item -ItemType Directory -Path $run -Force | Out-Null
$installer = New-Object -ComObject WindowsInstaller.Installer

function Get-MsiField($Record, [int]$Index) {
    $Record.GetType().InvokeMember('StringData', 'GetProperty', $null, $Record, @($Index))
}
function Get-MsiProperty($Database, [string]$Name) {
    # Names are internal constants, never external input.
    $view = $Database.OpenView(('SELECT `Value` FROM `Property` WHERE `Property` = ''{0}''' -f $Name))
    try { [void]$view.Execute($null); $record = $view.Fetch(); if ($record) { Get-MsiField $record 1 } } finally { [void]$view.Close() }
}
function Invoke-MsiSql($Database, [string]$Sql) {
    $view = $Database.OpenView($Sql)
    try { [void]$view.Execute($null) } catch { throw "MSI SQL failed: $Sql; $($_.Exception.Message)" } finally {
        [void]$view.Close()
        [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($view)
    }
}
function Invoke-Msi([string]$Name, [string[]]$Arguments, [int[]]$ExpectedCodes = @(0, 3010)) {
    $log = Join-Path $ReportDirectory ($Name + '.log')
    # Only internally generated paths/identifiers are quoted; no shell command is constructed.
    $argumentsWithLog = @($Arguments) + @('/qn', '/norestart', '/L*v', ('"' + $log + '"'))
    $process = Start-Process -FilePath "$env:SystemRoot/System32/msiexec.exe" -ArgumentList $argumentsWithLog -WindowStyle Hidden -PassThru
    try {
        if (-not $process.WaitForExit(180000)) { $process.Kill(); throw "MSI timed out: $Name" }
        if ($process.ExitCode -notin $ExpectedCodes) { throw "MSI $Name failed: $($process.ExitCode); see $log" }
        $process.ExitCode
    } finally { $process.Dispose() }
}
function Get-InstalledProduct([string]$Code) {
    $state = $installer.GetType().InvokeMember('ProductState', 'GetProperty', $null, $installer, @($Code))
    if ($state -eq -1) { return }
    if ($state -ne 5) { throw "Unexpected product state $state for $Code" }
    [pscustomobject]@{
        PSChildName = $Code
        DisplayName = $installer.GetType().InvokeMember('ProductInfo', 'GetProperty', $null, $installer, @($Code, 'ProductName'))
        DisplayVersion = $installer.GetType().InvokeMember('ProductInfo', 'GetProperty', $null, $installer, @($Code, 'VersionString'))
        AssignmentType = $installer.GetType().InvokeMember('ProductInfo', 'GetProperty', $null, $installer, @($Code, 'AssignmentType'))
    }
}
function Get-RelatedProductCodes {
    $list = $installer.GetType().InvokeMember('RelatedProducts', 'GetProperty', $null, $installer, @('{16CC1334-CE7A-42B6-B29C-DAF4707AAF03}', 0))
    try {
        $length = $list.GetType().InvokeMember('Count', 'GetProperty', $null, $list, $null)
        for ($index = 0; $index -lt $length; $index++) { $list.GetType().InvokeMember('Item', 'GetProperty', $null, $list, @($index)) }
    } finally { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($list) }
}
function Assert-InstalledRuntime([string]$Phase) {
    $jar = @(Get-ChildItem -LiteralPath (Join-Path $installDirectory 'app') -File | Where-Object { $_.Name -match '^desktop-[a-f0-9]+\.jar$' })
    if ($jar.Count -ne 1 -or (Get-FileHash -LiteralPath $jar[0].FullName -Algorithm SHA256).Hash -ne $ExpectedApplicationJarSha256) { throw 'Installed code differs from the tested JAR.' }
    if (-not (Test-Path -LiteralPath (Join-Path $installDirectory 'app/resources/notices/LICENSE-INDEX.txt'))) { throw 'License index is missing.' }
    if ((Get-FileHash -LiteralPath (Join-Path $installDirectory 'app/resources/mpv/notices/sources.lock.json') -Algorithm SHA256).Hash -ne $ExpectedNativeSourceLockSha256) { throw 'Installed native source lock differs from the candidate.' }
    $longPaths = @(Get-ChildItem -LiteralPath $installDirectory -File -Recurse | Where-Object { $_.FullName.Substring($installDirectory.Length + 1).Length -gt 120 })
    if ($longPaths.Count) { throw 'Installed package exceeds the path budget.' }
    $output = Join-Path $ReportDirectory $Phase
    $process = Start-Process -FilePath (Join-Path $installDirectory 'MyLive.exe') -ArgumentList @(
        ('"--self-check=' + $output + '"'), ('"--self-check-media=' + [IO.Path]::GetFullPath($Media) + '"')
    ) -WindowStyle Hidden -PassThru
    try {
        if (-not $process.WaitForExit(90000)) { $process.Kill(); throw "Installed runtime timed out: $Phase" }
        $check = Get-Content -LiteralPath (Join-Path $output 'self-check.json') -Raw | ConvertFrom-Json
        if ($process.ExitCode -ne 0 -or -not $check.passed -or $check.version -ne $ExpectedVersion -or -not $check.checks.native_media.decoded) {
            throw "Installed runtime check failed: $Phase"
        }
    } finally { $process.Dispose() }
}

$currentCode = $null
$baselineCode = $null
$setupCode = $null
$checks = [ordered]@{}
try {
    $existing = @(Get-ItemProperty -Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
        'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
        'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*' -ErrorAction SilentlyContinue |
        Where-Object { $_.PSObject.Properties['DisplayName'] -and $_.DisplayName -eq 'MyLive' })
    if ($existing.Count -gt 0 -or @(Get-RelatedProductCodes).Count -gt 0) { throw 'Refusing to alter an existing MyLive installation.' }
    $database = $installer.OpenDatabase([IO.Path]::GetFullPath($Msi), 0)
    try {
        $currentCode = Get-MsiProperty $database 'ProductCode'
        $version = Get-MsiProperty $database 'ProductVersion'
        $upgradeCode = Get-MsiProperty $database 'UpgradeCode'
        if ($version -ne $ExpectedVersion -or $upgradeCode -ne '{16CC1334-CE7A-42B6-B29C-DAF4707AAF03}' -or (Get-MsiProperty $database 'ALLUSERS') -eq '1') {
            throw "Incorrect installer metadata: version=[$version], upgrade=[$upgradeCode], allUsers=[$(Get-MsiProperty $database 'ALLUSERS')]"
        }
        $view = $database.OpenView('SELECT `Action` FROM `CustomAction` WHERE `Action` = ''JpFindRelatedProducts''')
        try { [void]$view.Execute($null); if ($view.Fetch()) { throw 'The unsafe jpackage upgrade helper is still in the installer.' } } finally { [void]$view.Close(); [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($view) }
    } finally { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($database) }

    $profile = Join-Path $env:LOCALAPPDATA 'MyLive/library.json'
    if (Test-Path -LiteralPath $profile) { throw 'Refusing to overwrite existing MyLive profile.' }
    New-Item -ItemType Directory -Path (Split-Path $profile) -Force | Out-Null
    [IO.File]::WriteAllText($profile, '{"version":1,"follows":[],"history":[],"marker":"installer-must-preserve"}')
    $profileHash = (Get-FileHash -LiteralPath $profile -Algorithm SHA256).Hash
    $sourceHash = (Get-FileHash -LiteralPath $Msi -Algorithm SHA256).Hash

    # A copy of this exact MSI with only installer identity/version lowered is an upgrade fixture.
    # It tests Windows Installer major-upgrade behavior, not migration from an older app executable.
    $baseline = Join-Path $run 'baseline-fixture.msi'
    Copy-Item -LiteralPath $Msi -Destination $baseline
    $baselineCode = '{' + [Guid]::NewGuid().ToString().ToUpperInvariant() + '}'
    $database = $installer.OpenDatabase($baseline, 1)
    try {
        Invoke-MsiSql $database (('UPDATE `Property` SET `Value` = ''{0}'' WHERE `Property` = ''ProductCode''' -f $baselineCode))
        Invoke-MsiSql $database 'UPDATE `Property` SET `Value` = ''0.0.1'' WHERE `Property` = ''ProductVersion'''
        # Version bounds are primary-key columns in MSI; replace the two jpackage rows.
        Invoke-MsiSql $database 'DELETE FROM `Upgrade`'
        Invoke-MsiSql $database ('INSERT INTO `Upgrade` (`UpgradeCode`,`VersionMax`,`Attributes`,`ActionProperty`) VALUES (''{0}'',''0.0.1'',257,''JP_UPGRADABLE_FOUND'')' -f $upgradeCode)
        Invoke-MsiSql $database ('INSERT INTO `Upgrade` (`UpgradeCode`,`VersionMin`,`Attributes`,`ActionProperty`) VALUES (''{0}'',''0.0.1'',3,''JP_DOWNGRADABLE_FOUND'')' -f $upgradeCode)
        $summary = $database.GetType().InvokeMember('SummaryInformation', 'GetProperty', $null, $database, @(20))
        [void]$summary.GetType().InvokeMember('Property', 'SetProperty', $null, $summary, @(9, ('{' + [Guid]::NewGuid().ToString().ToUpperInvariant() + '}')))
        [void]$summary.Persist()
        [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($summary)
        [void]$database.Commit()
    } finally { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($database) }
    $checks.baselineInstall = Invoke-Msi '01-install-baseline' @('/i', ('"' + $baseline + '"'), ('INSTALLDIR="' + $installDirectory + '"'))
    $baselineProduct = Get-InstalledProduct $baselineCode
    if (-not $baselineProduct -or $baselineProduct.AssignmentType -ne '0') { throw 'Baseline was not installed per user.' }
    Assert-InstalledRuntime 'baseline-runtime'
    $checks.upgrade = Invoke-Msi '02-upgrade' @('/i', ('"' + [IO.Path]::GetFullPath($Msi) + '"'), ('INSTALLDIR="' + $installDirectory + '"'))
    if ((Get-InstalledProduct $baselineCode) -or -not (Get-InstalledProduct $currentCode)) { throw 'Major upgrade did not replace the old product.' }
    if ((Get-InstalledProduct $currentCode).DisplayVersion -ne $ExpectedVersion -or (Get-InstalledProduct $currentCode).AssignmentType -ne '0') { throw 'Wrong installed version or context.' }
    Assert-InstalledRuntime 'upgraded-runtime'
    $checks.downgradeRejected = Invoke-Msi '03-reject-downgrade' @('/i', ('"' + $baseline + '"')) @(1603, 1638)
    if ((Get-InstalledProduct $baselineCode) -or -not (Get-InstalledProduct $currentCode)) { throw 'Downgrade changed installed products.' }
    $checks.uninstall = Invoke-Msi '04-uninstall' @('/x', $currentCode)
    if ((Get-InstalledProduct $currentCode) -or (Test-Path -LiteralPath (Join-Path $installDirectory 'MyLive.exe'))) { throw 'Uninstall left the application installed.' }
    $setupLog = Join-Path $ReportDirectory '05-setup-exe.log'
    # Sandbox mapped folders behave differently from a local downloaded executable.
    # Copy the unchanged file locally and invoke it directly, as a user would after download.
    $localSetup = Join-Path $run 'setup.exe'
    Copy-Item -LiteralPath $SetupExe -Destination $localSetup
    $setupInfo = New-Object Diagnostics.ProcessStartInfo
    $setupInfo.FileName = $localSetup
    $setupInfo.Arguments = '/qn /norestart /L*v "' + $setupLog + '" INSTALLDIR="' + $installDirectory + '"'
    $setupInfo.UseShellExecute = $false
    $setupInfo.CreateNoWindow = $true
    $setup = [Diagnostics.Process]::Start($setupInfo)
    try {
        if (-not $setup.WaitForExit(180000)) { $setup.Kill(); throw 'Setup EXE timed out.' }
        if ($setup.ExitCode -notin @(0,3010)) { throw "Setup EXE failed: $($setup.ExitCode)" }
        $checks.setupExe = $setup.ExitCode
    } finally { $setup.Dispose() }
    $setupProducts = @(Get-RelatedProductCodes | ForEach-Object { Get-InstalledProduct $_ })
    if ($setupProducts.Count -ne 1 -or $setupProducts[0].DisplayVersion -ne $ExpectedVersion -or $setupProducts[0].AssignmentType -ne '0' -or $setupProducts[0].PSChildName -notmatch '^\{[A-Fa-f0-9-]{36}\}$') { throw 'Setup EXE did not register the expected per-user product.' }
    $setupCode = $setupProducts[0].PSChildName
    Assert-InstalledRuntime 'setup-exe-runtime'
    $checks.setupExeUninstall = Invoke-Msi '06-setup-uninstall' @('/x', $setupCode)
    if ((Get-InstalledProduct $setupCode) -or (Test-Path -LiteralPath (Join-Path $installDirectory 'MyLive.exe'))) { throw 'Setup EXE product was not uninstalled.' }
    if ((Get-FileHash -LiteralPath $profile -Algorithm SHA256).Hash -ne $profileHash) { throw 'User profile was modified.' }
    if ((Get-FileHash -LiteralPath $Msi -Algorithm SHA256).Hash -ne $sourceHash) { throw 'Original MSI was modified.' }
    @{ passed = $true; version = $ExpectedVersion; checks = $checks; perUser = $true;
        upgradeFixture = 'same payload, baseline installer version 0.0.1'; profilePreserved = $true;
        originalMsiUnchanged = $true; unicodeInstallPath = $unicodeInstallPath; msiSha256 = $sourceHash;
        setupExeSha256 = (Get-FileHash -LiteralPath $SetupExe -Algorithm SHA256).Hash;
        applicationJarSha256 = $ExpectedApplicationJarSha256; nativeSourceLockSha256 = $ExpectedNativeSourceLockSha256 } |
        ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $report -Encoding UTF8
} catch {
    @{ passed = $false; status = 'failed'; message = $_.Exception.Message; stack = $_.ScriptStackTrace; checks = $checks } |
        ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $report -Encoding UTF8
    throw
} finally {
    # Remove only products whose identifiers we read/generated for this disposable test.
    foreach ($code in @($currentCode, $baselineCode, $setupCode) | Select-Object -Unique) {
        if ($code -and (Get-InstalledProduct $code)) { [void](Invoke-Msi 'cleanup' @('/x', $code)) }
    }
    [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($installer)
}
