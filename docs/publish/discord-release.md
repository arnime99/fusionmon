# Mensajes de Discord para el lanzamiento

Para publicar cuando CurseForge y Modrinth aprueben el mod. Cambiar `[CURSEFORGE LINK]` y `[MODRINTH LINK]` por las
direcciones de las páginas. `@Updates` hay que elegirlo en la lista que sale al escribir `@` (pegado como texto no avisa
a nadie). Cada mensaje cabe en un mensaje de Discord (máx. 2000 caracteres).

---

## #download

```
# ⬇️ Download Fusionmon

**CurseForge:** [CURSEFORGE LINK]
**Modrinth:** [MODRINTH LINK]

## Which file do I need?
Just the latest one! Since **0.1.1-beta**, the same file works with **Cobblemon 1.8.2, 1.8.1 and 1.7.3 (Cobbleverse)**.

## Requirements
• Minecraft **1.21.1** with **Fabric Loader 0.17.2+**
• **Fabric API**
• **Cobblemon** 1.7.3, 1.8.1 or 1.8.2

## How to install
1. Download the latest file.
2. Put it in your `mods` folder, next to Cobblemon.
3. On a server, install it on the **server and on every player's game**.

Something not working? Ask in the support forum 💬
```

---

## #changelog

```
# 📜 Fusionmon 0.1.0-beta
*First public beta!*

🧬 **Fusions**
• Fuse any two Pokémon with the **Fusion Injector**: one is the head, the other the body
• Fused model: the body's model with the head grafted on, recolored Infinite Fusion style
• Name, types and stats calculated for every pair; level, IVs and EVs averaged; you choose nature and ability

✨ **Discovery**
• Undiscovered fusions appear as a **silhouette** with hidden stats
• **Fusion animation**, also right in the world if both Pokémon are out
• **Fusion Album**: Sneak + right-click with the injector or `/fusionalbum`

⚔️ **Playing with them**
• Battle, ride (if the body can be ridden) and evolve them (both parts, by level, item or trade)
• Split them back or swap head and body anytime, keeping all the experience

🖥️ Works on servers. One file for **Cobblemon 1.8.2, 1.8.1 and 1.7.3 (Cobbleverse)** (since 0.1.1-beta).

⚠️ This is a beta: back up your worlds, and report odd-looking fusions with a screenshot in the support forum!
```

---

## #announcements

```
@Updates 🎉 **Fusionmon is OUT!**

The first beta is now available on CurseForge and Modrinth. Fuse any two Pokémon in Cobblemon, discover what comes out, and build your Fusion Album!

⬇️ **CurseForge:** [CURSEFORGE LINK]
⬇️ **Modrinth:** [MODRINTH LINK]

One file for **Cobblemon 1.8.2, 1.8.1 and 1.7.3 (Cobbleverse)**: check #download to install it, and #changelog for everything new.

This is a **beta**, so your feedback matters a lot:
🐛 Found a bug or a weird-looking fusion? Post it in the support forum with a screenshot
📸 Made a cool fusion? Show it off in the community channels!

Thank you all for being here from the very start 💜
```

---

## Tutorial de fusiones hechas a mano (publicar con la 0.1.2-beta)

```
# 🎨 Make your own hand-made fusions!

Every pair of Pokémon already has an automatic fusion. Now you can make a specific pair look exactly how you want, with your own model or texture! Your hand-made fusion replaces the automatic one everywhere: in the world, battles, menus and the Fusion Album.

It's a normal Cobblemon resource pack:
🧩 Your model (Blockbench, "Bedrock Entity"), texture and poser
📄 One resolver file: head species + `"aspects": ["fusionmon-body-<body>"]`
✨ Shiny versions and texture-only fusions are supported too

📖 Full guide: https://github.com/arnime99/fusionmon/blob/main/docs/custom-fusions.md
Requires Fusionmon 0.1.2-beta or newer and Cobblemon 1.8+.

Made one? Show it off in #fusion-showcase! 🧬
```

## Changelog de CurseForge para la 0.1.2-beta

Pegar con el editor en modo **Markdown**.

```
## 0.1.2-beta

### New
- **Hand-made fusions!** Resource packs can now replace the automatic model of any pair with their own model or texture (Cobblemon 1.8.1+). Name, types and stats stay calculated. Guide: https://github.com/arnime99/fusionmon/blob/main/docs/custom-fusions.md
- **Server option `statBonusPercent`** in `config/fusionmon.json`: raises every base stat of every fusion by a % (0 by default = the Infinite Fusion formula; 5-10 recommended; each stat capped at 255). Applies to battles, the summary and the Fusion Album, also for fusions that already exist.

### Fixes
- **Mega Showdown:** mega evolving a fusion no longer kicks the player from the server ("Failed to decode packet"), and the fusion keeps its body's moves.
- **Mega Showdown:** fusing a mega evolved Pokémon now returns it to its normal form first (with a message), and the fusion keeps the Mega Stone. Before, the fusion could end up half-mega (wrong look, ability, types and Fusion Album entry).
- **Shoulder:** a fusion on your shoulder now shows the fused head instead of the body's original head. Whether it can sit on your shoulder, and its size there, now depend on the **body** (like its size and riding everywhere else).

### More stable with big modpacks
- If something in Fusionmon clashes with another mod, that detail is skipped (for example the fusion is drawn like its head) and a warning is written to the log, instead of crashing the game.
- Emergency switches in `config/fusionmon.json`: `disabledMixins` turns off a single part by name (the name shown in the log or crash report) and `forceColorsMode` draws fusions recolored without head-on-body (a server can force it for every player).
- The log now says which parts of Fusionmon were checked at startup, so problems with other mods are easier to report.

Still **one file** for Cobblemon 1.8.2, 1.8.1 and 1.7.3 (Cobbleverse). Thanks to the CobbleFusion F2P server for the detailed reports!
```

## #changelog (Discord) para la 0.1.2-beta

```
# 📜 Fusionmon 0.1.2-beta

🎨 **Hand-made fusions**
• Resource packs can give any pair its own model or texture, replacing the automatic one (Cobblemon 1.8.1+). See the tutorial post!

⚙️ **For server owners**
• `statBonusPercent` in `config/fusionmon.json`: fusions get +X % on every base stat (0 by default, 5-10 recommended)
• Emergency switches if Fusionmon clashes with another mod: `disabledMixins` and `forceColorsMode`

🛠️ **Fixes**
• Mega Showdown: no more kicks when a fusion mega evolves, and no lost moves
• Mega Showdown: mega evolved Pokémon go back to normal before fusing; the fusion keeps the Mega Stone
• Shoulder: fusions look right on your shoulder, and the body decides if they fit and their size

🛡️ **More stable**: if something clashes with another mod, Fusionmon skips that detail and logs it instead of crashing.

Mega evolving fusions for real (stats, battles, body megas) is coming in the next big update 👀
Thanks to the CobbleFusion F2P server for the reports 💜
```