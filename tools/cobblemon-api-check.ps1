param([string]$ModJar, [string]$CobblemonJar)
# Busca en el jar del mod todas las referencias a clases, metodos y campos de Cobblemon y comprueba que existen en el
# jar de Cobblemon dado (con la misma firma), mirando tambien las clases padre e interfaces.
$javap = (Get-ChildItem "$env:USERPROFILE\.jdks" -Recurse -Filter javap.exe | Where-Object FullName -match '21' | Select-Object -First 1).FullName
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($ModJar)
$classes = $zip.Entries | Where-Object { $_.FullName -like 'com/arnau/*.class' } | ForEach-Object { $_.FullName -replace '\.class$','' -replace '/','.' }
$zip.Dispose()

$refs = @{}   # "owner|name|desc|kind" -> lista de clases nuestras que lo usan
foreach ($c in $classes) {
    $out = & $javap -c -p -cp $ModJar $c 2>$null
    foreach ($line in $out) {
        if ($line -match '//\s*(Method|InterfaceMethod|Field)\s+(com/cobblemon/[\w/$]+)\.([\w$<>"]+):(\S+)') {
            $key = "$($Matches[2])|$($Matches[3].Trim('"'))|$($Matches[4])|$($Matches[1])"
            if (-not $refs.ContainsKey($key)) { $refs[$key] = New-Object System.Collections.Generic.HashSet[string] }
            [void]$refs[$key].Add($c.Split('.')[-1])
        } elseif ($line -match '//\s*class\s+(com/cobblemon/[\w/$]+)') {
            $key = "$($Matches[1])|||Class"
            if (-not $refs.ContainsKey($key)) { $refs[$key] = New-Object System.Collections.Generic.HashSet[string] }
            [void]$refs[$key].Add($c.Split('.')[-1])
        }
    }
}

$members = @{}  # owner -> @{ supers; sigs }
function Get-Members($owner) {
    if ($members.ContainsKey($owner)) { return $members[$owner] }
    $out = & $javap -p -s -cp $CobblemonJar ($owner -replace '/','.') 2>$null
    if (-not $out) { $members[$owner] = $null; return $null }
    $supers = @()
    $head = ($out | Where-Object { $_ -match '(class|interface) ' } | Select-Object -First 1)
    if ($head -match 'extends ([\w.$]+(?:,\s*[\w.$]+)*)') { $supers += $Matches[1] -split ',\s*' }
    if ($head -match 'implements ([\w.$<>, ]+)\s*\{') { $supers += ($Matches[1] -replace '<[^>]*>','') -split ',\s*' }
    $sigs = New-Object System.Collections.Generic.HashSet[string]
    for ($i = 0; $i -lt $out.Count - 1; $i++) {
        if ($out[$i + 1] -match '^\s+descriptor:\s*(\S+)') {
            $d = $Matches[1]
            $name = if ($out[$i] -match '([\w$]+)\(') { $Matches[1] } elseif ($out[$i] -match '([\w$]+);\s*$') { $Matches[1] } else { '' }
            if ($out[$i] -match "^\s+.*\b$([regex]::Escape(($owner -split '/')[-1]))\(") { $name = '<init>' }
            [void]$sigs.Add("$name$d")
        }
    }
    $r = @{ supers = ($supers | Where-Object { $_ } | ForEach-Object { $_.Trim() -replace '\.','/' }); sigs = $sigs }
    $members[$owner] = $r
    return $r
}
function Has-Member($owner, $sig, $depth) {
    if ($depth -gt 8) { return $false }
    $m = Get-Members $owner
    if (-not $m) { return $false }
    if ($m.sigs.Contains($sig)) { return $true }
    foreach ($s in $m.supers) { if ($s -like 'com/cobblemon/*' -and (Has-Member $s $sig ($depth + 1))) { return $true } }
    return $false
}

$missing = 0
foreach ($key in ($refs.Keys | Sort-Object)) {
    $p = $key -split '\|'
    $owner = $p[0]; $name = $p[1]; $desc = $p[2]; $kind = $p[3]
    $users = ($refs[$key] | Sort-Object) -join ', '
    if ($kind -eq 'Class') {
        if (-not (Get-Members $owner)) { Write-Output "FALTA clase $owner   (usada en: $users)"; $missing++ }
        continue
    }
    if (-not (Get-Members $owner)) { Write-Output "FALTA clase $owner (para $name)   (usada en: $users)"; $missing++; continue }
    if (-not (Has-Member $owner "$name$desc" 0)) { Write-Output "FALTA $kind $($owner -replace 'com/cobblemon/mod/common/','').$name$desc   (usada en: $users)"; $missing++ }
}
Write-Output "Referencias revisadas: $($refs.Count); que faltan: $missing"
