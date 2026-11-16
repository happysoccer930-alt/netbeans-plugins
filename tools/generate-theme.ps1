# Genera los ficheros de colores de Soft Dark a partir de la paleta FlatLafDark
# de Apache NetBeans (Apache-2.0), aplicando una reduccion de contraste y
# saturacion. Los ficheros base / Java / highlights estan escritos a mano y
# este script NO los toca.
#
# Uso:
#   powershell -ExecutionPolicy Bypass -File tools\generate-theme.ps1

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$repo      = Split-Path -Parent $PSScriptRoot
$jar       = "C:\Program Files\netbeans\platform\modules\org-netbeans-swing-laf-flatlaf.jar"
$resDir    = Join-Path $repo "modules\softdark-theme\src\main\resources\org\deepsetk\softdark"
$colorDir  = Join-Path $resDir "fontscolors"
$tmp       = Join-Path $env:TEMP "softdark-src"

if (-not (Test-Path $jar)) { throw "No encuentro $jar" }
New-Item -ItemType Directory -Force -Path $colorDir, $tmp | Out-Null

# --- 1. Extraer los XML de referencia -------------------------------------
$zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
$zip.Entries | Where-Object { $_.FullName -match 'fontscolors/FlatLafDark-' } |
    ForEach-Object { [System.IO.Compression.ZipFileExtensions]::ExtractToFile($_, (Join-Path $tmp $_.Name), $true) }
$zip.Dispose()

# --- 2. Mapa de colores ---------------------------------------------------
$map = @{
  'ff283c26'='ff1e2521'; 'ff372b2b'='ff241c1c'; 'ff4b1919'='ff2d1d1d';
  'ff232525'='ff131518'; 'ff2b2b2b'='ff15171b'; 'ff303232'='ff191c20';
  'ff323232'='ff1a1d21'; 'ff3a3a3a'='ff212429'; 'ff3a2323'='ff1f1818';
  'ff313335'='ff111316'; 'ff2b2b41'='ff1c1f2b'; 'ff214283'='ff2a3a4d';
  'ff32593d'='ff2c4436'; 'ff3b514d'='ff2b3a37'; 'ff364135'='ff27312b';
  'ff52503a'='ff3b3729'; 'ff1e501c'='ff1f3a1f'; 'ff143c00'='ff1a2d14';
  'ff005000'='ff1f3d1f'; 'ff1c501c'='ff1d3820'; 'ff707000'='ff3a3a22';
  'ff813438'='ff3f2429'; 'ffe0e8f1'='ff2c3742'; 'ffffffec9'='ff2f3326';
  'ffe3f2e1'='ff2c3a2e'; 'ffd1ffbc'='ff2a3a26'; 'ffffb442'='ff5a4a28';
  'ffe0e0e0'='ffc9d0d9'; 'ffd5756c'='ffc08a83'; 'ff6d3206'='ff4a2c1e';
  'ff796977'='ff5f5561'; 'ff4757b7'='ff6f7fb0'; 'fff8bb00'='ffbfa969';
  'ffff9300'='ffc19058'; 'ffff7700'='ffba8455'; 'ffc67300'='ffb08a55';
  'ffce7b00'='ffb08a55'; 'ffc0c000'='ffb0a971'; 'ff7c6200'='ff6f5c33';
  'ff2c9c25'='ff5f9153'; 'ff339900'='ff5f9153'; 'ff009900'='ff5f9153';
  'ffd1243b'='ff9a5560'; 'ff9e2927'='ffb05c5c';
  'ffa9b7c6'='ffb8bfc9'; 'ffbbbbbb'='ffb0b7c0'; 'ffbababa'='ffaeb5be';
  'ffc3c3c3'='ffb4bbc4'; 'ff999999'='ff828a93'; 'ff969696'='ff80888f';
  'ff888888'='ff6f767e'; 'ff8c8c8c'='ff737a82'; 'ff666666'='ff5c636b';
  'ff5a5a5a'='ff4d545c'; 'ff555555'='ff484f57'; 'ff505050'='ff444b53';
  'bedded'='ffaebbc4'; 'ffcc7832'='ffb48eae'; 'ff9876aa'='ffa892b8';
  'ff6897bb'='ffc99f7a'; 'ff6a8759'='ff9db38a'; 'ffa5c261'='ffa3bd8a';
  'ffffc66d'='ffc7b184'; 'ffe8bf6a'='ffc0a97e'; 'ff9999ff'='ff9b9fc9';
  'ff287bde'='ff7ba6c9'; 'ff508098'='ff7d97a4'; 'ff628fb5'='ff8ba3b5';
  'ff6b8eb2'='ff8aa4b8'; 'ff6d9cbe'='ff8fa8bf'; 'ff8a653b'='ffa88f6f';
  'ff00a4a4'='ff69a5a5'; 'ffcc6932'='ffb98a72'; 'ffffef28'='ffe0cf8a';
  'ffff8080'='ffc98a8a'; 'ffffa0a0'='ffc98a8a'; 'ffcc0099'='ffb0769f';
  'ff996600'='ffa37a45'; 'ff800080'='ff8f6f96'; 'ff900090'='ff9a779f';
  'ff760076'='ff8a6a90'; 'ff780078'='ff8b6b91'; 'ff990099'='ff9a779f';
  'ff740074'='ff8a6a90'; 'ff9933cc'='ff9a7fb5'; 'ff9f0d79'='ff9a6f8f';
}

