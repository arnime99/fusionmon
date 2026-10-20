<#
  Modelo de partida para una fusión hecha a mano (ver FusionCustomModels): el modelo del CUERPO con la cabeza de otro
  Pokémon ya pegada, y una sola textura con las dos (Cobblemon usa una textura por modelo). Se abre en Blockbench
  (Bedrock Entity) para retocarlo a mano.

  - Se conservan TODOS los huesos del cuerpo (los de su cabeza, vacíos): así sirven su poser y sus animaciones tal cual
    (un poser que nombra un hueso que no existe cuelga Cobblemon).
  - La cabeza nueva cuelga del hueso de la cabeza del cuerpo (-BodyHeadBone), con sus huesos renombrados con el prefijo
    de su especie, colocada para que su cráneo (el cubo más grande) quede donde estaba el del cuerpo. Solo se traslada:
    en el formato Bedrock las posiciones de los cubos son absolutas, antes de girar los huesos, así que heredan los giros
    de la cabeza del cuerpo. (Si algún hueso de la cadena de la cabeza viniera girado, habría que retocarlo a mano.)
  - Se quitan las piezas que solo usan las animaciones de la cabeza (-Exclude: boca abierta, ojos de emoción...): sin
    ellas saldrían todas superpuestas.
  - Textura: la del cuerpo arriba y la de la cabeza debajo; las coordenadas de la cabeza se bajan lo que mide la del
    cuerpo. -BodyLayers: texturas de capas del cuerpo (la llama de Charizard) que se amplían con transparente debajo.

  LIMITACIÓN CONOCIDA: la orientación. Hay modelos cuya cabeza en el archivo no está en su pose de reposo (Charizard
  la tiene mirando al cielo y la bajan sus animaciones): la cabeza pegada sale bien en el archivo pero girada al
  animarse. Hay que girarla a mano en Blockbench viendo la animación del cuerpo (panel Animate). FusionGraft no tiene
  este problema porque mide la pose animada.

  Uso:
  .\tools\fusion-starter-model.ps1 -HeadGeo pikachu.geo.json -HeadTexture pikachu.png `
      -BodyGeo charizard.geo.json -BodyTexture charizard.png -Name pikachu_charizard -OutDir carpeta `
      [-BodyLayers flame1.png,flame2.png] [-HeadBone head_angle] [-BodyHeadBone head_angle]
  Este archivo va en UTF-8 CON BOM (PowerShell 5.1).
#>
param(
    [Parameter(Mandatory)][string]$HeadGeo,
    [Parameter(Mandatory)][string]$HeadTexture,
    [Parameter(Mandatory)][string]$BodyGeo,
    [Parameter(Mandatory)][string]$BodyTexture,
    [Parameter(Mandatory)][string]$Name,
    [Parameter(Mandatory)][string]$OutDir,
    [string[]]$BodyLayers = @(),
    [string]$HeadBone = 'head_angle',
    [string]$BodyHeadBone = 'head_angle',
    [string]$Exclude = '^(mouth_open|tongue|acting_teeth|eye_emote|eyelid_emote)',
    [string]$HeadPrefix
)
$ErrorActionPreference = 'Stop'
[Threading.Thread]::CurrentThread.CurrentCulture = [Globalization.CultureInfo]::InvariantCulture
Add-Type -AssemblyName System.Drawing

function Read-Geo($path) {
    $json = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
    return @{ json = $json; geo = $json.'minecraft:geometry'[0] }
}
function Bones-Under($geo, $root) {
    # El hueso raíz y todo lo que cuelga de él, en el orden del archivo
    $set = New-Object System.Collections.Generic.HashSet[string]
    [void]$set.Add($root)
    $changed = $true
    while ($changed) {
        $changed = $false
        foreach ($b in $geo.bones) { if ($b.parent -and $set.Contains($b.parent) -and $set.Add($b.name)) { $changed = $true } }
    }
    return @($geo.bones | Where-Object { $set.Contains($_.name) })
}
function Skull-Center($bones) {
    # El cubo con más volumen (sin cuernos ni orejas, que pueden ser grandes y no son el cráneo)
    $best = $null; $bestVolume = -1
    foreach ($b in $bones) {
        if ($b.name -match 'horn|ear|locator') { continue }
        foreach ($c in @($b.cubes)) {
            if (-not $c) { continue }
            $v = [math]::Abs($c.size[0] * $c.size[1] * $c.size[2])
            if ($v -gt $bestVolume) { $bestVolume = $v; $best = $c }
        }
    }
    if (-not $best) { throw 'No se encuentra ningún cubo en la cabeza' }
    return @(($best.origin[0] + $best.size[0] / 2), ($best.origin[1] + $best.size[1] / 2), ($best.origin[2] + $best.size[2] / 2))
}
function Move-Cube($cube, $offset, $dv) {
    $c = $cube | ConvertTo-Json -Depth 10 | ConvertFrom-Json   # copia
    $c.origin = @(($c.origin[0] + $offset[0]), ($c.origin[1] + $offset[1]), ($c.origin[2] + $offset[2]))
    if ($c.pivot) { $c.pivot = @(($c.pivot[0] + $offset[0]), ($c.pivot[1] + $offset[1]), ($c.pivot[2] + $offset[2])) }
    if ($c.uv -is [array]) {
        $c.uv = @($c.uv[0], ($c.uv[1] + $dv))                     # UV de caja
    } elseif ($c.uv) {
        foreach ($face in $c.uv.PSObject.Properties) {           # UV por cara
            if ($face.Value.uv) { $face.Value.uv = @($face.Value.uv[0], ($face.Value.uv[1] + $dv)) }
        }
    }
    return $c
}

