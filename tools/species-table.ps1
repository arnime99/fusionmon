<#
  Tabla de especies para revisar los visuales de las fusiones (ver docs/visual-bugs.md, "revisión por especies").

  Lee los modelos (.geo.json) y resolvers del jar de Cobblemon y de los resource packs de run/resourcepacks (un pack
  sustituye los archivos con la misma ruta, como en el juego) y, para cada modelo en uso, aplica una COPIA de las
  reglas de detección de FusionGraft (findHeads, findTail, findTrunk, findDecorations, cráneo...). Es aproximada: usa
  la postura del .geo, sin animaciones ni giros de los huesos. Si cambia una regla en FusionGraft, cambiarla aquí.

  Uso (PowerShell, desde la raíz del proyecto):  .\tools\species-table.ps1
  Escribe docs/species/especies.csv (separado por ";", se abre con Excel) y docs/species/README.md (resumen).
  Este archivo tiene que estar guardado en UTF-8 CON BOM: PowerShell 5.1 lee los .ps1 sin BOM como ANSI.
#>
param([string]$OutDir)
$ErrorActionPreference = 'Stop'
[Threading.Thread]::CurrentThread.CurrentCulture = [Globalization.CultureInfo]::InvariantCulture
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not $OutDir) { $OutDir = Join-Path $repo 'docs\species' }

# ---------------------------------------------------------------------------------------------------------------
# 1. Archivos: el jar de Cobblemon y después los resource packs
# ---------------------------------------------------------------------------------------------------------------
$jar = Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\com.cobblemon\fabric\1.8.1+1.21.1" -Recurse -Filter '*.jar' |
        Where-Object { $_.Name -notmatch 'sources' } | Select-Object -First 1
$sources = @(@{ tag = 'Cobblemon'; path = $jar.FullName })
$packDir = Join-Path $repo 'run\resourcepacks'
if (Test-Path $packDir) {
    foreach ($p in (Get-ChildItem $packDir -Filter *.zip)) { $sources += @{ tag = $p.BaseName; path = $p.FullName } }
}
$files = @{}
foreach ($s in $sources) {
    $zip = [IO.Compression.ZipFile]::OpenRead($s.path)
    try {
        foreach ($e in $zip.Entries) {
            if ($e.FullName -notmatch '^assets/[^/]+/bedrock/pokemon/(models|resolvers)/.+\.json$') { continue }
            $r = New-Object IO.StreamReader($e.Open())
            $files[$e.FullName] = @{ tag = $s.tag; text = $r.ReadToEnd() }
            $r.Close()
        }
    } finally { $zip.Dispose() }
}

# Modelos por identificador, como los nombra un resolver: "cobblemon:pidgey.geo" (el nombre del archivo, sin carpeta)
$models = @{}
foreach ($k in $files.Keys) {
    if ($k -match '^assets/([^/]+)/bedrock/pokemon/models/(?:.*/)?([^/]+)\.json$') {
        $models["$($Matches[1]):$($Matches[2])".ToLower()] = @{ path = $k; tag = $files[$k].tag }
    }
}

# ---------------------------------------------------------------------------------------------------------------
# 2. Resolvers: qué modelos usa cada especie. Cobblemon junta los de una especie ordenados por "order" y recorre las
#    variaciones de la última a la primera: gana la última con esos aspects
# ---------------------------------------------------------------------------------------------------------------
$species = @{}
foreach ($k in $files.Keys) {
    if (-not ($k -match '/resolvers/(?:(\d+)_[^/]*/)?[^/]+\.json$')) { continue }
    $dex = if ($Matches[1]) { [int]$Matches[1] } else { 0 }
    try { $j = $files[$k].text | ConvertFrom-Json } catch { continue }
    if (-not $j.species) { continue }
    $sp = ($j.species -replace '^cobblemon:', '').ToLower()
    if (-not $species.ContainsKey($sp)) { $species[$sp] = @{ dex = $dex; resolvers = New-Object System.Collections.ArrayList } }
    if ($dex -gt 0) { $species[$sp].dex = $dex }
    $order = if ($null -ne $j.order) { [int]$j.order } else { 0 }
    [void]$species[$sp].resolvers.Add(@{ order = $order; vars = @($j.variations) })
}
$usage = @{}   # modelo -> lista de @{ species; dex; aspects }
foreach ($sp in $species.Keys) {
    $vars = @()
    foreach ($r in ($species[$sp].resolvers | Sort-Object { $_.order })) { $vars += $r.vars }
    $seen = @{}
    for ($i = $vars.Count - 1; $i -ge 0; $i--) {
        $v = $vars[$i]
        if (-not $v -or -not $v.model) { continue }
        $aspects = (@($v.aspects | Where-Object { $_ }) | Sort-Object) -join ','
        if ($seen.ContainsKey($aspects)) { continue }   # sustituida por una variación posterior
        $seen[$aspects] = $true
        $id = $v.model.ToLower()
        if ($id -notmatch ':') { $id = "cobblemon:$id" }
        if (-not $usage.ContainsKey($id)) { $usage[$id] = New-Object System.Collections.ArrayList }
        [void]$usage[$id].Add(@{ species = $sp; dex = $species[$sp].dex; aspects = $aspects })
    }
}

