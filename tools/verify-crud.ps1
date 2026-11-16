# Verifica el generador CRUD sin abrir NetBeans:
#  - monta layer.xml y comprueba accion/menu/boton/icono
#  - prueba el parser de CREATE TABLE, tipos, PK simple/compuesta/sin PK
#  - revisa el Entity, DAO y Service generados
#
# Uso: powershell -ExecutionPolicy Bypass -File tools\verify-crud.ps1

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

$cpFile = Join-Path $PSScriptRoot 'verify\crud-cp.txt'
if (-not (Test-Path -LiteralPath $cpFile)) {
    $mvn = Join-Path $nb 'java\maven\bin\mvn.cmd'
    & $mvn -B -ntp -f (Join-Path $repo 'modules\crud-generator\pom.xml') dependency:build-classpath "-Dmdep.outputFile=$cpFile" "-Dmdep.includeScope=runtime"
    if ($LASTEXITCODE -ne 0) { throw "No se pudo calcular el classpath del generador" }
}
$cp = (Get-Content -LiteralPath $cpFile -Raw).Trim()

$work = Join-Path $env:TEMP "crud-generator-verify"
Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $work | Out-Null

$src = Join-Path $repo "modules\crud-generator\src\main\java"
$sources = @(Get-ChildItem -Path $src -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$sources += Join-Path $repo "tools\verify\VerifyCrudGenerator.java"

& (Join-Path $jdkBin 'javac.exe') -encoding UTF-8 -d $work -cp $cp $sources
if ($LASTEXITCODE -ne 0) { throw "Fallo al compilar el generador y el verificador" }

$res = Join-Path $repo "modules\crud-generator\src\main\resources\org\deepsetk\crud"
& (Join-Path $jdkBin 'java.exe') "-Djava.awt.headless=true" -cp "$work;$cp" org.deepsetk.crud.VerifyCrudGenerator $res
exit $LASTEXITCODE
