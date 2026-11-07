package dev.unyielding.upsidedown.dungeon;

import java.io.IOException;
import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;

/** Immutable, bounded authored geometry. No world generation or schematic importer is involved. */
public final class DungeonTemplate {
    public static final List<String> IDS = List.of("sakura_labyrinth", "mirror_pagoda");
    public final String id, name;
    public final int sx, sy, sz, volume;
    public final BlockPos offset, spawn;
    public final List<BlockState> palette;
    public final int[] blocks;
    public final List<CompoundTag> blockEntities;
    public final List<Zone> zones;
    public record Zone(String name, BlockPos min, BlockPos max, Direction gravity) {}

    private DungeonTemplate(String id, CompoundTag tag) throws Exception {
        this.id=id; name=tag.getString("Name");
        int[] size=triple(tag,"Size"); sx=size[0];sy=size[1];sz=size[2];
        long count=(long)sx*sy*sz;
        if(sx<1||sy<1||sz<1||sx>512||sy>1024||sz>512||count>16_000_000) throw new IOException("Invalid dungeon bounds");
        volume=(int)count; offset=pos(tag,"Offset"); spawn=pos(tag,"Spawn");
        checkInside(spawn);
        for(Tag mod:tag.getList("Requirements",Tag.TAG_STRING))
            if(!ModList.get().isLoaded(mod.getAsString())) throw new IOException("Missing required mod: "+mod.getAsString());
        palette=parsePalette(tag.getList("Palette",Tag.TAG_STRING));
        blocks=decodeRuns(tag.getIntArray("Runs"),volume,palette.size());
        blockEntities=new ArrayList<>(); Set<BlockPos> used=new HashSet<>();
        for(Tag raw:tag.getList("BlockEntities",Tag.TAG_COMPOUND)) {
            CompoundTag be=((CompoundTag)raw).copy();
            BlockPos p=new BlockPos(be.getInt("x"),be.getInt("y"),be.getInt("z")); checkInside(p);
            if(!used.add(p)||!palette.get(blocks[index(p)]).hasBlockEntity()) throw new IOException("Invalid block entity at "+p);
            var type=BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(net.minecraft.resources.ResourceLocation.parse(be.getString("id")));
            if(type.isEmpty()||!type.get().isValid(palette.get(blocks[index(p)])))throw new IOException("Wrong block entity type at "+p);
            blockEntities.add(be);
        }
        int expectedEntities=0;for(int state:blocks)if(palette.get(state).hasBlockEntity())expectedEntities++;
        if(expectedEntities!=blockEntities.size())throw new IOException("Template omits block entity data (loot or spawner settings)");
        zones=new ArrayList<>();Set<String> names=new HashSet<>();
        for(Tag raw:tag.getList("Zones",Tag.TAG_COMPOUND)) {
            CompoundTag z=(CompoundTag)raw;BlockPos min=pos(z,"Min"),max=pos(z,"Max");
            checkInside(min);checkInside(max);Direction gravity=Direction.byName(z.getString("Gravity"));
            if(gravity==null||min.getX()>max.getX()||min.getY()>max.getY()||min.getZ()>max.getZ()||!names.add(z.getString("Name"))) throw new IOException("Invalid gravity zone");
            zones.add(new Zone(z.getString("Name"),min,max,gravity));
        }
        if(name.isBlank()||zones.isEmpty()) throw new IOException("Dungeon description/gravity zones missing");
    }
    public static DungeonTemplate load(String id) throws Exception {
        if(!IDS.contains(id)) throw new IOException("Unknown dungeon: "+id);
        try(var stream=DungeonTemplate.class.getResourceAsStream("/data/cuupsidedown/dungeons/"+id+".nbt")) {
            if(stream==null) throw new IOException("Dungeon template not bundled: "+id);
            return new DungeonTemplate(id,NbtIo.readCompressed(stream,NbtAccounter.create(256L*1024*1024)));
        }
    }
    public static List<BlockState> parsePalette(ListTag entries) throws Exception {
        if(entries.isEmpty()||entries.size()>65536) throw new IOException("Invalid block palette");
        List<BlockState> states=new ArrayList<>();
        for(Tag entry:entries) states.add(BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(),entry.getAsString(),false).blockState());
        return List.copyOf(states);
    }
    public static int[] decodeRuns(int[] runs,int volume,int paletteSize)throws IOException{return DungeonEncoding.decodeRuns(runs,volume,paletteSize);}
    public static int[] encodeRuns(int[] blocks){return DungeonEncoding.encodeRuns(blocks);}
    public static String stateString(BlockState state) {return BlockStateParser.serialize(state);}
    public static BlockPos pos(CompoundTag tag,String key)throws IOException {int[] a=triple(tag,key);return new BlockPos(a[0],a[1],a[2]);}
    private static int[] triple(CompoundTag tag,String key)throws IOException {int[] a=tag.getIntArray(key);if(a.length!=3)throw new IOException("Missing "+key);return a;}
    public int index(BlockPos p){return(p.getY()*sz+p.getZ())*sx+p.getX();}
    public BlockPos local(int i){return new BlockPos(i%sx,i/(sx*sz),(i/sx)%sz);}
    public AABB box(BlockPos min){return new AABB(min.getX(),min.getY(),min.getZ(),min.getX()+sx,min.getY()+sy,min.getZ()+sz);}
    private void checkInside(BlockPos p)throws IOException {
        if(p.getX()<0||p.getY()<0||p.getZ()<0||p.getX()>=sx||p.getY()>=sy||p.getZ()>=sz)throw new IOException("Coordinate outside dungeon: "+p);
    }
}
