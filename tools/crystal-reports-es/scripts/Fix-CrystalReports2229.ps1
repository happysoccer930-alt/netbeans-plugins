<#
.SYNOPSIS
    Corrige el error 2229 ("No es posible cargar la tabla Control") al instalar
    SAP Crystal Reports for Visual Studio (64-bit) en un Windows en español.

.DESCRIPTION
    El instalador oficial (CRforVS6413SP40_0-80007712.EXE) aplica la
    transformación de idioma <LCID>.mst según el idioma de Windows. En el SP40
    las transformaciones no inglesas están mal generadas: al fusionarlas se
    corrompe el "string pool" de la base MSI y el diálogo inicial falla con
    error 2229.

    Este script:
      1. Extrae el paquete (EXE autoextraíble) a una carpeta de trabajo.
      2. Comprueba la firma digital del EXE y si está bloqueado.
      3. Diagnostica qué transformaciones (.mst) están rotas.
      4. Genera un MSI con la interfaz traducida al español (sin transform).
      5. Verifica que la base resultante es consistente.
      6. Opcionalmente genera un .mst equivalente para poder seguir usando
         setup.exe.

.PARAMETER Package
    Ruta al CRforVS...EXE descargado de SAP, o a un .msi ya extraído.

.PARAMETER OutDir
    Carpeta de trabajo/salida. Por defecto %TEMP%\CR-es.

.PARAMETER MakeTransform
    Además del MSI traducido, genera <OutDir>\<LCID>.mst válido.

.PARAMETER Language
    LCID de la traducción a aplicar. 3082 = español (España).

.EXAMPLE
    .\Fix-CrystalReports2229.ps1 -Package "$env:USERPROFILE\Downloads\CRforVS6413SP40_0-80007712 (2).EXE"

.EXAMPLE
    .\Fix-CrystalReports2229.ps1 -Package "C:\Temp\CR\package\CrystalReportsForVisualStudio64.msi" -MakeTransform

.NOTES
    Requiere Windows PowerShell 5.1, Windows Installer y 7-Zip (solo si la
    entrada es un EXE). No modifica el paquete original.
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Package,

    [string]$OutDir = (Join-Path $env:TEMP 'CR-es'),

    [switch]$MakeTransform,

    [int]$Language = 3082
)

$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------------------
# Utilidades
# ---------------------------------------------------------------------------

function Write-Step($text) { Write-Host ""; Write-Host "== $text" -ForegroundColor Cyan }
function Write-Ok($text)   { Write-Host "   OK  $text" -ForegroundColor Green }
function Write-Warn2($text){ Write-Host "   !!  $text" -ForegroundColor Yellow }

function Get-Wi {
    New-Object -ComObject WindowsInstaller.Installer
}

function Invoke-MsiQuery {
    <# Devuelve una fila por elemento, con las columnas unidas por '|'. #>
    param($Database, [string]$Sql)
    $view = $Database.OpenView($Sql)
    $view.Execute()
    $out = @()
    while ($true) {
        $rec = $view.Fetch()
        if (-not $rec) { break }
        $vals = @()
        for ($i = 1; $i -le 12; $i++) {
            try { $vals += [string]$rec.StringData($i) } catch { break }
        }
        $out += ($vals -join '|')
    }
    $view.Close()
    return $out
}

function Test-TransformApplies {
    <# Devuelve 'VALID' si la transformación se puede aplicar sin corromper la base. #>
    param([string]$BaseMsi, [string]$Transform, [string]$Scratch)

    $ok = $false
    $wi = Get-Wi
    $db = $null
    $null = Copy-Item $BaseMsi $Scratch -Force
    try {
        $db = $wi.OpenDatabase($Scratch, 1)
        $null = $db.ApplyTransform($Transform, 0)
        # Si el string pool se corrompe, esta consulta (todas las columnas,
        # incluidos los textos) falla con "OpenView,Sql".
        $null = Invoke-MsiQuery -Database $db -Sql "SELECT * FROM ``Control``"
        $ok = $true
    } catch {
        $ok = $false
    } finally {
        if ($db) { $null = [Runtime.InteropServices.Marshal]::ReleaseComObject($db) }
        $null = [Runtime.InteropServices.Marshal]::ReleaseComObject($wi)
        $null = Start-Sleep -Milliseconds 200
        Remove-Item $Scratch -Force -ErrorAction SilentlyContinue
    }
    if ($ok) { return 'VALID' } else { return 'INVALID' }
}

# ---------------------------------------------------------------------------
# 1. Localizar y validar el paquete
# ---------------------------------------------------------------------------

Write-Step "Paquete de entrada"
if (-not (Test-Path -LiteralPath $Package)) { throw "No existe el archivo: $Package" }
$item = Get-Item -LiteralPath $Package
Write-Host "   $($item.FullName)"
Write-Host ("   {0:N1} MB" -f ($item.Length / 1MB))