# ---------------------------------------------------------------------------------------------------------------
# 3. Copia de las reglas de FusionGraft
# ---------------------------------------------------------------------------------------------------------------
$NAME_MODIFIERS = @('left','right','l','r','front','back','top','bottom','upper','lower','base','master','main','mid',
    'middle','inner','outer','open','closed','side','flying','big','small')
$ANATOMY = @('head','neck','torso','ftorso','body','fbody','chest','belly','abdomen','thorax','waist','hip','pelvis',
    'butt','spine','segment','leg','fleg','thigh','knee','foot','feet','toe','arm','shoulder','hand','finger','tail',
    'tentacle','jaw','mouth','mounch','eye','face','tongue','locator','bone','cube','group','root','bb','seat','shadow')
$LEGS = @('leg','legs','foot','feet')
$WHOLE_MODEL_SHARE = 0.8
$SERPENT_TAIL_SHARE = 0.25

function StripModifiers([string]$t) {
    $changed = $true
    while ($changed) {
        $changed = $false
        foreach ($m in $NAME_MODIFIERS) {
            if ($m.Length -ge 3 -and $t.StartsWith($m) -and ($t.Length - $m.Length) -ge 3) { $t = $t.Substring($m.Length); $changed = $true }
        }
    }
    $t
}
function IsAnatomy([string]$w) {
    if ($ANATOMY -contains $w) { return $true }
    foreach ($p in $ANATOMY) { if ($p.Length -ge 3 -and $w.EndsWith($p)) { return $true } }
    $false
}
function Category([string]$name) {
    if ($name.StartsWith('%') -or $name.StartsWith('internal_locator')) { return $null }
    $lower = $name.ToLower()
    foreach ($tok in $lower.Split('_')) { if (@('kid','baby','child') -contains ($tok -replace '\d+$','')) { return 'kid' } }
    $cat = $null
    foreach ($tok in $lower.Split('_')) {
        $tok = StripModifiers ($tok -replace '\d+$','')
        if ($tok -eq '' -or $NAME_MODIFIERS -contains $tok) { continue }
        $sing = if ($tok.Length -gt 3 -and $tok.EndsWith('s')) { $tok.Substring(0, $tok.Length - 1) } else { $tok }
        if ((IsAnatomy $tok) -or (IsAnatomy $sing)) { return $null }
        if (-not $cat) { $cat = $sing }
    }
    $cat
}
function IsArm([string]$name) {
    if ($name.StartsWith('%') -or $name.StartsWith('internal_locator')) { return $false }
    foreach ($tok in $name.ToLower().Split('_')) {
        $w = StripModifiers ($tok -replace '\d+$','')
        if ($w -eq 'arm' -or $w -eq 'arms' -or $w -eq 'shoulder') { return $true }
    }
    $false
}
function IsLegName([string]$name) {
    foreach ($tok in $name.ToLower().Split('_')) {
        $w = StripModifiers ($tok -replace '\d+$','')
        # "lleg", "rfoot" (Groudon de AllTheMons): la l/r pegada; StripModifiers no quita letras sueltas
        if ($LEGS -contains $w -or $w -match '^[lr](leg|legs|foot|feet)$') { return $true }
    }
    $false
}
# Cuántas patas hay dentro de un grupo de patas sin cubos ("legs"): cada cadena con cubos que cuelga de él es una
# pata, se llame como se llame ("right_thigh" en Skarmory); los grupos de dentro (patas delanteras/traseras) se
# cuentan igual. Los dedos no son patas
function LegUnits([string]$n) {
    if (HasOwnCubes $n) { return 1 }
    $units = 0
    foreach ($k in (Kids $n)) {
        if ((TreeCnt $k.name) -eq 0 -or $k.name.ToLower() -match '^(toe|claw|nail|talon)') { continue }
        $units += LegUnits $k.name
    }
    $units
}

