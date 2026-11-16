# Genera el perfil de editor Cyberpunk a partir de las paletas Soft Dark.
# Transforma fondos a negro azulado y textos/acentos a azul electrico/cian.
#
# Uso: powershell -ExecutionPolicy Bypass -File tools\generate-cyberpunk.ps1

$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot
$src  = Join-Path $repo 'modules\softdark-theme\src\main\resources\org\deepsetk\softdark'
$dst  = Join-Path $repo 'modules\cyberpunk-theme\src\main\resources\org\deepsetk\cyberpunk'
$srcColors = Join-Path $src 'fontscolors'
$dstColors = Join-Path $dst 'fontscolors'
New-Item -ItemType Directory -Force -Path $dstColors | Out-Null

function Get-Parts([string]$hex) {
    $h = $hex.TrimStart('#')
    if ($h.Length -eq 8) {
        return @(
            [Convert]::ToInt32($h.Substring(0, 2), 16),
            [Convert]::ToInt32($h.Substring(2, 2), 16),
            [Convert]::ToInt32($h.Substring(4, 2), 16),
            [Convert]::ToInt32($h.Substring(6, 2), 16)
        )
    }
    if ($h.Length -eq 6) {
        return @(
            255,
            [Convert]::ToInt32($h.Substring(0, 2), 16),
            [Convert]::ToInt32($h.Substring(2, 2), 16),
            [Convert]::ToInt32($h.Substring(4, 2), 16)
        )
    }
    return @(255, 0, 0, 0)
}

function To-Hex($a, $r, $g, $b) {
    $r = [Math]::Max(0, [Math]::Min(255, [Math]::Round($r)))
    $g = [Math]::Max(0, [Math]::Min(255, [Math]::Round($g)))
    $b = [Math]::Max(0, [Math]::Min(255, [Math]::Round($b)))
    return ("{0:x2}{1:x2}{2:x2}{3:x2}" -f [int]$a, [int]$r, [int]$g, [int]$b)
}

function Get-Hsl($r, $g, $b) {
    $r = $r / 255.0; $g = $g / 255.0; $b = $b / 255.0
    $max = [Math]::Max($r, [Math]::Max($g, $b))
    $min = [Math]::Min($r, [Math]::Min($g, $b))
    $l = ($max + $min) / 2.0
    $d = $max - $min
    $h = 0.0
    $s = 0.0
    if ($d -gt 0.0001) {
        if ($l -gt 0.5) {
            $s = $d / (2.0 - $max - $min)
        } else {
            $s = $d / ($max + $min)
        }
        if ($max -eq $r) {
            $h = (($g - $b) / $d) % 6.0
        } elseif ($max -eq $g) {
            $h = (($b - $r) / $d) + 2.0
        } else {
            $h = (($r - $g) / $d) + 4.0
        }
        $h = $h * 60.0
        if ($h -lt 0) { $h += 360.0 }
    }
    return @($h, $s, $l)
}

function From-Hsl($a, $h, $s, $l) {
    $h = $h / 360.0
    $q = if ($l -lt 0.5) { $l * (1.0 + $s) } else { $l + $s - ($l * $s) }
    $p = 2.0 * $l - $q
    $r = $p; $g = $p; $b = $p
    if ($s -gt 0.0001) {
        $r = Get-Hue $p $q ($h + 1.0 / 3.0)
        $g = Get-Hue $p $q $h
        $b = Get-Hue $p $q ($h - 1.0 / 3.0)
    }
    return To-Hex $a ($r * 255) ($g * 255) ($b * 255)
}

function Get-Hue($p, $q, $t) {
    if ($t -lt 0) { $t += 1.0 }
    if ($t -gt 1) { $t -= 1.0 }
    if ($t -lt 1.0 / 6.0) { return $p + ($q - $p) * 6.0 * $t }
    if ($t -lt 1.0 / 2.0) { return $q }
    if ($t -lt 2.0 / 3.0) { return $p + ($q - $p) * (2.0 / 3.0 - $t) * 6.0 }
    return $p
}