$head = Read-Geo $HeadGeo
$body = Read-Geo $BodyGeo
if (-not $HeadPrefix) { $HeadPrefix = ($head.geo.description.identifier -replace '^geometry\.', '' -replace '_(male|female)$', '') + '_' }
New-Item -ItemType Directory -Force $OutDir | Out-Null

# 1. Textura combinada: cuerpo arriba, cabeza debajo
$bodyImage = [System.Drawing.Image]::FromFile((Resolve-Path -LiteralPath $BodyTexture).Path)
$headImage = [System.Drawing.Image]::FromFile((Resolve-Path -LiteralPath $HeadTexture).Path)
$bodyW = $body.geo.description.texture_width; $bodyH = $body.geo.description.texture_height
$headW = $head.geo.description.texture_width; $headH = $head.geo.description.texture_height
if ($bodyImage.Width -ne $bodyW -or $bodyImage.Height -ne $bodyH -or $headImage.Width -ne $headW -or $headImage.Height -ne $headH) {
    Write-Warning 'El tamaño de alguna textura no coincide con su modelo: las coordenadas pueden salir desplazadas'
}
$atlasW = [math]::Max($bodyImage.Width, $headImage.Width); $atlasH = $bodyImage.Height + $headImage.Height
function New-Atlas($top, $bottom) {
    $bmp = New-Object System.Drawing.Bitmap $atlasW, $atlasH
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear([System.Drawing.Color]::Transparent)
    $g.InterpolationMode = 'NearestNeighbor'
    $g.DrawImage($top, 0, 0, $top.Width, $top.Height)
    if ($bottom) { $g.DrawImage($bottom, 0, $top.Height, $bottom.Width, $bottom.Height) }
    $g.Dispose()
    return $bmp
}
$atlas = New-Atlas $bodyImage $headImage
$atlas.Save((Join-Path $OutDir "$Name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$atlas.Dispose()
foreach ($layer in $BodyLayers) {
    $img = [System.Drawing.Image]::FromFile((Resolve-Path -LiteralPath $layer).Path)
    $padded = New-Atlas $img $null
    $padded.Save((Join-Path $OutDir ("$Name" + '_' + [IO.Path]::GetFileName($layer))), [System.Drawing.Imaging.ImageFormat]::Png)
    $padded.Dispose(); $img.Dispose()
}
$bodyImage.Dispose(); $headImage.Dispose()

# 2. Cabeza del cuerpo: vaciar sus piezas (los huesos se quedan para el poser y las animaciones)
$bodyHeadBones = Bones-Under $body.geo $BodyHeadBone
$bodySkull = Skull-Center $bodyHeadBones
foreach ($b in $bodyHeadBones) {
    if ($b.name -notlike 'locator*' -and $b.PSObject.Properties['cubes']) { $b.PSObject.Properties.Remove('cubes') }
}

# 3. Cabeza nueva: lo que cuelga de su hueso de cabeza, sin localizadores ni piezas de animación
$excluded = New-Object System.Collections.Generic.HashSet[string]
$headBones = Bones-Under $head.geo $HeadBone
foreach ($b in $headBones) {
    if ($b.name -like 'locator*' -or $b.name -match $Exclude -or ($b.parent -and $excluded.Contains($b.parent))) { [void]$excluded.Add($b.name) }
}
$kept = @($headBones | Where-Object { -not $excluded.Contains($_.name) })
$headSkull = Skull-Center $kept
$offset = @(($bodySkull[0] - $headSkull[0]), ($bodySkull[1] - $headSkull[1]), ($bodySkull[2] - $headSkull[2]))
$dv = $bodyH
$newBones = @()
foreach ($b in $kept) {
    $nb = [ordered]@{}
    $nb.name = if ($b.name -eq $HeadBone) { "$($HeadPrefix)head" } else { "$HeadPrefix$($b.name)" }
    $nb.parent = if ($b.name -eq $HeadBone) { $BodyHeadBone } elseif ($b.parent -eq $HeadBone) { "$($HeadPrefix)head" } else { "$HeadPrefix$($b.parent)" }
    $nb.pivot = @(($b.pivot[0] + $offset[0]), ($b.pivot[1] + $offset[1]), ($b.pivot[2] + $offset[2]))
    if ($b.rotation) { $nb.rotation = $b.rotation }
    if ($b.mirror) { $nb.mirror = $b.mirror }
    $cubes = @($b.cubes | Where-Object { $_ } | ForEach-Object { Move-Cube $_ $offset $dv })
    if ($cubes.Count -gt 0) { $nb.cubes = $cubes }
    $newBones += [pscustomobject]$nb
}

# 4. Juntar y guardar
$body.geo.bones = @($body.geo.bones) + $newBones
$body.geo.description.identifier = "geometry.$Name"
$body.geo.description.texture_width = $atlasW
$body.geo.description.texture_height = $atlasH
$out = Join-Path $OutDir "$Name.geo.json"
[IO.File]::WriteAllText($out, ($body.json | ConvertTo-Json -Depth 30), (New-Object Text.UTF8Encoding $false))
"Modelo: $out"
"Textura: $(Join-Path $OutDir "$Name.png") ($atlasW x $atlasH; la cabeza empieza en y=$dv)"
"Cabeza: $($kept.Count) huesos de $HeadBone (fuera: $($excluded.Count)), desplazada ($($offset -join ', '))"