# --- Árbol de huesos del modelo que se analiza ($B: nombre -> hueso, $K: padre -> hijos; '' es la raíz) ---
function Kids([string]$n) { if ($script:K.ContainsKey($n)) { return ,$script:K[$n] }; return ,@() }
function OwnCubes([string]$n) { if ($n -eq '') { return ,@() }; return ,@($script:B[$n].cubes | Where-Object { $_ }) }
function CubeVol($c) { [double]$c.size[0] * [double]$c.size[1] * [double]$c.size[2] }
function TreeCnt([string]$n) {
    if ($script:CNT.ContainsKey($n)) { return $script:CNT[$n] }
    $c = (OwnCubes $n).Count
    foreach ($k in (Kids $n)) { $c += TreeCnt $k.name }
    $script:CNT[$n] = $c; $c
}
function TreeVol([string]$n) {
    if ($script:VOL.ContainsKey($n)) { return $script:VOL[$n] }
    $v = 0.0
    foreach ($c in (OwnCubes $n)) { $v += CubeVol $c }
    foreach ($k in (Kids $n)) { $v += TreeVol $k.name }
    $script:VOL[$n] = $v; $v
}
function CubeBox($c) {
    $lo = @([double]$c.origin[0], [double]$c.origin[1], [double]$c.origin[2])
    @($lo[0], $lo[1], $lo[2], ($lo[0] + $c.size[0]), ($lo[1] + $c.size[1]), ($lo[2] + $c.size[2]))
}
function Grow($box, $b) {
    if (-not $box) { return ,@($b[0], $b[1], $b[2], $b[3], $b[4], $b[5]) }
    for ($i = 0; $i -lt 3; $i++) { $box[$i] = [math]::Min($box[$i], $b[$i]); $box[$i + 3] = [math]::Max($box[$i + 3], $b[$i + 3]) }
    return ,$box
}
# Caja de los cubos propios (sin giros); $solid: solo los que tienen volumen
function OwnBox([string]$n, [bool]$solid) {
    $box = $null
    foreach ($c in (OwnCubes $n)) { if ($solid -and (CubeVol $c) -le 0) { continue }; $box = Grow $box (CubeBox $c) }
    return ,$box
}
function BoxVol($b) { if (-not $b) { return 0 }; ($b[3] - $b[0]) * ($b[4] - $b[1]) * ($b[5] - $b[2]) }
function MeanSide($b) { (($b[3] - $b[0]) + ($b[4] - $b[1]) + ($b[5] - $b[2])) / 3 }
function HasOwnCubes([string]$n) { (OwnCubes $n).Count -gt 0 }

function CollectPaths([string]$n, [scriptblock]$pred, $cur, $found) {
    foreach ($k in (Kids $n)) {
        $cur.Add($k.name)
        if (& $pred $k.name.ToLower()) { [void]$found.Add([string[]]$cur.ToArray()) }
        CollectPaths $k.name $pred $cur $found
        $cur.RemoveAt($cur.Count - 1)
    }
}
function Paths([scriptblock]$pred, [string]$from = '') {
    $found = New-Object System.Collections.ArrayList
    $cur = New-Object 'System.Collections.Generic.List[string]'
    $cur.Add($from)
    CollectPaths $from $pred $cur $found
    return ,$found
}
function Holds([string]$node, [string]$target) {
    $t = $target
    while ($true) {
        if ($t -eq $node) { return $true }
        if ($t -eq '' -or -not $script:B.ContainsKey($t)) { return $false }
        $p = $script:B[$t].parent
        $t = if ($p) { [string]$p } else { '' }
    }
}
function Sub($arr, [int]$count) { return ,[string[]]($arr[0..($count - 1)]) }
function InsideAnother($path, $bones) {
    foreach ($h in $bones) { for ($i = 0; $i -lt $path.Count - 1; $i++) { if ($path[$i] -eq $h.part) { return $true } } }
    $false
}
function HasLegs([string]$n) { (Paths { param($x) IsLegName $x } $n).Count -gt 0 }
function IsNeck($path, [int]$i) { if ($i -lt 1) { return $false }; $x = $path[$i].ToLower(); $x.Contains('neck') -or $x.Contains('necc') }