function Convert-Foreground([string]$hex) {
    $parts = Get-Parts $hex
    $a = $parts[0]; $r = $parts[1]; $g = $parts[2]; $b = $parts[3]
    $hsl = Get-Hsl $r $g $b
    $h = $hsl[0]; $s = $hsl[1]; $l = $hsl[2]
    if ($l -lt 0.18) {
        return To-Hex $a (24 + $l * 90) (42 + $l * 120) (72 + $l * 150)
    }
    if ($h -ge 70 -and $h -le 180) {
        $hue = 190
    } elseif ($h -lt 70) {
        $hue = 218
    } else {
        $hue = 208
    }
    $sat = [Math]::Min(0.72, [Math]::Max(0.38, $s * 0.85 + 0.18))
    $light = [Math]::Min(0.86, [Math]::Max(0.60, $l * 0.70 + 0.28))
    return From-Hsl $a $hue $sat $light
}

function Convert-Background([string]$hex) {
    $parts = Get-Parts $hex
    $a = $parts[0]; $r = $parts[1]; $g = $parts[2]; $b = $parts[3]
    $hsl = Get-Hsl $r $g $b
    $l = $hsl[2]
    if ($l -lt 0.16) {
        return To-Hex $a (5 + $l * 18) (10 + $l * 24) (18 + $l * 40)
    }
    $k = [Math]::Min(1.0, ($l - 0.16) / 0.18)
    return To-Hex $a (10 + $k * 20) (26 + $k * 44) (50 + $k * 76)
}

function Convert-Accent([string]$hex) {
    $parts = Get-Parts $hex
    $a = $parts[0]; $r = $parts[1]; $g = $parts[2]; $b = $parts[3]
    $hsl = Get-Hsl $r $g $b
    $h = $hsl[0]
    if ($h -lt 55 -or $h -gt 340) {
        return From-Hsl $a 355 0.72 0.66
    }
    return From-Hsl $a 195 0.75 0.68
}

$foreground = @{
    'default' = 'ffd7e3f4'; 'identifier' = 'ffd7e3f4'; 'comment' = 'ff5f7fa8'
    'keyword' = 'ff4ea1ff'; 'string' = 'ff22d3ee'; 'char' = 'ff22d3ee'
    'number' = 'ff8ab4f8'; 'operator' = 'ff9cc7ff'; 'separator' = 'ff9cc7ff'
    'method' = 'ff7fb3f0'; 'field' = 'ffb4c7e7'; 'markup-element' = 'ff4ea1ff'
    'markup-attribute' = 'ff9cc7ff'; 'markup-attribute-value' = 'ff22d3ee'
    'url' = 'ff22d3ee'; 'whitespace' = 'ff1e2a44'; 'line-number' = 'ff4f6f96'
    'caret-color-insert-mode' = 'ff00e5ff'; 'caret-color-overwrite-mode' = 'ff00e5ff'
    'code-folding' = 'ff5f7fa8'; 'code-folding-bar' = 'ff2a4a6a'
    'code-folding-border' = 'ff2a4a6a'; 'indent-guide-lines' = 'ff142840'
    'status-bar-bold' = 'ff22d3ee'
}
$background = @{
    'default' = 'ff070b12'; 'selection' = 'ff123a66'; 'highlight-search' = 'ff14406a'
    'inc-search' = 'ff1b4b7a'; 'mark-occurrences' = 'ff0e2a4a'
    'highlight-caret-row' = 'ff0a1526'; 'line-number' = 'ff050810'
    'block-search' = 'ff1b4b7a'; 'code-folding' = 'ff0b1424'
    'guarded' = 'ff0a1220'; 'readonly-files' = 'ff0a1220'
    'nbeditor-bracesMatching-match' = 'ff123a66'
    'nbeditor-bracesMatching-match-multichar' = 'ff123a66'
    'nbeditor-bracesMatching-mismatch' = 'ff4a1420'
    'nbeditor-bracesMatching-mismatch-multichar' = 'ff4a1420'
    'warning' = 'ff2a1e10'
}
$accent = @{
    'error' = 'ffff5c7a'; 'warning' = 'ffffb86c'; 'url' = 'ff22d3ee'
    'hyperlinks' = 'ff22d3ee'; 'nbeditor-bracesMatching-mismatch' = 'ffff5c7a'
    'nbeditor-bracesMatching-mismatch-multichar' = 'ffff5c7a'
}

