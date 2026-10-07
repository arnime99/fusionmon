# Registro de fallos visuales (cabeza sobre cuerpo)

## Cómo trabajamos

1. El usuario prueba en el visor (`/fusiondex`) y apunta aquí: **cabeza + cuerpo · síntoma · captura** (si hay).
2. Claude agrupa los fallos por **patrón** (qué tienen en común los modelos), lo confirma leyendo los `.geo.json`
   y simulando la regla sobre **todos** los modelos (scripts de PowerShell sobre el jar de Cobblemon y
   `run/resourcepacks/AllTheMons-R4.0.zip`), y arregla la regla, no el Pokémon.
3. El usuario vuelve a probar las parejas de la lista; lo que funcione se tacha y se hace commit.

Nada de código por especie: si hiciera falta un caso a mano, sería con datos (JSON de ajustes por especie).

## Siguiente: revisión por especies (en marcha)

Hecho: **tabla automática** `docs/species/` (`README.md` resumen, `especies.csv` para Excel), generada por
`tools/species-table.ps1` (copia de las reglas de `FusionGraft`: si cambia una regla, cambiarla también ahí y
regenerar). **Modo inspector** (`/fusioninspect`, sin commit, pendiente de probar): la revisión del usuario queda en
`run/fusionmon/species-review.json`.


Idea del usuario: probar parejas al azar no acaba nunca (~1 000 000 de combinaciones). Cada fallo es o una pieza mal
detectada en **una especie** o una **regla de combinación** mala, así que se revisa en dos ejes:

1. **Especies una a una** (~1400 modelos con formas y AllTheMons):
   - Tabla automática (script) con la categoría de cada modelo (bípedo, cuadrúpedo, serpiente/pez, todo cabeza, cuerpo
     sin cabeza, varias cabezas, varios individuos...), lo detectado (cabeza, cráneo, punto del cuello, tronco, cola,
     adornos) y **avisos** de lo sospechoso. Se revisan primero los avisos y una muestra de cada categoría.
   - **Modo inspector** en `/fusiondex`: una especie sola con la cabeza aislada (lo que se pegaría), el punto del
     cuello, la caja del cráneo y adornos/cola en otro color; teclas bien/fallo + nota, guardado en un archivo.
   - Lo que no tenga regla general → **ajustes por especie en JSON** (Magneton = 3 cabezas, hueso de la cabeza de
     Skeledirge...), ampliable por resource packs. Nunca código por especie.
2. **Reglas por categoría**: una lista fija de cruces (2 representantes por categoría, ~50-60 parejas) que se repasa
   tras cada cambio para ver si se ha roto algo.

### Revisión 1: los 200 con avisos (`run/fusionmon/species-review.json`, 23 bien, 177 fallo)

Patrones (por orden de cuántas especies arreglan):

- **A. El cuerpo no está en el camino a la cabeza** (~27): el tronco cuelga al lado del cuello/cabeza, no por encima
  (`torso` → `torso1_scale` y `torso` → `torso2` → cabeza en Wigglytuff; `torso` → `belly` y `torso` → `neck` en
  Snorlax; `torso2` hermano de `head` en Yamper; `thorax` en Ariados). Hoy: "sin tronco" o "el tronco es el cuello".
  Wigglytuff, Yamper, Snorlax, Ariados, Keldeo, Slugma, Magcargo, Blipbug, Mismagius, Rockruff, Necrozma, Bellibolt,
  Slurpuff, Misdreavus, Masquerain, Iron Moth, Revavroom, Tapu Bulu, Spinarak, Anorith, Clauncher, Amoonguss,
  Jellicent, Frillish, Galvantula, Wailord, Veluza, Flapple. Idea: si no hay tronco en el camino (o es un solo cuello),
  buscarlo en las ramas que cuelgan del camino y no son cabeza, patas, brazos, cola ni adorno.
- **B. Serpientes** (11): el cuerpo es una cadena de segmentos al lado de la cabeza (`body` → `head` y `body` → `tail`...
  en Ekans; `segment1...` en Onix). Tronco = la cadena sin la punta; cola = la punta. Ekans, Onix, Steelix, Rayquaza,
  Dratini, Eelektrik, Centiskorch, Sizzlipede, Huntail, Gorebyss, Silicobra.