function WithFace($head) {
    $faces = Paths { param($x) $x.StartsWith('eye') -or $x.StartsWith('face') -or $x.StartsWith('mouth') }
    $part = $head[$head.Count - 1]
    $common = $head
    foreach ($f in $faces) {
        if ($f -contains $part) { return ,$head }
        $shared = 0
        while ($shared -lt $common.Count -and $shared -lt $f.Count -and $common[$shared] -eq $f[$shared]) { $shared++ }
        $common = Sub $common $shared
    }
    return ,$common
}
function WithSkull($head) {
    if (HasOwnCubes $head[$head.Count - 1]) { return ,$head }
    for ($i = $head.Count - 2; $i -ge 1; $i--) { if (HasOwnCubes $head[$i]) { return ,(Sub $head ($i + 1)) } }
    return ,$head
}
function Tip($start) {
    $path = [System.Collections.Generic.List[string]]$start
    while ($true) {
        $next = $null
        foreach ($k in (Kids $path[$path.Count - 1])) {
            if (-not $k.name.ToLower().StartsWith('tail')) { continue }
            if ((BoxVol (OwnBox $k.name $false)) -le 0) { continue }
            if (-not $next -or (TreeCnt $k.name) -gt (TreeCnt $next)) { $next = $k.name }
        }
        if (-not $next) { return ,[string[]]$path.ToArray() }
        $path.Add($next)
    }
}
function FindTail($heads) {
    $roots = New-Object System.Collections.ArrayList
    $parent = $null
    foreach ($p in (Paths { param($x) $x.StartsWith('tail') })) {
        if ($p.Count -lt 2 -or (InsideAnother $p $heads) -or (InsideAnother $p $roots)) { continue }
        $from = $p[$p.Count - 2]
        if ($null -eq $parent) { $parent = $from } elseif ($from -ne $parent) { continue }
        [void]$roots.Add(@{ part = $p[$p.Count - 1]; path = $p })
    }
    if ($roots.Count -eq 0) { return $null }
    $anchor = $roots[0]
    foreach ($r in $roots) { if ((TreeCnt $r.part) -gt (TreeCnt $anchor.part)) { $anchor = $r } }
    $share = 0.0; $legsOnTail = $false
    foreach ($r in $roots) { $share += TreeVol $r.part; if (HasLegs $r.part) { $legsOnTail = $true } }
    if (($share -gt $SERPENT_TAIL_SHARE * (TreeVol '') -and -not (HasLegs '')) -or $legsOnTail) {
        $tip = Tip $anchor.path
        if ($tip[$tip.Count - 1] -ne $anchor.part) { return @{ roots = @(@{ part = $tip[$tip.Count - 1]; path = $tip }); tip = $true } }
    }
    @{ roots = $roots.ToArray(); tip = $false }
}
function BiggestOwnCubes($headPath, [scriptblock]$allowed) {
    $best = 0; $trunk = $null
    for ($i = 0; $i -lt $headPath.Count - 1; $i++) {
        if (-not (& $allowed $i)) { continue }
        $v = BoxVol (OwnBox $headPath[$i] $false)
        if ($v -gt $best) { $best = $v; $trunk = @{ part = $headPath[$i]; path = (Sub $headPath ($i + 1)); index = $i } }
    }
    $trunk
}
function FindTrunk($headPath) {
    $t = BiggestOwnCubes $headPath { param($i) -not (IsNeck $headPath $i) }
    if ($t) { return $t }
    $lastNeck = -1
    for ($i = 0; $i -lt $headPath.Count - 1; $i++) { if (IsNeck $headPath $i) { $lastNeck = $i } }
    for ($i = 0; $i -lt $lastNeck; $i++) {
        if ((IsNeck $headPath $i) -and (HasOwnCubes $headPath[$i])) { return @{ part = $headPath[$i]; path = (Sub $headPath ($i + 1)); index = $i } }
    }
    BiggestOwnCubes $headPath { param($i) $true }
}
function FindSpineEnd($headPath, $trunk) {
    $from = if ($trunk) { $trunk.path.Count } else { 1 }
    $serpent = $trunk -and (IsNeck $headPath ($trunk.path.Count - 1))
    $end = -1
    for ($i = [math]::Max($from, 1); $i -lt $headPath.Count - 1; $i++) {
        if (IsNeck $headPath $i) { $end = $i; if (-not $serpent) { break } }
    }
    if ($end -lt 0) { return @{ part = $headPath[$headPath.Count - 1]; path = $headPath } }
    @{ part = $headPath[$end]; path = (Sub $headPath ($end + 1)) }
}
function FindDecorations($headPath, $heads, $tail, $cluster) {
    $found = New-Object System.Collections.ArrayList
    for ($i = 0; $i -lt $headPath.Count - 1; $i++) {
        $neck = (IsNeck $headPath $i) -or ($cluster -and $headPath[$i] -eq $cluster)
        foreach ($k in (Kids $headPath[$i])) {
            $n = $k.name
            if ($headPath -contains $n -or (TreeCnt $n) -eq 0) { continue }
            $held = $false
            foreach ($h in $heads) { if (Holds $n $h.part) { $held = $true } }
            if ($tail) { foreach ($r in $tail.roots) { if (Holds $n $r.part) { $held = $true } } }
            if ($held) { continue }
            $cat = Category $n
            if (-not $cat -and (IsArm $n)) { $cat = 'arm' }
            if (-not $cat) { continue }
            [void]$found.Add(@{ name = $n; category = $cat; neck = $neck })
        }
    }
    return ,$found
}
# Cráneo: el cubo con más volumen de la cabeza que no cuelgue de un adorno (si no hay, contando adornos)
function Skull([string]$head) {
    $best = @{ vol = -1 }; $any = @{ vol = -1 }
    $stack = New-Object System.Collections.Stack
    $stack.Push(@{ n = $head; clean = $true })
    while ($stack.Count -gt 0) {
        $e = $stack.Pop()
        foreach ($c in (OwnCubes $e.n)) {
            $v = CubeVol $c
            if ($v -gt $any.vol) { $any = @{ vol = $v; bone = $e.n; box = (CubeBox $c) } }
            if ($e.clean -and $v -gt $best.vol) { $best = @{ vol = $v; bone = $e.n; box = (CubeBox $c) } }
        }
        foreach ($k in (Kids $e.n)) { $stack.Push(@{ n = $k.name; clean = ($e.clean -and -not (Category $k.name)) }) }
    }
    if ($best.vol -gt 0) { $best.fallback = $false; return $best }
    if ($any.vol -gt 0) { $any.fallback = $true; return $any }
    $null
}
# Patas: cada hueso de pierna/pie que no cuelga de otro cuenta como una; un grupo sin cubos ("legs"), las que lleve
function LegCount([string]$n) {
    $count = 0
    foreach ($k in (Kids $n)) {
        if (IsLegName $k.name) {
            $count += [math]::Max(1, (LegUnits $k.name))
        } else {
            $count += LegCount $k.name
        }
    }
    $count
}

