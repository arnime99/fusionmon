# Hand-made fusions (resource packs)

Fusionmon builds a fused model for **every** pair automatically. If you want a specific pair to look exactly the way you
designed it, you can make it by hand: a **resource pack** with your own model or texture for that pair replaces the
automatic one, everywhere (in the world, in battles, in menus, in the Fusion Album and as a silhouette).

It works like the community sprites of Pokémon Infinite Fusion: every pair exists, and the best ones get made by hand.

> Requires **Fusionmon 0.1.2-beta** or newer and **Cobblemon 1.8** or newer (on Cobblemon 1.7.3 / Cobbleverse,
> fusions always use the automatic model for now).

## What you need

A normal Cobblemon resource pack. If you have ever made a Cobblemon model, you already know how:

```
my-fusion-pack/
├─ pack.mcmeta
└─ assets/<your_namespace>/
   ├─ bedrock/pokemon/models/      your model, e.g. pikachu_charizard.geo.json (Blockbench, "Bedrock Entity")
   ├─ bedrock/pokemon/posers/      your poser (optional: you can reuse an existing one, see below)
   ├─ bedrock/pokemon/animations/  your animations (optional)
   ├─ bedrock/pokemon/resolvers/   the resolver that tells Cobblemon when to use your model
   └─ textures/pokemon/            your texture
```

Use **your own namespace** (any lowercase name, like `mypack_fusions`) so your files never clash with other packs.
File names are up to you; only the resolver content below matters.

## The resolver

The resolver goes in `bedrock/pokemon/resolvers/` (any subfolder, any unique file name). It belongs to the
**head** species, and its variation asks for the **body** species:

```json
{
  "species": "cobblemon:pikachu",
  "order": 100,
  "variations": [
    {
      "aspects": ["fusionmon-body-charizard"],
      "poser": "cobblemon:charizard",
      "model": "mypack_fusions:pikachu_charizard.geo",
      "texture": "mypack_fusions:textures/pokemon/pikachu_charizard.png",
      "layers": []
    }
  ]
}
```

- `species`: the **head** of the fusion.
- `order`: use **100** or more, so your variation goes after Cobblemon's own (the last matching variation wins).
- `aspects`: `fusionmon-body-` + the body species name as in its Cobblemon ID (`cobblemon:charizard` → `charizard`).
- `model`: your namespace + the model file name without `.json` (`pikachu_charizard.geo.json` → `pikachu_charizard.geo`).
- `poser`: your own poser, or an existing one. **Tip:** if your model keeps the bone names of the body Pokémon
  (`body`, `torso`, `neck`, `head`, `tail`, `wings`...), you can use the body's poser (`cobblemon:charizard`) and it
  moves with all of its animations, no animating needed.
- `layers`: glowing or animated layers (like Charizard's flame). Always write it, even as `[]`: otherwise the
  head Pokémon's layers are used on your model.

Each pair is one way: **Pikachu head + Charizard body** and **Charizard head + Pikachu body** are different fusions,
each with its own resolver.

### Texture only

You can also give a pair just a new texture, keeping the head's model (like an Infinite Fusion custom sprite):
write only `texture` (and `layers`) in the variation, without `poser` and `model`.

### Shiny and forms

Add more variations **after** the first one in the same file; they win when they match:

- The fusion is shiny if the **head** is shiny: aspect `shiny`.
- If only the **body** is shiny: aspect `fusionmon-bodyaspect-shiny`.
- Body forms work the same way: `fusionmon-bodyaspect-alolan`, `fusionmon-bodyaspect-female`...

```json
{ "aspects": ["fusionmon-body-charizard", "shiny"], "texture": "mypack_fusions:textures/pokemon/pikachu_charizard_shiny.png" },
{ "aspects": ["fusionmon-body-charizard", "fusionmon-bodyaspect-shiny"], "texture": "mypack_fusions:textures/pokemon/pikachu_charizard_shiny.png" }
```

## What it changes

Only how the fusion **looks**. Its name, types, base stats and abilities are still calculated by Fusionmon, the same for
every player and server.

## Testing

Put the pack (a folder works too) in `resourcepacks`, enable it, and press **F3 + T** in game to reload after each
change. A working example is in
[`examples/custom-fusion-example`](../examples/custom-fusion-example): with it, Pikachu + Charizard shows up as a shiny
Raichu, using only files that Cobblemon already has.

Cobblemon's own models are a great starting point to learn from, but they belong to Cobblemon: check their asset
license before publishing a pack made from them.
