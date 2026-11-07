# Installing the combined dungeon beta

Use a copy of the world to try this beta first. Keep a world backup before changing server mods.

## Requirements

- Minecraft **1.21.1**, **Java 21**, NeoForge **21.1.195 or later**; development targets **21.1.249**.
- Lootr **1.21.1-1.11.x**; inspected pack version **1.21.1-1.11.38.125**.
- Cobblemon **1.8.4 or later**; inspected pack version **1.8.4+1.21.1**.
- SimplySwords **1.70.2 or later**; inspected pack version **1.70.2-1.21.1**.
- Keep the pack's matching dependencies: Architectury **13.0.11**, Fzzy Config **0.7.7+1.21+neoforge**, Kotlin for Forge **5.12.0**, and client-side SimplyTooltips **0.1.5** were inspected. Other releases in the accepted ranges still need compatibility testing.

**CU Build Height remains separate.** The twin pagoda is **97 × 636 × 107** blocks and extends from anchor Y−323 through Y+312. It cannot fit the ordinary Overworld height. The existing CU Build Height **1.3.0** describes an Overworld range of Y−512 through Y511; confirm the actual server dimension before placing. For example, anchor Y64 would occupy Y−259 through Y376. This dungeon jar does not expand or otherwise globally modify dimensions.

## Replace the two old modules

1. Stop Minecraft and the server.
2. In each relevant `mods` folder, move the old Upside Down jar and old Mimic Dungeon jar into a backup folder **outside `mods`**. Examples are `upsidedown-1.1.0.jar`, `cuupsidedown-1.1.0.jar`, and `cu-mimic-dungeon-1.0.1.jar`; identify the module, not only its filename.
3. Put **`upsidedown-1.2.0-beta.1.jar`** in the server's `mods` folder and every participating client's `mods` folder.
4. Keep Lootr, Cobblemon, SimplySwords, their dependencies and the separate CU Build Height mod where already required. Do not replace unrelated pack mods.
5. Start the server and clients. A duplicate `cuupsidedown` or `cumimic` error means an old standalone jar is still loading.

This one jar supplies both `cuupsidedown` and `cumimic`. Never install the old standalone Mimic jar beside it, even if it has a different filename. Existing schematic files do not need to be deleted or replaced.

## Preview and place

Commands require administrator permission level 2. Run them in the intended dimension. Coordinates are a placement anchor; the preview reports the exact occupied bounds. **Rotation is not supported in this beta.**

```text
/dungeon list
/dungeon verifyloot
/dungeon preview sakura_labyrinth <x> <y> <z>
/dungeon preview mirror_pagoda <x> <y> <z> terrain
/dungeon status
/dungeon confirm <token>
```

Replace angle-bracket placeholders with coordinates or the token shown by the command. `/dungeon list` lists the two templates and saved instances. Only one preview or placement job runs at a time.

The default preview accepts empty space and suitable replaceable dry vegetation. It refuses ordinary terrain. Adding **`terrain`** explicitly permits a limited set of natural blocks, including stone, dirt, ores and bedrock. It **cannot distinguish player-built stone from natural stone**: inspect the full reported bounds before confirming. Fluids, block entities such as containers, arbitrary construction blocks, overlapping saved dungeons/gravity zones, world borders and insufficient world height are refused.

A preview scans the entire volume but places no dungeon blocks. After a successful scan, the owner receives a token valid for three minutes. Move players and entities outside the reported area, review the bounds, and run `/dungeon confirm <token>` in the same dimension. The server rechecks the original blocks, writes its backup, then places the dungeon over successive ticks. Wait for the completion message before entering.

`/dungeon cancel` cancels a preview or verification before world writes. It does **not** cancel placement or restoration once writes are underway. Use `/dungeon status` to see progress.

After completion:

```text
/dungeon list
/dungeon entrance <instance-id>
```

`entrance` reports the entrance coordinates of a completed instance. It does not teleport anyone.

## Protection, undo and interrupted work

The saved region is protected, including its air space. Ordinary breaking/placing, explosion damage, nearby piston interference and block replacement through `/setblock` are blocked. Changes to properties of the **same block type** remain possible so normal block behavior can work. Chest opening remains available; the native Lootr chests are upright even in inverted sections.

To remove a dungeon and restore the original blocks:

```text
/dungeon undo <instance-id>
/dungeon confirm <new-token>
```

Undo first shows a separate confirmation. It restores the saved original volume, so it also removes later admin edits within that volume. Run it in the instance's dimension and move entities outside first. The saved original-block backup is retained after successful restoration.

After a crash or interrupted placement/restoration, use `/dungeon list` to find the persisted instance ID, then use the same confirmed undo process. **Do not manually delete the journal or backup files** under the world's `data/cu_dungeons` folder: they retain the restoration information and region protection. An interrupted operation is not automatically resumed as a fresh placement.

## Loot and encounters

The maze has **400 Lootr chests**, and the paired pagoda has **200**. The sole final chest at the deepest inverted level uses an unconditional high-end roll, with no empty or chance branch. Its pool is Master Ball, Ancient Origin Ball, Star's Edge, Whisperwind, Ribboncleaver, Mjolnir, Hearthflame and Arcanethyst. SimplySwords classifies the six weapons as **unique**; “high-end” is this dungeon's curated reward tier.

Loot generation is per player through Lootr. The inspected local Lootr configuration randomizes players' loot and does not automatically refresh these tables; actual server settings can differ. A loot-table override or third-party loot modifier can alter results. Run `/dungeon verifyloot` against the actual server to check the loaded reward pool.

There are **147 Mimic spawners** across both complete builds: 17 in the maze and 130 in the pagoda. Each uses an initial delay of about **1 second**, subsequent delays of **2–5 seconds**, up to **3 spawn attempts per burst**, a nearby-zombie limit of **12**, and a required player range of **16 blocks**. Spawners permit bright rooms. These values do not mean all 147 run at once; activation, mob difficulty, nearby entities and valid spawning space still matter.

## Beta test record

Static geometry, content and packaging checks passed. The completed isolated server smoke test booted, validated 512 guaranteed final-loot samples and native Lootr identities, placed both full dungeons, and checked protected walls, fast spawner NBT and the final chest's loot-table assignment.

Further heavy builds/server/client tests belong on **GitHub-hosted Actions runners**, not the owner's desktop. Restart recovery and confirmed undo remain pending there. The CI environment may use an expanded-height datapack in its disposable test world; that fixture is not shipped in this mod.

Before production use, separately verify opening the same chest with two players and rejoining, real wall/ceiling transitions, inverted stairs, client rendering and full-pack gameplay balance. Headless tests do not provide client screenshots or prove traversal. No completed two-player or client walkthrough is claimed.
