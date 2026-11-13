package dev.unyielding.upsidedown.dungeon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Original blocks and block entities, written incrementally before the corresponding placement edits. */
final class DungeonPasteBackup {
    private static final int PART_CELLS = 32768;
    private static final int PART_ENTITY_BYTES = 8 << 20;
    private static final int FORMAT = 1;
    private final DungeonStore.Entry entry;
    private final Path manifest;
    private final Path directory;
    private final int volume;
    private int covered;
    private BitSet mask;

    private DungeonPasteBackup(DungeonStore store, DungeonStore.Entry entry) {
        this.entry = entry;
        manifest = store.backup(entry.id());
        directory = store.directory.resolve(entry.id() + ".parts");
        volume = Math.multiplyExact(Math.multiplyExact(entry.sx(), entry.sy()), entry.sz());
    }

    static DungeonPasteBackup create(DungeonStore store, DungeonStore.Entry entry, BitSet mask) throws IOException {
        var backup = new DungeonPasteBackup(store, entry);
        backup.mask = (BitSet) mask.clone();
        Files.createDirectory(backup.directory);
        backup.saveManifest(0);
        return backup;
    }

    static DungeonPasteBackup open(DungeonStore store, DungeonStore.Entry entry) throws IOException {
        var backup = new DungeonPasteBackup(store, entry);
        CompoundTag tag = DungeonStore.read(backup.manifest);
        if (tag.getInt("Format") != FORMAT || !entry.id().equals(tag.getString("Instance"))
                || !entry.dimension().equals(tag.getString("Dimension"))
                || !Arrays.equals(tag.getIntArray("Min"), DungeonStore.vec(entry.min()))
                || !Arrays.equals(tag.getIntArray("Size"), new int[]{entry.sx(), entry.sy(), entry.sz()})
                || !tag.contains("Covered", Tag.TAG_INT)) {
            throw new IOException("Direct placement backup does not match the dungeon journal");
        }
        if(tag.contains("PlacementMask", Tag.TAG_LONG_ARRAY)) backup.mask=BitSet.valueOf(tag.getLongArray("PlacementMask"));
        else { backup.mask=new BitSet(backup.volume); backup.mask.set(0,backup.volume); }
        if(backup.mask.length()>backup.volume) throw new IOException("Invalid placement mask");
        backup.covered = tag.getInt("Covered");
        if (backup.covered < 0 || backup.covered > backup.volume
                || ("PLACED".equals(entry.status()) && backup.covered != backup.volume)) {
            throw new IOException("Invalid backed-up placement length");
        }
        return backup;
    }

    BitSet mask() { return (BitSet) mask.clone(); }

    /** Direct backups capture every cell, even when the original placement skipped it. */
    void include(BitSet added) throws Exception {
        if(covered!=volume)throw new IOException("Cannot extend an incomplete direct backup");
        BitSet newlyIncluded=(BitSet)added.clone();newlyIncluded.andNot(mask);
        if(!newlyIncluded.isEmpty()) {
            // A skipped cell may have been edited after the original dungeon placement.
            // Only extend undo coverage when the saved original is air, matching the
            // air required by the repair. This avoids resurrecting old containers/items.
            TreeSet<Integer> starts=new TreeSet<>();
            try(var files=Files.list(directory)) {
                files.map(p->p.getFileName().toString()).filter(n->n.matches("[0-9]+\\.nbt"))
                    .forEach(n->starts.add(Integer.parseInt(n.substring(0,n.length()-4))));
            }
            Part part=null;
            for(int i=newlyIncluded.nextSetBit(0);i>=0;i=newlyIncluded.nextSetBit(i+1)) {
                Integer start=starts.floor(i);
                if(start==null)throw new IOException("Missing backup segment for repaired floor");
                if(part==null||part.start()!=start)part=readPart(start);
                if(i>=part.end()||!part.state(i).isAir())throw new IOException("Original terrain in the skipped floor strip was not empty; undo cannot safely cover this repair.");
            }
        }
        BitSet previous=(BitSet)mask.clone();
        mask.or(added);
        try { saveManifest(covered); } catch(IOException failure) { mask=previous;throw failure; }
    }

    int covered() {
        return covered;
    }

