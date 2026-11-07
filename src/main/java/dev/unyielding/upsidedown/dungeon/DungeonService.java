package dev.unyielding.upsidedown.dungeon;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.unyielding.upsidedown.DungeonZoneBridge;
import dev.unyielding.upsidedown.Flip;
import dev.unyielding.upsidedown.UpsideDown;
import java.io.IOException;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Preview -> terrain scan -> owner-bound confirmation -> revalidation -> backed-up, tick-budgeted placement. */
public final class DungeonService {
    private static final UUID CONSOLE=new UUID(0,0);
    private static final TicketType<String> TICKET=TicketType.create("cu_dungeon_work",Comparator.<String>naturalOrder());
    private static final int BATCH=8192, FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
    private static final Set<String> TERRAIN=Set.of("stone","deepslate","dirt","coarse_dirt","rooted_dirt","grass_block","podzol","mycelium","gravel","sand","red_sand","sandstone","red_sandstone","tuff","granite","diorite","andesite","calcite","bedrock","clay","mud","snow","snow_block","ice","packed_ice","blue_ice","netherrack","end_stone","dripstone_block");
    private final MinecraftServer server;
    private final DungeonStore store;
    private Job job;
    private int ticks;

    private enum Phase { SCAN, READY, VERIFY, PLACE, RESTORE }
    private static final class Job {
        String token,instance; UUID owner;CommandSourceStack source;ServerLevel level;
        DungeonTemplate template;DungeonStore.Entry entry;
        BlockPos min;int sx,sy,sz,volume,cursor;boolean terrain,undo,locked,journaled;
        Phase phase;long expires;int[] before;
        List<BlockState> palette=new ArrayList<>();Map<BlockState,Integer> paletteMap=new HashMap<>();
        Set<ChunkPos> tickets=new HashSet<>();
        AABB box(){return new AABB(min.getX(),min.getY(),min.getZ(),min.getX()+sx,min.getY()+sy,min.getZ()+sz);}
        BlockPos at(int i){return min.offset(i%sx,i/(sx*sz),(i/sx)%sz);}
    }