- **C. Todo cabeza con adornos/brazos/patas** (~38, + 5 peces): no hay cuerpo, solo cabeza con cosas. Como cabeza
  debería pegarse entero. Chimecho, Comfey, Cottonee, Eldegoss, Gossifleur, Litwick, Milcery, Shuppet, Yamask, Woobat,
  Cutiefly, Flutter Mane, Pecharunt, Swirlix, Duosion, Drifloon, Drifblim, Sunkern, Gulpin, Corsola, Klefki, Pineco,
  Boldore, Ferrothorn, Geodude, Jigglypuff, Azurill, Clobbopus, Scream Tail, Stonjourner, Metagross, Shelgon, Surskit,
  Toedscruel, Trubbish, Omanyte, Omastar, Inkay. Peces (cabeza + cuerpo = cabeza, aletas y cola): Sharpedo, Carvanha,
  Wailmer, Stunfisk, Seaking. Idea: con cabeza y sin tronco en ningún sitio (tras A) → "todo cabeza".
- **D. Coberturas que son tronco** (~16): vestido, lana, chaqueta, cesta, flor... envuelven el tronco: no son adornos.
  Gothitelle, Gothorita, Magearna, Jynx, Dolliv, Mareep, Dubwool, Wooloo, Obstagoon, Sneasler, Flabébé, Floette,
  Tarountula, Vullaby, Rabsca, Rellor (Meloetta, Reuniclus, Tapu Fini: bien así). Idea geométrica: un "adorno" cuya caja
  contiene casi todo el tronco es parte del tronco.
- **E. Mismo adorno con otro nombre** (7): caparazones `collar` (Chesnaught), `rock` (Dwebble), `back_bubbles`
  (Froakie), `mushroom` (Parasect), `cannon` (Genesect) → como `shell`; `tuff` (Mandibuzz) → alas. Idea: clase por
  posición y tamaño (lo grande sobre la espalda = caparazón) además del nombre.
- **F. Punto de pegado / cráneo** (~16): pivote lejos o adelantado → pegar en el borde de abajo del cráneo:
  Cresselia, Meltan, Xurkitree, Regieleki, Guzzlord, Mawile, Corphish, Crawdaunt, Raging Bolt, Hatterene. Cubo del
  cráneo equivocado: Coalossal (`coal`), Lampent, Galvantula, Seaking, Feebas (coge la cola), Vespiquen.
- **G. Varios individuos o cabezas** (12): Magneton, Sandy Shocks, Klink (2), Klang (2), Klinklang (3), Dugtrio,
  Exeggcute, Falinks, Combee, Vanilluxe (2), Doublade, Drakloak (lleva un Dreepy). Una cabeza por individuo:
  probablemente datos por especie (JSON).
- **H. Cabeza con otro nombre** (8): Blacephalon (`head_ball`), Skeledirge (`head_top`), Wo-Chien (`top_head` es
  caparazón; la cabeza es otro bloque), Dondozo, Silvally, Dhelmise (`wheel`), Nihilego, Magnemite.
- **Únicos, dejar o JSON**: Aegislash, Archaludon, Cofagrigus, Duraludon, Eternatus, Miraidon, Tatsugiri, Malamar,
  Magnezone, Applin, Enamorus, Yveltal, Hoopa.
- **Avisos que sobran**: "cara fuera de la cabeza" casi siempre está bien (Jirachi, Mimikyu, Shiftry, Palpitoad,
  Slowbro, Ogerpon, Sinistcha, Tapu Fini, Cosmoem, Crabominable); "adorno mayor que el tronco" con alas (Altaria,
  Swablu, Swanna, Talonflame, Togekiss, Fearow) está bien.

## Cambios sin commit (pendientes de probar)

