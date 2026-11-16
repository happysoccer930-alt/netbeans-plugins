param([string]$Profile = 'SoftDark')

# Verifica un perfil de tema sin abrir NetBeans:
#  - monta layer.xml con el XMLFileSystem real de NetBeans
#  - valida todos los XML de colores contra el DTD oficial
#
# Uso: powershell -ExecutionPolicy Bypass -File tools\verify-theme.ps1
#      powershell -ExecutionPolicy Bypass -File tools\verify-theme.ps1 -Profile Cyberpunk

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$repo    = Split-Path -Parent $PSScriptRoot
$nb      = "C:\Program Files\netbeans"
$jdkBin = $null
if ($env:JAVA_HOME) { $jdkBin = Join-Path $env:JAVA_HOME 'bin' }
if (-not $jdkBin -or -not (Test-Path -LiteralPath (Join-Path $jdkBin 'javac.exe'))) {
    $candidate = Get-ChildItem "C:\Program Files\Java" -Directory -ErrorAction SilentlyContinue |
                 Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\javac.exe') } |
                 Sort-Object Name -Descending | Select-Object -First 1
    if (-not $candidate) { throw "No encuentro un JDK. Define JAVA_HOME." }
    $jdkBin = Join-Path $candidate.FullName 'bin'
}

$work = Join-Path $env:TEMP "softdark-verify"
$dtd  = Join-Path $work "dtd"
Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $work, $dtd | Out-Null

$storageJar = Join-Path $nb "ide\modules\org-netbeans-modules-editor-settings-storage.jar"
$zip = [System.IO.Compression.ZipFile]::OpenRead($storageJar)
$zip.Entries | Where-Object { $_.FullName -match 'EditorFontsColors-1_[12]\.dtd' } |
    ForEach-Object { [System.IO.Compression.ZipFileExtensions]::ExtractToFile($_, (Join-Path $dtd $_.Name), $true) }
$zip.Dispose()

$cp = @(
    (Join-Path $nb "platform\core\org-openide-filesystems.jar"),
    (Join-Path $nb "platform\lib\org-openide-util.jar"),
    (Join-Path $nb "platform\lib\org-openide-util-lookup.jar"),
    (Join-Path $nb "platform\lib\org-openide-modules.jar"),
    (Join-Path $nb "platform\lib\boot.jar")
) -join ';'

& (Join-Path $jdkBin 'javac.exe') -d $work -cp $cp (Join-Path $repo "tools\verify\VerifyTheme.java")
if ($LASTEXITCODE -ne 0) { throw "Fallo al compilar VerifyTheme.java" }

$res = Join-Path $repo "modules\softdark-theme\src\main\resources\org\deepsetk\softdark"
if ($Profile -eq 'Cyberpunk') {
    $res = Join-Path $repo "modules\cyberpunk-theme\src\main\resources\org\deepsetk\cyberpunk"
}
& (Join-Path $jdkBin 'java.exe') -cp "$work;$cp" VerifyTheme $res $dtd $Profile
exit $LASTEXITCODE