function Analyze($geoText) {
    $j = $geoText | ConvertFrom-Json
    $geo = @($j.'minecraft:geometry')[0]
    $script:B = @{}; $script:K = @{}; $script:CNT = @{}; $script:VOL = @{}
    foreach ($b in @($geo.bones)) {
        if (-not $b -or -not $b.name -or $script:B.ContainsKey($b.name)) { continue }
        $script:B[$b.name] = $b
        $p = if ($b.parent) { [string]$b.parent } else { '' }
        if (-not $script:K.ContainsKey($p)) { $script:K[$p] = New-Object System.Collections.ArrayList }
        [void]$script:K[$p].Add($b)
    }
    $r = @{ shape = ''; tags = @(); head = ''; pivot = ''; attach = ''; skull = ''; neck = ''; trunk = ''; spine = ''; tail = '';
            decor = ''; neckDecor = ''; warnings = New-Object System.Collections.ArrayList }
    $total = TreeCnt ''
    $headless = $false; $whole = $false

    # --- findHeads ---
    $primaryList = Paths { param($x) $x -eq 'head' }
    $primary = if ($primaryList.Count) { $primaryList[0] } else { $null }
    if ($primary -and (TreeCnt $primary[$primary.Count - 1]) -eq $total) { $headless = $true }
    if (-not $headless -and -not $primary) {
        $loc = Paths { param($x) $x -eq 'locator_head' }
        if ($loc.Count) {
            $primary = WithSkull (WithFace (Sub $loc[0] ($loc[0].Count - 1)))
            $part = $primary[$primary.Count - 1]
            $whole = (TreeVol $part) -ge $WHOLE_MODEL_SHARE * (TreeVol '')
            if ((TreeCnt $part) -eq $total) { $headless = $true }
        }
    }
    $heads = New-Object System.Collections.ArrayList
    if (-not $headless) {
        $paths = New-Object System.Collections.ArrayList
        if ($primary) { [void]$paths.Add($primary) }
        foreach ($p in (Paths { param($x) $x -match '^(head\d*|head_(left|right))$' })) { [void]$paths.Add($p) }
        foreach ($p in $paths) {
            $part = $p[$p.Count - 1]
            if ($p.Count -lt 2 -or (InsideAnother $p $heads)) { continue }
            $contains = $false
            foreach ($h in $heads) { if ($h.path -contains $part) { $contains = $true } }
            if ($contains) { continue }
            [void]$heads.Add(@{ part = $part; path = $p })
        }
    }
    $tail = if ($headless) { $null } else { FindTail $heads }
    if ($tail) { $r.tail = (($tail.roots | ForEach-Object { $_.part }) -join ', ') + $(if ($tail.tip) { ' (punta)' } else { '' }) }

    if ($headless -or $heads.Count -eq 0) {
        # Cuerpo sin cabeza: como cabeza se pega entero; como cuerpo, la cabeza va encima (findTop)
        $r.shape = 'sin cabeza'
        # PSBase.Keys: con .Keys, un hueso llamado "keys" (las llaves de Klefki) devolvería ese hueso
        $named = @($script:B.PSBase.Keys | Where-Object { $_.ToLower().Contains('head') -and -not $_.ToLower().StartsWith('locator') -and -not $_.StartsWith('%') })
        if ($named.Count) { [void]$r.warnings.Add("posible cabeza por el nombre: $(($named | Select-Object -First 3) -join ', ')") }
        $faces = Paths { param($x) ($x.StartsWith('eye') -or $x.StartsWith('face')) -and -not $x.StartsWith('locator') }
        if ($faces.Count -and -not $named.Count -and $faces[0].Count -ge 3) {
            $holder = $faces[0][$faces[0].Count - 2]
            if ((HasOwnCubes $holder) -and (TreeVol $holder) -lt 0.6 * (TreeVol '')) { [void]$r.warnings.Add("posible cabeza: $holder (lleva la cara)") }
        }
        return $r
    }

    $main = $heads[0]
    $headPath = $main.path
    $r.head = $main.part
    $r.neck = $headPath[$headPath.Count - 2]
    $pv = $script:B[$main.part].pivot
    $pivot = if ($pv) { @([double]$pv[0], [double]$pv[1], [double]$pv[2]) } else { @(0.0, 0.0, 0.0) }
    $r.pivot = ($pivot | ForEach-Object { [math]::Round($_, 1) }) -join ' '

    $cluster = $null; $companions = 0; $chains = 0
    if ($heads.Count -gt 1) {
        $parent = $heads[0].path[$heads[0].path.Count - 2]
        $same = $true
        foreach ($h in $heads) { if ($h.path[$h.path.Count - 2] -ne $parent) { $same = $false } }
        if ($same) { $cluster = $parent; $companions = $heads.Count - 1 } else { $chains = $heads.Count }
    }
    $trunk = FindTrunk $headPath
    if ($cluster -and $trunk -and [array]::IndexOf($headPath, $cluster) -le $trunk.index) { $cluster = $null }
    $spine = FindSpineEnd $headPath $trunk
    $decor = FindDecorations $headPath $heads $tail $cluster

    # --- Categoría ---
    # Serpiente: la cola es la punta, o el camino a la cabeza es una cadena de cuellos (Gyarados: neck1...neck5). Con un
    # solo cuello como tronco no (Snorlax: su "neck" es todo el cuerpo; Ariados: el cuerpo cuelga al lado del cuello)
    $necks = 0
    for ($i = 0; $i -lt $headPath.Count - 1; $i++) { if (IsNeck $headPath $i) { $necks++ } }
    $neckTrunk = $trunk -and (IsNeck $headPath $trunk.index)
    $serpent = ($tail -and $tail.tip) -or ($neckTrunk -and $necks -ge 2)
    if ($neckTrunk -and $necks -lt 2) { [void]$r.warnings.Add("el tronco es el cuello ($($trunk.part)): el cuerpo no está en el camino a la cabeza o el cuello es todo el cuerpo") }
    $legs = LegCount ''
    if ($whole) { $r.shape = 'todo cabeza' }
    elseif ($serpent) { $r.shape = 'serpiente/pez' }
    elseif ($legs -eq 0) { $r.shape = 'sin patas' }
    elseif ($legs -eq 2) { $r.shape = 'bípedo' }
    elseif ($legs -eq 4) { $r.shape = 'cuadrúpedo' }
    elseif ($legs -ge 6) { $r.shape = 'muchas patas' }
    else { $r.shape = "patas: $legs" }
    $tags = @()
    if ($chains) { $tags += "varias cabezas ($chains)" }
    if ($companions) { $tags += "racimo ($($companions + 1))" }
    if (@($decor | Where-Object { $_.category -eq 'wing' }).Count) { $tags += 'alas' }
    if ($tail) { $tags += $(if ($tail.tip) { 'cola (punta)' } else { 'cola' }) }
    $r.tags = $tags

    $r.trunk = if ($trunk) { $trunk.part } else { '' }
    $r.spine = $spine.part
    $r.decor = (@($decor | Where-Object { -not $_.neck } | ForEach-Object { "$($_.name) [$($_.category)]" }) -join ', ')
    $r.neckDecor = (@($decor | Where-Object { $_.neck } | ForEach-Object { "$($_.name) [$($_.category)]" }) -join ', ')

    # --- Avisos ---
    $skull = Skull $main.part
    if (-not $skull) {
        [void]$r.warnings.Add('cabeza sin cubos con volumen (solo planos)')
    } else {
        $r.skull = $skull.bone
        if ($skull.fallback) { [void]$r.warnings.Add("cráneo de emergencia: solo hay adornos ($($skull.bone))") }
        if (-not $whole) {
            # Dónde está el pivote (el punto que se pega en el cuello del cuerpo) respecto al cráneo
            $box = $skull.box
            $inner = $true; $dist2 = 0.0
            for ($i = 0; $i -lt 3; $i++) {
                $size = $box[$i + 3] - $box[$i]
                if ($size -le 0) { $inner = $false; continue }
                $rel = ($pivot[$i] - $box[$i]) / $size
                if ($rel -lt 0.25 -or $rel -gt 0.75) { $inner = $false }
                $d = [math]::Max(0, [math]::Max($box[$i] - $pivot[$i], $pivot[$i] - $box[$i + 3]))
                $dist2 += $d * $d
            }
            $dist = [math]::Sqrt($dist2)
            # No es un fallo por sí solo (Charizard, Pidgey...), pero al juntar una cabeza "centro" con un cuerpo "base"
            # la cabeza queda desplazada media cabeza (ver skullAlign en FusionGraft)
            if ($inner) { $r.attach = 'centro' }
            elseif ($dist -gt (MeanSide $box)) {
                $r.attach = 'lejos'
                [void]$r.warnings.Add("pivote de la cabeza lejos del cráneo ($([math]::Round($dist, 1)) px)")
            }
            else { $r.attach = 'base' }
        }
    }
    # Cara fuera de la cabeza: al cambiar la cabeza se queda la cara vieja en el cuerpo
    $outside = @()
    foreach ($f in (Paths { param($x) ($x.StartsWith('eye') -or $x.StartsWith('mouth') -or $x.StartsWith('face')) })) {
        $inHead = $false
        foreach ($h in $heads) { if ($f -contains $h.part) { $inHead = $true } }
        if (-not $inHead) { $outside += $f[$f.Count - 1] }
    }
    if ($outside.Count) { [void]$r.warnings.Add("cara fuera de la cabeza: $(($outside | Select-Object -First 3) -join ', ')") }
    if (-not $whole) {
        if (-not $trunk) { [void]$r.warnings.Add('sin tronco (ningún hueso con cubos hasta la cabeza): sin adornos') }
        else {
            $bones = Sub $spine.path ($spine.path.Count - 1)
            $solidVol = 0.0; $planes = $false
            foreach ($bn in $bones) { foreach ($c in (OwnCubes $bn)) { $v = CubeVol $c; $solidVol += $v; if ($v -le 0) { $planes = $true } } }
            if ($solidVol -le 0 -and $planes) { [void]$r.warnings.Add('tronco hecho solo de planos: escala de adornos poco fiable') }
            if ($solidVol -gt 0) {
                $big = @($decor | Where-Object { -not $_.neck -and $_.category -ne 'arm' -and (TreeVol $_.name) -gt $solidVol } | ForEach-Object { $_.name })
                if ($big.Count) { [void]$r.warnings.Add("adorno mayor que el tronco: $(($big | Select-Object -First 3) -join ', ')") }
            }
        }
    }
    $r
}

