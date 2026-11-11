# Unyielding: Upside Down Dungeons

Minecraft **1.21.1**, **NeoForge**, **Java 21**. This beta combines Upside Down Zones and Mimic Dungeon in one jar and adds two command-placed dungeons. It retains the `cuupsidedown` and `cumimic` mod IDs and the existing `/flipzone` commands.

| Dungeon | Contents |
|---|---|
| `sakura_labyrinth` | 100 themed rooms, 17 route loops, 400 personal-loot chests, 17 Mimic spawners |
| `mirror_pagoda` | 25 upright + 25 inverted floors, 200 personal-loot chests, 130 Mimic spawners |

The maze includes 300 supply chests on connected walkways and 100 chamber rewards. Eighty rewards sit on elevated normal-gravity ledges reached from rooms with altered gravity. Themes include cherry blossom, emo, dark, zen, flower and moon rooms.

The deepest inverted-pagoda chest guarantees **one curated high-end reward per player's first loot generation**: a Master Ball, Ancient Origin Ball, or one of six verified SimplySwords unique weapons. Additional supplies accompany it. Ordinary rare chests have a small optional unique-weapon chance; they do not use the final guaranteed pool.

Lootr chests use native per-player inventories and remain upright. Custom tables and planters can face any of six directions; pagoda stairs are mirrored for inverted traversal. Dungeon regions protect the builds, furniture, chests and spawners from ordinary editing and damage.

Administrators can place either dungeon at their position with one command. The dungeon replaces the blocks inside its bounds, including fluids, modded terrain and containers. Rooms clear to air as the build is placed.

```mcfunction
/place structure cuupsidedown:sakura_labyrinth
/place structure cuupsidedown:mirror_pagoda
```

Append coordinates to choose a different anchor. Placement starts immediately and writes the build over successive server ticks. Use `/dungeon status` for progress. The dungeons spawn only by command. The older `/dungeon preview` and confirmation flow is still available.

## Install and use

Follow [installation and command instructions](docs/INSTALL.md). Install the combined jar on the server **and every client**. Replace the old Upside Down and Mimic jars; do not run both old modules alongside this combined jar.

Required pack mods: Lootr, Cobblemon and SimplySwords, with their normal dependencies. The 636-block-tall twin pagoda also needs an already expanded dimension, such as the existing **CU Build Height** server mod. This jar does **not** alter world height globally.

## Source and checks

[Reconstruction notes](RECONSTRUCTION.md) describe the recovered starting source. Development targets Java 21 and NeoForge 21.1.249. **Run builds and heavy server/client tests on GitHub-hosted Actions runners, not on the owner's desktop.** The following are CI commands; lightweight source/content review can remain local.

```text
./gradlew build
python tools/make_loot_tables.py --check
python tools/validate_content.py --mods "PATH/TO/CLIENT/mods" --jar build/libs/upsidedown-1.2.0-beta.3.jar
```

`make_loot_tables.py` regenerates the four loot tables; `--check` checks for differences without writing. `validate_content.py` checks the loot guarantee, template bounds and runs, chest identities, spawner settings, room routes and packaged resources. When template JSON sidecars are present, it also checks room graphs, reward pockets and accessible walkways.

The administrator command `/dungeon verifyloot` checks the running server's registries and loaded tables, fills 704 temporary chest inventories, verifies all 512 final-chest samples retain a high-end reward, and checks independent native Lootr chest identities. It does not place blocks or grant items.

## Beta verification status

Static content checks passed: 600 fresh Lootr chests; 147 enclosed fast spawners; 100 connected maze rooms; 80 normal-gravity reward pockets excluded from gravity zones; both 25-floor wings; and one guaranteed final reward chest at the deepest chest level. The packaged templates and loot tables match the validated source.

[GitHub-hosted build and integration run 42410149580 passed](https://github.com/WeepingAngeIs/unyielddungrilssidedown/actions/runs/42410149580) for source commit `dc4b3e62fdfe6dbc6aee3bf81c411516b45ae2a8`. It placed both complete dungeons, checked all 8,239,404 saved block positions and 747 block entities, verified live gravity directions before and after restart, preserved high-end rewards in all 512 final-chest samples, and confirmed protection and fast spawner data. Confirmed undo restored every tested position to its original air and removed both journal entries and all dungeon zones.

A third startup tested a simulated unfinished journal entry with stale gravity: its gravity was removed, its backup and protection remained, and the completed sibling dungeon retained its zones. This is an interrupted-state fixture, not a claim that arbitrary real crashes were simulated.

Client rendering, real gravity traversal, two-player inventory persistence and full-pack performance remain unverified. Screenshots and walkthrough observations must come from an actual client playtest; no headless test establishes them. The jar has not been installed into an existing client profile or live server.

CI downloads the six pinned external runtime mods from official CurseForge CDN URLs in [the runtime manifest](ci/runtime-mods.json) and verifies their SHA-256 hashes. Third-party jars are not bundled. A disposable CI-only height datapack can supply the expanded test world; it is not part of the released mod and does not replace the server's CU Build Height setup.
