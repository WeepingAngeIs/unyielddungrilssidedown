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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Per-instance block masks keep open space around and inside structures buildable. */
final class DungeonProtection {
    private final DungeonStore store;
    private final Map<String, BitSet> masks = new HashMap<>();
    DungeonProtection(DungeonStore store) { this.store = store; }

    void save(DungeonStore.Entry entry, DungeonTemplate template) throws IOException {
        BitSet mask = new BitSet(template.volume);
        for (int i = 0; i < template.volume; i++)
            if (!template.palette.get(template.blocks[i]).isAir()
                    && !treeBlock(entry, i, template.palette.get(template.blocks[i]))) mask.set(i);
        persist(entry,mask);
    }

    void include(DungeonStore.Entry entry,BitSet added) throws IOException {
        BitSet result=(BitSet)mask(entry).clone();
        result.or(added);
        persist(entry,result);
    }

    private void persist(DungeonStore.Entry entry,BitSet mask) throws IOException {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 2);
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
                    int version=tag.getInt("Version");
                    if ((version!=1 && version!=2) || !tag.contains("Blocks",Tag.TAG_LONG_ARRAY)) throw new IOException("Invalid protection mask");
                    BitSet result=BitSet.valueOf(tag.getLongArray("Blocks"));
                    if(result.length()>(long)e.sx()*e.sy()*e.sz())throw new IOException("Protection mask outside instance bounds");
                    if(version==1) removeOldTrees(e,result);
                    return result;
                }
                var palette = DungeonTemplate.parsePalette(tag.getList("Palette", Tag.TAG_STRING));
                int volume = e.sx()*e.sy()*e.sz();
                int[] blocks = DungeonTemplate.decodeRuns(tag.getIntArray("Runs"), volume, palette.size());
                BitSet result = new BitSet(volume);
                for (int i=0; i<volume; i++) {
                    BlockState state=palette.get(blocks[i]);
                    if (!state.isAir() && !treeBlock(e,i,state)) result.set(i);
                }
                return result;
            } catch (Exception ex) { throw new IllegalStateException("Cannot load dungeon protection for " + e.id(), ex); }
        });
    }

    /** Only the authored decorative cherry trees are exempt; wooden building posts stay protected. */
    private static boolean treeBlock(DungeonStore.Entry e,int i,BlockState state) {
        if (!state.is(Blocks.CHERRY_LOG) && !state.is(Blocks.CHERRY_LEAVES)) return false;
        int x=i%e.sx(), y=i/(e.sx()*e.sz()), z=(i/e.sx())%e.sz();
        if (e.template().equals("sakura_labyrinth") && e.sx()==205 && e.sy()==36 && e.sz()==222) {
            if (y<6 || y>15 || !(Math.abs(x-6)<=3 || Math.abs(x-198)<=3)) return false;
            for(int center=10;center<=210;center+=20)if(Math.abs(z-center)<=3)return true;
        }
        if (e.template().equals("mirror_pagoda") && e.sx()==97 && e.sy()==636 && e.sz()==107)
            return y>=324 && y<=336 && (Math.abs(x-10)<=5 || Math.abs(x-86)<=5)
                && (Math.abs(z-12)<=5 || Math.abs(z-90)<=5);
        return false;
    }

    /** Beta.4 saved every non-air block. Remove only its original square tree cells. */
    private static void removeOldTrees(DungeonStore.Entry e,BitSet mask) {
        if(e.template().equals("sakura_labyrinth") && e.sx()==205 && e.sy()==36 && e.sz()==222) {
            for(int x:new int[]{6,198}) for(int z=10;z<=210;z+=20) {
                clearBox(e,mask,x,6,z,x,10,z);
                clearBox(e,mask,x-3,10,z-3,x+3,12,z+3);
            }
        } else if(e.template().equals("mirror_pagoda") && e.sx()==97 && e.sy()==636 && e.sz()==107) {
            for(int x:new int[]{10,86}) for(int z:new int[]{12,90}) {
                clearBox(e,mask,x,324,z,x,330,z);
                clearBox(e,mask,x-5,329,z-5,x+5,333,z+5);
            }
        }
    }
    private static void clearBox(DungeonStore.Entry e,BitSet mask,int x0,int y0,int z0,int x1,int y1,int z1) {
        for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++) {
            int start=(y*e.sz()+z)*e.sx()+x0;
            mask.clear(start,start+x1-x0+1);
        }
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
