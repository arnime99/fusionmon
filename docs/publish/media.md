# Vídeo, GIF y capturas para el lanzamiento

Lista de planos para grabar. Lo graba el usuario; aquí está qué grabar, cómo prepararlo y para qué sirve cada cosa.

## Preparación (una vez)

- **Mundo nuevo** para grabar (así todas las parejas salen en silueta la primera vez, que es lo vistoso), en
  `runClient` (tiene `/fusionmon spawn` para los planos de grupo). Bioma bonito y abierto: llanura de flores, bosque de
  cerezos o playa.
- Comandos de preparación:
  - `/time set day`, `/weather clear`, `/gamerule doDaylightCycle false`, `/gamerule doWeatherCycle false`
  - `/gamerule doMobSpawning false` y `/gamerule doPokemonSpawning false` (nada se cuela en el plano)
  - `/give @s fusionmon:fusion_crystal 64`
  - `/pokegive <especie> level=30` para cada Pokémon que vayas a fusionar
- Vídeo: **F1** oculta la interfaz en los planos del mundo; FOV 70; partículas al máximo.
- Grabar con **OBS** a 1920×1080 y 60 fps. Para los GIF, **ScreenToGif** (gratis, Windows) o recortar del vídeo.

## Parejas que lucen (probadas)

Charizard + Pikachu, Pikachu + Charizard, Gengar + Pikachu, Pikachu + Gengar, Charizard + Voltorb, Eevee + Voltorb,
Venusaur + Lunatone, Dodrio (como cabeza: sus tres cabezas), Exeggutor como cabeza, Lapras, Vaporeon, Rapidash,
Decidueye. Evitar en el material promocional: Groudon (AllTheMons) y los "únicos" de `docs/visual-bugs.md`.

## 1. GIF de la página (lo primero que se ve)

- **La animación de fusión completa**, de la pantalla de fusionar con la silueta a la revelación: unos 5-6 s, en bucle.
- Ancho 800 px, 20-25 fps, menos de 10 MB (CurseForge y Modrinth limitan el tamaño).
- Pareja muy reconocible y que quede bien: Charizard + Pikachu.

## 2. Tráiler (30-45 s, YouTube y la página)

| Seg. | Plano |
|---|---|
| 0-3 | **Gancho**: corte directo al destello y la revelación de una fusión. Sin intro ni logo antes. |
| 3-8 | Dos Pokémon en el mundo; el jugador saca el Fusion Crystal y hace clic. |
| 8-15 | Pantalla de fusionar: la silueta, pulsar Swap (la otra silueta), elegir naturaleza, Accept → animación entera. |
| 15-20 | La fusión nueva sale de su Poké Ball y camina por el mundo (cámara a su altura, F1). |
| 20-25 | Montando una fusión (el cuerpo debe poder montarse: Charizard, Rapidash, Lapras...). |
| 25-30 | Un combate rápido: la fusión ataca (se ven sus tipos fusionados). |
| 30-35 | Plano de grupo: 10-15 fusiones distintas juntas (`/fusionmon spawn random random 15`). |
| 35-40 | El Fusion Album pasando por varias fusiones (Shift + clic con el cristal). |
| 40-45 | Cierre: nombre del mod, "Cobblemon 1.8.1 · Fabric 1.21.1", "CurseForge / Modrinth". |

Música: sin derechos (biblioteca de audio de YouTube). Nada de música de Pokémon (copyright).

## 3. Capturas (6-8, galería de CurseForge/Modrinth)

1. Pantalla de fusionar con **silueta** (stats en "?").
2. La misma pareja **revelada** en la animación (fotograma con chispas).
3. **Grupo** de fusiones variadas en un paisaje bonito (F1).
4. **Montando** una fusión.
5. **Combate** con una fusión (que se vean los tipos).
6. **Menú de evolución** de Cobblemon ofreciendo la evolución de una de las partes.
7. **Fusion Album** con varias fusiones y una seleccionada.
8. **Receta** del Fusion Crystal en la mesa de trabajo.

## 4. Redes (para después)

- **Vídeo vertical (9:16) de 15-20 s** para TikTok, YouTube Shorts y X: solo silueta → animación → revelación, con un
  texto "Which fusion should I make next?" (las respuestas dan ideas y movimiento a la publicación).
- **Reddit (r/Cobblemon)**: el GIF y un texto corto en primera persona ("I made a mod that lets you fuse any two
  Cobblemon"); leer antes las normas del subreddit sobre promoción.
