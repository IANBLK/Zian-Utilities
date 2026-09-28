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
    } finally {
        $stream.Dispose()
        $memory.Dispose()
    }
}

function Recolor([System.Drawing.Bitmap]$source, [string]$theme, [bool]$itemIcon) {
    $palette = switch ($theme) {
        'captura' { @(@(24, 31, 43), @(78, 89, 105), @(219, 229, 225), @(213, 59, 72), @(255, 196, 87)) }
        'explorador' { @(@(22, 42, 48), @(42, 109, 123), @(155, 223, 224), @(43, 192, 205), @(244, 183, 91)) }
        'campeon' { @(@(35, 27, 58), @(91, 64, 134), @(178, 153, 228), @(231, 181, 66), @(255, 232, 137)) }
    }
    $result = [System.Drawing.Bitmap]::new($source.Width, $source.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $source.Height; $y++) {
        for ($x = 0; $x -lt $source.Width; $x++) {
            $old = $source.GetPixel($x, $y)
            if ($old.A -eq 0) { continue }
            $lum = ($old.R + $old.G + $old.B) / 3
            $band = if ($lum -lt 65) { 0 } elseif ($lum -lt 115) { 1 } else { 2 }
            if ($theme -eq 'captura' -and $y -eq [int]($source.Height / 2) -and $x % 3 -ne 0) { $band = 3 }
            if ($theme -eq 'explorador' -and (($x + $y) % 11 -eq 0)) { $band = 3 }
            if ($theme -eq 'campeon' -and (($x - $y + 99) % 9 -eq 0)) { $band = 3 }
            $c = $palette[$band]
            $result.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($old.A, $c[0], $c[1], $c[2]))
        }
    }
    if ($itemIcon) {
        $cx = 8; $cy = 9
        for ($dy = -2; $dy -le 2; $dy++) {
            for ($dx = -2; $dx -le 2; $dx++) {
                $x = $cx + $dx; $y = $cy + $dy
                if ($x -ge $result.Width -or $y -ge $result.Height -or $result.GetPixel($x, $y).A -eq 0) { continue }
                $draw = $false
                if ($theme -eq 'captura') { $draw = ([Math]::Abs($dx) + [Math]::Abs($dy) -eq 2 -or ($dx -eq 0 -and $dy -eq 0)) }
                if ($theme -eq 'explorador') { $draw = ($dx -eq 0 -or $dy -eq 0) }
                if ($theme -eq 'campeon') { $draw = ($dy -eq -1 -and [Math]::Abs($dx) -eq 2) -or ($dy -eq 0 -and [Math]::Abs($dx) -le 1) -or ($dy -eq 1 -and [Math]::Abs($dx) -le 2) }
                if ($draw) { $c = $palette[4]; $result.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $c[0], $c[1], $c[2])) }
            }
        }
    }
    return $result
}

try {
    foreach ($theme in @('captura', 'explorador', 'campeon')) {
        foreach ($layer in @(1, 2)) {
            $original = Read-Png "models/armor/netherite_layer_$layer.png"
            try {
                $result = Recolor $original $theme $false
                try {
                    $dir = Join-Path $root 'models/armor'
                    New-Item -ItemType Directory -Force -Path $dir | Out-Null
                    $result.Save((Join-Path $dir "${theme}_layer_$layer.png"), [System.Drawing.Imaging.ImageFormat]::Png)
                } finally { $result.Dispose() }
            } finally { $original.Dispose() }
        }
        foreach ($piece in @('helmet', 'chestplate', 'leggings', 'boots')) {
            $original = Read-Png "item/netherite_$piece.png"
            try {
                $result = Recolor $original $theme $true
                try {
                    $dir = Join-Path $root 'item'
                    New-Item -ItemType Directory -Force -Path $dir | Out-Null
                    $result.Save((Join-Path $dir "${theme}_$piece.png"), [System.Drawing.Imaging.ImageFormat]::Png)
                } finally { $result.Dispose() }
            } finally { $original.Dispose() }
        }
    }
} finally { $archive.Dispose() }