# ---------------------------------------------------------------------------------------------------------------
# 4. Tabla
# ---------------------------------------------------------------------------------------------------------------
$rows = New-Object System.Collections.ArrayList
$failed = @()
foreach ($id in $usage.Keys) {
    if (-not $models.ContainsKey($id)) { continue }
    $uses = $usage[$id] | Sort-Object { $_.dex }, { $_.species }
    try { $a = Analyze $files[$models[$id].path].text } catch { $failed += "$id ($($_.Exception.Message))"; continue }
    $first = @($uses)[0]
    # Una entrada por especie: su nombre si es su modelo base; si solo lo usa con aspects, el primero y cuántos más
    # (Pikachu usa el mismo modelo con una docena de aspects cosméticos)
    $names = @($uses | Group-Object { $_.species } | ForEach-Object {
        if (@($_.Group | Where-Object { -not $_.aspects }).Count) { $_.Name }
        else {
            $more = @($_.Group).Count - 1
            "$($_.Name) [$(@($_.Group)[0].aspects)]" + $(if ($more -gt 0) { " (+$more)" } else { '' })
        }
    })
    # base: el modelo normal de alguna especie; forma: solo con aspects (sexo, regional, megas...); cosmético: solo con
    # objetos cosméticos (las gorras de Pikachu): se pueden dejar para el final
    $kind = if (@($uses | Where-Object { -not $_.aspects }).Count) { 'base' }
            elseif (@($uses | Where-Object { $_.aspects -notmatch 'cosmetic_item' }).Count -eq 0) { 'cosmético' }
            else { 'forma' }
    [void]$rows.Add([pscustomobject][ordered]@{
        dex = $first.dex; especie = ($names -join ', '); tipo = $kind; modelo = $id; fuente = $models[$id].tag
        forma = $a.shape; etiquetas = ($a.tags -join ', '); cabeza = $a.head; pivote = $a.pivot; pegado = $a.attach; cuello = $a.neck
        craneo = $a.skull; tronco = $a.trunk; fin_columna = $a.spine; cola = $a.tail
        adornos_tronco = $a.decor; adornos_cuello = $a.neckDecor; n_avisos = $a.warnings.Count
        avisos = ($a.warnings -join ' | ')
    })
}
$rows = @($rows | Sort-Object dex, modelo)

