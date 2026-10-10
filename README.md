# Fusionmon

A Fabric mod for **Minecraft 1.21.1** that adds Pokémon fusions to **Cobblemon 1.8.1**, inspired by Pokémon Infinite Fusion.

Every pair is built on the fly, so any two species can be fused: their own model, name, types and stats.

## Features

- **Fusion Crystal**: right-click it to pick two Pokémon from your party and fuse them, or pick a fusion to split it back apart or swap head and body. Each fusion, split or swap consumes one crystal.
- **Fused model**: the body's model with the head grafted on, recolored with the head's palette. Tails, wings, horns, arms and legs are mixed too. Shiny if either part is shiny.
- **Infinite Fusion stats**: name, types and base stats follow Infinite Fusion's formulas. Level, IVs and EVs are averaged; you choose which nature and ability to keep.
- **Discovery**: a fusion you have never made appears as a silhouette with hidden stats; confirming it plays a fusion animation that reveals it.
- **Fusion Album**: sneak + right-click with the crystal (or `/fusionalbum`) to browse the fusions you have discovered.
- Fusions battle with their fused types and stats, have the size and ride of their body, evolve through either part (level, items and trade with the Link Cable) and learn level-up moves from both.
- Splitting gives both Pokémon all the experience earned as a fusion.

## Crafting

Fusion Crystal (crafting table): redstone in the bottom-left corner, lapis lazuli in the center and one of copper ingot, iron ingot, gold ingot, diamond or netherite ingot in the top-right corner. The accepted materials are the item tag `fusionmon:fusion_crystal_materials`, so datapacks can change them.

## Commands

- `/fusionalbum`: open your Fusion Album.
- `/fusionvisual graft|colors`: how fusions look on your screen (fused model, or just the head recolored).
- `/fusionmon info|unfuse <slot>` (operators): inspect or split the fusion in one of your party slots.

## Requirements

- Minecraft 1.21.1 with Fabric Loader 0.17.2 or newer
- Fabric API
- Cobblemon 1.8.1

Install it on both the client and the server.

## License

Fusionmon is licensed under the [Mozilla Public License 2.0](LICENSE).
Pokémon is © Nintendo, Game Freak and The Pokémon Company. This is an unofficial fan project, not affiliated with or endorsed by them.
