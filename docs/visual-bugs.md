# Registro de fallos visuales (cabeza sobre cuerpo)

## Cómo trabajamos

1. **Por especies, no por parejas.** El usuario revisa en el inspector (`/fusioninspect`) y marca cada especie bien
   o fallo con una nota `pieza: qué pasa`; queda en `run/fusionmon/species-review.json` (**leerlo**). Las parejas del
   visor (`/fusiondex`) se usan para probar las reglas de combinación (posición, tamaños) y lo que se arregla.
2. Claude agrupa los fallos por **patrón**, lo confirma en los `.geo.json` y lo simula sobre **todos** los modelos con
   `tools/species-table.ps1` (copia de las reglas de `FusionGraft`: si cambia una regla, cambiarla también ahí y
   regenerar `docs/species/`), y arregla la regla, no el Pokémon.
3. El usuario vuelve a mirar las afectadas (**Filter: wrong** en el inspector) y se hace commit de lo que confirma.

Nada de código por especie: si hiciera falta un caso a mano, sería con datos (JSON de ajustes por especie).

## Familia cuerpo-cabeza (2026-10-09): SIGUIENTE CHAT EMPIEZA AQUÍ

Idea del usuario: no arreglar especie a especie, sino **normas por familia de estructura y por pieza** (brazos,
piernas, cola, alas, orejas/cuernos/pelo, cara). Primer paso hecho y probado (ver CLAUDE.md, "Familia cuerpo-cabeza"):
cuerpo-cabeza = sin cabeza + "todo cabeza". Como cabeza, se pega entero sin brazos; como cuerpo, se queda entero y
lleva complementos, cola, alas y los brazos de la cabeza si no tiene; cuerpo-cabeza + cuerpo-cabeza = complementos y
brazos, sin modelo hundido encima.

Feedback del usuario tras probarlo (pendiente de resolver en el nuevo chat):
1. Charizard + Voltorb: bien.
2. Pikachu + Gengar: bien; la cola de Pikachu queda un poco alta (encima).
3. Gengar + Pikachu: bien.
4. Gengar + Voltorb: perfecto. Al revés (Voltorb + Gengar) solo cambian los colores (Voltorb no aporta piezas) y
   **no aparece el blanco** de Voltorb: probablemente la regla de FusionPalette de no tocar casi blancos/negros.
   Mirarlo.
5. **Haunter (cabeza) + Pikachu: sus manos sueltas NO deben ir** sobre un cuerpo con brazos (Pikachu). Al revés que
   lo que se hizo: las manos de Haunter cuentan como brazos. Fuera al pegarlo como cabeza en cuerpos con brazos; sí
   se ponen en cuerpos-cabeza sin brazos. Hoy `isArm` no reconoce `hands`/`hand_*`, y se quedan siempre.
6. Clefairy ↔ Charizard: bien, pero **Clefairy como cabeza lleva su cola** (su `tail` cuelga de `torso`, que es su
   "cabeza": `collectLimbs` no mira dentro de la cabeza). Quitarla al pegarla entera, como los brazos.
7. Machoke + Voltorb: bien. Pero en cuerpos-cabeza **con** brazos propios (Clefairy) no se aplica nada de la cabeza
   (sale Clefairy recoloreado). Idea del usuario: a los cuerpo-cabeza ponerles **brazos y patas de la especie de la
   cabeza** (cambiar los suyos, como la cola), porque el cuerpo se queda entero y así se nota la otra especie.
   Excepción comentada: Geodude ya queda bien con la norma actual. Concretar con el usuario: ¿se cambian siempre los
   brazos/patas del cuerpo-cabeza por los de la cabeza, o solo si la cabeza tiene y el cuerpo es "pequeño"?
8. Tentacool, Venusaur + Lunatone, Pikachu + Voltorb...: bien.

Pendiente del plan:
- **Paso 2: detectar mejor la familia cuerpo-cabeza**: medir la cabeza sin contar brazos, piernas, cola, alas ni
  orejas ("cabeza ≥ X % del resto"); hoy Geodude (`head` = la roca, brazos colgando de `torso`) y Jigglypuff salen
  como bípedos. Simular sobre todos los modelos y enseñar al usuario qué modelos cambian de familia antes de aplicarlo.
