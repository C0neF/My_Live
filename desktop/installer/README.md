# Windows Installer template

Based on OpenJDK 21 main.wxs at adoptium/jdk21u commit 1c417fbfc2f7, under the OpenJDK
GPL-2.0-only license; original license and additional licensing information are included here.

The pinned jpackage native helper stores `upgradeCode.toMsiString().c_str()` beyond the
temporary string's lifetime. In actual upgrade acceptance it enumerated a user SID as
the upgrade UUID, returned ERROR_INVALID_PARAMETER (87), cleared the standard action's
correct old-product result, and left both versions installed.

Our stable versions are restricted to MSI-compatible X.Y.Z. The standard FindRelatedProducts
action already handles these versions. This template removes only the redundant custom
FindRelatedProductsEx invocation and explicitly rejects downgrades in UI and silent modes.
Other jpackage installer UI, file associations, shortcuts and directory handling are retained.

Compose's packaging task does not expose jpackage's resource override directory. The
packageMsi delegates to an explicit jpackage task using the same tested Compose app image,
icon, upgrade UUID and this resource directory. packageExe wraps the resulting exact MSI
using the self-contained .NET launcher in ../installer-launcher/, preserving each argument
and the Windows Installer exit code. End users do not need an installed .NET runtime.
Windows Installer property values use its documented NAME="value" syntax; the launcher
formats these separately from file arguments. Standard whole-argument quoting of a
property with spaces opens Windows Installer help instead of starting installation.
Test-InstallerArguments.ps1 covers the serialization contract, and Sandbox acceptance
uses a Chinese installation path containing spaces and an ampersand.
Keep this template under review when changing the packaging JDK.
