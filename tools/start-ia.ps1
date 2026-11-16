# DeepSetk - Iniciar entorno de IA local + NetBeans
# Comprueba el servidor, abre Bionic si hace falta y arranca NetBeans.

$ErrorActionPreference = 'SilentlyContinue'

$bionic   = Join-Path $env:LOCALAPPDATA 'Programs\Bionic\Bionic.exe'
$netbeans = 'C:\Program Files\netbeans\bin\netbeans64.exe'
$port     = 1235
$url      = "http://127.0.0.1:$port/v1/models"

function Test-Ia {
    try {
        $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3
        return ($r.StatusCode -eq 200)
    } catch {
        return $false
    }
}

function Line { param([string]$t, [string]$c = 'Gray') Write-Host $t -ForegroundColor $c }

Write-Host ''
Line '  DeepSetk - entorno de IA local' 'Cyan'
Line '  ------------------------------' 'DarkGray'

# 1) Bionic
if (Get-Process Bionic -ErrorAction SilentlyContinue) {
    Line '  [1/3] Bionic ya esta abierto' 'Green'
} elseif (Test-Path $bionic) {
    Line '  [1/3] Abriendo Bionic...' 'Yellow'
    Start-Process $bionic
} else {
    Line '  [1/3] No encuentro Bionic en:' 'Red'
    Line "        $bionic" 'DarkGray'
}

# 2) Servidor
Line '  [2/3] Esperando el puerto 1235...' 'Yellow'
for ($i = 0; $i -lt 30; $i++) {
    if (Test-Ia) { break }
    Start-Sleep -Seconds 2
}

if (Test-Ia) {
    $models = (Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5).Content | ConvertFrom-Json
    Line '  IA local OK en 127.0.0.1:1235' 'Green'
    foreach ($m in $models.data) { Line ("        - " + $m.id) 'DarkGray' }
} else {
    Line '  AVISO: el puerto 1235 no responde.' 'Red'
    Line '  Abre Bionic y pulsa Start Server (Developer).' 'DarkGray'
}

# 3) NetBeans
if (Get-Process netbeans64 -ErrorAction SilentlyContinue) {
    Line '  [3/3] NetBeans ya esta abierto' 'Green'
} elseif (Test-Path $netbeans) {
    Line '  [3/3] Abriendo NetBeans...' 'Yellow'
    Start-Process $netbeans -ArgumentList '--nosplash'
} else {
    Line '  [3/3] No encuentro NetBeans en:' 'Red'
    Line "        $netbeans" 'DarkGray'
}

Write-Host ''
Line '  Listo. Herramientas: tema, sugerencias (Tab), Ctrl+Alt+Espacio.' 'Cyan'
Write-Host ''
Start-Sleep -Seconds 3
