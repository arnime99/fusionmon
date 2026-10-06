# Registro de fallos visuales (cabeza sobre cuerpo)

## Cómo trabajamos

1. El usuario prueba en el visor (`/fusiondex`) y apunta aquí: **cabeza + cuerpo · síntoma · captura** (si hay).
2. Claude agrupa los fallos por **patrón** (qué tienen en común los modelos), lo confirma leyendo los `.geo.json`
   y simulando la regla sobre **todos** los modelos (scripts de PowerShell sobre el jar de Cobblemon y
   `run/resourcepacks/AllTheMons-R4.0.zip`), y arregla la regla, no el Pokémon.
3. El usuario vuelve a probar las parejas de la lista; lo que funcione se tacha y se hace commit.

Nada de código por especie: si hiciera falta un caso a mano, sería con datos (JSON de ajustes por especie).

## Cambios sin commit (pendientes de probar)

- Visor: muestra todas las especies con modelo aunque no estén "implementadas" (Gulpin, Darkrai de AllTheMons).
- Cuerpos sin cabeza: los complementos, adornos y cola se colocan respecto al **núcleo** del cuerpo (`coreBox`: cubo
  principal + piezas grandes pegadas), no a la caja de todo lo que cuelga. Debería arreglar: Eevee + Wingull, Cacnea,
  Solrock, Whiscash, Feebas (orejas desplazadas) y la cola flotante de Pikachu + Corsola.

## Abiertos

- **Silicobra** como cabeza: sale desplazada. Como cuerpo: parece que lo que falta se pinta como todo el cuerpo
  (cabeza blanca flotando encima). Hay dos modelos (Cobblemon y AllTheMons); en Cobblemon su tronco es un cuello.
- **Gabite + Salamence**: la cola sale desplazada y algo grande.
- **Steelix** como cabeza (Steelix + Charmander): cabeza muy grande; además la textura del cuerpo recoloreada tiene
  pocos colores y "píxeles grandes" comparada con la cabeza (las dos texturas tienen distinta densidad de píxeles:
  límite de los modelos, habría que pensar si se puede hacer algo).
- **Crawdaunt** como cuerpo: la cabeza pegada sale inclinada hacia atrás con todas las cabezas. Su modelo está girado
  45° (cubos en diagonal, `torso` 45° en X que `chest` deshace). Probar con "Pegar: cráneo".
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
