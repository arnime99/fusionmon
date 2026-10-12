# Vídeo, GIF y capturas para el lanzamiento

Lista de planos para grabar. Lo graba el usuario; aquí está qué grabar, cómo prepararlo y para qué sirve cada cosa.

**El gancho de todo el material es la fusión en el mundo** (`FusionWorldAnimation`): los dos Pokémon fuera, espirales
roja y azul, se juntan en el punto medio, nube, destello y la fusión aparece creciendo. Va antes que la animación de
pantalla, que queda para cuando no están fuera.

## Preparación (una vez)

- **Mundo nuevo** para grabar (así todas las parejas salen en silueta la primera vez, que es lo vistoso), en
  `runClient` (tiene `/fusionmon spawn` y `/fusionmon forget`). Bioma bonito, llano y abierto (la animación necesita
  sitio entre los dos): llanura de flores, bosque de cerezos o playa.
- Comandos de preparación:
  - `/time set day`, `/weather clear`, `/gamerule doDaylightCycle false`, `/gamerule doWeatherCycle false`
  - `/gamerule doMobSpawning false` y `/gamerule doPokemonSpawning false` (nada se cuela en el plano ni empieza un
    combate a mitad)
  - `/give @s fusionmon:fusion_injector 64`
  - `/pokegive <especie> level=30` para cada Pokémon que vayas a fusionar
- Vídeo: **F1** oculta la interfaz en los planos del mundo; FOV 70; partículas al máximo (Opciones → Vídeo →
  Partículas: Todas; si no, la nube sale con huecos).
- Grabar con **OBS a 2560×1600** (la resolución de la pantalla): así se puede recortar en vertical o hacer zoom sin que
  se vea borroso. Exportar a 1920×1080 o 1080×1920. Para los GIF, **ScreenToGif** (gratis, Windows) o recortar del vídeo.

## La fusión en el mundo: cómo grabarla

- Condiciones (si no se cumplen sale la animación de pantalla): **los dos fuera de la Poké Ball, a 16 bloques o menos
  de ti**, sin montarlos, sin combate.
- Colócalos **a 3-5 bloques el uno del otro**, de lado respecto a la cámara (así se ve cómo se acercan). La fusión
  aparece en el punto medio entre los dos.
- Cámara a la altura de los Pokémon, algo apartada para que quepan las espirales. Dura unos 6 s: espirales (1 s), se
  acercan (2 s), nube y destello (1 s), la fusión crece y grita (2 s).
- **Repetir una toma**: `/fusionmon unfuse 1` (separa la fusión del hueco 1) y `/fusionmon forget` (vacía el Fusion
  Album: la pareja vuelve a salir en silueta). Vuelve a sacar a los dos y repite.
- Graba **dos planos de cada fusión** si puedes: uno de la pantalla de fusionar (silueta) y otro del mundo. En el
  montaje se corta de la silueta al mundo justo al pulsar Aceptar.

## Parejas que lucen (probadas)

Charizard + Pikachu, Pikachu + Charizard, Gengar + Pikachu, Pikachu + Gengar, Charizard + Voltorb, Eevee + Voltorb,
Venusaur + Lunatone, Dodrio (como cabeza: sus tres cabezas), Exeggutor como cabeza, Lapras, Vaporeon, Rapidash,
Decidueye. Evitar en el material promocional: Groudon (AllTheMons) y los "únicos" de `docs/visual-bugs.md`.
En el mundo lucen más las parejas de **tamaños distintos** (Pikachu + Charizard, Gengar + Lapras): se nota el cambio.

## 1. GIF de la página (lo primero que se ve)

- **La fusión en el mundo de Charizard + Pikachu**, de las espirales a la fusión ya crecida: unos 5-6 s, en bucle. Si
  cabe, empezar con 1 s de la silueta en la pantalla de fusionar.
- Ancho 800 px, 20-25 fps, menos de 10 MB (CurseForge y Modrinth limitan el tamaño).

## 2. Tráiler (30-45 s, YouTube y la página)