    private void saveManifest(int end) throws IOException {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Format", FORMAT);
        tag.putString("Instance", entry.id());
        tag.putString("Dimension", entry.dimension());
        tag.putIntArray("Size", new int[]{entry.sx(), entry.sy(), entry.sz()});
        tag.putIntArray("Min", DungeonStore.vec(entry.min()));
        tag.putInt("Covered", end);
        tag.putLongArray("PlacementMask",mask.toLongArray());
        DungeonStore.write(manifest, tag);
    }

    /** One small band of the dungeon. Both capture and application run inside the ordinary tick budget. */
    final class Capture {
        final int start = covered;
        final int[] states = new int[Math.min(PART_CELLS, volume - start)];
        final Map<BlockState, Integer> indices = new HashMap<>();
        final ListTag palette = new ListTag();
        final ListTag entities = new ListTag();
        int count;
        long entityBytes;

        void add(ServerLevel level, BlockPos pos) throws IOException {
            BlockState state = level.getBlockState(pos);
            states[count++] = indices.computeIfAbsent(state, key -> {
                palette.add(StringTag.valueOf(DungeonTemplate.stateString(key)));
                return palette.size() - 1;
            });
            if (state.hasBlockEntity()) {
                BlockEntity entity = level.getBlockEntity(pos);
                if (entity == null) throw new IOException("Cannot capture the block entity at " + pos);
                CompoundTag original = entity.saveWithFullMetadata(level.registryAccess());
                int size = original.sizeInBytes();
                if (size < 0 || size > 128 << 20) throw new IOException("Block entity exceeds backup capacity at " + pos);
                entityBytes += size;
                entities.add(original);
            }
        }

        boolean full() {
            return count == states.length || entityBytes >= PART_ENTITY_BYTES;
        }

        void commit() throws IOException {
            CompoundTag tag = new CompoundTag();
            tag.putString("Instance", entry.id());
            tag.putInt("Start", start);
            tag.putInt("Length", count);
            tag.put("Palette", palette);
            tag.putIntArray("Runs", DungeonTemplate.encodeRuns(count == states.length ? states : Arrays.copyOf(states, count)));
            tag.put("BlockEntities", entities);
            DungeonStore.write(directory.resolve(start + ".nbt"), tag);
            // If the server stops between these writes, this unclaimed part has never been applied.
            saveManifest(start + count);
            covered = start + count;
        }
    }

    record Part(int start, int[] states, List<BlockState> palette, Map<BlockPos, CompoundTag> entities) {
        int end() { return start + states.length; }
        BlockState state(int cursor) { return palette.get(states[cursor - start]); }
    }

    Part readPart(int start) throws Exception {
        CompoundTag tag = DungeonStore.read(directory.resolve(start + ".nbt"));
        int count = tag.getInt("Length");
        if (!entry.id().equals(tag.getString("Instance")) || tag.getInt("Start") != start
                || count < 1 || count > PART_CELLS || (long) start + count > covered) {
            throw new IOException("Invalid original block backup at " + start);
        }
        var palette = DungeonTemplate.parsePalette(tag.getList("Palette", Tag.TAG_STRING));
        int[] states = DungeonTemplate.decodeRuns(tag.getIntArray("Runs"), count, palette.size());
        Map<BlockPos, CompoundTag> entities = new HashMap<>();
        for (Tag raw : tag.getList("BlockEntities", Tag.TAG_COMPOUND)) {
            CompoundTag entity = (CompoundTag) raw;
            BlockPos pos = new BlockPos(entity.getInt("x"), entity.getInt("y"), entity.getInt("z"));
            BlockPos local = pos.subtract(entry.min());
            if (local.getX() < 0 || local.getX() >= entry.sx() || local.getY() < 0 || local.getY() >= entry.sy()
                    || local.getZ() < 0 || local.getZ() >= entry.sz()) throw new IOException("Block entity outside backup bounds");
            int index = (local.getY() * entry.sz() + local.getZ()) * entry.sx() + local.getX() - start;
            var type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(ResourceLocation.parse(entity.getString("id")));
            if (index < 0 || index >= count || type.isEmpty() || !type.get().isValid(palette.get(states[index]))
                    || entities.put(pos, entity) != null) throw new IOException("Invalid block entity in backup at " + pos);
        }
        int expected = 0;
        for (int state : states) if (palette.get(state).hasBlockEntity()) expected++;
        if (expected != entities.size()) throw new IOException("Backup is missing original block entities");
        return new Part(start, states, palette, entities);
    }
}