$sig = Get-AuthenticodeSignature -LiteralPath $item.FullName
if ($sig.Status -eq 'Valid') {
    Write-Ok "Firma digital válida: $($sig.SignerCertificate.Subject -replace '^CN=([^,]+).*','$1')"
} else {
    Write-Warn2 "Firma no válida ($($sig.Status)). Verifica la descarga antes de continuar."
}

$blocked = Get-Item -LiteralPath $item.FullName -Stream * -ErrorAction SilentlyContinue |
           Where-Object { $_.Stream -eq 'Zone.Identifier' }
if ($blocked) {
    Write-Warn2 "El archivo está bloqueado (Zone.Identifier). Ejecuta: Unblock-File `"$($item.FullName)`""
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

# ---------------------------------------------------------------------------
# 2. Obtener el MSI y las transformaciones
# ---------------------------------------------------------------------------

Write-Step "Extrayendo el paquete"
$pkgDir = Join-Path $OutDir 'package'
if ($item.Extension -ieq '.msi') {
    New-Item -ItemType Directory -Force -Path $pkgDir | Out-Null
    Copy-Item $item.FullName (Join-Path $pkgDir $item.Name) -Force
    # Las transformaciones suelen estar junto al MSI cuando ya está extraído
    Get-ChildItem $item.Directory -Filter '*.mst' -ErrorAction SilentlyContinue |
        Copy-Item -Destination $pkgDir -Force
} else {
    $sevenZip = @(
        'C:\Program Files\7-Zip\7z.exe',
        'C:\Program Files (x86)\7-Zip\7z.exe'
    ) | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $sevenZip) { throw "Se necesita 7-Zip para extraer el EXE. Instálalo o extrae a mano y pásame el .msi." }

    $extract = Join-Path $OutDir 'extract'
    if (Test-Path $extract) { Remove-Item $extract -Recurse -Force }
    & $sevenZip x $item.FullName "-o$extract" -y | Out-Null
}

$msi = Get-ChildItem $OutDir -Recurse -Filter '*.msi' |
       Sort-Object Length -Descending | Select-Object -First 1
if (-not $msi) { throw "No se encontró ningún .msi dentro del paquete." }
Write-Ok "MSI: $($msi.Name) ($([math]::Round($msi.Length/1MB,1)) MB)"

$msts = @(Get-ChildItem $msi.Directory -Filter '*.mst')
Write-Host "   Transformaciones encontradas: $($msts.Count)"

# ---------------------------------------------------------------------------
# 3. Diagnóstico de las transformaciones
# ---------------------------------------------------------------------------

Write-Step "Diagnóstico de las transformaciones de idioma"
$broken = @()
$i = 0
foreach ($mst in $msts) {
    $i++
    $scratch = Join-Path $OutDir ("scratch_{0}.msi" -f $i)
    $verdict = Test-TransformApplies -BaseMsi $msi.FullName -Transform $mst.FullName -Scratch $scratch
    $lcid = [IO.Path]::GetFileNameWithoutExtension($mst.Name)
    if ($verdict -eq 'VALID') {
        Write-Ok "$lcid.mst  -> aplica correctamente"
    } else {
        Write-Warn2 "$lcid.mst  -> CORRUPTA (provoca el error 2229)"
        $broken += $lcid
    }
}

if ($broken -notcontains ([string]$Language)) {
    Write-Step "Nada que corregir"
    Write-Host "   Tu idioma ($Language) no está entre las transformaciones rotas."
    Write-Host "   Instala normalmente con setup.exe."
    return
}

# ---------------------------------------------------------------------------
# 4. Traducciones
# ---------------------------------------------------------------------------

# Clave: "Dialog_/Control". Valor: texto en español.
$controlEs = @{
'Progress_Dialog/ActionData'            = 'Inicializando...'
'Progress_Dialog/ActionProgress95'      = 'Progreso completado'
'Progress_Dialog/Cancel'                = 'Cancelar'
'Fatal_Error/Text'                      = 'La instalación de [ProductName] finalizó prematuramente debido a un error.'
'Fatal_Error/OK'                        = 'Aceptar'
'Admin_Install_Point/Text'              = '¿Dónde desea crear la instalación?'
'Admin_Install_Point/Next'              = '&Siguiente >'
'Admin_Install_Point/EditQ'             = 'Punto de &instalación de red:'
'Admin_Install_Point/Cancel'            = 'Cancelar'
'Admin_Install_Point/Browse'            = 'E&xaminar'
'Admin_Install_Point/Back'              = '< &Atrás'
'Admin_Install_Point/Text36'            = '[_WiseDialogTitleFontDefault]Instalación administrativa'
'User_Exit/Text'                        = 'La instalación de [ProductName] se interrumpió antes de que el nuevo software quedara instalado por completo. Puede volver a ejecutar esta instalación en otro momento para completar la configuración de este software.'
'User_Exit/OK'                          = 'Aceptar'
'Exit_Dialog/PushButton'                = '< &Atrás'
'Exit_Dialog/PushButton1'               = 'Cancelar'
'Exit_Dialog/Text'                      = '{&DialogTitleFont}[ProductName] se ha instalado correctamente.'
'Exit_Dialog/Text1922232437'            = '{&DialogTitleFont}[ProductName] se ha desinstalado correctamente'
'Exit_Dialog/OK'                        = '&Finalizar'
'Setup_Dialog/ActionText'               = 'Espere mientras el Asistente de instalación de [ProductName] prepara Windows Installer.'
'Setup_Dialog/Text15'                   = '{&DialogTitleFont}Bienvenido al Asistente de instalación de [ProductName]'
'Setup_Dialog/Text16'                   = '{&DialogTitleFont}Reanudando el Asistente de instalación de [ProductName]'
'Setup_Dialog/Cancel'                   = 'Cancelar'
'License_Dialog/Next'                   = '&Siguiente >'
'License_Dialog/Cancel'                 = 'Cancelar'
'License_Dialog/Previous'               = '< &Atrás'
'License_Dialog/Reset'                  = '&Restablecer'
'License_Dialog/Text13'                 = '[_WiseDialogTitleFontDefault]Contrato de licencia'
'License_Dialog/Text14'                 = 'Debe aceptar el contrato de licencia para continuar.'
'Reinstall_Dialog/Complete'             = 'C&ompleta'
'Reinstall_Dialog/PushButton33'         = '&Siguiente >'
'Reinstall_Dialog/Text2'                = '[_WiseDialogTitleFontDefault]Seleccione el tipo de reinstalación que desea realizar.'
'Reinstall_Dialog/Cancel'               = 'Cancelar'
'Reinstall_Dialog/Minimal'              = '&Mínima'
'Reinstall_Dialog/Normal'               = '&Normal'
'Reinstall_Dialog/Previous'             = '< &Atrás'
'Reinstall_Dialog/CompleteText'         = 'Reemplazar todos los archivos, volver a escribir todas las entradas del Registro y corregir todos los accesos directos'
'Reinstall_Dialog/MinimalText'          = 'Reemplazar solo los archivos que faltan y volver a escribir las entradas del Registro del equipo'
'Reinstall_Dialog/NormalText'           = 'Reemplazar todos los archivos cuya versión no coincida exactamente y volver a escribir todas las entradas del Registro'
'Resume_Install_Dialog/Text'            = '¿Desea que el Asistente de instalación de [ProductName] continúe con la instalación?'
'Resume_Install_Dialog/Text2'           = '¿Desea que el Asistente de instalación de [ProductName] complete la instalación suspendida?'
'Resume_Install_Dialog/Cancel'          = 'Cancelar'
'Resume_Install_Dialog/InstallNow'      = '&Siguiente >'
'Resume_Install_Dialog/Text31'          = '{&DialogTitleFont}Reanudando el Asistente de instalación de [ProductName]'
'Start_Installation_Dialog/Text'        = '¿Desea que el Asistente de instalación de [ProductName] inicie la instalación?'
'Start_Installation_Dialog/Text2'       = '¿Desea que el Asistente de instalación de [ProductName] complete la instalación suspendida?'
'Start_Installation_Dialog/Text25'      = '[_WiseDialogTitleFontDefault]Iniciar instalación'
'Start_Installation_Dialog/Text26'      = '[_WiseDialogTitleFontDefault]Reanudar instalación'
'Start_Installation_Dialog/Cancel'      = 'Cancelar'
'Start_Installation_Dialog/InstallNow'  = '&Siguiente >'
'Start_Installation_Dialog/Previous'    = '< &Atrás'
'Start_Installation_Dialog/Text5'       = 'Pulse el botón Siguiente para iniciar la instalación o el botón Atrás para revisar el contrato de licencia.'
'Start_Installation_Dialog/Line5'       = 'Asistente de instalación de [ProductName]'
'Cancel/Text'                           = "La instalación no está completa. Si sale del programa de instalación ahora, el programa no se instalará.`r`n`r`nPuede ejecutar el programa de instalación más adelante para completar la instalación.`r`n`r`nPara continuar con la instalación, haga clic en Reanudar. Para salir del programa de instalación, haga clic en Salir del programa de instalación."
'Cancel/No'                             = '&Reanudar'
'Cancel/Yes'                            = '&Salir del programa de instalación'
'Browse/Cancel'                         = 'Cancelar'
'Browse/OK'                             = 'Aceptar'
'Browse/TailText'                       = 'Nombre de &carpeta:'
'Browse/ComboText'                      = '&Buscar en:'
'Browse/Up'                             = 'Subir'
'Browse/NewFolder'                      = 'Nuevo'
'Browse/List'                           = 'Lista de directorios'
'BrowseReadOnly/Cancel'                 = 'Cancelar'
'BrowseReadOnly/OK'                     = 'Aceptar'
'BrowseReadOnly/TailText'               = 'Nombre de &carpeta:'
'BrowseReadOnly/ComboText'              = '&Buscar en:'
'BrowseReadOnly/Up'                     = 'Subir'
'BrowseReadOnly/NewFolder'              = 'Nuevo'
'BrowseReadOnly/List'                   = 'Lista de directorios'
'Admin_Verify_Ready/Text'               = '¿Está todo listo para instalar?'
'Admin_Verify_Ready/Cancel'             = 'Cancelar'
'Admin_Verify_Ready/InstallNow'         = '&Siguiente >'
'Admin_Verify_Ready/Back'               = '< &Atrás'
'Admin_Verify_Ready/Text37'             = '[_WiseDialogTitleFontDefault]Instalación administrativa'
'AdminInstallBrowse/Cancel'             = 'Cancelar'
'AdminInstallBrowse/OK'                 = 'Aceptar'
'AdminInstallBrowse/TailText'           = 'Ruta &seleccionada:'
'AdminInstallBrowse/ComboText'          = '&Ubicación de la carpeta:'
'AdminInstallBrowse/Up'                 = 'Subir'
'AdminInstallBrowse/NewFolder'          = 'Nuevo'
'BadPIDDialog/OK'                       = 'Aceptar'
'BadPIDDialog/TextMessage'              = 'El código de clave de producto [PIDKEY] no es válido o ha caducado.'
'DiskCost/Text'                         = 'Las selecciones actuales requieren la siguiente cantidad de espacio en disco en los distintos volúmenes. Los volúmenes resaltados no tienen espacio suficiente para las características seleccionadas.'
'DiskCost/OK'                           = 'Aceptar'
'DiskCost/List'                         = 'Lista de volúmenes'
'ErrorDialog/Y'                         = '&Sí'
'ErrorDialog/A'                         = '&Anular'
'ErrorDialog/C'                         = '&Cancelar'
'ErrorDialog/I'                         = '&Omitir'
'ErrorDialog/N'                         = '&No'
'ErrorDialog/O'                         = '&Aceptar'
'ErrorDialog/R'                         = '&Reintentar'
'FilesInUse/Text'                       = 'Debe cerrar las &siguientes aplicaciones antes de continuar con la instalación:'
'FilesInUse/Retry'                      = '&Reintentar'
'FilesInUse/Exit'                       = '&Salir'
'FilesInUse/Ignore'                     = '&Omitir'
'Maintenance_Welcome_Dialog/Next'       = '&Siguiente >'
'Maintenance_Welcome_Dialog/Cancel'     = 'Cancelar'
'Maintenance_Welcome_Dialog/RemoveAll'  = '&Quitar todo'
'Maintenance_Welcome_Dialog/ReinstallMode' = '&Seleccionar modo de reinstalación'
'Maintenance_Welcome_Dialog/Previous'   = '< &Atrás'
'Maintenance_Welcome_Dialog/Reinstall'  = 'R&einstalar'
'Maintenance_Welcome_Dialog/Text1'      = 'Seleccione la operación que desea realizar con los botones siguientes.'
'Maintenance_Welcome_Dialog/Text40'     = '[_WiseDialogTitleFontDefault]Agregar o quitar aplicación'
'ReinstallMode/Cancel'                  = 'Cancelar'
'ReinstallMode/OK'                      = 'Aceptar'
'ReinstallMode/ReinstallRepair'         = '&Reparar todos los problemas de reinstalación detectados'
'ReinstallMode/ReinstallFilePresent'    = 'Reinstalar solo si el archivo &falta'
'ReinstallMode/ReinstallFileOlderVersion' = 'Reinstalar si falta el archivo o existe una versión &anterior'
'ReinstallMode/ReinstallFileEqualVersion' = 'Reinstalar si falta el archivo o existe una versión anterior o &igual'
'ReinstallMode/ReinstallFileExact'      = 'Reinstalar si el archivo existente tiene una versión &diferente'
'ReinstallMode/ReinstallFileChecksum'   = 'Reinstalar si el exe o la dll tiene una &suma de comprobación no válida'
'ReinstallMode/ReinstallFileForce'      = '&Forzar la reinstalación de todos los archivos, independientemente de su suma de comprobación o versión'
'ReinstallMode/ReinstallUserData'       = 'Comprobar que existen las entradas del Registro del &usuario necesarias'
'ReinstallMode/ReinstallMachineData'    = 'Comprobar que existen las entradas del Registro del &equipo local necesarias'
'ReinstallMode/ReinstallShortcut'       = '&Validar los accesos directos'
'Need_Admin_Dialog/OK'                  = 'Aceptar'
'Need_Admin_Dialog/Text52'              = 'Debe tener privilegios de administrador para instalar [ProductName].'
'OutOfDisk/Text'                        = 'No hay espacio suficiente en disco. Los volúmenes resaltados no tienen espacio disponible para las características seleccionadas.'
'OutOfDisk/OK'                          = 'Aceptar'
'OutOfDisk/List'                        = 'Lista de volúmenes'
'FoundOldVersion_Dialog/Next'           = '&Siguiente >'
'FoundOldVersion_Dialog/Cancel'         = 'Cancelar'
'FoundOldVersion_Dialog/Previous'       = '< &Atrás'
'FoundOldVersion_Dialog/Text13'         = '[_WiseDialogTitleFontDefault]Actualización del producto'
'FoundOldVersion_Dialog/Text5'          = 'Se ha detectado una versión anterior de [ProductName]. Si continúa con esta instalación, esa versión se actualizará a [ProductVersion]. Para continuar, haga clic en Siguiente. Para cancelar esta instalación, haga clic en Cancelar y salga de la instalación.'
'Verify_Remove_All/Text'                = "Se quitará [ProductName] del equipo. ¿Seguro que desea continuar?`r`n`r`nPulse el botón Siguiente para quitar la aplicación.`r`n`r`nPulse el botón Cancelar para salir del proceso de desinstalación."
'Verify_Remove_All/Cancel'              = 'Cancelar'
'Verify_Remove_All/RemoveNow'           = '&Siguiente >'
'Verify_Remove_All/Previous'            = '< &Atrás'
'Verify_Remove_All/Text32'              = '{&DialogTitleFont}Desinstalar [ProductName]'
'WaitForCosting/Cancel'                 = 'Cancelar'
'WaitForCosting/TextMessage'            = 'Espere mientras el programa de instalación termina de calcular los requisitos de espacio en disco...'
'Welcome_Dialog/Text'                   = '{&DialogTitleFont}Bienvenido al Asistente de instalación de [ProductName]'
'Welcome_Dialog/Next'                   = '&Siguiente >'
'Welcome_Dialog/Text2'                  = "ADVERTENCIA: este programa está protegido por las leyes de copyright y los tratados internacionales.`r`n`r`nLa reproducción o distribución no autorizada de este programa, o de cualquier parte de él, puede dar lugar a graves sanciones civiles y penales y se perseguirá en la medida máxima que permita la ley."
'Welcome_Dialog/Cancel'                 = 'Cancelar'
'Welcome_Dialog/Previous'               = '< &Atrás'
'Welcome_Dialog/Text1'                  = "Se recomienda encarecidamente que salga de todos los programas de Windows antes de ejecutar este programa de instalación.`r`n`r`nHaga clic en Cancelar para salir del programa de instalación y cerrar los programas que tenga en ejecución. Haga clic en Siguiente para continuar."
}

# Acción = @(Descripción, Plantilla)
$actionTextEs = @{
'Advertise'                = @('Publicando la aplicación','')
'AllocateRegistrySpace'    = @('Asignando espacio del Registro','Espacio libre: [1]')
'AppSearch'                = @('Buscando aplicaciones instaladas','Propiedad: [1], Firma: [2]')
'BindImage'                = @('Vinculando ejecutables','Archivo: [1]')
'CCPSearch'                = @('Buscando productos válidos','')
'CleanupFiles'             = @('Quitando archivos de datos de la aplicación','Archivo: [1]')
'CostFinalize'             = @('Calculando los requisitos de espacio','')
'CostInitialize'           = @('Calculando los requisitos de espacio','')
'CreateFolders'            = @('Creando carpetas','Carpeta: [1]')
'CreateShortcuts'          = @('Creando accesos directos','Acceso directo: [1]')
'DeleteServices'           = @('Eliminando servicios','Servicio: [1]')
'DuplicateFiles'           = @('Creando archivos duplicados','Archivo: [1],  Directorio: [9],  Tamaño: [6]')
'FileCost'                 = @('Calculando los requisitos de espacio','')
'FindRelatedProducts'      = @('Buscando aplicaciones relacionadas','Aplicación encontrada: [1]')
'GenerateScript'           = @('Generando operaciones de script para la acción:','[1]')
'InstallAdminPackage'      = @('Copiando archivos de instalación de red','Archivo: [1], Directorio: [9], Tamaño: [6]')
'InstallFiles'             = @('Copiando archivos nuevos','Archivo: [1],  Directorio: [9],  Tamaño: [6]')
'InstallODBC'              = @('Instalando componentes ODBC','')
'InstallServices'          = @('Instalando nuevos servicios','Servicio: [2]')
'InstallValidate'          = @('Validando la instalación','')
'LaunchConditions'         = @('Evaluando condiciones de inicio','')
'MigrateFeatureStates'     = @('Migrando estados de características de aplicaciones relacionadas','Aplicación: [1]')
'MoveFiles'                = @('Moviendo archivos','Archivo: [1],  Directorio: [9],  Tamaño: [6]')
'MsiPublishAssemblies'     = @('Publicando información de ensamblado','Contexto de la aplicación:[1], Nombre del ensamblado:[2]')
'MsiUnpublishAssemblies'   = @('Anulando la publicación de información de ensamblado','Contexto de la aplicación:[1], Nombre del ensamblado:[2]')
'PatchFiles'               = @('Aplicando revisiones a los archivos','Archivo: [1],  Directorio: [2],  Tamaño: [3]')
'ProcessComponents'        = @('Actualizando el registro de componentes','')
'PublishComponents'        = @('Publicando componentes calificados','Id. de componente: [1], Calificador: [2]')
'PublishFeatures'          = @('Publicando características del producto','Característica: [1]')
'PublishProduct'           = @('Publicando información del producto','')
'RegisterClassInfo'        = @('Registrando servidores de clase','Id. de clase: [1]')
'RegisterComPlus'          = @('Registrando aplicaciones y componentes COM+','AppId: [1]{{, Tipo de aplicación: [2], Usuarios: [3], RSN: [4]}}')
'RegisterExtensionInfo'    = @('Registrando servidores de extensión','Extensión: [1]')
'RegisterFonts'            = @('Registrando fuentes','Fuente: [1]')
'RegisterMIMEInfo'         = @('Registrando información MIME','Tipo de contenido MIME: [1], Extensión: [2]')
'RegisterProduct'          = @('Registrando el producto','[1]')
'RegisterProgIdInfo'       = @('Registrando identificadores de programa','ProgId: [1]')
'RegisterTypeLibraries'    = @('Registrando bibliotecas de tipos','LibID: [1]')
'RegisterUser'             = @('Registrando usuario','[1]')
'RemoveDuplicateFiles'     = @('Quitando archivos duplicados','Archivo: [1], Directorio: [9]')
'RemoveEnvironmentStrings' = @('Actualizando cadenas de entorno','Nombre: [1], Valor: [2], Acción [3]')
'RemoveExistingProducts'   = @('Quitando aplicaciones','Aplicación: [1], Línea de comandos: [2]')
'RemoveFiles'              = @('Quitando archivos','Archivo: [1], Directorio: [9]')
'RemoveFolders'            = @('Quitando carpetas','Carpeta: [1]')
'RemoveIniValues'          = @('Quitando entradas de archivos INI','Archivo: [1],  Sección: [2],  Clave: [3], Valor: [4]')
'RemoveODBC'               = @('Quitando componentes ODBC','')
'RemoveRegistryValues'     = @('Quitando valores del Registro del sistema','Clave: [1], Nombre: [2]')
'RemoveShortcuts'          = @('Quitando accesos directos','Acceso directo: [1]')
'RMCCPSearch'              = @('Buscando productos válidos','')
'Rollback'                 = @('Reversión de la acción:','[1]')
'RollbackCleanup'          = @('Quitando archivos de copia de seguridad','Archivo: [1]')
'SelfRegModules'           = @('Registrando módulos','Archivo: [1], Carpeta: [2]')
'SelfUnregModules'         = @('Anulando el registro de módulos','Archivo: [1], Carpeta: [2]')
'SetODBCFolders'           = @('Inicializando directorios ODBC','El controlador ODBC [1] fuerza la carpeta [2] a [3]')
'StartServices'            = @('Iniciando servicios','Servicio: [1]')
'StopServices'             = @('Deteniendo servicios','Servicio: [1]')
'UnmoveFiles'              = @('Quitando archivos movidos','Archivo: [1], Directorio: [9]')
'UnpackJarFiles'           = @('Descomprimiendo archivos JAR comprimidos','Archivo: [1]')
'UnpublishComponents'      = @('Anulando la publicación de componentes calificados','Id. de componente: [1], Calificador: [2]')
'UnpublishFeatures'        = @('Anulando la publicación de características del producto','Característica: [1]')
'UnpublishProduct'         = @('Anulando la publicación de la información del producto','')
'UnregisterClassInfo'      = @('Anulando el registro de servidores de clase','Id. de clase: [1]')
'UnregisterComPlus'        = @('Anulando el registro de aplicaciones y componentes COM+','AppId: [1]{{, Tipo de aplicación: [2]}}')
'UnregisterExtensionInfo'  = @('Anulando el registro de servidores de extensión','Extensión: [1]')
'UnregisterFonts'          = @('Anulando el registro de fuentes','Fuente: [1]')
'UnregisterMIMEInfo'       = @('Anulando el registro de información MIME','Tipo de contenido MIME: [1], Extensión: [2]')
'UnregisterProgIdInfo'     = @('Anulando el registro de identificadores de programa','ProgId: [1]')
'UnregisterTypeLibraries'  = @('Anulando el registro de bibliotecas de tipos','LibID: [1]')
'WriteEnvironmentStrings'  = @('Actualizando cadenas de entorno','Nombre: [1], Valor: [2], Acción [3]')
'WriteIniValues'           = @('Escribiendo valores de archivos INI','Archivo: [1],  Sección: [2],  Clave: [3], Valor: [4]')
'WriteRegistryValues'      = @('Escribiendo valores del Registro del sistema','Clave: [1], Nombre: [2], Valor: [3]')
}

$errorsEs = @{
1301 = "No se puede crear el archivo '[3]'. Ya existe un directorio con ese nombre. Cancele la instalación e intente instalarla en otra ubicación."
1303 = 'El programa de instalación no tiene privilegios suficientes para acceder a este directorio: [2]. La instalación no puede continuar. Inicie sesión como administrador o póngase en contacto con el administrador del sistema.'
1305 = 'Error al leer el archivo: [2]. Compruebe que el archivo existe y que puede acceder a él.'
1310 = 'Error al escribir en el archivo: [3]. {{ Error del sistema [2].}} Compruebe que tiene acceso a ese directorio.'
1311 = 'No se encuentra el archivo de origen{{(cab)}}: [2]. Compruebe que el archivo existe y que puede acceder a él.'
1603 = "El archivo [2][3] está en uso{ por el siguiente proceso: Nombre: [4], Id.: [5], Título de la ventana: '[6]'}. Cierre esa aplicación y vuelva a intentarlo."
1605 = "No hay espacio suficiente en disco: volumen '[2]'; espacio necesario: [3] KB; espacio disponible: [4] KB. Si la reversión está deshabilitada, hay espacio suficiente. Haga clic en 'Cancelar' para salir, en 'Reintentar' para comprobar de nuevo el espacio disponible o en 'Omitir' para continuar sin reversión."
1708 = 'Error en la operación de instalación.'
1709 = 'Producto: [2]: [3]'
1925 = 'No tiene privilegios suficientes para completar esta instalación para todos los usuarios del equipo. Inicie sesión como administrador y vuelva a intentar la instalación.'
2203 = 'Base de datos: [2]. No se puede abrir el archivo de base de datos. Error del sistema [3]'
2205 = 'Base de datos: [2]. La tabla no existe: [3]'
2229 = 'Base de datos: [2]. No es posible cargar la tabla [3] en la consulta SQL: [4]'
2502 = 'Se llamó a InstallFinalize cuando no hay ninguna instalación en curso.'
2503 = 'Se llamó a RunScript sin que estuviera marcado en curso.'
2755 = 'El servidor devolvió un error inesperado [2] al intentar instalar el paquete [3].'
2762 = 'No se puede escribir el registro de script. La transacción no se ha iniciado.'
2902 = 'La operación [2] se llamó fuera de secuencia.'
2903 = 'Falta el archivo [2]'
}

# ---------------------------------------------------------------------------
# 5. Generar el MSI traducido
# ---------------------------------------------------------------------------

Write-Step "Generando el MSI en español"
$wi = Get-Wi
$esMsi = Join-Path $OutDir ("{0}_es.msi" -f $msi.BaseName)
Copy-Item $msi.FullName $esMsi -Force
$db = $wi.OpenDatabase($esMsi, 1)

$nControl = 0
foreach ($k in $controlEs.Keys) {
    $p = $k.Split('/')
    $v = $db.OpenView("UPDATE ``Control`` SET ``Text``=? WHERE ``Dialog_``=? AND ``Control``=?")
    $r = $wi.CreateRecord(3)
    $r.StringData(1, 1) = $controlEs[$k]
    $r.StringData(2, 1) = $p[0]
    $r.StringData(3, 1) = $p[1]
    $v.Execute($r); $v.Close()
    $nControl++
}

$nAction = 0
foreach ($a in $actionTextEs.Keys) {
    $v = $db.OpenView("UPDATE ``ActionText`` SET ``Description``=?, ``Template``=? WHERE ``Action``=?")
    $r = $wi.CreateRecord(3)
    $r.StringData(1, 1) = $actionTextEs[$a][0]
    $r.StringData(2, 1) = $actionTextEs[$a][1]
    $r.StringData(3, 1) = $a
    $v.Execute($r); $v.Close()
    $nAction++
}

$nError = 0
foreach ($id in $errorsEs.Keys) {
    $v = $db.OpenView("UPDATE ``Error`` SET ``Message``=? WHERE ``Error``=?")
    $r = $wi.CreateRecord(2)
    $r.StringData(1, 1) = $errorsEs[$id]
    $r.IntegerData(2, 1) = [int]$id
    $v.Execute($r); $v.Close()
    $nError++
}

# Título de todas las ventanas: "[ProductName] Setup" -> "[ProductName] Instalación"
$v = $db.OpenView("UPDATE ``Property`` SET ``Value``='Instalación' WHERE ``Property``='_WiseDialogSuffix'")
try { $v.Execute(); $v.Close() } catch { Write-Warn2 'No se pudo ajustar _WiseDialogSuffix' }

Write-Ok "$nControl textos de control, $nAction acciones de progreso, $nError mensajes de error"

# ---------------------------------------------------------------------------
# 6. Verificación (antes del Commit: Windows Installer bloquea el archivo
#    mientras el objeto Database siga vivo, así que se comprueba en caliente)
# ---------------------------------------------------------------------------

Write-Step "Verificando el MSI generado"
# Se leen tablas completas: en modo transacción las consultas con WHERE sobre
# columnas de texto no siempre devuelven las filas modificadas.
$ctrl = @(Invoke-MsiQuery -Database $db -Sql "SELECT * FROM ``Control``")
if ($ctrl.Count -gt 0) {
    Write-Ok "La tabla Control se lee correctamente ($($ctrl.Count) controles) -> el error 2229 no puede reproducirse"
} else {
    Write-Warn2 "La tabla Control no devolvió filas. Revisa el MSI generado."
}

$welcome = $ctrl | Where-Object { $_ -like 'Welcome_Dialog|Next|*' } | Select-Object -First 1
if ($welcome) { Write-Host ("   Boton de bienvenida : " + ($welcome -split '\|')[9]) }

$okBtn = $ctrl | Where-Object { $_ -like 'ErrorDialog|O|*' } | Select-Object -First 1
if ($okBtn) { Write-Host ("   Boton del error     : " + ($okBtn -split '\|')[9]) }

$act = @(Invoke-MsiQuery -Database $db -Sql "SELECT * FROM ``ActionText``")
$install = $act | Where-Object { $_ -like 'InstallFiles|*' } | Select-Object -First 1
if ($install) {
    $p = $install -split '\|'
    Write-Host ("   Progreso            : " + $p[1] + " | " + $p[2])
}

$errs = @(Invoke-MsiQuery -Database $db -Sql "SELECT * FROM ``Error``")
$e2229 = $errs | Where-Object { $_ -like '2229|*' } | Select-Object -First 1
if ($e2229) { Write-Host ("   Mensaje 2229        : " + ($e2229 -split '\|')[1]) }

$props = @(Invoke-MsiQuery -Database $db -Sql "SELECT * FROM ``Property``")
$suffix = $props | Where-Object { $_ -like '_WiseDialogSuffix|*' } | Select-Object -First 1
if ($suffix) { Write-Host ("   Titulo de ventanas  : " + ($suffix -split '\|')[1]) }

$db.Commit()

# ---------------------------------------------------------------------------
# 7. Transform opcional
# ---------------------------------------------------------------------------

if ($MakeTransform) {
    Write-Step "Generando transformación $Language.mst"
    $mstOut = Join-Path $OutDir "$Language.mst"
    if (Test-Path $mstOut) { Remove-Item $mstOut -Force }

    $dbBase = $wi.OpenDatabase($msi.FullName, 1)
    $null = $db.GenerateTransform($dbBase, $mstOut)
    $null = [Runtime.InteropServices.Marshal]::ReleaseComObject($dbBase)

    if (Test-Path $mstOut) {
        Write-Ok "$mstOut ($([math]::Round((Get-Item $mstOut).Length/1KB)) KB)"
        Write-Warn2 "Windows Installer rechaza los transforms generados por API si setup.exe los valida (error 1624/2758)."
        Write-Host  "   Recomendado: instalar directamente el MSI traducido."
    }
}

$null = [Runtime.InteropServices.Marshal]::ReleaseComObject($db)
$null = [Runtime.InteropServices.Marshal]::ReleaseComObject($wi)

# ---------------------------------------------------------------------------
# 8. Resumen
# ---------------------------------------------------------------------------

Write-Step "Listo"
Write-Host "   MSI en español : $esMsi"
Write-Host ""
Write-Host "   Cierra Visual Studio y ejecuta (PowerShell como administrador):"
Write-Host ""
Write-Host "   msiexec /i `"$esMsi`" /L*v `"$(Join-Path $OutDir 'install-es.log')`"" -ForegroundColor White
Write-Host ""
