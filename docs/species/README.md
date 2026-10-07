# Tabla de especies (generada)

Generada por `tools/species-table.ps1` (no editar a mano: se sobrescribe). Una fila por **modelo en uso**
(una especie puede tener varios: formas, sexos, remodelos de AllTheMons). Datos en `especies.csv` (Excel).
Copia aproximada de las reglas de `FusionGraft`: postura del `.geo`, sin animaciones.

Modelos en uso: **1323** · con algún aviso: **112**
Por tipo: base 1019 · forma 278 · cosmético 26 (columna `tipo`: base = modelo normal de una especie; forma = sexo, regional, mega...; cosmético = solo con objetos cosméticos)

## Categorías

| Forma | Modelos | Con avisos | Ejemplos |
|---|---:|---:|---|
| bípedo | 574 | 29 | charmander, charmeleon, charizard, squirtle, wartortle, blastoise |
| cuadrúpedo | 255 | 7 | bulbasaur [female], bulbasaur, ivysaur [female], ivysaur, venusaur [female], venusaur |
| sin cabeza | 174 | 29 | metapod, dugtrio, dugtrio [alolan], weepinbell, magnemite, gastly |
| sin patas | 167 | 37 | kakuna, arbok, diglett, diglett [alolan], victreebel, geodude |
| serpiente/pez | 55 | 2 | ekans, bellsprout, magneton, onix, gyarados [female], gyarados |
| todo cabeza | 55 | 1 | clefairy, clefable, zubat [female], zubat, golbat [female], golbat |
| muchas patas | 33 | 5 | weedle, venomoth, omanyte, spinarak, ariados, yanma |
| patas: 3 | 6 | 2 | boldore, ferrothorn, aromatisse, skrelp, grapploct, arboliva |
| patas: 1 | 4 | 0 | swalot, lilligant, maractus, accelgor |

Etiquetas (se pueden sumar a cualquier forma):

- varias cabezas: 14
- racimo: 3
- alas: 134
- cola (punta): 31
- cola: 681

Punto de pegado de la cabeza (columna `pegado`):

- base: 886 (bulbasaur [female], bulbasaur, ivysaur [female], ivysaur, venusaur [female], venusaur...)
- centro: 195 (charmander, charmeleon, charizard, pidgey, pidgeotto, pidgeot...)
- lejos: 13 (mawile, cresselia, klang, klinklang, avalugg, palossand...)

## Avisos

| Aviso | Modelos | Ejemplos |
|---|---:|---|
| sin tronco | 37 | jigglypuff, geodude, geodude [alolan], shellder, cloyster, omanyte, omastar, surskit |
| cara fuera de la cabeza | 30 | slowbro, magneton, seaking [female], seaking, shiftry [female], shiftry, sharpedo, corphish |
| posible cabeza por el nombre | 15 | magnemite, sunkern, corsola, corsola [galarian], gulpin, feebas, drifloon, drifblim |
| posible cabeza | 14 | dugtrio, dugtrio [alolan], exeggcute, pineco, shelgon, combee [female] (+1), combee, vespiquen |
| pivote de la cabeza lejos del cráneo | 13 | mawile, cresselia, klang, klinklang, avalugg, palossand, xurkitree, guzzlord |
| cráneo de emergencia | 4 | trubbish, garbodor, lampent, hoopa |
| el tronco es el cuello | 2 | mismagius, silicobra |
| tronco hecho solo de planos | 1 | fluttermane |
| adorno mayor que el tronco | 1 | sandyshocks |

## Columnas de `especies.csv`

- `forma`: sin cabeza (como cabeza se pega entero; como cuerpo lleva la cabeza encima), todo cabeza (como cabeza se pega entero), serpiente/pez (cola = solo la punta), sin patas, bípedo, cuadrúpedo, muchas patas.
- `cabeza` / `pivote` / `cuello`: hueso de la cabeza principal, su pivote (el punto que se pega donde estaba la cabeza del cuerpo) y el hueso del que cuelga.
- `pegado`: dónde está ese pivote respecto al cráneo. "base": en el cuello (lo normal); "centro": en medio de la cabeza (no es un fallo, pero al juntar una cabeza "centro" con un cuerpo "base", o al revés, queda desplazada media cabeza); "lejos": fuera del cráneo.
- `craneo`: hueso del cubo principal de la cabeza (posición y tamaño de la cabeza).
- `tronco` / `fin_columna`: hueso del tronco y hasta dónde llega la columna (cuello o cabeza): marcan dónde y a qué escala van adornos y cola.
- `cola`: piezas de la cola ("(punta)": serpientes y peces, solo se cambia la punta).
- `adornos_tronco` / `adornos_cuello`: hueso [clase]. Los del cuello van con la cabeza pegada.
