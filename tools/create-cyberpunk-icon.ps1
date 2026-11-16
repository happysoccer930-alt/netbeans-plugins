# Crea el icono .ico de NetBeans Cyberpunk a partir de assets\netbeans-cyberpunk.png
# y un acceso directo en el Escritorio. No modifica el .exe.
#
# Uso: powershell -ExecutionPolicy Bypass -File tools\create-cyberpunk-icon.ps1

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$repo = Split-Path -Parent $PSScriptRoot
$source = Join-Path $repo 'assets\netbeans-cyberpunk.png'
if (-not (Test-Path -LiteralPath $source)) { throw "Falta $source" }

$outDir = Join-Path $repo 'assets'
$sizes = @(256, 128, 64, 48, 32, 16)
$image = [System.Drawing.Image]::FromFile($source)
try {
    # Recorta el cubo central (sin los textos laterales).
    $crop = New-Object System.Drawing.Rectangle 260, 20, 720, 720
    $square = New-Object System.Drawing.Bitmap 720, 720
    $g = [System.Drawing.Graphics]::FromImage($square)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.DrawImage($image, (New-Object System.Drawing.Rectangle 0, 0, 720, 720), $crop, [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose()
} finally {
    $image.Dispose()
}

$pngs = @()
foreach ($size in $sizes) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.DrawImage($square, 0, 0, $size, $size)
    $g.Dispose()
    $png = Join-Path $outDir ("netbeans-cyberpunk-{0}.png" -f $size)
    $bmp.Save($png, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    $pngs += [pscustomobject]@{ Size = $size; Path = $png }
}
$square.Dispose()

# ICO con entradas PNG (Windows Vista+).
$ico = Join-Path $outDir 'netbeans-cyberpunk.ico'
$fs = [System.IO.File]::Create($ico)
$bw = New-Object System.IO.BinaryWriter($fs)
try {
    $bw.Write([UInt16]0)             # reserved
    $bw.Write([UInt16]1)             # type icon
    $bw.Write([UInt16]$pngs.Count)
    $offset = 6 + 16 * $pngs.Count
    foreach ($item in $pngs) {
        $data = [System.IO.File]::ReadAllBytes($item.Path)
        $dim = if ($item.Size -ge 256) { 0 } else { $item.Size }
        $bw.Write([Byte]$dim)        # width
        $bw.Write([Byte]$dim)        # height
        $bw.Write([Byte]0)           # colors
        $bw.Write([Byte]0)           # reserved
        $bw.Write([UInt16]1)         # planes
        $bw.Write([UInt16]32)        # bit count
        $bw.Write([UInt32]$data.Length)
        $bw.Write([UInt32]$offset)
        $offset += $data.Length
    }
    foreach ($item in $pngs) {
        $bw.Write([System.IO.File]::ReadAllBytes($item.Path))
    }
} finally {
    $bw.Dispose()
    $fs.Dispose()
}

# Acceso directo en el Escritorio con el icono.
$desktop = [Environment]::GetFolderPath('Desktop')
$linkPath = Join-Path $desktop 'NetBeans Cyberpunk.lnk'
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($linkPath)
$shortcut.TargetPath = 'C:\Program Files\netbeans\bin\netbeans64.exe'
$shortcut.Arguments = '--nosplash'
$shortcut.WorkingDirectory = 'C:\Program Files\netbeans'
$shortcut.IconLocation = "$ico,0"
$shortcut.Description = 'Apache NetBeans con estilo Cyberpunk'
$shortcut.Save()

Write-Output "PNG: $source"
Write-Output "ICO: $ico"
Write-Output "Acceso directo: $linkPath"
Get-ChildItem $outDir -Filter 'netbeans-cyberpunk-*' | Select-Object Name,Length | Format-Table -AutoSize
