# Source reconstruction and provenance

This project started by recovering editable Minecraft 1.21.1 / NeoForge source from two binaries supplied by their author. It was not recovered from an original Java-source checkout. The resulting source is integrated with the new dungeon content and placement system in `src/main/`.

## Recovery inputs

[The provenance record](ci/provenance.json) preserves the filenames, byte lengths and SHA-256 hashes of the unchanged recovery inputs:

| Original binary | Mod ID | SHA-256 |
|---|---|---|
| `upsidedown-1.1.0.jar` | `cuupsidedown` | `dfcaa5fc269cb34cb297498e43aca6e346f6045542542119a707c43a99331092` |
| `cu-mimic-dungeon-1.0.1.jar` | `cumimic` | `c439e07a3b9ae0eaf85709c8ccf2a5df15faeae6f295e3e510c42921e9bcc52f` |

Recovery used Vineflower 1.10.1. Original binary metadata declared the MIT license. The input jars, raw decompiler output and local recovery workspace are excluded from the public source repository. Original classes are rebuilt from editable source; this mod does not execute opaque embedded copies of the old jars.

## Build and verification

The source targets Java 21, Gradle 9.2.1, ModDevGradle 2.0.148 and NeoForge 21.1.249. The current output name is `build/libs/upsidedown-1.2.0-beta.3.jar`. Version and mod metadata must be updated together for later releases. Archives use stable entry ordering without preserved file timestamps.

Heavy builds and server/client tests for this project belong on GitHub-hosted Actions runners. See [the README](README.md) for commands and the current verification record. Compilation alone does not establish gameplay correctness. External runtime dependencies are downloaded from the pinned official URLs in [the runtime manifest](ci/runtime-mods.json), rather than redistributed with this source.

## Reconstruction repairs and integration

The recovered base required standard mixin cast repairs (`(Target)(Object)this`) and an explicit `SavedData.Factory<ZoneData>` generic parameter to compile. The original Mimic access transformer, resources and mixins were retained while integrating the new dungeon module.

`DungeonZoneBridge` adds dimension- and instance-scoped access to the existing gravity persistence and network synchronization. It runs on the server thread, validates replacement bounds and names before publishing, preserves unrelated zones, and requires names under `instancePrefix + "/"`.

Its public methods are:

- `replace(MinecraftServer server, ResourceKey<Level> dimension, String instancePrefix, List<Flip.Zone> replacements)`.
- `remove(MinecraftServer server, ResourceKey<Level> dimension, String instancePrefix)`, returning the removed count.
- `snapshot(MinecraftServer server, ResourceKey<Level> dimension, String instancePrefix)`, returning an immutable filtered list.

`Flip.Zone` is a public record containing its name, dimension, AABB bounds with exclusive maximum coordinates, and gravity direction. `UpsideDown.publish` has package-private visibility so the bridge can use the existing saved-data and client synchronization path. Existing `/flipzone` layout commands remain available.

The main entry point is `UpsideDown(IEventBus modBus, ModContainer container)` in `dev.unyielding.upsidedown`. The integrated Mimic module retains `cumimic` and its event registration for existing data and advancements. New dungeon placement, content, protection and undo code lives in its own module and uses the bridge to manage its zones.

The combined jar replaces **both** standalone inputs. Installing either old jar alongside it duplicates a mod ID. See [installation instructions](docs/INSTALL.md) for the complete replacement procedure.
