# Verifica el modulo de musica sin abrir NetBeans:
#  - monta layer.xml con el XMLFileSystem real
#  - prueba titulos, extensiones e iconos
#  - decodifica WAV, MP3, FLAC (16/24 bits) y OGG generados al vuelo con ffmpeg
#
# Requiere ffmpeg en el PATH (solo para crear los audios de prueba).
# Uso: powershell -ExecutionPolicy Bypass -File tools\verify-music.ps1

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

$cpFile = Join-Path $PSScriptRoot 'verify\music-cp.txt'
if (-not (Test-Path -LiteralPath $cpFile)) {
    $mvn = Join-Path $nb 'java\maven\bin\mvn.cmd'
    & $mvn -B -ntp -f (Join-Path $repo 'modules\music\pom.xml') dependency:build-classpath "-Dmdep.outputFile=$cpFile" "-Dmdep.includeScope=runtime"
    if ($LASTEXITCODE -ne 0) { throw "No se pudo calcular el classpath del modulo music" }
}
$cp = (Get-Content -LiteralPath $cpFile -Raw).Trim()

$work = Join-Path $env:TEMP "music-verify"
Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $work | Out-Null

$ffmpeg = (Get-Command ffmpeg -ErrorAction SilentlyContinue)
if (-not $ffmpeg) { throw "ffmpeg no esta en el PATH; hace falta para generar los audios de prueba." }
$audio = Join-Path $work "audio"
New-Item -ItemType Directory -Force -Path $audio | Out-Null
$wav = Join-Path $audio "test.wav"
& $ffmpeg.Source -hide_banner -loglevel error -y -f lavfi -i "sine=frequency=440:duration=2" -ac 2 -ar 44100 -c:a pcm_s16le $wav
& $ffmpeg.Source -hide_banner -loglevel error -y -i $wav -c:a libmp3lame -b:a 128k (Join-Path $audio "test.mp3")
& $ffmpeg.Source -hide_banner -loglevel error -y -i $wav -c:a flac (Join-Path $audio "test.flac")
& $ffmpeg.Source -hide_banner -loglevel error -y -i $wav -sample_fmt s32 -bits_per_raw_sample 24 -c:a flac (Join-Path $audio "test24.flac")
& $ffmpeg.Source -hide_banner -loglevel error -y -i $wav -c:a libvorbis -q:a 4 (Join-Path $audio "test.ogg")
if ($LASTEXITCODE -ne 0) { throw "ffmpeg fallo al generar los audios de prueba" }

$src = Join-Path $repo "modules\music\src\main\java"
$sources = @(Get-ChildItem -Path $src -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$sources += Join-Path $repo "tools\verify\VerifyMusic.java"

& (Join-Path $jdkBin 'javac.exe') -encoding UTF-8 -d $work -cp $cp $sources
if ($LASTEXITCODE -ne 0) { throw "Fallo al compilar el modulo music y el verificador" }

$res = Join-Path $repo "modules\music\src\main\resources\org\deepsetk\music"
& (Join-Path $jdkBin 'java.exe') "-Djava.awt.headless=true" -cp "$work;$cp" org.deepsetk.music.VerifyMusic $res $audio
exit $LASTEXITCODE
