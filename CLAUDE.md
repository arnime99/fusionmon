# Fusionmon

Mod de Fabric para **Minecraft 1.21.1** que añade fusiones de Pokémon a **Cobblemon 1.8.1**, inspirado en Pokémon Infinite Fusion. Los datos de la fusión (nombre, tipos, stats) se calculan dinámicamente; nunca se guardan tablas de combinaciones.

## Cómo trabajar con el usuario

- Habla en **español**. El usuario es nuevo en modding de Minecraft (sabe C#/Unity): explica Fabric/Gradle/Kotlin/mixins sin dar nada por sabido, paso a paso.
- Ciclo: investigar la API de Cobblemon → explicar el plan → implementar en pasos pequeños → `gradlew build` → el usuario prueba en el juego → **commit por paso** cuando confirma que funciona.
- No añadir dependencias ni cambiar versiones de Java "a ver si funciona": entender primero la causa.
- Comentarios del código en español, explicando el *porqué*.

## Entorno y comandos

- Gradle/Loom corre con **Java 26**; Minecraft con **Java 21** (`runClient` usa toolchain 21). Es deliberado, no cambiarlo.
- Compilar (PowerShell): `$env:JAVA_HOME='C:\Users\Arnau\.jdks\temurin-26.0.2.1'; .\gradlew.bat build`
- Lanzar el juego: lo mismo con `runClient`, o la configuración "Minecraft Client" de IntelliJ. El jugador de desarrollo se llama siempre `Arnau` (`build.gradle` → `loom.runs.client`), para que Cobblemon conserve equipo/PC entre arranques.
- `git` no está en el PATH: usar el de GitHub Desktop, `%LOCALAPPDATA%\GitHubDesktop\app-*\resources\app\git\cmd\git.exe`.
- Logs y crashes: `run/logs/latest.log`, `run/crash-reports/`. Showdown desempaquetado en `run/showdown/`.
- Comandos de prueba en el juego (con trucos): `/fusionmon info <hueco>`, `/fusionmon unfuse <hueco>`, `/pokegive <especie>`, `/spawnpokemon <especie> lvl=N`.

## Dependencia de Cobblemon (no tocar sin motivo)

- Maven oficial `https://maven.impactdev.net/repository/development/`, `modImplementation "com.cobblemon:fabric:1.8.1+1.21.1"`; su POM trae Fabric Language Kotlin.
- El plugin `org.jetbrains.kotlin.jvm` está aplicado **aunque no haya código Kotlin**: Loom solo remapea los metadatos Kotlin de Cobblemon si está; sin él, crash `ClassNotFoundException: net.minecraft.class_2960` en kotlin-reflect.
- El sources jar de Cobblemon está vacío. Para conocer su API: `javap` (JDK 21 en `~/.jdks`) sobre el jar en `~/.gradle/caches/modules-2/files-2.1/com.cobblemon/fabric/1.8.1+1.21.1/`. **Comprobar qué hace un método por dentro (`javap -c`) antes de usarlo**, no fiarse del nombre.

## Arquitectura (`src/main/java/com/arnau/fusionmon`)

- `item/FusionCrystalItem` — clic derecho → `FusionSelection.start` (solo servidor).
- `fusion/FusionSelection` — flujo: selector de equipo de Cobblemon (`PartySelectCallbacks`). Elegir una fusión → pantalla de separar; elegir un normal → 2.º selector → pantalla de fusión. Guarda la selección pendiente por jugador y **revalida todo** al recibir la respuesta del cliente.
- `fusion/FusionService` — `fuse` / `unfuse`: objetos al inventario, medias de nivel/IV/EV, naturaleza y habilidad elegidas (habilidad del cuerpo como *forced*), movimientos de ambos a *benched moves*, % de PS, reparto de EXP al separar.
- `fusion/FusionData` — datos en `pokemon.persistentData["fusionmon"]`: `version` (2), `head`/`body` (NBT completo de los originales), `headSpecies`/`headForm`/`bodySpecies`/`bodyForm`, `bodyAspects`, `startExperience`. Al cambiarlos llama a `updateAspects()`.
- `fusion/FusionAspects` — `AspectProvider` de Cobblemon: las fusiones llevan los aspects `fusionmon-fusion`, `fusionmon-body-<especie>` y `fusionmon-bodyaspect-<aspect del cuerpo>`. Cobblemon los sincroniza solo al cliente (entidad, equipo, PC) y los resolvers los usan para elegir modelo/textura (también sirven para resource packs).
- `fusion/FusionCalculator` — fórmulas puras (stats base ponderados 2/3, regla de tipos, nombre partido). Aquí se retoca el algoritmo.
- `fusion/FusionStatProvider` — envuelve `Cobblemon.statProvider` para los stats de fusiones.
- `fusion/FusionShowdown` + `mixin/BattleRegistryMixin` — antes de cada combate registra en Showdown la especie de fusión (`fusionmon<cabeza>x<cuerpo>`).
- `mixin/PokemonMixin` — tipos, nombre visible, `showdownId()` y `getEvolutions()` de las fusiones.
- `fusion/FusionEvolutions` — evoluciones de una fusión = las de cabeza y cuerpo guardados, envueltas en `FusionLevelUpEvolution` / `FusionItemEvolution` (heredan de las de Cobblemon; id `fusionmon_<parte>_<id>`). Cobblemon las comprueba y muestra solo; al aceptar, `evolvePart` evoluciona la parte guardada (`FusionData.writePart`), y si es la cabeza también la especie visible. `mixin/ServerEvolutionControllerMixin` bloquea cualquier evolución no envuelta en una fusión.
- `fusion/FusionMoves` — dar movimientos a la fusión después de crearla (sin emitir paquetes; el llamador sincroniza: Pokémon completo o `MoveSet/BenchedMoves.update()`).
- `fusion/FusionLevelUp` — `EXPERIENCE_GAINED_EVENT_POST`: Cobblemon solo enseña los movimientos por nivel de la cabeza; aquí se añaden los del cuerpo entre el nivel anterior y el nuevo.
- `network/*` — payloads de Fabric (servidor↔cliente) de las pantallas de fusionar/separar.
- `command/FusionCommands` — comandos de prueba.
- Cliente (`src/client/java/.../client`): `FusionmonClient` (receptores, recarga de recursos) y `screen/FusionConfirmScreen`, `screen/UnfuseConfirmScreen`.
- `client/texture/FusionPalette` — cambio de paleta puro (píxeles ARGB, sin clases de Minecraft; se puede probar fuera del juego): colores con color de cabeza y cuerpo ordenados por claridad y ponderados por nº de píxeles; cada color de la cabeza toma el del cuerpo en la misma posición. Casi negros/blancos no se tocan nunca; los grises solo si son el color del Pokémon (< 50 % de la textura con color). Aquí se retoca el aspecto.
- `client/texture/FusionTextures` — genera la textura de la fusión (cabeza recoloreada con la textura del cuerpo, que se pide al resolver del cuerpo con sus aspects) como `DynamicTexture` (`fusionmon:fusion_textures/N`), con caché; se vacía al recargar recursos.
- `client/texture/FusionBody` — el cuerpo según los aspects (especie, aspects sin prefijo, regla shiny), su resolver y un `FloatingState` con sus aspects.
- `client/mixin/VaryingRenderableResolverMixin` (`getTexture`: mundo, combate, hombro, menús) y `VaryingModelRepositoryMixin` (`getTextureNoSubstitute`, que rechazaría texturas que no existen como archivo; y los enganches de graft en `getPoser`/`getTexture`/`getLayers`). Config `fusionmon.client.mixins.json` (solo cliente).
- `client/model/FusionGraft` — cabeza sobre cuerpo, **modo por defecto** (`/fusionvisual graft|colors`, comando de cliente, no se guarda): modelo/capas del cuerpo con su textura recoloreada con la de la cabeza (estilo Infinite Fusion); `PosableModelMixin` oculta las cabezas del cuerpo y pinta en cada una la cabeza principal del modelo de la cabeza, animada con su propio `FloatingState` (reposo, parpadeo, mirar), orientada como en su modelo animado y escalada por el tamaño de cabeza (cráneo = cubo con más volumen + piezas grandes pegadas, máx. 1,5× el cráneo).
  - Cabezas (`findHeads` → `ModelHeads`): `head`; si no, padre de `locator_head` ampliado hasta incluir la cara (`eye*`/`face*`/`mouth*`, `withFace`: Koffing); además `headN` y `head_left/right` (Scovillain, Binacle).
  - "Todo cabeza": como CABEZA se pega el modelo entero si no hay cabeza o la de `locator_head` es ≥ 80 % del volumen (Koffing, Voltorb, Tentacool…), sin extremidades de fuera de la cabeza (`leg`/`foot`/`toe`/`tentacle`/`tail`; las manos de Haunter se quedan) y apoyado por abajo (`groundOffset`: punto más bajo con el de la cabeza del cuerpo). Como CUERPO solo cuenta como sin cabeza si al ocultarla no queda ningún cubo (Tentacool sí tiene: sus tentáculos) → modo colores.
  - Especie de cabeza sin modelo (Groudon, Kyogre, Raikou… en Cobblemon 1.8.1 solo hay ~890 modelos de 1025 especies) → se pinta el cuerpo con sus colores en vez del sustituto (`onlyBody`).
- `fusion/FusionBodyForm` (común) — forma del cuerpo en servidor (persistentData) y cliente (aspects de la entidad). La fusión tiene **tamaño y montura del cuerpo**: `PokemonEntityMixin` (escala y caja de colisión en `getDimensions`, recalculada al cambiar los aspects; montura en `getSeats`/`getRideProp`/`refreshRiding`), `PokemonMixin` (`getRiding` y estadísticas de montura), `RidingControllerMixin`; cliente: `PokemonRendererMixin` (escala al pintar), `ClientboundSeatAssignmentPacketHandlerMixin`, `StatWidgetMixin`. Si el cuerpo no se puede montar, la montura de la cabeza.
- `FusionConfirmScreen` — visor 3D de la fusión (`FusionPreview.model`, un `RenderablePokemon` con los aspects de fusión) y aviso de si el graft encuentra cabeza.

## Decisiones de diseño acordadas

- Pokémon visible = la cabeza (mantiene su modelo), pintada con los colores del cuerpo. Se ve shiny si lo es cualquiera de las dos partes (usa los colores shiny del cuerpo).
- Con cabeza sobre cuerpo (por defecto): modelo del cuerpo con la cabeza pegada; tamaño (escala, caja de colisión) y montura del cuerpo (si no tiene montura, la de la cabeza).
- Fórmulas de Infinite Fusion. Nivel, IVs y EVs = media. Naturaleza y habilidad: el jugador elige entre las dos.
- Mantiene los movimientos de la cabeza; el resto, recordables (benched moves).
- No hay fusión de fusiones. El mismo cristal fusiona y separa.
- Al separar: cada parte recibe toda la EXP ganada como fusión y el % de PS de la fusión.
- Evolución: el menú de Cobblemon ofrece las evoluciones de ambas partes; al elegir una, la otra sigue pendiente. Sin animación en el mundo (sonido + mensaje con el nombre de fusión). Evoluciones por intercambio y clic en bloque no se ofrecen a fusiones.
- Objetivo: multijugador/servidores y publicar en CurseForge.

## Trampas de Cobblemon ya encontradas

- `PartyStore.sendTo(player)` envía el equipo como si fuera de **otro** jugador → el cliente se desincroniza (huecos fantasma). Para refrescar un Pokémon: `SetPartyPokemonPacket` con `CobblemonNetwork.sendPacket`.
- `Pokemon.getPersistentData()` es `null` mientras el Pokémon se construye (Cobblemon calcula stats en el constructor): todo hook debe comprobar null.
- `persistentData` solo llega al cliente en sincronizaciones completas del Pokémon; no hay paquete de actualización propio.
- El `receiveEntry` de Showdown (detrás de `ShowdownService.sendRegistryEntry`) está roto en el JS; usar `sendRegistryData(map, "species")`.
- Mixins sobre clases de Cobblemon: `@Mixin(value = ..., remap = false)`. Si el método es de Minecraft sobrescrito por Cobblemon (`getDimensions`, `onSyncedDataUpdated`, `renderWidget`), poner también su nombre intermediary (`method_18377`...): con `remap = false` no se traduce y en el juego publicado se llama así. Las llamadas cuya firma lleva clases de Minecraft se buscan solo por nombre (`Lcom/.../FormData;getHitbox`). Los errores de mixin no salen al compilar sino al cargar la clase (arrancar el juego).
- Tamaño y montura: Cobblemon los lee de `form.baseScale`/`form.hitbox`/`form.riding` en muchos sitios (la forma es la de la cabeza); ver `FusionBodyForm`. En el cliente, el `Pokemon` de una entidad no tiene persistentData ni aspects: los aspects están en `entity.getEntityData().get(PokemonEntity.Companion.getASPECTS())`.
- Los paquetes de actualización de Cobblemon (`BenchedMovesUpdatePacket`, `MoveSet`...) llevan la colección **viva** y se serializan más tarde en el hilo de red: modificarla varias veces seguidas → `ConcurrentModificationException` y desconexión. Agrupar cambios con `doWithoutEmitting` y sincronizar una vez al final.
- Cobblemon comprueba evoluciones cada segundo (`PlayerPartyStore.onSecondPassed` → `getLockedEvolutions()`); `setSpecies`/`setForm` vacían las pendientes. El envío completo del Pokémon (`SetPartyPokemonPacket`) incluye las evoluciones pendientes.
- `setForm` (también al evolucionar) llama a `updateMovesOnFormChange`, que borra los benched moves que la nueva especie no aprende; en fusiones se lo impide `PokemonMixin` con `@WrapOperation` (MixinExtras viene con Fabric Loader).
- `runServer`: hay que forzar Java 21 (como `runClient`) y añadirle ICU4J 71.1 a mano, porque Loom quita la 73.2 del cliente y Showdown se queda sin ella (en un servidor real va dentro del jar de Cobblemon). En modo offline, `op` antes de haber entrado nunca da op a la cuenta de Mojang real con ese nombre: corregir `run/server/ops.json`.
- Render: cada especie tiene en `assets/cobblemon/bedrock/pokemon/` `models` (huesos), `animations`, `posers` y `resolvers`. El resolver recorre sus variaciones **de la última a la primera** y, por propiedad (modelo, textura, capas), usa la primera cuyos aspects tenga el Pokémon. Los aspects se calculan solo en el servidor (`updateAspects`, que al cargar un Pokémon va después de leer `persistentData`).
- Huesos: cada hueso es un `ModelPart` de Minecraft que Cobblemon convierte en `Bone` con un mixin (en Java hay que pasar por `(Object)` para hacer `instanceof`). La postura del `.geo` NO es la de reposo (p. ej. Charizard mira al cielo): para orientar algo hay que aplicar las animaciones (`setCurrentModel`, `setPoseToFirstSuitable`, `updatePartialTicks`, `applyAnimations`, como `drawProfilePokemon`). Muchas piezas duplicadas (bocas, párpados) solo las ocultan las animaciones.
- `PosableModel.context` es `lateinit`: un modelo que nunca se ha pintado por sí mismo crashea al animarlo → asignarle uno (`setContext`). Pintar con `RenderType.entityCutout` como Cobblemon: con `NoCull` los planos de grosor cero hacen z-fighting.
- Escribir JSON desde PowerShell 5.1 con `Set-Content -Encoding utf8` mete BOM; usar las herramientas de edición o UTF-8 sin BOM.

## Estado

Hecho y probado: entorno, fase 1 (cristal + selector), fase 2 (fusión de datos, pantalla con vista previa/intercambiar/naturaleza/habilidad y descripciones, movimientos), fase 3 (separar con el cristal), fase 4 (combates en Showdown), fase 5 (evolución de cabeza y cuerpo desde el menú de Cobblemon; movimientos del cuerpo al subir de nivel, también en combate).

Pendiente:
- **Fase 6 — pulido y publicación:** ~~gastar el cristal~~ (hecho: 1 al confirmar fusión o separación, no en creativo), ~~receta~~ (hecho: redstone–lapis–material en diagonal, materiales en la etiqueta `fusionmon:fusion_crystal_materials`), ~~`fabric.mod.json`~~ (hecho: autor `Rupikola`, licencia **MPL-2.0** como Cobblemon, README; faltan los enlaces `contact` cuando exista el repo de GitHub), ~~quitar `ExampleClientMixin`~~ (hecho), ~~publicar el repo en GitHub~~ (https://github.com/arnime99/fusionmon), ~~probar en servidor dedicado~~ (hecho: `runServer`, datos en `run/server`), publicar en CurseForge (y Modrinth).
- **Fase 7 — visuales (antes de publicar la Beta):** ~~aspects de fusión~~ (hecho: `FusionAspects`), ~~cambio de paleta en el cliente~~ (hecho: `FusionPalette`/`FusionTextures`), ~~prototipo cabeza sobre cuerpo~~ (hecho y probado: `FusionGraft`, visor en la pantalla de fusión), mejorar el objeto (el usuario está con el sprite). Escalado por cráneo: probado, el usuario lo ve bien.
  **Bloque graft (feedback del usuario tras probar):** ~~colocación de modelos enteros~~ (hecho: `groundOffset`), ~~Koffing~~ (hecho: `withFace`), ~~Tentacool/Haunter/Scovillain~~ (hecho), ~~escalado (Charizard)~~ (hecho: tamaño del cuerpo + medida de cabeza), ~~graft por defecto~~ (hecho), ~~montura del cuerpo~~ (hecho y probado), ~~shiny negros~~ (hecho y probado: si < 50 % de una textura tiene color, sus grises intermedios cuentan como color, `NEUTRAL_BODY_FRACTION`; Charizard shiny, Umbreon, Luxray...). Pendiente:
  2. Cuerpos sin cabeza: no se pinta nada de la otra especie (p. ej. Lunatone, Koffing como cuerpo). Ideas: poner la cabeza encima (`locator_top`) y/o "complementos".
  3. Dugtrio como cuerpo: no detecta cabezas; idea: poner 3 cabezas de la otra especie (una por cada Dugtrio) y mantener sus marcas.
  4. **Siguiente:** decoraciones / "Frankenstein": añadir piezas de la otra especie (cola, piernas `leg_left`..., aletas, orejas). Datos: 51 % de modelos tienen `tail`, 43 % brazos, 42 % piernas, 28 % orejas, 24 % aletas. Empezar por cambiar lo equivalente (cola ↔ cola); añadir lo que el cuerpo no tiene es más difícil (sin sitio donde engancharlo).
  5. Probar con mods que añadan modelos (las heurísticas son por nombres de huesos y volúmenes; el cuerpo va en los aspects solo con la ruta de la especie).
  Otras ideas: ajustes por especie en JSON (escala/desplazamiento/hueso cabeza, ampliable por resource packs); guardar la elección de `/fusionvisual`; recolorear las capas (emissive) de la textura.
- Ideas para más adelante: propiedad `fusion_body=<especie>` (`CustomPokemonProperty`) para comandos, NPC y apariciones salvajes; entrenadores/líderes con fusiones (mirar Cobbleverse); cristales por niveles de material.
- No se puede cobrar por el mod (EULA de Mojang + propiedad de Pokémon): se publica gratis.