function Convert-Hex([string]$hex) {
    $k = $hex.ToLower()
    if ($map.ContainsKey($k)) { return $map[$k] }
    # Sin entrada explicita: pasar a HSL, bajar saturacion y limitar el brillo.
    $core = if ($k.Length -eq 8) { $k.Substring(2) } else { $k }
    $r = [Convert]::ToInt32($core.Substring(0,2),16) / 255.0
    $g = [Convert]::ToInt32($core.Substring(2,2),16) / 255.0
    $b = [Convert]::ToInt32($core.Substring(4,2),16) / 255.0
    $max = [Math]::Max($r,[Math]::Max($g,$b)); $min = [Math]::Min($r,[Math]::Min($g,$b))
    $l = ($max + $min) / 2.0
    if ($max -eq $min) { $h = 0.0; $s = 0.0 }
    else {
        $d = $max - $min
        $s = if ($l -gt 0.5) { $d / (2.0 - $max - $min) } else { $d / ($max + $min) }
        if ($max -eq $r)      { $h = (($g - $b) / $d) + $(if ($g -lt $b) { 6 } else { 0 }) }
        elseif ($max -eq $g)  { $h = (($b - $r) / $d) + 2 }
        else                  { $h = (($r - $g) / $d) + 4 }
        $h = $h / 6.0
    }
    if ($l -lt 0.40) {
        # color de fondo: mas oscuro y algo mas calido
        $l = $l * 0.62
        $s = [Math]::Min($s, 0.35)
    } else {
        # color de texto: nunca por encima de ~72% de brillo, saturacion suave
        $l = [Math]::Min($l, 0.72)
        $s = [Math]::Min($s, 0.45)
    }
    # HSL -> RGB
    if ($s -eq 0) { $nr = $l; $ng = $l; $nb = $l }
    else {
        $q = if ($l -lt 0.5) { $l * (1 + $s) } else { $l + $s - $l * $s }
        $p = 2 * $l - $q
        function Hue2Rgb($p,$q,$t) {
            if ($t -lt 0) { $t += 1 }; if ($t -gt 1) { $t -= 1 }
            if ($t -lt 1/6) { return $p + ($q - $p) * 6 * $t }
            if ($t -lt 1/2) { return $q }
            if ($t -lt 2/3) { return $p + ($q - $p) * (2/3 - $t) * 6 }
            return $p
        }
        $nr = Hue2Rgb $p $q ($h + 1/3); $ng = Hue2Rgb $p $q $h; $nb = Hue2Rgb $p $q ($h - 1/3)
    }
    return ('ff{0:x2}{1:x2}{2:x2}' -f [int][Math]::Round($nr*255),[int][Math]::Round($ng*255),[int][Math]::Round($nb*255))
}

