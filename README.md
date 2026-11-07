# Unyielding: Upside Down Dungeons

Minecraft **1.21.1**, **NeoForge**, **Java 21**. This beta combines Upside Down Zones and Mimic Dungeon in one jar and adds two command-placed dungeons. It retains the `cuupsidedown` and `cumimic` mod IDs and the existing `/flipzone` commands.

| Dungeon | Contents |
|---|---|
| `sakura_labyrinth` | 100 themed rooms, 17 route loops, 400 personal-loot chests, 17 Mimic spawners |
| `mirror_pagoda` | 25 upright + 25 inverted floors, 200 personal-loot chests, 130 Mimic spawners |

The maze includes 300 supply chests on connected walkways and 100 chamber rewards. Eighty rewards sit on elevated normal-gravity ledges reached from rooms with altered gravity. Themes include cherry blossom, emo, dark, zen, flower and moon rooms.

The deepest inverted-pagoda chest guarantees **one curated high-end reward per player's first loot generation**: a Master Ball, Ancient Origin Ball, or one of six verified SimplySwords unique weapons. Additional supplies accompany it. Ordinary rare chests have a small optional unique-weapon chance; they do not use the final guaranteed pool.

Lootr chests use native per-player inventories and remain upright. Custom tables and planters can face any of six directions; pagoda stairs are mirrored for inverted traversal. Dungeon regions protect the builds, furniture, chests and spawners from ordinary editing and damage.

Nothing spawns naturally. Administrators preview an area, inspect its bounds, then confirm placement. Placement saves the original blocks and supports a separately confirmed undo.

## Install and use

Follow [installation and command instructions](docs/INSTALL.md). Install the combined jar on the server **and every client**. Replace the old Upside Down and Mimic jars; do not run both old modules alongside this combined jar.

Required pack mods: Lootr, Cobblemon and SimplySwords, with their normal dependencies. The 636-block-tall twin pagoda also needs an already expanded dimension, such as the existing **CU Build Height** server mod. This jar does **not** alter world height globally.

## Source and checks

[Reconstruction notes](RECONSTRUCTION.md) describe the recovered starting source. Development targets Java 21 and NeoForge 21.1.249. **Run builds and heavy server/client tests on GitHub-hosted Actions runners, not on the owner's desktop.** The following are CI commands; lightweight source/content review can remain local.

```text
./gradlew build
python tools/make_loot_tables.py --check
python tools/validate_content.py --mods "PATH/TO/CLIENT/mods" --jar build/libs/upsidedown-1.2.0-beta.1.jar
```

`make_loot_tables.py` regenerates the four loot tables; `--check` checks for differences without writing. `validate_content.py` checks the loot guarantee, template bounds and runs, chest identities, spawner settings, room routes and packaged resources. When template JSON sidecars are present, it also checks room graphs, reward pockets and accessible walkways.

The administrator command `/dungeon verifyloot` checks the running server's registries and loaded tables, fills 704 temporary chest inventories, verifies all 512 final-chest samples retain a high-end reward, and checks independent native Lootr chest identities. It does not place blocks or grant items.

## Beta verification status

Static content checks passed: 600 fresh Lootr chests; 147 enclosed fast spawners; 100 connected maze rooms; 80 normal-gravity reward pockets excluded from gravity zones; both 25-floor wings; and one guaranteed final reward chest at the deepest chest level. The packaged templates and loot tables match the validated source.

The completed isolated server smoke test booted successfully, validated the loaded loot tables and native Lootr identities, and preserved a high-end reward in all 512 final-chest samples. Both complete dungeons were placed. Protected walls resisted replacement, fast spawner NBT was inspected, and the deepest chest referenced the final reward table.

**Pending on GitHub-hosted runtime tests:** restart recovery and confirmed undo. Client rendering, real gravity traversal, two-player inventory persistence and full-pack performance remain unverified. Screenshots and walkthrough observations must come from an actual client playtest; no headless test establishes them.

CI downloads the six pinned external runtime mods from official CurseForge CDN URLs in [the runtime manifest](ci/runtime-mods.json) and verifies their SHA-256 hashes. Third-party jars are not bundled. A disposable CI-only height datapack can supply the expanded test world; it is not part of the released mod and does not replace the server's CU Build Height setup.
