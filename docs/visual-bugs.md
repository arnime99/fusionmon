# Registro de fallos visuales (cabeza sobre cuerpo)

## Cómo trabajamos

1. El usuario prueba en el visor (`/fusiondex`) y apunta aquí: **cabeza + cuerpo · síntoma · captura** (si hay).
2. Claude agrupa los fallos por **patrón** (qué tienen en común los modelos), lo confirma leyendo los `.geo.json`
   y simulando la regla sobre **todos** los modelos (scripts de PowerShell sobre el jar de Cobblemon y
   `run/resourcepacks/AllTheMons-R4.0.zip`), y arregla la regla, no el Pokémon.
3. El usuario vuelve a probar las parejas de la lista; lo que funcione se tacha y se hace commit.

Nada de código por especie: si hiciera falta un caso a mano, sería con datos (JSON de ajustes por especie).

## Siguiente: revisión por especies (propuesto, sin empezar)

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

## Cambios sin commit (pendientes de probar)

(ninguno)

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
