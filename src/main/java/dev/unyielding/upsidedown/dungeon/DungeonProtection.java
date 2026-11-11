package dev.unyielding.upsidedown.dungeon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.AABB;

/** Per-instance block masks keep open space around and inside structures buildable. */
final class DungeonProtection {
    private final DungeonStore store;
    private final Map<String, BitSet> masks = new HashMap<>();
    DungeonProtection(DungeonStore store) { this.store = store; }

    void save(DungeonStore.Entry entry, DungeonTemplate template) throws IOException {
        BitSet mask = new BitSet(template.volume);
        for (int i = 0; i < template.volume; i++)
            if (!template.palette.get(template.blocks[i]).isAir()) mask.set(i);
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putIntArray("Size", new int[]{entry.sx(), entry.sy(), entry.sz()});
        tag.putLongArray("Blocks", mask.toLongArray());
        DungeonStore.write(path(entry), tag);
        masks.put(entry.id(), mask);
    }

    private Path path(DungeonStore.Entry e) { return store.directory.resolve(e.id()+".protection.nbt"); }

    private BitSet mask(DungeonStore.Entry e) {
        return masks.computeIfAbsent(e.id(), id -> {
            try {
                CompoundTag tag;
                boolean legacy = !Files.exists(path(e));
                if (legacy) {
                    if (!DungeonTemplate.IDS.contains(e.template())) throw new IOException("Unknown legacy dungeon template");
                    try (var in = DungeonProtection.class.getResourceAsStream("/data/cuupsidedown/dungeons/legacy_"+e.template()+".nbt")) {
                        if (in == null) throw new IOException("Missing legacy protection template");
                        tag = NbtIo.readCompressed(in, NbtAccounter.create(256L*1024*1024));
                    }
                } else tag = DungeonStore.read(path(e));
                if (!java.util.Arrays.equals(tag.getIntArray("Size"), new int[]{e.sx(),e.sy(),e.sz()}))
                    throw new IOException("Protection mask dimensions do not match instance " + e.id());
                if (!legacy) {
                    if (tag.getInt("Version")!=1 || !tag.contains("Blocks",Tag.TAG_LONG_ARRAY)) throw new IOException("Invalid protection mask");
                    BitSet result=BitSet.valueOf(tag.getLongArray("Blocks"));
                    if(result.length()>(long)e.sx()*e.sy()*e.sz())throw new IOException("Protection mask outside instance bounds");
                    return result;
                }
                var palette = DungeonTemplate.parsePalette(tag.getList("Palette", Tag.TAG_STRING));
                int volume = e.sx()*e.sy()*e.sz();
                int[] blocks = DungeonTemplate.decodeRuns(tag.getIntArray("Runs"), volume, palette.size());
                BitSet result = new BitSet(volume);
                for (int i=0; i<volume; i++) if (!palette.get(blocks[i]).isAir()) result.set(i);
                return result;
            } catch (Exception ex) { throw new IllegalStateException("Cannot load dungeon protection for " + e.id(), ex); }
        });
    }

    boolean contains(DungeonStore.Entry e, BlockPos p) {
        int x=p.getX()-e.min().getX(), y=p.getY()-e.min().getY(), z=p.getZ()-e.min().getZ();
        return x>=0 && x<e.sx() && y>=0 && y<e.sy() && z>=0 && z<e.sz()
            && mask(e).get((y*e.sz()+z)*e.sx()+x);
    }

    boolean intersects(DungeonStore.Entry e, AABB box) {
        if (!e.box().intersects(box)) return false;
        int x0=Math.max(0,(int)Math.floor(box.minX)-e.min().getX());
        int y0=Math.max(0,(int)Math.floor(box.minY)-e.min().getY());
        int z0=Math.max(0,(int)Math.floor(box.minZ)-e.min().getZ());
        int x1=Math.min(e.sx()-1,(int)Math.ceil(box.maxX)-1-e.min().getX());
        int y1=Math.min(e.sy()-1,(int)Math.ceil(box.maxY)-1-e.min().getY());
        int z1=Math.min(e.sz()-1,(int)Math.ceil(box.maxZ)-1-e.min().getZ());
        BitSet bits=mask(e);
        for(int y=y0;y<=y1;y++) for(int z=z0;z<=z1;z++) {
            int start=(y*e.sz()+z)*e.sx()+x0;
            int found=bits.nextSetBit(start);
            if(found>=start && found<=start+x1-x0)return true;
        }
        return false;
    }
}
