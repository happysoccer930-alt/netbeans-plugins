# Parcha el icono REAL de netbeans64.exe con el cubo Cyberpunk.
# Requiere ejecutar PowerShell como Administrador. Hace copia de seguridad.
#
# Uso (PowerShell elevado):
#   powershell -ExecutionPolicy Bypass -File tools\patch-netbeans-exe-icon.ps1
#
# Nota: modifica un .exe firmado de Program Files. Una actualizacion de
# NetBeans puede reemplazarlo. Para volver atras, restaura el .bak.

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$exe  = 'C:\Program Files\netbeans\bin\netbeans64.exe'
$ico  = Join-Path $repo 'assets\netbeans-cyberpunk.ico'
$tool = Join-Path $PSScriptRoot 'rcedit-x64.exe'

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Ejecuta PowerShell como Administrador para parchear netbeans64.exe.'
}
if (-not (Test-Path -LiteralPath $ico)) { throw "Falta $ico" }
if (-not (Test-Path -LiteralPath $tool)) {
    Write-Output 'Descargando rcedit...'
    Invoke-WebRequest -Uri 'https://github.com/electron/rcedit/releases/latest/download/rcedit-x64.exe' -OutFile $tool
}

$backup = "$exe.bak-" + (Get-Date -Format 'yyyyMMdd-HHmmss')
Copy-Item -LiteralPath $exe -Destination $backup
Write-Output "Copia de seguridad: $backup"

& $tool --set-icon $ico $exe
if ($LASTEXITCODE -ne 0) {
    Copy-Item -LiteralPath $backup -Destination $exe -Force
    throw "rcedit fallo (codigo $LASTEXITCODE). Se restauro el .exe original."
}
Write-Output "Icono aplicado a $exe"