- Inspector: punto naranja donde se engancha la cola (pivote de la principal).
- **A + B, tronco al lado** (`findTrunk` → `trunkBeside`, `TRUNK_BRANCHES`, `trunkSpace`, `findSpineEnd`): si el camino
  a la cabeza no tiene tronco o solo un cuello, el tronco es el hueso más grande de las ramas que cuelgan del camino y
  no son adorno, cabeza, extremidad (`NOT_TRUNK`: patas, manos, tentáculos...), cara ni cola; su caja abarca toda la
  rama, y la columna sale del centro de la rama. Cambia el tronco de 51 modelos base (Snorlax `belly`, Ariados
  `thorax`, Ekans la cadena `tail`...`tail5`, Onix los 14 segmentos, Wigglytuff, Yamper, Keldeo...). Quedan 34 sin
  tronco: casi todos del grupo C (todo cabeza). Jellicent y Galvantula siguen sin tronco (mirar aparte).
  Para probar en el inspector (vista partes/cuerpo, caja azul): Snorlax, Ariados, Wigglytuff, Yamper, Keldeo, Slugma,
  Magcargo, Ekans, Onix, Steelix, Rayquaza, Dratini, Huntail, Centiskorch; y en el visor alguna fusión con ellos de
  cuerpo y una cabeza con adornos (Charizard, Butterfree, Lapras), y Pikachu + Ekans/Onix.

## Abiertos

- **Adornos del tronco gigantes sobre Emboar**: Iron Valiant, Gardevoir, Roselia y Wartortle como cabeza sobre
  Emboar: su falda/caparazón sale enorme (el caparazón de Wartortle tapa todo el cuerpo). Las tres capturas son con
  Emboar: sospecha de `trunkScale` (tronco de Emboar mucho mayor que los finos de Gardevoir/Iron Valiant → ×3-4, tope
  4) o de que el tronco de Emboar se mide mal. Mismo patrón que Carvanha: revisar juntos (¿limitar la escala de
  adornos respecto a la de la cabeza?).
- **Silicobra** como cabeza: sale desplazada. Como cuerpo: parece que lo que falta se pinta como todo el cuerpo
  (cabeza blanca flotando encima). Hay dos modelos (Cobblemon y AllTheMons); en Cobblemon su tronco es un cuello.
- **Gabite + Salamence**: la cola sale desplazada y algo grande.
- **Steelix** como cabeza (Steelix + Charmander): cabeza muy grande; además la textura del cuerpo recoloreada tiene
  pocos colores y "píxeles grandes" comparada con la cabeza (las dos texturas tienen distinta densidad de píxeles:
  límite de los modelos, habría que pensar si se puede hacer algo).
- **Crawdaunt** como cuerpo: la cabeza pegada sale inclinada hacia atrás con todas las cabezas. Su modelo está girado
  45° (cubos en diagonal, `torso` 45° en X que `chest` deshace). Probar con "Pegar: cráneo".
- **Carvanha** como cabeza sobre cuerpos grandes (Darkrai): aletas enormes. Su "tronco" es solo la aleta dorsal (un
  plano de 3×11×6), así que la escala de tronco sale ×3-4. Su cuerpo es casi todo cabeza + mandíbula + cola, pero la
  cabeza no llega al 80 % para pegarlo entero. Idea: limitar la escala de adornos respecto a la de la cabeza.
- Decidir si "Pegar: cráneo" (`/fusionvisual align skull`) pasa a ser el modo por defecto para todas las cabezas.

## Ideas (no son fallos)

- (b) Los detalles de la cabeza de la especie del **cuerpo** sobre la cabeza nueva (antenas de Butterfree, crestas
  de Scyther, ramas de Corsola): `findExtras` del cuerpo, colocados cráneo→cráneo como en `renderOnBody`. Cambia todas
  las fusiones: como opción del visor primero.
- Patas de la especie de la cabeza en cuerpos sin cabeza (habría que levantar el modelo).
- Separar la cabeza de las partes móviles (imanes de Magnemite, rayos de Solrock) para montar cabezas con ellas.
- Varios individuos (Dugtrio, Magneton, Exeggcute, Falinks): una cabeza por individuo.
- Cabezas que no encontramos por el nombre (~30 modelos: Skeledirge, Wo-Chien, Blacephalon, Hydrapple, Drifblim,
  Heracross, Krabby...).
