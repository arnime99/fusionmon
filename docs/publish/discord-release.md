# Mensajes de Discord para el lanzamiento

## Guía de publicación de la 0.1.2-beta (en orden)

1. **CurseForge, subir el archivo**: proyecto → Upload File → `build\libs\fusionmon-0.1.2-beta.jar` (no el
   `-sources`). Display name `Fusionmon 0.1.2-beta`, Release type **Beta**, 1.21.1 + Fabric, dependencias Cobblemon y
   Fabric API (Required). Changelog: editor en modo **Markdown** y pegar "Changelog de CurseForge para la 0.1.2-beta"
   (más abajo).
2. **CurseForge, descripción** (se puede hacer mientras revisan el archivo): Description → editor en modo **Markdown** →
   pegar `docs/publish/page.md` desde el aviso ✅ hasta el final, sin las notas entre corchetes. Novedades: el aviso 🆕,
   "Hand-made fusions" y "Plays well with Mega Showdown" en Features, y la sección "For server owners".
3. **Esperar la aprobación** del archivo (CurseForge la avisa por correo; suele tardar de minutos a unas horas). Hasta
   entonces no anunciar nada: la gente lo buscaría y no estaría.
4. **Discord, en este orden** (cada bloque cabe en un mensaje; `@Updates` hay que elegirlo en la lista que sale al
   escribir `@`, pegado como texto no avisa a nadie):
   1. `#changelog`: "#changelog (Discord) para la 0.1.2-beta".
   2. `#announcements`: "Anuncio de la 0.1.2-beta" (con `@Updates`), cambiando `[CURSEFORGE LINK]`.
   3. El tutorial de fusiones hechas a mano: en `#announcements` justo después, o fijado en `#fusion-showcase`.
   4. `#faq`: añadir las tres preguntas de "Añadidos al #faq para la 0.1.2-beta" al final del mensaje que ya hay
      (editarlo, no un mensaje nuevo).
   5. `#download`: no cambia ("Just the latest one!").
5. **CobbleFusion**: mandar al dueño "Mensaje para el dueño de CobbleFusion" (por el DM de Discord o CurseForge,
   donde habló).
6. **Modrinth**: cuando aprueben el proyecto, subir la 0.1.2 con el mismo changelog y la misma descripción.

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

Made one? Show it off in <#1559939532651237506>! 🧬
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

## Anuncio de la 0.1.2-beta (#announcements)

```
@Updates 🧬 **Fusionmon 0.1.2-beta is out!**

🎨 **Hand-made fusions:** artists can now give any pair its own model or texture with a resource pack, and it replaces the automatic fusion everywhere. Tutorial below 👇
⚙️ **Server owners:** new `config/fusionmon.json` with a stat bonus for fusions and emergency switches
🛠️ **Fixes:** no more kicks with Mega Showdown, fusions look right on your shoulder
🛡️ **More stable** in big modpacks

⬇️ **CurseForge:** [CURSEFORGE LINK]
Full list in #changelog. Same single file for Cobblemon 1.8.2, 1.8.1 and 1.7.3 (Cobbleverse): just update it in your mods folder (servers: update the server **and** every player).

Something wrong? <#1559939680672419962> · Need help? <#1559939634375430184>
```

## Añadidos al #faq para la 0.1.2-beta

[Añadir al final del mensaje del #faq que ya está publicado.]

```
**Can fusions mega evolve?**
Not yet. With Mega Showdown, fusing a mega evolved Pokémon returns it to normal first and the fusion keeps the Mega Stone. Real mega evolving fusions (stats, battles) are planned for the next big update.

**I run a server. Can fusions be stronger?**
Yes: set `statBonusPercent` in `config/fusionmon.json` (0 by default, 5-10 recommended) and restart the server. Players get the same numbers in their Fusion Album automatically. To make the Fusion Injector a reward instead of craftable, remove its recipe with a datapack.

**Fusionmon conflicts with another mod in my pack. What can I do?**
Fusionmon skips the part that clashes and writes a warning starting with `Fusionmon:` in `logs/latest.log`. You can turn that part off by its name in `disabledMixins`, or use `forceColorsMode: true` to draw fusions in the simpler style. Please post the log in <#1559939680672419962> so we can fix it!
```

## Mensaje para el dueño de CobbleFusion

[Por DM, donde habló la otra vez. Cambiar `[CURSEFORGE LINK]`.]

```
Hi! Thanks a lot for the detailed feedback, it helped a ton. Fusionmon 0.1.2-beta is out with everything you reported:

• **Mega Showdown kick:** fixed. The "Failed to decode packet" disconnect came from Mega Showdown removing the fusion's remembered moves one by one; fusions no longer get kicked or lose their body's moves when they mega evolve.
• **Fusing megas:** a mega evolved Pokémon now goes back to its normal form before fusing, and the fusion keeps the Mega Stone, so no more half-mega fusions (wrong ability, look or types).
• **Shoulder:** fusions now show their fused head on your shoulder. Whether they fit, and their size there, now depend on the **body**, like their size and riding everywhere else.
• **Stats:** new server option. In `config/fusionmon.json` set `"statBonusPercent": 10` (any value 0-100, each stat capped at 255) and restart: every fusion gets +10% on every base stat, existing ones too. Players see the same numbers in their Fusion Album. We kept the default at 0 so the formula stays faithful to Infinite Fusion, but it fits perfectly with injectors being a reward like on your server.
• **Stability:** if Fusionmon ever clashes with another mod, it now skips that detail and logs a `Fusionmon:` warning instead of crashing. There are also emergency switches in the same file (`disabledMixins`, `forceColorsMode`).

About megas: right now fusions can only mega evolve visually (when the head has a mega). Real mega evolving fusions, with stats and in battle, are planned for the next big update.

⬇️ [CURSEFORGE LINK]

Remember to update both the server and the client pack. If anything else comes up, send it my way!
```