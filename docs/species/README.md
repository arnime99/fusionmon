# Tabla de especies (generada)

Generada por `tools/species-table.ps1` (no editar a mano: se sobrescribe). Una fila por **modelo en uso**
(una especie puede tener varios: formas, sexos, remodelos de AllTheMons). Datos en `especies.csv` (Excel).
Copia aproximada de las reglas de `FusionGraft`: postura del `.geo`, sin animaciones.

Modelos en uso: **1329** · con algún aviso: **109**
Por tipo: base 1023 · forma 280 · cosmético 26 (columna `tipo`: base = modelo normal de una especie; forma = sexo, regional, mega...; cosmético = solo con objetos cosméticos)

## Categorías

| Forma | Modelos | Con avisos | Ejemplos |
|---|---:|---:|---|
| bípedo | 578 | 28 | charmander, charmeleon, charizard, squirtle, wartortle, blastoise |
| cuadrúpedo | 254 | 10 | bulbasaur [female], bulbasaur, ivysaur [female], ivysaur, venusaur [female], venusaur |
| sin patas | 182 | 43 | kakuna, arbok, diglett, diglett [alolan], victreebel, geodude |
| sin cabeza | 127 | 20 | metapod, dugtrio, dugtrio [alolan], weepinbell, magnemite, gastly |
| todo cabeza | 94 | 1 | clefairy, clefable, zubat [female], zubat, golbat [female], golbat |
| serpiente/pez | 54 | 2 | ekans, arcanine [hisuian] (+1), bellsprout, magneton, onix, gyarados [female] |
| muchas patas | 30 | 3 | weedle, venomoth, omanyte, spinarak, ariados, yanma |
| patas: 3 | 5 | 2 | boldore, ferrothorn, skrelp, grapploct, arboliva |
| patas: 1 | 5 | 0 | swalot, lilligant, maractus, accelgor, hatterene |

Etiquetas (se pueden sumar a cualquier forma):

- varias cabezas: 14
- racimo: 3
- alas: 134
- cola (punta): 39
- cola: 720

Punto de pegado de la cabeza (columna `pegado`):

- base: 894 (bulbasaur [female], bulbasaur, ivysaur [female], ivysaur, venusaur [female], venusaur...)
- centro: 197 (charmander, charmeleon, charizard, pidgey, pidgeotto, pidgeot...)
- lejos: 16 (mawile, shelgon, klang, klinklang, aromatisse, avalugg...)

## Avisos

| Aviso | Modelos | Ejemplos |
|---|---:|---|
| sin tronco | 42 | jigglypuff, geodude, geodude [alolan], shellder, cloyster, omanyte, omastar, surskit |
| cara fuera de la cabeza | 28 | slowbro, magneton, seaking [female], seaking, shiftry [female], shiftry, sharpedo, corphish |
| pivote de la cabeza lejos del cráneo | 16 | mawile, shelgon, klang, klinklang, aromatisse, avalugg, avalugg [hisuian], palossand |
| posible cabeza por el nombre | 11 | magnemite, sunkern, corsola, corsola [galarian], gulpin, feebas, inkay, nihilego |
| posible cabeza | 9 | dugtrio, dugtrio [alolan], exeggcute, pineco, combee [female] (+1), combee, magnezone, doublade |
| cráneo de emergencia | 5 | shelgon, trubbish, garbodor, lampent, hoopa |
| el tronco es el cuello | 3 | wurmple, mismagius, silicobra |
| adorno mayor que el tronco | 1 | sandyshocks |
| cabeza sin cubos con volumen | 1 | spiritomb |

## Columnas de `especies.csv`

- `forma`: sin cabeza (como cabeza se pega entero; como cuerpo lleva la cabeza encima), todo cabeza (como cabeza se pega entero), serpiente/pez (cola = solo la punta), sin patas, bípedo, cuadrúpedo, muchas patas.
- `cabeza` / `pivote` / `cuello`: hueso de la cabeza principal, su pivote (el punto que se pega donde estaba la cabeza del cuerpo) y el hueso del que cuelga.
- `pegado`: dónde está ese pivote respecto al cráneo. "base": en el cuello (lo normal); "centro": en medio de la cabeza (no es un fallo, pero al juntar una cabeza "centro" con un cuerpo "base", o al revés, queda desplazada media cabeza); "lejos": fuera del cráneo.
- `craneo`: hueso del cubo principal de la cabeza (posición y tamaño de la cabeza).
- `tronco` / `fin_columna`: hueso del tronco y hasta dónde llega la columna (cuello o cabeza): marcan dónde y a qué escala van adornos y cola.
- `cola`: piezas de la cola ("(punta)": serpientes y peces, solo se cambia la punta).
- `adornos_tronco` / `adornos_cuello`: hueso [clase]. Los del cuello van con la cabeza pegada.
