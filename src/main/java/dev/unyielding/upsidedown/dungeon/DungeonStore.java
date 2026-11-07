package dev.unyielding.upsidedown.dungeon;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

/** Durable placement journal. An interrupted placement remains protected until explicitly restored. */
final class DungeonStore {
    final Path directory;
    final Map<String,Entry> entries=new LinkedHashMap<>();
    record Entry(String id,String template,String dimension,BlockPos min,int sx,int sy,int sz,BlockPos spawn,String status) {
        AABB box(){return new AABB(min.getX(),min.getY(),min.getZ(),min.getX()+sx,min.getY()+sy,min.getZ()+sz);}
        Entry status(String state){return new Entry(id,template,dimension,min,sx,sy,sz,spawn,state);}
        CompoundTag save(){CompoundTag t=new CompoundTag();t.putString("Id",id);t.putString("Template",template);t.putString("Dimension",dimension);t.putIntArray("Min",vec(min));t.putIntArray("Size",new int[]{sx,sy,sz});t.putIntArray("Spawn",vec(spawn));t.putString("Status",status);return t;}
        static Entry load(CompoundTag t)throws IOException {
            int[] s=t.getIntArray("Size");String id=t.getString("Id");
            if(!id.matches("[a-f0-9]{12}")||s.length!=3||s[0]<1||s[1]<1||s[2]<1||(long)s[0]*s[1]*s[2]>16_000_000)throw new IOException("Invalid dungeon journal entry");
            return new Entry(id,t.getString("Template"),t.getString("Dimension"),DungeonTemplate.pos(t,"Min"),s[0],s[1],s[2],DungeonTemplate.pos(t,"Spawn"),t.getString("Status"));
        }
    }
    DungeonStore(MinecraftServer server)throws IOException {
        directory=server.getWorldPath(LevelResource.ROOT).resolve("data/cu_dungeons");Files.createDirectories(directory);
        Path file=directory.resolve("placements.nbt");
        if(Files.exists(file))for(Tag t:read(file).getList("Entries",Tag.TAG_COMPOUND)){Entry e=Entry.load((CompoundTag)t);entries.put(e.id(),e);}
    }
    void put(Entry entry)throws IOException {
        Entry previous=entries.put(entry.id(),entry);
        try{save();}catch(IOException e){if(previous==null)entries.remove(entry.id());else entries.put(entry.id(),previous);throw e;}
    }
    void remove(String id)throws IOException {
        Entry previous=entries.remove(id);
        try{save();}catch(IOException e){if(previous!=null)entries.put(id,previous);throw e;}
    }
    void save()throws IOException {CompoundTag root=new CompoundTag();ListTag list=new ListTag();entries.values().forEach(e->list.add(e.save()));root.put("Entries",list);write(directory.resolve("placements.nbt"),root);}
    Path backup(String id){if(!id.matches("[a-f0-9]{12}"))throw new IllegalArgumentException("Invalid instance ID");return directory.resolve(id+".before.nbt");}
    static CompoundTag read(Path path)throws IOException{return NbtIo.readCompressed(path,NbtAccounter.create(256L*1024*1024));}
    static void write(Path path,CompoundTag data)throws IOException {
        net.neoforged.neoforge.common.IOUtilities.writeNbtCompressed(data,path);
    }
    static int[] vec(BlockPos p){return new int[]{p.getX(),p.getY(),p.getZ()};}
}
