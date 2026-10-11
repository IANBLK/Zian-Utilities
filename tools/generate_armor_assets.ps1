param([string]$MinecraftClientJar = "$env:USERPROFILE/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar")

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$root = Join-Path $repo 'neoforge/src/main/resources/assets/zianutilities/textures'
$archive = [System.IO.Compression.ZipFile]::OpenRead($MinecraftClientJar)

function Read-Png([string]$name) {
    $entry = $archive.GetEntry("assets/minecraft/textures/$name")
    if (-not $entry) { throw "Missing vanilla texture: $name" }
    $stream = $entry.Open()
    $memory = [System.IO.MemoryStream]::new()
    try {
        $stream.CopyTo($memory)
        $memory.Position = 0
        $loaded = [System.Drawing.Bitmap]::new($memory)
        try { return [System.Drawing.Bitmap]::new($loaded) }
        finally { $loaded.Dispose() }
    } finally { $stream.Dispose(); $memory.Dispose() }
}

function Color-At([int[]]$rgb, [int]$alpha = 255) {
    return [System.Drawing.Color]::FromArgb($alpha, $rgb[0], $rgb[1], $rgb[2])
}

function Pixel([System.Drawing.Bitmap]$bitmap, [int]$x, [int]$y, [int[]]$rgb, [bool]$onlyExisting = $true) {
    if ($x -lt 0 -or $y -lt 0 -or $x -ge $bitmap.Width -or $y -ge $bitmap.Height) { return }
    if ($onlyExisting -and $bitmap.GetPixel($x, $y).A -eq 0) { return }
    $bitmap.SetPixel($x, $y, (Color-At $rgb))
}

function Mark([System.Drawing.Bitmap]$bitmap, [string]$theme, [int]$cx, [int]$cy, [bool]$onlyExisting = $true) {
    $pattern = switch ($theme) {
        'captura' { @('..Y..', '.YY..', '.RRR.', '..Y..', '.Y...') } # lightning and cheeks
        'explorador' { @('T...T', '.T.T.', '..C..', '.CCC.', '..C..') } # wings and belly
        'campeon' { @('B...B', '.B.B.', '..W..', '.WWW.', '..W..') } # ears and chest spike
    }
    $colors = switch ($theme) {
        'captura' { @{ Y = @(255, 245, 131); R = @(232, 64, 73) } }
        'explorador' { @{ T = @(77, 205, 188); C = @(255, 239, 185) } }
        'campeon' { @{ B = @(18, 37, 78); W = @(244, 237, 219) } }
    }
    for ($y = 0; $y -lt 5; $y++) {
        for ($x = 0; $x -lt 5; $x++) {
            $symbol = [string]$pattern[$y][$x]
            if ($symbol -ne '.') { Pixel $bitmap ($cx + $x - 2) ($cy + $y - 2) $colors[$symbol] $onlyExisting }
        }
    }
}

function Palette([string]$theme) {
    switch ($theme) {
        'captura' { return ,@(@(135, 102, 29), @(236, 192, 53), @(255, 234, 127), @(59, 47, 37), @(226, 64, 73)) }
        'explorador' { return ,@(@(141, 72, 38), @(230, 141, 65), @(255, 207, 122), @(65, 169, 159), @(255, 237, 180)) }
        'campeon' { return ,@(@(27, 58, 116), @(57, 120, 194), @(135, 198, 232), @(20, 34, 65), @(239, 230, 210)) }
    }
}