    public DungeonService(MinecraftServer server)throws IOException {this.server=server;store=new DungeonStore(server);}
    public void stop(){if(job!=null)release(job);job=null;}
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        var root=Commands.literal("dungeon").requires(s->s.hasPermission(2))
          .executes(c->get(c.getSource()).help(c.getSource()))
          .then(Commands.literal("list").executes(c->get(c.getSource()).list(c.getSource())))
          .then(Commands.literal("status").executes(c->get(c.getSource()).status(c.getSource())))
          .then(Commands.literal("cancel").executes(c->get(c.getSource()).cancel(c.getSource())))
          .then(Commands.literal("verifyloot").executes(c->DungeonLootChecks.run(c.getSource())))
          .then(Commands.literal("confirm").then(Commands.argument("token",StringArgumentType.word()).executes(c->get(c.getSource()).confirm(c.getSource(),StringArgumentType.getString(c,"token")))))
          .then(Commands.literal("undo").then(Commands.argument("instance",StringArgumentType.word()).executes(c->get(c.getSource()).undo(c.getSource(),StringArgumentType.getString(c,"instance")))))
          .then(Commands.literal("entrance").then(Commands.argument("instance",StringArgumentType.word()).executes(c->get(c.getSource()).entrance(c.getSource(),StringArgumentType.getString(c,"instance")))))
          .then(Commands.literal("preview").then(Commands.argument("template",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(DungeonTemplate.IDS,b))
             .then(Commands.argument("anchor",BlockPosArgument.blockPos())
                .executes(c->get(c.getSource()).preview(c.getSource(),StringArgumentType.getString(c,"template"),BlockPosArgument.getBlockPos(c,"anchor"),false))
                .then(Commands.literal("terrain").executes(c->get(c.getSource()).preview(c.getSource(),StringArgumentType.getString(c,"template"),BlockPosArgument.getBlockPos(c,"anchor"),true))))));
        d.register(root);
    }
    private static DungeonService get(CommandSourceStack s){return DungeonModule.service(s.getServer());}
    private static UUID owner(CommandSourceStack s){return s.getEntity()==null?CONSOLE:s.getEntity().getUUID();}
    private static void say(CommandSourceStack s,String text){s.sendSuccess(()->Component.literal(text),false);}
    private static int error(CommandSourceStack s,String text){s.sendFailure(Component.literal(text));return 0;}
    private static String shortId(){return UUID.randomUUID().toString().replace("-","").substring(0,12);}

    int help(CommandSourceStack s){say(s,"Dungeon commands: list; preview <template> <x y z> [terrain]; confirm <token>; status; cancel; undo <instance>; entrance <instance>; verifyloot. Preview places NO blocks. Default accepts empty space/vegetation only. Terrain mode explicitly permits natural stone/dirt/bedrock, never containers or arbitrary blocks.");return 1;}
    int list(CommandSourceStack s){say(s,"Templates: sakura_labyrinth — 100 themed gravity chambers; mirror_pagoda — upright and inverted Japanese Mimic towers. No natural spawning. Existing instances:");store.entries.values().forEach(e->say(s,e.id()+" "+e.template()+" "+e.dimension()+" "+e.status()+" "+e.min().toShortString()));return 1;}
    int status(CommandSourceStack s){if(job==null)say(s,"No placement job. /dungeon list shows saved instances, including interrupted ones that can be restored with undo.");else say(s,job.phase+" "+job.instance+" "+(100L*job.cursor/Math.max(1,job.volume))+"%; bounds "+bounds(job)+"; preview token "+job.token);return 1;}
    private String bounds(Job j){return j.min.toShortString()+" through "+j.min.offset(j.sx-1,j.sy-1,j.sz-1).toShortString();}

    int preview(CommandSourceStack s,String id,BlockPos anchor,boolean terrain) {
        if(job!=null)return error(s,"Finish or cancel the current preview/job first. /dungeon status");
        try {
            List<String> problems=DungeonLootChecks.problems(s.getLevel());
            if(!problems.isEmpty())return error(s,"Dungeon dependencies/loot not ready: "+String.join("; ",problems));
            DungeonTemplate t=DungeonTemplate.load(id);Job j=new Job();j.template=t;j.source=s;j.owner=owner(s);j.level=s.getLevel();
            j.token=shortId();j.instance=shortId();j.min=anchor.offset(t.offset);j.sx=t.sx;j.sy=t.sy;j.sz=t.sz;j.volume=t.volume;j.terrain=terrain;j.phase=Phase.SCAN;j.before=new int[t.volume];
            if(j.min.getY()<j.level.getMinBuildHeight()||(long)j.min.getY()+j.sy>j.level.getMaxBuildHeight())return error(s,"Does not fit this dimension: dungeon Y "+j.min.getY()+".."+(j.min.getY()+j.sy-1)+", world Y "+j.level.getMinBuildHeight()+".."+(j.level.getMaxBuildHeight()-1)+". The twin pagoda needs expanded height; no blocks changed.");
            if(!j.level.getWorldBorder().isWithinBounds(j.min)||!j.level.getWorldBorder().isWithinBounds(j.min.offset(j.sx-1,0,j.sz-1)))return error(s,"Dungeon would cross the world border.");
            for(var e:store.entries.values())if(e.dimension().equals(j.level.dimension().location().toString())&&e.box().intersects(j.box()))return error(s,"Overlaps saved dungeon "+e.id()+". Choose another location.");
            for(var z:Flip.SERVER)if(z.dim().equals(j.level.dimension())&&z.box().intersects(j.box()))return error(s,"Overlaps existing gravity zone "+z.name()+". Choose another location or review that zone first.");
            job=j;say(s,"PREVIEW ONLY: "+t.name+". Bounds "+bounds(j)+" ("+j.volume+" cells). Scanning before offering confirmation. "+(terrain?"TERRAIN mode may replace player-built natural stone too; inspect the bounds carefully.":"Empty-space mode: terrain and existing builds are refused."));draw(j);return 1;
        }catch(Exception e){UpsideDown.LOG.error("Dungeon preview failed",e);return error(s,"Cannot preview dungeon: "+e.getMessage());}
    }

    int confirm(CommandSourceStack s,String token) {
        Job j=job;
        if(j==null||j.phase!=Phase.READY||!j.token.equals(token)||!j.owner.equals(owner(s)))return error(s,"No ready preview with this token owned by you.");
        if(System.currentTimeMillis()>j.expires){release(j);job=null;return error(s,"Preview expired. Preview again.");}
        if(s.getLevel()!=j.level)return error(s,"Return to the preview's dimension before confirming.");
        if(occupied(j))return error(s,"A player or entity is inside the bounds. Move everyone outside the outlined area, then confirm again.");
        j.source=s;j.locked=true;j.cursor=0;
        if(j.undo){
            try {store.put(j.entry.status("RESTORING"));DungeonZoneBridge.remove(server,j.level.dimension(),"dungeon/"+j.instance);DungeonZoneBridge.flush(server);j.phase=Phase.RESTORE;say(s,"Restoring the original terrain from the saved backup. Stay outside the bounds.");}
            catch(Exception e){fail(j,e);}
        } else {j.phase=Phase.VERIFY;say(s,"Confirmation accepted. Rechecking every original block before writing the backup and placing the dungeon.");}
        return 1;
    }

    int cancel(CommandSourceStack s) {
        Job j=job;if(j==null)return error(s,"No active job.");if(!j.owner.equals(owner(s)))return error(s,"This job belongs to another admin.");
        if(j.phase==Phase.PLACE||j.phase==Phase.RESTORE){return error(s,"World writes are in progress. Let them finish; then /dungeon undo "+j.instance+" restores the original terrain. If interrupted, the saved instance remains recoverable.");}
        release(j);job=null;say(s,"Preview cancelled; no blocks changed.");return 1;
    }

    int undo(CommandSourceStack s,String instance) {
        if(job!=null)return error(s,"Finish or cancel the current job first.");
        var e=store.entries.get(instance);if(e==null)return error(s,"Unknown dungeon instance. Use /dungeon list.");
        if(!e.dimension().equals(s.getLevel().dimension().location().toString()))return error(s,"Run undo in "+e.dimension());
        try {
            CompoundTag backup=DungeonStore.read(store.backup(instance));
            Job j=new Job();j.instance=instance;j.entry=e;j.owner=owner(s);j.source=s;j.level=s.getLevel();j.token=shortId();j.undo=true;j.phase=Phase.READY;j.expires=System.currentTimeMillis()+180_000;
            j.min=e.min();j.sx=e.sx();j.sy=e.sy();j.sz=e.sz();j.volume=Math.multiplyExact(Math.multiplyExact(j.sx,j.sy),j.sz);
            if(!Arrays.equals(backup.getIntArray("Size"),new int[]{j.sx,j.sy,j.sz})||!Arrays.equals(backup.getIntArray("Min"),DungeonStore.vec(j.min))||!backup.getString("Dimension").equals(e.dimension()))throw new IOException("Backup bounds/dimension do not match journal");
            j.palette=new ArrayList<>(DungeonTemplate.parsePalette(backup.getList("Palette",Tag.TAG_STRING)));j.before=DungeonTemplate.decodeRuns(backup.getIntArray("Runs"),j.volume,j.palette.size());
            job=j;say(s,"UNDO PREVIEW: this removes dungeon "+instance+" and restores its original blocks, including any later admin edits inside "+bounds(j)+". Move everyone outside first.");offer(j);draw(j);return 1;
        }catch(Exception ex){return error(s,"Backup cannot be restored: "+ex.getMessage()+". Existing dungeon protection remains active.");}
    }

    int entrance(CommandSourceStack s,String id) {
        var e=store.entries.get(id);if(e==null||!e.status().equals("PLACED"))return error(s,"No completed dungeon with that ID.");
        if(!s.getLevel().dimension().location().toString().equals(e.dimension()))return error(s,"Entrance is in "+e.dimension());
        BlockPos p=e.spawn();say(s,"Entrance: "+p.toShortString()+" in "+e.dimension()+". Teleport manually if desired; this command does not move you.");return 1;
    }

    public void tick() {
        ticks++;Job j=job;if(j==null)return;
        if(j.phase==Phase.READY){if(System.currentTimeMillis()>j.expires){say(j.source,"Dungeon preview expired; no blocks changed.");release(j);job=null;}else if(ticks%20==0)draw(j);return;}
        if(ticks%100==0)say(j.source,"Dungeon "+j.phase.toString().toLowerCase(Locale.ROOT)+": "+(100L*j.cursor/j.volume)+"%.");
        try {
            if((j.phase==Phase.PLACE||j.phase==Phase.RESTORE)&&occupied(j))throw new IOException("An entity entered the work area. Move it out before restoring/retrying");
            long deadline=System.nanoTime()+6_000_000L;
            for(int n=0;n<BATCH&&j.cursor<j.volume&&System.nanoTime()<deadline;n++) {
                BlockPos p=j.at(j.cursor);ChunkPos cp=new ChunkPos(p);
                if(j.tickets.add(cp)){j.level.getChunkSource().addRegionTicket(TICKET,cp,2,j.instance);j.level.getChunk(cp.x,cp.z);break;}
                switch(j.phase) {
                    case SCAN -> {
                        BlockState old=j.level.getBlockState(p);
                        if(j.level.getBlockEntity(p)!=null||!allowed(old,j.terrain))throw new IOException("Would overwrite "+DungeonTemplate.stateString(old)+" at "+p.toShortString()+". Nothing placed. "+(!j.terrain?"Use a clear area, or explicitly preview with terrain mode after inspecting the location.":"Containers and non-terrain blocks are always refused."));
                        j.before[j.cursor]=j.paletteMap.computeIfAbsent(old,state->{j.palette.add(state);return j.palette.size()-1;});
                    }
                    case VERIFY -> {
                        if(j.level.getBlockEntity(p)!=null||!j.level.getBlockState(p).equals(j.palette.get(j.before[j.cursor])))throw new IOException("World changed after preview at "+p.toShortString()+". Preview again; no blocks were placed.");
                    }
                    case PLACE -> set(j,p,j.template.palette.get(j.template.blocks[j.cursor]));
                    case RESTORE -> {j.level.removeBlockEntity(p);set(j,p,j.palette.get(j.before[j.cursor]));}
                    default -> throw new IllegalStateException("Unexpected phase");
                }
                j.cursor++;
            }
            if(j.cursor==j.volume)finishPhase(j);
        }catch(Exception e){fail(j,e);}
    }

    private boolean allowed(BlockState state,boolean terrain) {
        if(state.isAir())return true;
        if(!state.hasBlockEntity()&&state.canBeReplaced()&&state.getFluidState().isEmpty())return true;
        if(!terrain||state.hasBlockEntity())return false;
        ResourceLocation id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id.getNamespace().equals("minecraft")&&(TERRAIN.contains(id.getPath())||id.getPath().endsWith("_ore"));
    }
    private void set(Job j,BlockPos p,BlockState state)throws IOException {
        if(!j.level.getBlockState(p).equals(state)&&!DungeonModule.withEdits(()->j.level.setBlock(p,state,FLAGS)))throw new IOException("World rejected block at "+p.toShortString());
        if(j.phase==Phase.PLACE&&j.level.getBlockEntity(p) instanceof SpawnerBlockEntity spawner){
            CompoundTag sleeping=new CompoundTag();sleeping.putShort("Delay",(short)32767);sleeping.putShort("RequiredPlayerRange",(short)0);
            spawner.loadWithComponents(sleeping,j.level.registryAccess());spawner.setChanged();
        }
    }
    private boolean occupied(Job j) {return !j.level.getEntities((Entity)null,j.box().inflate(1),e->e.isAlive()&&!(e instanceof ItemEntity)).isEmpty();}

    private void finishPhase(Job j)throws Exception {
        switch(j.phase) {
            case SCAN -> {j.phase=Phase.READY;j.expires=System.currentTimeMillis()+180_000;say(j.source,"Scan passed. Bounds "+bounds(j)+". No blocks have been changed. Confirmation expires in 3 minutes; move outside the bounds first.");offer(j);draw(j);}
            case VERIFY -> {
                if(occupied(j))throw new IOException("An entity entered the area during verification. No blocks placed; preview again.");
                CompoundTag backup=new CompoundTag();backup.putIntArray("Size",new int[]{j.sx,j.sy,j.sz});backup.putIntArray("Min",DungeonStore.vec(j.min));backup.putString("Dimension",j.level.dimension().location().toString());
                ListTag pal=new ListTag();j.palette.forEach(state->pal.add(StringTag.valueOf(DungeonTemplate.stateString(state))));backup.put("Palette",pal);backup.putIntArray("Runs",DungeonTemplate.encodeRuns(j.before));
                DungeonStore.write(store.backup(j.instance),backup);
                j.entry=new DungeonStore.Entry(j.instance,j.template.id,j.level.dimension().location().toString(),j.min,j.sx,j.sy,j.sz,j.min.offset(j.template.spawn),"BUILDING");store.put(j.entry);j.journaled=true;
                j.phase=Phase.PLACE;j.cursor=0;say(j.source,"Original terrain backed up. Placing "+j.template.name+"; keep players outside until complete. Instance "+j.instance);
            }
            case PLACE -> {
                for(CompoundTag original:j.template.blockEntities){
                    CompoundTag data=original.copy();BlockPos p=j.min.offset(data.getInt("x"),data.getInt("y"),data.getInt("z"));data.putInt("x",p.getX());data.putInt("y",p.getY());data.putInt("z",p.getZ());
                    data.remove("LootrId");data.remove("lootrid");data.remove("Openers");data.remove("openers");
                    BlockEntity be=j.level.getBlockEntity(p);if(be==null)throw new IOException("Missing block entity after placement at "+p);
                    be.loadWithComponents(data,j.level.registryAccess());be.setChanged();j.level.sendBlockUpdated(p,j.level.getBlockState(p),j.level.getBlockState(p),Block.UPDATE_CLIENTS);
                }
                List<Flip.Zone> zones=new ArrayList<>();String prefix="dungeon/"+j.instance;
                for(var z:j.template.zones){BlockPos a=j.min.offset(z.min()),b=j.min.offset(z.max());zones.add(new Flip.Zone(prefix+"/"+z.name(),j.level.dimension(),new AABB(a.getX(),a.getY(),a.getZ(),b.getX()+1,b.getY()+1,b.getZ()+1),z.gravity()));}
                DungeonZoneBridge.replace(server,j.level.dimension(),prefix,zones);
                DungeonZoneBridge.flush(server);
                // Saving chunks before marking complete prevents the journal from claiming unflushed geometry is ready.
                j.level.getChunkSource().save(true);store.put(j.entry.status("PLACED"));
                say(j.source,"Dungeon complete: "+j.instance+". Entrance "+j.entry.spawn().toShortString()+". Gravity active, region protected, per-player Lootr ready. /dungeon undo "+j.instance+" previews a restoration.");release(j);job=null;
            }
            case RESTORE -> {
                DungeonZoneBridge.remove(server,j.level.dimension(),"dungeon/"+j.instance);DungeonZoneBridge.flush(server);j.level.getChunkSource().save(true);store.remove(j.instance);
                say(j.source,"Original terrain restored; dungeon gravity and protection removed. Backup retained at data/cu_dungeons/"+j.instance+".before.nbt.");release(j);job=null;
            }
            default -> {}
        }
    }
    private void offer(Job j) {
        String command="/dungeon confirm "+j.token;
        j.source.sendSuccess(()->Component.literal("[Review bounds, then click to prepare confirmation] "+command).withStyle(style->style.withColor(net.minecraft.ChatFormatting.AQUA).withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,command))),false);
    }
    private void draw(Job j) {
        AABB b=j.box();double[] xs={b.minX,b.maxX},ys={b.minY,b.maxY},zs={b.minZ,b.maxZ};
        for(double x:xs)for(double z:zs)for(int k=0;k<=24;k++)particle(j,x,b.minY+(b.maxY-b.minY)*k/24,z);
        for(double y:ys)for(double z:zs)for(int k=0;k<=16;k++)particle(j,b.minX+(b.maxX-b.minX)*k/16,y,z);
        for(double x:xs)for(double y:ys)for(int k=0;k<=16;k++)particle(j,x,y,b.minZ+(b.maxZ-b.minZ)*k/16);
    }
    private void particle(Job j,double x,double y,double z){ServerPlayer player=server.getPlayerList().getPlayer(j.owner);if(player!=null)j.level.sendParticles(player,ParticleTypes.END_ROD,true,x,y,z,1,0,0,0,0);}
    private void release(Job j){for(ChunkPos cp:j.tickets)j.level.getChunkSource().removeRegionTicket(TICKET,cp,2,j.instance);j.tickets.clear();}
    private void fail(Job j,Exception e) {
        UpsideDown.LOG.error("Dungeon operation failed (instance {})",j.instance,e);
        if(j.journaled||j.undo){
            try {store.put(j.entry.status("INTERRUPTED"));DungeonZoneBridge.remove(server,j.level.dimension(),"dungeon/"+j.instance);DungeonZoneBridge.flush(server);}catch(Exception nested){UpsideDown.LOG.error("Could not update interrupted dungeon journal",nested);}
            error(j.source,"Placement/restoration interrupted: "+e.getMessage()+". Backup and protection retained. /dungeon undo "+j.instance+" previews recovery.");
        }else error(j.source,e.getMessage()+" No dungeon blocks were placed.");
        release(j);job=null;
    }
    public boolean protectedAt(ServerLevel level,BlockPos p){return protectedBox(level,new AABB(p));}
    public boolean isWriting(ServerLevel level,BlockPos p){return job!=null&&job.locked&&job.level==level&&job.box().intersects(new AABB(p));}
    public boolean protectedBox(ServerLevel level,AABB box) {
        String dim=level.dimension().location().toString();
        if(job!=null&&job.locked&&job.level==level&&job.box().intersects(box))return true;
        for(var e:store.entries.values())if(e.dimension().equals(dim)&&e.box().intersects(box))return true;
        return false;
    }
}
