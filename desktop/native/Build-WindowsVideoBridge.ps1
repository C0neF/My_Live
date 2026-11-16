param(
    [Parameter(Mandatory)][string]$RuntimeDirectory,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [Parameter(Mandatory)][string]$JavaHome
)
$ErrorActionPreference = 'Stop'
[Diagnostics.Process]::GetCurrentProcess().PriorityClass = [Diagnostics.ProcessPriorityClass]::BelowNormal
$nativeRoot = $PSScriptRoot
$runtimeRoot = (Resolve-Path -LiteralPath $RuntimeDirectory).Path
$nativeOutput = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $nativeOutput -Force | Out-Null
$vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
$visualStudio = & $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
$vcvars = Join-Path $visualStudio 'VC\Auxiliary\Build\vcvars64.bat'
$exports = Join-Path $nativeOutput 'mpv-exports.txt'
$setupScript = Join-Path $nativeOutput 'exports.cmd'
@"
@echo off
call "$vcvars" >nul
dumpbin.exe /nologo /exports "$runtimeRoot\libmpv-2.dll" > "$exports"
"@ | Set-Content -LiteralPath $setupScript -Encoding ascii
& cmd.exe /d /c $setupScript
if ($LASTEXITCODE) { throw 'Cannot read libmpv exports' }
$names = Get-Content -LiteralPath $exports | ForEach-Object {
    if ($_ -match '^\s+\d+\s+[0-9A-F]+\s+[0-9A-F]+\s+(mpv_[A-Za-z0-9_]+)') { $Matches[1] }
}
$definition = Join-Path $nativeOutput 'mpv.def'
@('LIBRARY libmpv-2.dll', 'EXPORTS') + $names | Set-Content -LiteralPath $definition -Encoding ascii
$response = Join-Path $nativeOutput 'compile.rsp'
$sources = Get-ChildItem -LiteralPath (Join-Path $nativeRoot 'mediamp') -Filter '*.cpp' | ForEach-Object { '"' + $_.FullName + '"' }
@('/nologo', '/std:c++20', '/O2', '/EHsc', '/MT', '/LD', '/DWIN32_LEAN_AND_MEAN', '/DNOMINMAX', '/utf-8',
    "/I`"$nativeRoot\mediamp\include`"", "/I`"$nativeRoot\include`"",
    "/I`"$JavaHome\include`"", "/I`"$JavaHome\include\win32`"") + $sources | Set-Content -LiteralPath $response -Encoding utf8
$compileScript = Join-Path $nativeOutput 'compile.cmd'
@"
@echo off
call "$vcvars" >nul
lib.exe /nologo /machine:x64 /def:"$definition" /out:"$nativeOutput\mpv.lib"
if errorlevel 1 exit /b 1
cl.exe @"$response" /link /OUT:"$nativeOutput\mediampv.dll" /IMPLIB:"$nativeOutput\mediampv.lib" "$nativeOutput\mpv.lib" user32.lib gdi32.lib opengl32.lib d3d11.lib d3d12.lib dxgi.lib dxguid.lib windowscodecs.lib ole32.lib uuid.lib
"@ | Set-Content -LiteralPath $compileScript -Encoding ascii
Push-Location -LiteralPath $nativeOutput
try {
    & cmd.exe /d /c $compileScript
    if ($LASTEXITCODE) { throw 'Windows video bridge compilation failed' }
} finally { Pop-Location }
