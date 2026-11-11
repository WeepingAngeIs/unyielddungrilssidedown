# Working on Upside Down Dungeons

- Run heavy builds, complete template checks, and game/server/client tests on GitHub-hosted Actions runners. Keep local work to source editing and lightweight inspection.
- The owner allows local testing only as a last resort when hosted options cannot perform a required check. Explain the concrete hosted blocker before using that fallback. An unprepared workflow alone is not a blocker; prepare it first.
- Never launch or modify the owner's installed Minecraft profiles, worlds, or live server as part of development checks.
- This source produces one replacement jar containing both `cuupsidedown` and `cumimic`. Keep the existing mod IDs and original gravity commands compatible.
- Dungeons spawn by command. Support immediate /place structure placement, optional /dungeon preview, persistent protection, and confirmed undo from saved backups.
- Keep native Lootr per-player containers. The deepest inverted-pagoda chest must preserve its unconditional curated high-end reward roll.
- External pack dependencies remain external. CI downloads the pinned official files in `ci/runtime-mods.json` and checks their hashes; do not commit third-party jars or test worlds.
- Distinguish source checks, real headless server checks, and actual client/two-player playtesting in reports. Never infer rendered appearance or traversal success from headless checks.