- **Tema colas** (apuntado, sin decidir): "tipos de cola" (pequeña como la de Gengar, larga, de serpiente...) y quién
  la lleva. Por ahora como está: si los dos tienen cola, va la de la cabeza.
- Después: tabla de normas por pieza para todas las familias (`docs/`), y fichas de corrección por modelo (Fase B:
  solo correcciones, editor con lista de huesos en el inspector) para lo que las normas no cubran.

## Estado (2026-10-07)

Hecho y probado: tabla automática (`docs/species/`), inspector, **A + B** (tronco al lado: `trunkBeside`),
**"Pegar: cráneo" por defecto** (`Align.SKULL`; "pivote" y "base" siguen en el botón y en `/fusionvisual align`),
**tamaño "suave al crecer" por defecto** (`Sizing.SOFT_UP`: lo que se agranda, proporción^0,6; lo que se encoge,
exacto; `MIN_SCALE` 0,25), cola de serpientes sin `tail` (`chainTail`: último segmento).

**Siguiente:**
1. **Tamaños de las colas** (idea del usuario): hoy son proporcionales a la cola que sustituyen / al tronco. Tienen que
   notarse sin pasarse: equilibrar para los muy pequeños y los muy grandes. Las de serpiente (Onix, Steelix) salen
   diminutas o no se ven, y la cabeza de Onix/Steelix sigue grande en cuerpos pequeños. Ideas: medir el tronco por la
   raíz cúbica del volumen (una caja larga como la de Onix "mide" demasiado por su lado medio); un mínimo de cola
   respecto al cuerpo (que siempre se note) además del tope (`TAIL_GROWTH`).
2. **C. Todo cabeza** (34 sin tronco en la tabla, casi todos de este grupo): con cabeza y sin tronco en ningún
   sitio → como cabeza se pega entero. Y los de tronco diminuto al lado de la cabeza (Gossifleur `waist`, Yamask).
3. **D. Coberturas** (vestido, lana, chaqueta → tronco), **F** (cráneo equivocado), **E**, **H**, **G** (JSON).
4. Quitar avisos que sobran de la tabla ("cara fuera de la cabeza", "adorno mayor que el tronco" con alas).
5. Después de C–D: seguir revisando especies "sin revisar" en el inspector.
6. Más adelante: **ajustes por especie en JSON** para lo que no tenga regla (Magneton = 3 cabezas, hueso de la cabeza
   de Skeledirge...), ampliable por resource packs; y una **lista fija de cruces por categoría** (2 representantes por
   categoría, ~50-60 parejas) para repasar tras cada cambio.

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

A + B (tronco al lado) hecho y probado. Quedan 34 sin tronco: casi todos del grupo C. Jellicent y Galvantula siguen
sin tronco (mirar aparte).

### Pegado y tamaños (probado por el usuario)

- Modos de pegado (`Align`, botón del visor, `/fusionvisual align pivot|skull|base`): **cráneo** (por defecto, el que
  mejor queda), pivote, y **base** (`attachPoint`/`neckFace`: centro de la cara del cráneo por la que entra el cuello;
  punto verde lima en el inspector).
- Tamaños (`Sizing`/`soften`, botón "Tamaño" del visor): **suave al crecer** (por defecto), exacto, suave.
- Cola de serpientes sin `tail` (`chainTail`): Onix `boulder14`, Steelix `boulder8`, Dratini `segment8`, Rayquaza
  `tip`. Mal: Magneton (grupo G), Frillish (`frill_right_end2`), Necrozma (`bone90`).

## Cambios sin commit (pendientes de probar)

(ninguno)

## Abiertos

- **Ninetales**: su cabeza está inclinada en su modelo y su cráneo también; al pegar, no queda del todo bien.
- **Vaporeon + Lopunny**: una línea larga y fina en diagonal atraviesa la fusión (¿un plano de Vaporeon, volante o
  aleta, estirado?). Vaporeon + Haxorus: bien.
- **Colas**: ver "Siguiente" arriba (equilibrar tamaños, serpientes).

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