function Recolor([System.Drawing.Bitmap]$source, [string]$theme, [string]$piece, [bool]$itemIcon) {
    $palette = Palette $theme
    $result = [System.Drawing.Bitmap]::new($source.Width, $source.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $source.Height; $y++) {
        for ($x = 0; $x -lt $source.Width; $x++) {
            $old = $source.GetPixel($x, $y)
            if ($old.A -eq 0) { continue }
            $lum = ($old.R + $old.G + $old.B) / 3
            $band = if ($lum -lt 65) { 0 } elseif ($lum -lt 115) { 1 } else { 2 }
            if ($theme -eq 'captura' -and (($x + 2 * $y) % 13 -eq 0)) { $band = 3 }
            if ($theme -eq 'explorador' -and (($x - $y + 99) % 13 -eq 0)) { $band = 3 }
            if ($theme -eq 'campeon' -and (($x + $y) % 11 -eq 0)) { $band = 3 }
            $result.SetPixel($x, $y, (Color-At $palette[$band] $old.A))
        }
    }
    if ($itemIcon) {
        if ($piece -in @('helmet', 'chestplate', 'leggings', 'boots', 'shield')) {
            $cy = if ($piece -eq 'helmet') { 7 } elseif ($piece -eq 'boots') { 9 } else { 8 }
            Mark $result $theme 8 $cy $true
            if ($piece -eq 'helmet' -and $theme -eq 'captura') {
                Pixel $result 4 1 @(59, 47, 37) $false; Pixel $result 11 1 @(59, 47, 37) $false
                Pixel $result 4 2 @(236, 192, 53) $false; Pixel $result 11 2 @(236, 192, 53) $false
            }
            if ($piece -eq 'helmet' -and $theme -eq 'campeon') {
                Pixel $result 4 1 @(20, 34, 65) $false; Pixel $result 11 1 @(20, 34, 65) $false
                Pixel $result 5 6 @(207, 60, 67) $true; Pixel $result 10 6 @(207, 60, 67) $true
            }
        } else {
            # The shape of each tool stays readable; a colored grip identifies its family.
            Pixel $result 4 12 $palette[4] $true
            Pixel $result 5 11 $palette[4] $true
        }
    } elseif ($piece -eq 'layer_1') {
        Mark $result $theme 24 24 $true # torso front
        Mark $result $theme 12 12 $true # helmet front
    } else {
        Mark $result $theme 8 24 $true # leggings front
    }
    return $result
}

function Make-Shield([string]$theme) {
    $palette = Palette $theme
    $result = [System.Drawing.Bitmap]::new(16, 16, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 1; $y -le 14; $y++) {
        $inset = if ($y -lt 9) { 2 } elseif ($y -lt 12) { 3 } elseif ($y -lt 14) { 4 } else { 6 }
        for ($x = $inset; $x -le (15 - $inset); $x++) {
            $border = ($x -eq $inset -or $x -eq (15 - $inset) -or $y -eq 1 -or $y -eq 14)
            $stripe = (($x + $y) % 6 -eq 0)
            $band = if ($border) { 3 } elseif ($stripe) { 2 } else { 1 }
            Pixel $result $x $y $palette[$band] $false
        }
    }
    Mark $result $theme 8 8 $true
    return $result
}

function Save-Png([System.Drawing.Bitmap]$bitmap, [string]$part, [string]$name) {
    $dir = Join-Path $root $part
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $bitmap.Save((Join-Path $dir "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
}

try {
    foreach ($theme in @('captura', 'explorador', 'campeon')) {
        foreach ($layer in @(1, 2)) {
            $original = Read-Png "models/armor/netherite_layer_$layer.png"
            try {
                $result = Recolor $original $theme "layer_$layer" $false
                try { Save-Png $result 'models/armor' "${theme}_layer_$layer" }
                finally { $result.Dispose() }
            } finally { $original.Dispose() }
        }
        foreach ($piece in @('helmet', 'chestplate', 'leggings', 'boots', 'sword', 'axe', 'pickaxe', 'shovel', 'hoe', 'bow', 'bow_pulling_0', 'bow_pulling_1', 'bow_pulling_2')) {
            $sourceName = if ($piece -in @('helmet', 'chestplate', 'leggings', 'boots', 'sword', 'axe', 'pickaxe', 'shovel', 'hoe')) { "netherite_$piece" } else { $piece }
            $original = Read-Png "item/$sourceName.png"
            try {
                $result = Recolor $original $theme $piece $true
                try { Save-Png $result 'item' "${theme}_$piece" }
                finally { $result.Dispose() }
            } finally { $original.Dispose() }
        }
        $shield = Make-Shield $theme
        try { Save-Png $shield 'item' "${theme}_shield" }
        finally { $shield.Dispose() }
    }
} finally { $archive.Dispose() }