New-Item -ItemType Directory -Force $OutDir | Out-Null
$utf8Bom = New-Object Text.UTF8Encoding $true
$utf8 = New-Object Text.UTF8Encoding $false
$csv = @($rows | ConvertTo-Csv -Delimiter ';' -NoTypeInformation)
[IO.File]::WriteAllLines((Join-Path $OutDir 'especies.csv'), $csv, $utf8Bom)

# Resumen
$md = New-Object System.Collections.Generic.List[string]
$md.Add('# Tabla de especies (generada)')
$md.Add('')
$md.Add('Generada por `tools/species-table.ps1` (no editar a mano: se sobrescribe). Una fila por **modelo en uso**')
$md.Add('(una especie puede tener varios: formas, sexos, remodelos de AllTheMons). Datos en `especies.csv` (Excel).')
$md.Add('Copia aproximada de las reglas de `FusionGraft`: postura del `.geo`, sin animaciones.')
$md.Add('')
$md.Add("Modelos en uso: **$($rows.Count)** · con algún aviso: **$(@($rows | Where-Object { $_.n_avisos -gt 0 }).Count)**")
$md.Add("Por tipo: $((@($rows | Group-Object tipo | Sort-Object Count -Descending | ForEach-Object { "$($_.Name) $($_.Count)" })) -join ' · ') (columna ``tipo``: base = modelo normal de una especie; forma = sexo, regional, mega...; cosmético = solo con objetos cosméticos)")
if ($failed.Count) { $md.Add("No se pudieron leer: $($failed -join ', ')") }
$md.Add('')
$md.Add('## Categorías')
$md.Add('')
$md.Add('| Forma | Modelos | Con avisos | Ejemplos |')
$md.Add('|---|---:|---:|---|')
foreach ($g in ($rows | Group-Object forma | Sort-Object Count -Descending)) {
    $ex = (@($g.Group | Select-Object -First 6 | ForEach-Object { ($_.especie -split ',')[0] }) -join ', ')
    $md.Add("| $($g.Name) | $($g.Count) | $(@($g.Group | Where-Object { $_.n_avisos -gt 0 }).Count) | $ex |")
}
$md.Add('')
$md.Add('Etiquetas (se pueden sumar a cualquier forma):')
$md.Add('')
foreach ($t in @('varias cabezas', 'racimo', 'alas', 'cola (punta)', 'cola')) {
    $n = @($rows | Where-Object { ($_.etiquetas -split ', ') | Where-Object { $_.StartsWith($t) -and -not ($t -eq 'cola' -and $_ -eq 'cola (punta)') } }).Count
    $md.Add("- $($t): $n")
}
$md.Add('')
$md.Add('Punto de pegado de la cabeza (columna `pegado`):')
$md.Add('')
foreach ($g in ($rows | Where-Object { $_.pegado } | Group-Object pegado | Sort-Object Count -Descending)) {
    $md.Add("- $($g.Name): $($g.Count) ($((@($g.Group | Select-Object -First 6 | ForEach-Object { ($_.especie -split ',')[0] })) -join ', ')...)")
}
$md.Add('')
$md.Add('## Avisos')
$md.Add('')
$md.Add('| Aviso | Modelos | Ejemplos |')
$md.Add('|---|---:|---|')
$kinds = @{}
foreach ($row in $rows) {
    foreach ($w in ($row.avisos -split ' \| ' | Where-Object { $_ })) {
        $kind = ($w -replace ':.*$', '' -replace '\(.*\)', '').Trim()
        if (-not $kinds.ContainsKey($kind)) { $kinds[$kind] = New-Object System.Collections.ArrayList }
        [void]$kinds[$kind].Add(($row.especie -split ',')[0])
    }
}
foreach ($k in ($kinds.Keys | Sort-Object { -$kinds[$_].Count })) {
    $md.Add("| $k | $($kinds[$k].Count) | $((@($kinds[$k] | Select-Object -Unique -First 8)) -join ', ') |")
}
$md.Add('')
$md.Add('## Columnas de `especies.csv`')
$md.Add('')
$md.Add('- `forma`: sin cabeza (como cabeza se pega entero; como cuerpo lleva la cabeza encima), todo cabeza (como cabeza se pega entero), serpiente/pez (cola = solo la punta), sin patas, bípedo, cuadrúpedo, muchas patas.')
$md.Add('- `cabeza` / `pivote` / `cuello`: hueso de la cabeza principal, su pivote (el punto que se pega donde estaba la cabeza del cuerpo) y el hueso del que cuelga.')
$md.Add('- `pegado`: dónde está ese pivote respecto al cráneo. "base": en el cuello (lo normal); "centro": en medio de la cabeza (no es un fallo, pero al juntar una cabeza "centro" con un cuerpo "base", o al revés, queda desplazada media cabeza); "lejos": fuera del cráneo.')
$md.Add('- `craneo`: hueso del cubo principal de la cabeza (posición y tamaño de la cabeza).')
$md.Add('- `tronco` / `fin_columna`: hueso del tronco y hasta dónde llega la columna (cuello o cabeza): marcan dónde y a qué escala van adornos y cola.')
$md.Add('- `cola`: piezas de la cola ("(punta)": serpientes y peces, solo se cambia la punta).')
$md.Add('- `adornos_tronco` / `adornos_cuello`: hueso [clase]. Los del cuello van con la cabeza pegada.')
[IO.File]::WriteAllLines((Join-Path $OutDir 'README.md'), $md, $utf8)
"Modelos: $($rows.Count); con avisos: $(@($rows | Where-Object { $_.n_avisos -gt 0 }).Count); errores: $($failed.Count)"
