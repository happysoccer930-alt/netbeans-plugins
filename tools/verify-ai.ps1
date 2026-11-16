# Verifica el modulo Gemini sin abrir NetBeans ni gastar cuota de API:
#  - monta layer.xml con el XMLFileSystem real
#  - prueba el parser JSON (generateContent, /models, errores)
#  - prueba el cifrado/descifrado de la API key
#  - prueba el conversor Markdown
#
# Uso: powershell -ExecutionPolicy Bypass -File tools\verify-ai.ps1

$ErrorActionPreference = 'Stop'

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

$work   = Join-Path $env:TEMP "aierrors-verify"
$src    = Join-Path $repo "modules\ai-errors\src\main\java"
$res    = Join-Path $repo "modules\ai-errors\src\main\resources\org\deepsetk\aierrors"
$verify = Join-Path $repo "tools\verify\VerifyAiErrors.java"
Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $work | Out-Null

# Los jars de NetBeans se referencian con comodines: javac/java los expanden.
$cp = @(
    (Join-Path $nb "platform\lib\*"),
    (Join-Path $nb "platform\core\*"),
    (Join-Path $nb "platform\modules\*"),
    (Join-Path $nb "ide\modules\*"),
    (Join-Path $nb "ide\core\*"),
    (Join-Path $nb "java\modules\*")
) -join ';'

$sources = @(Get-ChildItem -Path $src -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$sources += $verify

& (Join-Path $jdkBin 'javac.exe') -encoding UTF-8 -d $work -cp $cp $sources
if ($LASTEXITCODE -ne 0) { throw "Fallo al compilar el modulo ai-errors y el verificador" }

& (Join-Path $jdkBin 'java.exe') -cp "$work;$cp" org.deepsetk.aierrors.VerifyAiErrors $res
exit $LASTEXITCODE
