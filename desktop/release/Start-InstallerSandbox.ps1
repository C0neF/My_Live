param(
    [Parameter(Mandatory)][string]$Msi,
    [Parameter(Mandatory)][string]$SetupExe,
    [Parameter(Mandatory)][string]$Media,
    [Parameter(Mandatory)][string]$AppJar,
    [Parameter(Mandatory)][string]$ReportDirectory,
    [Parameter(Mandatory)][ValidatePattern('^\d+\.\d+\.\d+$')][string]$ExpectedVersion
)
$ErrorActionPreference = 'Stop'
$run = Join-Path ([IO.Path]::GetFullPath($ReportDirectory)) ([Guid]::NewGuid().ToString('N'))
$inputDirectory = Join-Path $run 'input'
$results = Join-Path $run 'results'
New-Item -ItemType Directory -Path $inputDirectory,$results -Force | Out-Null
Copy-Item -LiteralPath $Msi -Destination (Join-Path $inputDirectory 'candidate.msi')
Copy-Item -LiteralPath $SetupExe -Destination (Join-Path $inputDirectory 'candidate-setup.exe')
Copy-Item -LiteralPath $Media -Destination (Join-Path $inputDirectory 'sample.flv')
$verificationSource = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'Verify-Installer.ps1'))
# The guest uses Windows PowerShell 5.1, which needs a BOM for Unicode script literals.
[IO.File]::WriteAllText((Join-Path $inputDirectory 'Verify-Installer.ps1'), $verificationSource, [Text.UTF8Encoding]::new($true))
$jarHash = (Get-FileHash -LiteralPath $AppJar -Algorithm SHA256).Hash
$sourceLockHash = (Get-FileHash -LiteralPath (Join-Path $PSScriptRoot '../native-notices/sources.lock.json') -Algorithm SHA256).Hash
$command = 'powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "try { & C:\Input\Verify-Installer.ps1 -Msi C:\Input\candidate.msi -SetupExe C:\Input\candidate-setup.exe -Media C:\Input\sample.flv -ReportDirectory C:\Results -ExpectedVersion ' + $ExpectedVersion + ' -ExpectedApplicationJarSha256 ' + $jarHash + ' -ExpectedNativeSourceLockSha256 ' + $sourceLockHash + ' } finally { shutdown.exe /s /t 10 }"'
$configuration = @"
<Configuration><MemoryInMB>6144</MemoryInMB><Networking>Disable</Networking><AudioInput>Disable</AudioInput><ClipboardRedirection>Disable</ClipboardRedirection>
<MappedFolders><MappedFolder><HostFolder>$([Security.SecurityElement]::Escape($inputDirectory))</HostFolder><SandboxFolder>C:\Input</SandboxFolder><ReadOnly>true</ReadOnly></MappedFolder>
<MappedFolder><HostFolder>$([Security.SecurityElement]::Escape($results))</HostFolder><SandboxFolder>C:\Results</SandboxFolder><ReadOnly>false</ReadOnly></MappedFolder></MappedFolders>
<LogonCommand><Command>$([Security.SecurityElement]::Escape($command))</Command></LogonCommand></Configuration>
"@
$file = Join-Path $run 'installer.wsb'
$configuration | Set-Content -LiteralPath $file -Encoding utf8
$cli = Get-Command wsb.exe -ErrorAction SilentlyContinue
$sandboxId = $null
if ($cli) {
    $created = (& $cli.Source start --config $configuration --raw) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'Windows Sandbox CLI could not start the guest.' }
    $sandboxId = ($created | ConvertFrom-Json).Id
    $process = Start-Process -FilePath $cli.Source -ArgumentList @('connect','--id',$sandboxId) -WindowStyle Hidden -PassThru
} else {
    $process = Start-Process -FilePath "$env:SystemRoot/System32/WindowsSandbox.exe" -ArgumentList ('"' + $file + '"') -WindowStyle Hidden -PassThru
}
@{ processId = $process.Id; sandboxId = $sandboxId; evidenceDirectory = $results; configuration = $file } | ConvertTo-Json
$process.Dispose()