foreach ($file in Get-ChildItem -Path $srcColors -Filter 'SoftDark-*.xml') {
    $text = Get-Content -LiteralPath $file.FullName -Raw
    $text = [regex]::Replace($text, '<fontcolor\b[^>]*>', {
        param($tagMatch)
        $tag = $tagMatch.Value
        $nameMatch = [regex]::Match($tag, 'name="([^"]+)"')
        $name = if ($nameMatch.Success) { $nameMatch.Groups[1].Value.ToLowerInvariant() } else { '' }

        $fore = [regex]::Match($tag, '(?i)foreColor="([0-9a-fA-F]{6,8})"')
        if ($fore.Success) {
            $value = if ($foreground.ContainsKey($name)) { $foreground[$name] } else { Convert-Foreground $fore.Groups[1].Value }
            $tag = $tag.Replace($fore.Value, 'foreColor="' + $value + '"')
        }
        $bg = [regex]::Match($tag, '(?i)bgColor="([0-9a-fA-F]{6,8})"')
        if ($bg.Success) {
            $value = if ($background.ContainsKey($name)) { $background[$name] } else { Convert-Background $bg.Groups[1].Value }
            $tag = $tag.Replace($bg.Value, 'bgColor="' + $value + '"')
        }
        foreach ($attr in @('underline', 'waveUnderlined', 'strikeThrough')) {
            $m = [regex]::Match($tag, '(?i)' + $attr + '="([0-9a-fA-F]{6,8})"')
            if ($m.Success) {
                $value = if ($accent.ContainsKey($name)) { $accent[$name] } else { Convert-Accent $m.Groups[1].Value }
                $tag = $tag.Replace($m.Value, $attr + '="' + $value + '"')
            }
        }
        return $tag
    })
    $text = $text.Replace('Soft Dark', 'Cyberpunk')
    $text = $text.Replace('SoftDark', 'Cyberpunk')
    $name = $file.Name.Replace('SoftDark-', 'Cyberpunk-')
    Set-Content -LiteralPath (Join-Path $dstColors $name) -Value $text -Encoding UTF8 -NoNewline
    Write-Output "  generado: $name"
}

# Layer: copia la seccion Editors del layer Soft Dark y renombra el perfil.
$softLayer = Get-Content -LiteralPath (Join-Path $src 'layer.xml') -Raw
$start = $softLayer.IndexOf('<folder name="Editors">')
$end = $softLayer.IndexOf('    <folder name="Actions">')
if ($start -lt 0 -or $end -lt 0) { throw 'No se pudo leer la seccion Editors del layer Soft Dark' }
$editors = $softLayer.Substring($start, $end - $start)
$editors = $editors.Replace('SoftDark', 'Cyberpunk')
$editors = $editors.Replace('currentFontColorProfile', 'currentFontColorProfileCyberpunk')
$editors = [regex]::Replace($editors, '(?m)^\s*<attr name="currentFontColorProfileCyberpunk"[^>]*/>\s*\r?\n', '')

$layer = @"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE filesystem PUBLIC "-//NetBeans//DTD Filesystem 1.2//EN" "https://netbeans.apache.org/dtds/filesystem-1_2.dtd">
<!-- Generado por tools/generate-cyberpunk.ps1 -->
<filesystem>
$editors
    <folder name="Actions">
        <folder name="Tools">
            <file name="org-deepsetk-cyberpunk-apply.instance">
                <attr name="instanceClass" stringvalue="org.deepsetk.cyberpunk.ApplyCyberpunkAction"/>
            </file>
            <file name="org-deepsetk-cyberpunk-disable.instance">
                <attr name="instanceClass" stringvalue="org.deepsetk.cyberpunk.DisableCyberpunkAction"/>
            </file>
        </folder>
    </folder>
    <folder name="Menu">
        <folder name="Tools">
            <file name="org-deepsetk-cyberpunk-apply.shadow">
                <attr name="originalFile" stringvalue="Actions/Tools/org-deepsetk-cyberpunk-apply.instance"/>
                <attr name="position" intvalue="1520"/>
            </file>
            <file name="org-deepsetk-cyberpunk-disable.shadow">
                <attr name="originalFile" stringvalue="Actions/Tools/org-deepsetk-cyberpunk-disable.instance"/>
                <attr name="position" intvalue="1530"/>
            </file>
        </folder>
    </folder>
</filesystem>
"@
Set-Content -LiteralPath (Join-Path $dst 'layer.xml') -Value $layer -Encoding UTF8 -NoNewline
Write-Output 'layer.xml generado'
