# Página de CurseForge / Modrinth (borrador)

Texto para copiar en las páginas del mod. En inglés (el público de CurseForge/Modrinth); las notas en español entre
corchetes son para nosotros y no se copian.

---

## Nombre

Fusionmon

## Resumen (una línea, máx. ~250 caracteres)

Fuse any two Cobblemon, Pokémon Infinite Fusion style: every pair gets its own model, name, types and stats. Discover
fusions as you play, battle with them, ride them and evolve them.

## Categorías / etiquetas

[CurseForge: Adventure and RPG, Mobs. Modrinth: adventure, mobs, game-mechanics. Loader: Fabric. Versión: 1.21.1.
Entorno: cliente y servidor (los dos).]

## Descripción

[Imágenes: se sirven desde el repo de GitHub (docs/publish/gallery); si se mueven o renombran, se rompen aquí.
El tráiler es una miniatura que enlaza a YouTube (https://youtu.be/Vj9k-KyJ7kA). Copiar desde el aviso ⚠️ hasta el
final, sin estas notas entre corchetes.]

> ✅ **One file for every Cobblemon version:** works with **Cobblemon 1.8.2, 1.8.1 and 1.7.3 (Cobbleverse)** since
> 0.1.1-beta.

💬 **[Join the Discord](https://discord.gg/mmaXAA4MkX)** to share your fusions, suggest ideas and report bugs!

[![Watch the trailer](https://img.youtube.com/vi/Vj9k-KyJ7kA/maxresdefault.jpg)](https://www.youtube.com/watch?v=Vj9k-KyJ7kA)

**Fusionmon** brings Pokémon fusions to **Cobblemon**, inspired by *Pokémon Infinite Fusion*. Pick any two of your
Pokémon, use a **Fusion Injector**, and watch them become something new.

![Pikachu + Charizard](https://raw.githubusercontent.com/arnime99/fusionmon/main/docs/publish/gallery/01-pikachu-charizard.jpg)

There is no list of hand-made fusions: every pair is built on the fly, so **any two species can be fused**. With
Cobblemon's models that is close to **800,000 combinations**, and over **a million** with model packs like AllTheMons.

### Features

- **Fuse any two Pokémon.** One is the *head*, the other the *body*. Swap them before confirming to see the other
  version.
- **A real fused model.** The fusion uses the body's model with the head grafted on, recolored with the head's palette,
  Infinite Fusion style. Tails, wings, horns, arms and legs are mixed too, and Pokémon that are "all head" (Voltorb,
  Gengar, Clefairy...) get special rules. Shiny if either part is shiny.
- **Infinite Fusion stats.** Name, types and base stats follow Infinite Fusion's formulas. Level, IVs and EVs are
  averaged, and you choose which nature and ability to keep.
- **Discover them as you play.** A fusion you have never made appears as a **silhouette** with hidden stats. Confirm it
  and a **fusion animation** reveals it.
- **Fusion Album.** Every fusion you create is recorded. Sneak + right-click with the Fusion Injector (or `/fusionalbum`) to
  browse your discoveries in 3D.
- **Battle with them.** Fusions fight in Cobblemon battles with their fused types and stats.
- **Ride them.** A fusion has the size of its body, and you can ride it if its body can be ridden.
- **Evolve them.** Both parts can evolve, by level, items or trade (the Link Cable works on fusions). Fusions learn
  level-up moves from both parts.
- **Split or swap anytime.** The same injector splits a fusion back into its two Pokémon (both keep all the experience
  earned as a fusion) or swaps head and body in one step.
- **Multiplayer ready.** Works on servers; each player has their own discoveries.

### See it in action

![A fusion you have never made shows up as a silhouette](https://raw.githubusercontent.com/arnime99/fusionmon/main/docs/publish/gallery/02-fusion-screen.jpg)
*Undiscovered fusions show up as a silhouette with hidden stats.*

![Fusing in the world](https://raw.githubusercontent.com/arnime99/fusionmon/main/docs/publish/gallery/03-spirals.jpg)
*With both Pokémon out, they fuse right in the world...*

![The fusion appears](https://raw.githubusercontent.com/arnime99/fusionmon/main/docs/publish/gallery/04-growing.jpg)
*...and the new Pokémon appears where they met.*

![Fused types and stats](https://raw.githubusercontent.com/arnime99/fusionmon/main/docs/publish/gallery/05-summary.jpg)
*Lapras + Arcanine: a Water/Fire fusion.*

![Riding a fusion](https://raw.githubusercontent.com/arnime99/fusionmon/main/docs/publish/gallery/06-riding.jpg)
*Ride fusions whose body can be ridden.*

![Fusion Album](https://raw.githubusercontent.com/arnime99/fusionmon/main/docs/publish/gallery/07-fusion-album.jpg)
*The Fusion Album keeps every fusion you discover.*

### How to use it

1. Craft a **Fusion Injector**: redstone in the bottom-left corner, lapis lazuli in the center and a copper, iron, gold
   or netherite ingot or a diamond in the top-right corner.
2. Right-click with the injector and pick two Pokémon from your party.
3. Choose head and body, nature and ability, and confirm. Each fusion, split or swap uses one injector.
4. Right-click again with the injector and pick a fusion to split or swap it.
5. Sneak + right-click (or `/fusionalbum`) to open your Fusion Album.

![Fusion Injector recipe](https://raw.githubusercontent.com/arnime99/fusionmon/main/docs/publish/gallery/08-recipe.jpg)

If a fusion looks odd or your game runs slow with many fusions around, `/fusionvisual colors` switches to a simpler
look (the head recolored with the body's palette) and `/fusionvisual graft` switches back.

### Requirements

- Minecraft **1.21.1** with **Fabric Loader 0.17.2+**
- **Fabric API**
- **Cobblemon 1.7.3** (Cobbleverse), **1.8.1** or **1.8.2**: the same file works for all of them

Install it on the **server and on every client**.

### Beta

This is a beta. Fusion data is stored safely: if you remove the mod, fusions stay as their head Pokémon and come back
when you reinstall it. Visuals are generated by rules that work on any model, so a few odd-shaped Pokémon may look
strange as fusions. Please report them, with
screenshots, on the issue tracker or in the Discord. Back up your worlds before trying any beta mod.

### Credits and license

Made by **Rupikola**. Inspired by *Pokémon Infinite Fusion* and built on top of the amazing **Cobblemon**.
Licensed under the **MPL-2.0**. Source code and issues: https://github.com/arnime99/fusionmon

**Join the community on Discord:** https://discord.gg/mmaXAA4MkX (share your fusions, get help, follow the updates)

Pokémon is © Nintendo, Game Freak and The Pokémon Company. This is an unofficial fan project, not affiliated with or
endorsed by them, Cobblemon or Pokémon Infinite Fusion.