function Convert-Xml([string]$text) {
    $named = @{ black='ffe4e9ef'; white='ffe4e9ef'; gray='ff6f7883'; darkgray='ff343a42'; red='ffc98a8a' }
    $text = [regex]::Replace($text, '(?i)\b(foreColor|bgColor|fore|bg|underline|waveUnderlined|strikeThrough)="(black|white|gray|darkGray|red)"', {
        param($m)
        $a = $m.Groups[1].Value
        if ($a -ieq 'fore') { $a = 'foreColor' } elseif ($a -ieq 'bg') { $a = 'bgColor' }
        "$a=`"$($named[$m.Groups[2].Value.ToLower()])`""
    })
    $text = [regex]::Replace($text, '(?i)\b(foreColor|bgColor|fore|bg|underline|waveUnderlined|strikeThrough)="([0-9a-f]{6,8})"', {
        param($m)
        $a = $m.Groups[1].Value
        if ($a -ieq 'fore') { $a = 'foreColor' } elseif ($a -ieq 'bg') { $a = 'bgColor' }
        "$a=`"$(Convert-Hex $m.Groups[2].Value)`""
    })
    return $text
}

# --- 3. Generar los ficheros de lenguaje y anotaciones --------------------
$keep = @('FlatLafDark-tokenColorings.xml','FlatLafDark-Java-tokenColorings.xml','FlatLafDark-highlights.xml')
$generated = @()
Get-ChildItem $tmp -Filter 'FlatLafDark-*.xml' | Where-Object { $keep -notcontains $_.Name } | ForEach-Object {
    $name = $_.Name -replace '^FlatLafDark-', 'SoftDark-'
    $out  = Join-Path $colorDir $name
    $xml  = Convert-Xml (Get-Content $_.FullName -Raw)
    $xml  = $xml -replace 'FlatLafDark', 'SoftDark'
    Set-Content -Path $out -Value $xml -Encoding UTF8 -NoNewline
    $generated += $name
}

# --- 4. Generar layer.xml -------------------------------------------------
$langs = [ordered]@{
  'application/xml-dtd'            = 'DTD'
  'text/css'                       = 'CSS'
  'text/html'                      = 'HTML'
  'text/javascript'                = 'JavaScript'
  'text/x-apache-conf'             = 'ApacheConf'
  'text/x-diff'                    = 'Diff'
  'text/x-el'                      = 'EL'
  'text/x-ini'                     = 'INI'
  'text/x-java'                    = 'Java'
  'text/x-json'                    = 'JSON'
  'text/x-jsp'                     = 'JSP'
  'text/x-php5'                    = 'PHP'
  'text/x-properties'              = 'Properties'
  'text/x-sql'                     = 'SQL'
  'text/x-tag'                     = 'Tag'
  'text/x-tpl'                     = 'TPL'
  'text/xhtml'                     = 'XHTML'
  'text/xml'                       = 'XML'
  'text/xml-external-parsed-entity'= 'XMLexternalentity'
  'text/x-yaml'                    = 'YAML'
  'text/x-markdown'                = 'Markdown'
}

