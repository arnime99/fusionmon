param([string]$OldJar, [string]$NewJar)
# Compara, para cada enganche de los mixins de Fusionmon, el jar viejo y el nuevo de Cobblemon:
# que el metodo exista, su firma, y cuantas veces llama a lo que interceptamos.
$javap = (Get-ChildItem "$env:USERPROFILE\.jdks" -Recurse -Filter javap.exe | Where-Object FullName -match '21' | Select-Object -First 1).FullName
$R = 'Lcom/cobblemon/mod/common/api/riding/RidingProperties;'
$riding = "FormData.getRiding:()$R"
$checks = @(
  @{c='com.cobblemon.mod.common.entity.pokemon.PokemonEntity'; m=@('getSeats','getRideProp','refreshRiding'); call=$riding},
  @{c='com.cobblemon.mod.common.entity.pokemon.PokemonEntity'; m=@('method_18377'); call='FormData.getBaseScale:()F'},
  @{c='com.cobblemon.mod.common.entity.pokemon.PokemonEntity'; m=@('method_18377'); call='FormData.getHitbox:'},
  @{c='com.cobblemon.mod.common.entity.pokemon.PokemonEntity'; m=@('method_5674'); call=''},
  @{c='com.cobblemon.mod.common.battles.BattleRegistry'; m=@('startShowdown'); call=''},
  @{c='com.cobblemon.mod.common.pokemon.evolution.controller.ServerEvolutionController'; m=@('start'); call=''},
  @{c='com.cobblemon.mod.common.pokemon.Pokemon'; m=@('getPrimaryType','getSecondaryType','getTypes','getDisplayName','showdownId','getEvolutions'); call=''},
  @{c='com.cobblemon.mod.common.pokemon.Pokemon'; m=@('updateMovesOnFormChange'); call='LearnsetQuery.canLearn:(Lcom/cobblemon/mod/common/api/moves/MoveTemplate;Lcom/cobblemon/mod/common/api/pokemon/moves/Learnset;)Z'},
  @{c='com.cobblemon.mod.common.pokemon.Pokemon'; m=@('getRiding','getBaseRideStat','getMaxRideBoost','getRideStat'); call=$riding},
  @{c='com.cobblemon.mod.common.client.render.pokemon.PokemonRenderer'; m=@('scale'); call='FormData.getBaseScale:()F'},
  @{c='com.cobblemon.mod.common.api.riding.behaviour.RidingController'; m=@('changeBehaviour'); call=$riding},
  @{c='com.cobblemon.mod.common.client.gui.summary.widgets.screens.stats.StatWidget'; m=@('<init>','getRideBehaviourIndex','setRideBehaviourIndex','getTabIndexFromPos','method_48579'); call=$riding},
  @{c='com.cobblemon.mod.common.client.net.pokemon.update.ClientboundSeatAssignmentPacketHandler'; m=@('handle'); call=$riding},
  @{c='com.cobblemon.mod.common.client.gui.summary.widgets.screens.info.InfoWidget'; m=@('method_48579'); call='Species.getTranslatedName:'},
  @{c='com.cobblemon.mod.common.client.render.models.blockbench.PosableModel'; m=@('render'); call=''},
  @{c='com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository'; m=@('getPoser','getTexture','getLayers','getTextureNoSubstitute'); call=''},
  @{c='com.cobblemon.mod.common.client.gui.summary.Summary'; m=@('method_25394'); call=''},
  @{c='com.cobblemon.mod.common.client.render.VaryingRenderableResolver'; m=@('getTexture'); call=''}
)
$fields = @(
  @{c='com.cobblemon.mod.common.api.riding.behaviour.RidingController'; f='entity'},
  @{c='com.cobblemon.mod.common.client.gui.summary.widgets.screens.stats.StatWidget'; f='pokemon'},
  @{c='com.cobblemon.mod.common.client.gui.summary.widgets.screens.info.InfoWidget'; f='pokemon'}
)

$cache = @{}
function Get-Methods($jar, $cls) {
  $key = "$jar|$cls"
  if ($cache.ContainsKey($key)) { return $cache[$key] }
  $out = & $javap -c -p -cp $jar $cls 2>$null
  $methods = @{}; $current = $null
  foreach ($line in $out) {
    if ($line -match '^  \S.*\(.*\).*;$' -or $line -match '^  \S.*\(.*\)\s*throws.*;$') {
      $name = if ($line -match '([\w$<>]+)\(') { $Matches[1] } else { '?' }
      if ($line -match "^  .*\b$([regex]::Escape($cls.Split('.')[-1]))\(") { $name = '<init>' }
      $current = [pscustomobject]@{ sig = $line.Trim(); body = New-Object System.Text.StringBuilder }
      if (-not $methods.ContainsKey($name)) { $methods[$name] = New-Object System.Collections.ArrayList }
      [void]$methods[$name].Add($current)
    } elseif ($current) { [void]$current.body.AppendLine($line) }
  }
  $result = @{ methods = $methods; raw = $out }
  $cache[$key] = $result
  return $result
}

$problems = 0
foreach ($k in $checks) {
  $old = Get-Methods $OldJar $k.c; $new = Get-Methods $NewJar $k.c
  $short = $k.c.Split('.')[-1]
  if (-not $new.raw) { Write-Output "XX $short : CLASE NO ENCONTRADA en el jar nuevo"; $problems++; continue }
  foreach ($m in $k.m) {
    $o = $old.methods[$m]; $n = $new.methods[$m]
    if (-not $n) { Write-Output "XX $short.$m : ya no existe"; $problems++; continue }
    $oSig = ($o | ForEach-Object sig) -join ' | '; $nSig = ($n | ForEach-Object sig) -join ' | '
    $sigNote = if ($oSig -eq $nSig) { 'firma igual' } else { $problems++; "FIRMA CAMBIADA`n     antes: $oSig`n     ahora: $nSig" }
    $callNote = ''
    if ($k.call) {
      $oc = ($o | ForEach-Object { ([regex]::Matches($_.body.ToString(), [regex]::Escape($k.call))).Count } | Measure-Object -Sum).Sum
      $nc = ($n | ForEach-Object { ([regex]::Matches($_.body.ToString(), [regex]::Escape($k.call))).Count } | Measure-Object -Sum).Sum
      $callNote = " · llamadas $($k.call.Split(':')[0]): $oc -> $nc"
      if ($nc -eq 0) { $problems++; $callNote += '  <-- YA NO LA HACE' }
    }
    $flag = if ($sigNote -eq 'firma igual' -and -not ($callNote -match 'YA NO')) { 'ok' } else { 'XX' }
    Write-Output "$flag $short.$m : $sigNote$callNote"
  }
}
foreach ($f in $fields) {
  $new = Get-Methods $NewJar $f.c
  $has = $new.raw | Where-Object { $_ -match "^\s+\S.*\s$($f.f);$" }
  if ($has) { Write-Output "ok campo $($f.c.Split('.')[-1]).$($f.f) : $((@($has)[0]).Trim())" } else { Write-Output "XX campo $($f.c.Split('.')[-1]).$($f.f) : NO ENCONTRADO"; $problems++ }
}
Write-Output "`nProblemas: $problems"
