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
- `fusion/FusionData` — datos en `pokemon.persistentData["fusionmon"]`: `version` (2), `head`/`body` (NBT completo de los originales), `headSpecies`/`headForm`/`bodySpecies`/`bodyForm`, `startExperience`.
- `fusion/FusionCalculator` — fórmulas puras (stats base ponderados 2/3, regla de tipos, nombre partido). Aquí se retoca el algoritmo.
- `fusion/FusionStatProvider` — envuelve `Cobblemon.statProvider` para los stats de fusiones.
- `fusion/FusionShowdown` + `mixin/BattleRegistryMixin` — antes de cada combate registra en Showdown la especie de fusión (`fusionmon<cabeza>x<cuerpo>`).
- `mixin/PokemonMixin` — tipos, nombre visible, `showdownId()` y `getEvolutions()` de las fusiones.
- `fusion/FusionEvolutions` — evoluciones de una fusión = las de cabeza y cuerpo guardados, envueltas en `FusionLevelUpEvolution` / `FusionItemEvolution` (heredan de las de Cobblemon; id `fusionmon_<parte>_<id>`). Cobblemon las comprueba y muestra solo; al aceptar, `evolvePart` evoluciona la parte guardada (`FusionData.writePart`), y si es la cabeza también la especie visible. `mixin/ServerEvolutionControllerMixin` bloquea cualquier evolución no envuelta en una fusión.
- `fusion/FusionMoves` — dar movimientos a la fusión después de crearla (sin emitir paquetes; el llamador sincroniza: Pokémon completo o `MoveSet/BenchedMoves.update()`).
- `fusion/FusionLevelUp` — `EXPERIENCE_GAINED_EVENT_POST`: Cobblemon solo enseña los movimientos por nivel de la cabeza; aquí se añaden los del cuerpo entre el nivel anterior y el nuevo.
- `network/*` — payloads de Fabric (servidor↔cliente) de las pantallas de fusionar/separar.
- `command/FusionCommands` — comandos de prueba.
- Cliente (`src/client/java/.../client`): `FusionmonClient` (receptores) y `screen/FusionConfirmScreen`, `screen/UnfuseConfirmScreen`.

## Decisiones de diseño acordadas

- Pokémon visible = la cabeza (mantiene su modelo y shiny). Sin cambios visuales por ahora.
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
- Mixins sobre clases de Cobblemon: `@Mixin(value = ..., remap = false)`.
- Los paquetes de actualización de Cobblemon (`BenchedMovesUpdatePacket`, `MoveSet`...) llevan la colección **viva** y se serializan más tarde en el hilo de red: modificarla varias veces seguidas → `ConcurrentModificationException` y desconexión. Agrupar cambios con `doWithoutEmitting` y sincronizar una vez al final.
- Cobblemon comprueba evoluciones cada segundo (`PlayerPartyStore.onSecondPassed` → `getLockedEvolutions()`); `setSpecies`/`setForm` vacían las pendientes. El envío completo del Pokémon (`SetPartyPokemonPacket`) incluye las evoluciones pendientes.
- Escribir JSON desde PowerShell 5.1 con `Set-Content -Encoding utf8` mete BOM; usar las herramientas de edición o UTF-8 sin BOM.

## Estado

Hecho y probado: entorno, fase 1 (cristal + selector), fase 2 (fusión de datos, pantalla con vista previa/intercambiar/naturaleza/habilidad y descripciones, movimientos), fase 3 (separar con el cristal), fase 4 (combates en Showdown), fase 5 (evolución de cabeza y cuerpo desde el menú de Cobblemon; movimientos del cuerpo al subir de nivel, también en combate).

Pendiente:
- **Fase 6 — pulido y publicación:** ~~gastar el cristal~~ (hecho: 1 al confirmar fusión o separación, no en creativo), ~~receta~~ (hecho: redstone–lapis–material en diagonal, materiales en la etiqueta `fusionmon:fusion_crystal_materials`), `fabric.mod.json` (descripción, autor, dependencia), quitar `ExampleClientMixin`, probar en servidor dedicado, publicar en CurseForge.
- Futuro: modelos visuales de fusiones.