$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine('<?xml version="1.0" encoding="UTF-8"?>')
[void]$sb.AppendLine('<!DOCTYPE filesystem PUBLIC "-//NetBeans//DTD Filesystem 1.2//EN" "https://netbeans.apache.org/dtds/filesystem-1_2.dtd">')
[void]$sb.AppendLine('<!-- Generado por tools/generate-theme.ps1 -->')
[void]$sb.AppendLine('<filesystem>')
[void]$sb.AppendLine('    <folder name="Editors">')
[void]$sb.AppendLine('        <attr name="currentFontColorProfile" stringvalue="SoftDark"/>')
[void]$sb.AppendLine('        <folder name="FontsColors">')
[void]$sb.AppendLine('            <folder name="SoftDark">')
[void]$sb.AppendLine('                <folder name="Defaults">')
[void]$sb.AppendLine('                    <file name="org-deepsetk-softdark-annotations.xml" url="fontscolors/SoftDark-annotations.xml">')
[void]$sb.AppendLine('                        <attr name="nbeditor-settings-ColoringType" stringvalue="annotation"/>')
[void]$sb.AppendLine('                    </file>')
[void]$sb.AppendLine('                    <file name="org-deepsetk-softdark-highlights.xml" url="fontscolors/SoftDark-highlights.xml">')
[void]$sb.AppendLine('                        <attr name="nbeditor-settings-ColoringType" stringvalue="highlight"/>')
[void]$sb.AppendLine('                    </file>')
[void]$sb.AppendLine('                    <file name="org-deepsetk-softdark-token.xml" url="fontscolors/SoftDark-tokenColorings.xml">')
[void]$sb.AppendLine('                        <attr name="nbeditor-settings-ColoringType" stringvalue="token"/>')
[void]$sb.AppendLine('                    </file>')
[void]$sb.AppendLine('                </folder>')
[void]$sb.AppendLine('            </folder>')
[void]$sb.AppendLine('        </folder>')

$groups = [ordered]@{}
foreach ($mime in $langs.Keys) {
    $parts = $mime -split '/'
    if (-not $groups.Contains($parts[0])) {
        $groups[$parts[0]] = New-Object System.Collections.ArrayList
    }
    [void]$groups[$parts[0]].Add([pscustomobject]@{ Sub = $parts[1]; Suffix = $langs[$mime] })
}
foreach ($root in $groups.Keys) {
    [void]$sb.AppendLine('        <folder name="' + $root + '">')
    foreach ($entry in $groups[$root]) {
        [void]$sb.AppendLine('            <folder name="' + $entry.Sub + '">')
        [void]$sb.AppendLine('                <folder name="FontsColors">')
        [void]$sb.AppendLine('                    <folder name="SoftDark">')
        [void]$sb.AppendLine('                        <folder name="Defaults">')
        [void]$sb.AppendLine('                            <file name="org-deepsetk-softdark-' + $entry.Suffix.ToLower() + '-colorings.xml" url="fontscolors/SoftDark-' + $entry.Suffix + '-tokenColorings.xml"/>')
        [void]$sb.AppendLine('                        </folder>')
        [void]$sb.AppendLine('                    </folder>')
        [void]$sb.AppendLine('                </folder>')
        [void]$sb.AppendLine('            </folder>')
    }
    [void]$sb.AppendLine('        </folder>')
}
[void]$sb.AppendLine('    </folder>')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('    <folder name="Actions">')
[void]$sb.AppendLine('        <folder name="Tools">')
[void]$sb.AppendLine('            <file name="org-deepsetk-softdark-apply.instance">')
[void]$sb.AppendLine('                <attr name="instanceClass" stringvalue="org.deepsetk.softdark.ApplyThemeAction"/>')
[void]$sb.AppendLine('            </file>')
[void]$sb.AppendLine('        </folder>')
[void]$sb.AppendLine('    </folder>')
[void]$sb.AppendLine('    <folder name="Menu">')
[void]$sb.AppendLine('        <folder name="Tools">')
[void]$sb.AppendLine('            <file name="org-deepsetk-softdark-apply.shadow">')
[void]$sb.AppendLine('                <attr name="originalFile" stringvalue="Actions/Tools/org-deepsetk-softdark-apply.instance"/>')
[void]$sb.AppendLine('                <attr name="position" intvalue="1510"/>')
[void]$sb.AppendLine('            </file>')
[void]$sb.AppendLine('        </folder>')
[void]$sb.AppendLine('    </folder>')
[void]$sb.AppendLine('</filesystem>')

Set-Content -Path (Join-Path $resDir 'layer.xml') -Value $sb.ToString() -Encoding UTF8 -NoNewline
$generated | Sort-Object | ForEach-Object { "  generado: $_" }
"layer.xml generado"