| Seg. | Plano |
|---|---|
| 0-3 | **Gancho**: corte directo al destello y la fusión creciendo en el mundo. Sin intro ni logo antes. |
| 3-7 | Dos Pokémon en el mundo, uno frente al otro; el jugador saca el Fusion Injector y hace clic. |
| 7-12 | Pantalla de fusionar: la silueta con "???", Swap (la otra silueta), naturaleza, Accept. |
| 12-18 | **La fusión en el mundo entera** (F1): espirales, se juntan, nube, destello, la fusión crece y grita. |
| 18-22 | La fusión camina por el mundo (cámara a su altura). |
| 22-26 | Montando una fusión (el cuerpo debe poder montarse: Charizard, Rapidash, Lapras...). |
| 26-30 | Un combate rápido: la fusión ataca (se ven sus tipos fusionados). |
| 30-35 | Plano de grupo: 10-15 fusiones distintas juntas (`/fusionmon spawn random random 15`). |
| 35-40 | El Fusion Album pasando por varias fusiones (Shift + clic con el inyector). |
| 40-45 | Cierre: nombre del mod, "Cobblemon 1.8.1 · Fabric 1.21.1", "CurseForge / Modrinth". |

Música: sin derechos (biblioteca de audio de YouTube). Nada de música de Pokémon (copyright). Para movimientos de
cámara suaves, un mod de repetición (Flashback o Replay Mod) en la instancia de CurseForge con el jar publicable;
comprobar antes que funciona con Cobblemon.

## 3. Capturas (6-8, galería de CurseForge/Modrinth)

1. Pantalla de fusionar con **silueta** (stats en "?").
2. **La nube y el destello** de la fusión en el mundo (fotograma con espirales roja y azul o el destello).
3. La fusión **recién aparecida** en el mundo, entre los destellos.
4. **Grupo** de fusiones variadas en un paisaje bonito (F1).
5. **Montando** una fusión.
6. **Combate** con una fusión (que se vean los tipos).
7. **Menú de evolución** de Cobblemon ofreciendo la evolución de una de las partes.
8. **Fusion Album** con varias fusiones y una seleccionada. (La receta del inyector, si sobra sitio.)

## 4. TikTok, Shorts y redes

### Formato: "adivina la fusión" (10-15 s)

| Seg. | Qué se ve | Texto en pantalla |
|---|---|---|
| 0-2 | Silueta negra en la pantalla de fusionar | **"Charizard + Pikachu = ???"** |
| 2-3 | Un instante quieto | "Guess before the reveal 👀" |
| 3-9 | Corte al mundo: espirales, se juntan, nube, destello, la fusión crece | — |
| 9-13 | La fusión caminando o mirando a cámara (F1) | "Which 2 should I fuse next?" |

La pregunta del final hace que comenten parejas: TikTok deja **responder a un comentario con un vídeo**, y eso es una
serie infinita ("Fusionando los Pokémon que me pedís").

### Montaje en CapCut

- **Vertical sin bordes negros**: proyecto 9:16 → Lienzo / Fondo → **Desenfocar**. Texto gancho en la franja de arriba.
- **Zoom** con fotogramas clave sobre la fusión en el momento de aparecer (el vídeo a 2560×1600 lo aguanta).
- **Transiciones**: casi ninguna; cortes secos. El destello ya es la transición: corta ahí y añade un efecto de sonido
  ("whoosh", "boom": Audio → Efectos de sonido).
- **Texto siempre**: mucha gente lo ve sin sonido. Corto y en inglés (la comunidad de Cobblemon es sobre todo
  angloparlante).
- **Voz**: opcional. Si no, Texto → Texto a voz (la voz típica de TikTok).
- **Música**: el sonido en tendencia se añade **en la app de TikTok al publicar** (con licencia para TikTok), bajo
  (10-20 %), con los sonidos del juego por encima. Cuenta personal o de creador: las de empresa solo tienen la
  biblioteca comercial.

### Ritmo

- **Graba por lotes**: una sesión, 10-15 parejas (silueta + mundo de cada una), y luego monta varios con la misma receta.
- **Constancia**: 3-4 vídeos por semana durante un mes. Los primeros suelen tener pocas visitas: es normal.
- Pokémon muy conocidos primero (iniciales, Pikachu, Gengar, Eevee, legendarios famosos).
- El enlace puede no estar disponible en la biografía hasta tener seguidores: "Fusionmon on CurseForge" en la
  biografía y en los vídeos.

### Otros sitios

- **Reddit (r/Cobblemon)**: el GIF y un texto corto en primera persona ("I made a mod that lets you fuse any two
  Cobblemon"); leer antes las normas del subreddit sobre promoción.
