package dev.unyielding.upsidedown.dungeon;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.unyielding.upsidedown.DungeonZoneBridge;
import dev.unyielding.upsidedown.Flip;
import dev.unyielding.upsidedown.UpsideDown;
import dev.unyielding.upsidedown.dungeon.block.DungeonBlocks;
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
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Direct and previewed placement share region protection, authored block entities, gravity, and restoration. */
public final class DungeonService {
    private static final UUID CONSOLE=new UUID(0,0);
    private static final TicketType<String> TICKET=TicketType.create("cu_dungeon_work",Comparator.<String>naturalOrder());
    private static final int BATCH=8192, FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
    private static final Set<String> TERRAIN=Set.of("stone","deepslate","dirt","coarse_dirt","rooted_dirt","grass_block","podzol","mycelium","gravel","sand","red_sand","sandstone","red_sandstone","tuff","granite","diorite","andesite","calcite","bedrock","clay","mud","snow","snow_block","ice","packed_ice","blue_ice","netherrack","end_stone","dripstone_block");
    private final MinecraftServer server;
    private final DungeonStore store;
    private final DungeonProtection protection;
    private Job job;
    private int ticks;

    private enum Phase { SCAN, READY, VERIFY, PLACE, RESTORE }
    private static final class Job {
        String token,instance; UUID owner;CommandSourceStack source;ServerLevel level;
        DungeonTemplate template;DungeonStore.Entry entry;
        BlockPos min;int sx,sy,sz,volume,cursor;boolean terrain,undo,locked,journaled;
        Phase phase;long expires;int[] before;
        DungeonPasteBackup directBackup;
        BitSet mask;
        DungeonPasteBackup.Capture capture;
        DungeonPasteBackup.Part restorePart;
        List<BlockState> palette=new ArrayList<>();Map<BlockState,Integer> paletteMap=new HashMap<>();
        Set<ChunkPos> tickets=new HashSet<>();
        AABB box(){return new AABB(min.getX(),min.getY(),min.getZ(),min.getX()+sx,min.getY()+sy,min.getZ()+sz);}
        BlockPos at(int i){return min.offset(i%sx,i/(sx*sz),(i/sx)%sz);}
        int workLimit(){return undo&&directBackup!=null?directBackup.covered():volume;}
    }

    public DungeonService(MinecraftServer server)throws IOException {this.server=server;store=new DungeonStore(server);protection=new DungeonProtection(store);}
    /** Remove crash-left gravity only for unfinished journal entries; retain their protection and backups. */
    void reconcileInterruptedZones() throws IOException {
        int removed=0;
        for(var entry:store.entries.values()) {
            if("PLACED".equals(entry.status()))continue;
            var dimension=ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(entry.dimension()));
            String prefix="dungeon/"+entry.id();
            if(!DungeonZoneBridge.snapshot(server,dimension,prefix).isEmpty())
                removed+=DungeonZoneBridge.remove(server,dimension,prefix);
        }
        if(removed>0) {
            DungeonZoneBridge.flush(server);
            UpsideDown.LOG.warn("Removed {} stale gravity zones from unfinished dungeon instances; backups and protection retained",removed);
        }
    }
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
          .then(Commands.literal("repair").then(Commands.argument("instance",StringArgumentType.word()).executes(c->get(c.getSource()).repair(c.getSource(),StringArgumentType.getString(c,"instance")))))
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

    int help(CommandSourceStack s){say(s,"Dungeon commands: list; preview <template> <x y z> [terrain]; confirm <token>; status; cancel; undo <instance>; repair <maze-instance>; entrance <instance>; verifyloot. Preview places NO blocks. Default accepts empty space/vegetation only. Terrain mode explicitly permits natural stone/dirt/bedrock, never containers or arbitrary blocks.");return 1;}
    int list(CommandSourceStack s){say(s,"Templates: sakura_labyrinth — 100 themed gravity chambers; mirror_pagoda — upright and inverted Japanese Mimic towers. No natural spawning. Existing instances:");store.entries.values().forEach(e->say(s,e.id()+" "+e.template()+" "+e.dimension()+" "+e.status()+" "+e.min().toShortString()));return 1;}
    int status(CommandSourceStack s){if(job==null)say(s,"No placement job. /dungeon list shows saved instances, including interrupted ones that can be restored with undo.");else say(s,job.phase+" "+job.instance+" "+(100L*job.cursor/Math.max(1,job.workLimit()))+"%; bounds "+bounds(job)+(job.phase==Phase.READY?"; preview token "+job.token:""));return 1;}
    private String bounds(Job j){return j.min.toShortString()+" through "+j.min.offset(j.sx-1,j.sy-1,j.sz-1).toShortString();}

    private Job placement(CommandSourceStack s,String id,BlockPos anchor) throws Exception {
        List<String> problems=DungeonLootChecks.problems(s.getLevel());
        if(!problems.isEmpty())throw new IOException("Dungeon dependencies/loot not ready: "+String.join("; ",problems));
        DungeonTemplate t=DungeonTemplate.load(id);
        Job j=new Job();
        j.template=t;j.mask=(BitSet)t.placementMask.clone();j.source=s;j.owner=owner(s);j.level=s.getLevel();j.instance=shortId();
        j.min=anchor.offset(t.offset);j.sx=t.sx;j.sy=t.sy;j.sz=t.sz;j.volume=t.volume;
        if(j.min.getY()<j.level.getMinBuildHeight()||(long)j.min.getY()+j.sy>j.level.getMaxBuildHeight())throw new IOException("Does not fit this dimension: dungeon Y "+j.min.getY()+".."+(j.min.getY()+j.sy-1)+", world Y "+j.level.getMinBuildHeight()+".."+(j.level.getMaxBuildHeight()-1)+". The twin pagoda needs expanded height; no blocks changed.");
        if(!j.level.getWorldBorder().isWithinBounds(j.min)||!j.level.getWorldBorder().isWithinBounds(j.min.offset(j.sx-1,0,j.sz-1)))throw new IOException("Dungeon would cross the world border.");
        for(var e:store.entries.values())if(e.dimension().equals(j.level.dimension().location().toString())&&e.box().intersects(j.box()))throw new IOException("Overlaps saved dungeon "+e.id()+". Choose another location.");
        for(var z:Flip.SERVER)if(z.dim().equals(j.level.dimension())&&z.box().intersects(j.box()))throw new IOException("Overlaps existing gravity zone "+z.name()+". Choose another location or review that zone first.");
        return j;
    }

    public static int placeDirect(CommandSourceStack source,String templateId,BlockPos anchor) {
        return get(source).startDirect(source,templateId,anchor);
    }

    private int startDirect(CommandSourceStack source,String templateId,BlockPos anchor) {
        if(job!=null)return error(source,"Finish or cancel the current job first. /dungeon status");
        try {
            Job j=placement(source,templateId,anchor);
            j.entry=buildingEntry(j,true);
            j.directBackup=DungeonPasteBackup.create(store,j.entry,j.mask);
            // The protection journal and an empty, restorable manifest exist before the first block edit.
            store.put(j.entry);
            j.journaled=true;
            j.locked=true;
            j.phase=Phase.PLACE;
            job=j;
            say(source,"Direct placement started: "+j.instance+". Replacing authored dungeon blocks and interior spaces inside "+bounds(j)+". /dungeon status tracks progress.");
            return 1;
        } catch(Exception ex) {
            return error(source,"Cannot place dungeon: "+ex.getMessage());
        }
    }

    private DungeonStore.Entry buildingEntry(Job j,boolean direct) throws IOException {
        DungeonStore.Entry entry=new DungeonStore.Entry(j.instance,j.template.id,j.level.dimension().location().toString(),j.min,j.sx,j.sy,j.sz,j.min.offset(j.template.spawn),"BUILDING",direct);
        protection.save(entry,j.template);
        return entry;
    }

    int preview(CommandSourceStack s,String id,BlockPos anchor,boolean terrain) {
        if(job!=null)return error(s,"Finish or cancel the current preview/job first. /dungeon status");
        try {
            Job j=placement(s,id,anchor);
            j.token=shortId();j.terrain=terrain;j.phase=Phase.SCAN;j.before=new int[j.volume];
            job=j;say(s,"PREVIEW ONLY: "+j.template.name+". Bounds "+bounds(j)+" ("+j.volume+" cells). Scanning before offering confirmation. "+(terrain?"TERRAIN mode may replace player-built natural stone too; inspect the bounds carefully.":"Empty-space mode: terrain and existing builds are refused."));draw(j);return 1;
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
            Job j=new Job();j.instance=instance;j.entry=e;j.owner=owner(s);j.source=s;j.level=s.getLevel();j.token=shortId();j.undo=true;j.phase=Phase.READY;j.expires=System.currentTimeMillis()+180_000;
            j.min=e.min();j.sx=e.sx();j.sy=e.sy();j.sz=e.sz();j.volume=Math.multiplyExact(Math.multiplyExact(j.sx,j.sy),j.sz);
            if(e.direct()) {
                j.directBackup=DungeonPasteBackup.open(store,e);
                j.mask=j.directBackup.mask();
            } else {
                CompoundTag backup=DungeonStore.read(store.backup(instance));
                if(!Arrays.equals(backup.getIntArray("Size"),new int[]{j.sx,j.sy,j.sz})||!Arrays.equals(backup.getIntArray("Min"),DungeonStore.vec(j.min))||!backup.getString("Dimension").equals(e.dimension()))throw new IOException("Backup bounds/dimension do not match journal");
                j.mask=backup.contains("PlacementMask",Tag.TAG_LONG_ARRAY)?BitSet.valueOf(backup.getLongArray("PlacementMask")):new BitSet(j.volume);
                if(!backup.contains("PlacementMask",Tag.TAG_LONG_ARRAY))j.mask.set(0,j.volume);
                if(j.mask.length()>j.volume)throw new IOException("Invalid backup placement mask");
                j.palette=new ArrayList<>(DungeonTemplate.parsePalette(backup.getList("Palette",Tag.TAG_STRING)));j.before=DungeonTemplate.decodeRuns(backup.getIntArray("Runs"),j.volume,j.palette.size());
            }
            job=j;say(s,"UNDO PREVIEW: this removes dungeon "+instance+" and restores its original blocks, including any later admin edits inside "+bounds(j)+". Move everyone outside first.");offer(j);draw(j);return 1;
        }catch(Exception ex){return error(s,"Backup cannot be restored: "+ex.getMessage()+". Existing dungeon protection remains active.");}
    }

    int entrance(CommandSourceStack s,String id) {
        var e=store.entries.get(id);if(e==null||!e.status().equals("PLACED"))return error(s,"No completed dungeon with that ID.");
        if(!s.getLevel().dimension().location().toString().equals(e.dimension()))return error(s,"Entrance is in "+e.dimension());
        BlockPos p=e.spawn();say(s,"Entrance: "+p.toShortString()+" in "+e.dimension()+". Teleport manually if desired; this command does not move you.");return 1;
    }

    /** Repairs only the missing south floor strip; it never replaces existing blocks or loot. */
    int repair(CommandSourceStack s,String id) {
        if(job!=null)return error(s,"Finish the active dungeon job before repairing.");
        var e=store.entries.get(id);
        if(e==null||!e.status().equals("PLACED")||!e.template().equals("sakura_labyrinth"))return error(s,"Choose a completed Sakura Labyrinth instance from /dungeon list.");
        if(!e.dimension().equals(s.getLevel().dimension().location().toString()))return error(s,"Run repair in "+e.dimension()+".");
        if(e.sx()!=205||e.sy()!=36||e.sz()!=222)return error(s,"This maze has unfamiliar dimensions; no blocks were changed.");
        try {
            ServerLevel level=s.getLevel();BlockState floor=DungeonBlocks.BLOSSOM_FLOOR.get().defaultBlockState();
            BitSet cells=new BitSet(e.sx()*e.sy()*e.sz());List<BlockPos> fill=new ArrayList<>();
            for(int z=219;z<=220;z++)for(int x=2;x<=202;x++) {
                int index=(5*e.sz()+z)*e.sx()+x;cells.set(index);
                BlockPos pos=e.min().offset(x,5,z);BlockState current=level.getBlockState(pos);
                if(!current.isAir()&&!current.equals(floor))throw new IOException("Floor strip contains "+DungeonTemplate.stateString(current)+" at "+pos.toShortString()+"; no blocks changed.");
                if(current.isAir())fill.add(pos);
            }
            if(e.direct()) DungeonPasteBackup.open(store,e).include(cells);
            else {
                CompoundTag backup=DungeonStore.read(store.backup(id));
                if(!Arrays.equals(backup.getIntArray("Size"),new int[]{e.sx(),e.sy(),e.sz()})||!Arrays.equals(backup.getIntArray("Min"),DungeonStore.vec(e.min()))||!backup.getString("Dimension").equals(e.dimension()))throw new IOException("Backup does not match this maze.");
                if(backup.contains("PlacementMask",Tag.TAG_LONG_ARRAY)) {
                    BitSet saved=BitSet.valueOf(backup.getLongArray("PlacementMask"));BitSet missing=(BitSet)cells.clone();missing.andNot(saved);
                    if(!missing.isEmpty())throw new IOException("This older preview backup does not include the strip; use a newly placed maze or restore from your world backup.");
                }
            }
            // Persist undo coverage and exact protection before touching the world.
            protection.include(e,cells);
            for(BlockPos pos:fill)if(!DungeonModule.withEdits(()->level.setBlock(pos,floor,FLAGS)))throw new IOException("World rejected floor at "+pos.toShortString()+"; some earlier cells may have been repaired.");
            say(s,"Maze edge repaired: "+fill.size()+" missing floor blocks filled and protected. Existing blocks were preserved.");return 1;
        } catch(Exception ex) {return error(s,"Cannot complete maze repair: "+ex.getMessage());}
    }

    public void tick() {
        ticks++;Job j=job;if(j==null)return;
        if(j.phase==Phase.READY){if(System.currentTimeMillis()>j.expires){say(j.source,"Dungeon preview expired; no blocks changed.");release(j);job=null;}else if(ticks%20==0)draw(j);return;}
        int limit=j.workLimit();
        if(ticks%100==0)say(j.source,"Dungeon "+j.phase.toString().toLowerCase(Locale.ROOT)+": "+(100L*j.cursor/Math.max(1,limit))+"%.");
        try {
            if((j.phase==Phase.RESTORE||(j.phase==Phase.PLACE&&j.directBackup==null))&&occupied(j))throw new IOException("An entity entered the work area. Move it out before restoring/retrying");
            long deadline=System.nanoTime()+6_000_000L;
            for(int n=0;n<BATCH&&j.cursor<limit&&System.nanoTime()<deadline;n++) {
                // On direct placement only the next band is backed up, immediately before writing it.
                boolean capturing=j.phase==Phase.PLACE&&j.directBackup!=null&&j.cursor==j.directBackup.covered();
                if(capturing&&j.capture==null)j.capture=j.directBackup.new Capture();
                BlockPos p=j.at(capturing?j.cursor+j.capture.count:j.cursor);ChunkPos cp=new ChunkPos(p);
                if(j.tickets.add(cp)){j.level.getChunkSource().addRegionTicket(TICKET,cp,2,j.instance);j.level.getChunk(cp.x,cp.z);break;}
                if(capturing) {
                    j.capture.add(j.level,p);
                    if(j.capture.full()){j.capture.commit();j.capture=null;}
                    continue;
                }
                if(j.phase==Phase.RESTORE&&j.directBackup!=null&&(j.restorePart==null||j.cursor==j.restorePart.end()))j.restorePart=j.directBackup.readPart(j.cursor);
                if(!j.mask.get(j.cursor)) { j.cursor++; continue; }
                switch(j.phase) {
                    case SCAN -> {
                        BlockState old=j.level.getBlockState(p);
                        if(j.level.getBlockEntity(p)!=null||!allowed(old,j.terrain))throw new IOException("Would overwrite "+DungeonTemplate.stateString(old)+" at "+p.toShortString()+". Nothing placed. "+(!j.terrain?"Use a clear area, or explicitly preview with terrain mode after inspecting the location.":"Containers and non-terrain blocks are always refused."));
                        j.before[j.cursor]=j.paletteMap.computeIfAbsent(old,state->{j.palette.add(state);return j.palette.size()-1;});
                    }
                    case VERIFY -> {
                        if(j.level.getBlockEntity(p)!=null||!j.level.getBlockState(p).equals(j.palette.get(j.before[j.cursor])))throw new IOException("World changed after preview at "+p.toShortString()+". Preview again; no blocks were placed.");
                    }
                    case PLACE -> {
                        if(j.directBackup!=null)removeOriginalBlockEntity(j,p);
                        set(j,p,j.template.palette.get(j.template.blocks[j.cursor]));
                    }
                    case RESTORE -> restoreBlock(j,p);
                    default -> throw new IllegalStateException("Unexpected phase");
                }
                j.cursor++;
            }
            if(j.cursor==limit)finishPhase(j);
        }catch(Exception e){fail(j,e);}
    }

    private boolean allowed(BlockState state,boolean terrain) {
        if(state.isAir())return true;
        if(!state.hasBlockEntity()&&state.canBeReplaced()&&state.getFluidState().isEmpty())return true;
        if(!terrain||state.hasBlockEntity())return false;
        ResourceLocation id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id.getNamespace().equals("minecraft")&&(TERRAIN.contains(id.getPath())||id.getPath().endsWith("_ore"));
    }
    private void removeOriginalBlockEntity(Job j,BlockPos p)throws IOException {
        if(!j.level.getBlockState(p).hasBlockEntity())return;
        // Vanilla structure placement clears the inventory and inserts a temporary barrier. Also detach
        // the old entity so removal callbacks cannot drop contents or reuse a previous Lootr identity.
        Clearable.tryClear(j.level.getBlockEntity(p));
        j.level.removeBlockEntity(p);
        writeState(j,p,Blocks.BARRIER.defaultBlockState());
    }

    private void restoreBlock(Job j,BlockPos p)throws Exception {
        if(j.directBackup==null) {
            removeOriginalBlockEntity(j,p);
            set(j,p,j.palette.get(j.before[j.cursor]));
            return;
        }
        if(j.restorePart==null||j.cursor==j.restorePart.end())j.restorePart=j.directBackup.readPart(j.cursor);
        CompoundTag original=j.restorePart.entities().get(p);
        removeOriginalBlockEntity(j,p);
        set(j,p,j.restorePart.state(j.cursor));
        if(original!=null)loadBlockEntity(j,p,original);
    }

    private void loadBlockEntity(Job j,BlockPos p,CompoundTag data)throws IOException {
        BlockEntity be=j.level.getBlockEntity(p);
        if(be==null)throw new IOException("Missing block entity after placement at "+p);
        be.loadWithComponents(data,j.level.registryAccess());
        be.setChanged();
        BlockState state=j.level.getBlockState(p);
        j.level.sendBlockUpdated(p,state,state,Block.UPDATE_CLIENTS);
    }

    private void writeState(Job j,BlockPos p,BlockState state)throws IOException {
        if(!j.level.getBlockState(p).equals(state)&&!DungeonModule.withEdits(()->j.level.setBlock(p,state,FLAGS)))throw new IOException("World rejected block at "+p.toShortString());
    }

    private void set(Job j,BlockPos p,BlockState state)throws IOException {
        writeState(j,p,state);
        if(j.phase==Phase.PLACE&&state.is(Blocks.SPAWNER)&&j.level.getBlockEntity(p) instanceof SpawnerBlockEntity spawner){
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
                ListTag pal=new ListTag();j.palette.forEach(state->pal.add(StringTag.valueOf(DungeonTemplate.stateString(state))));backup.put("Palette",pal);backup.putIntArray("Runs",DungeonTemplate.encodeRuns(j.before));backup.putLongArray("PlacementMask",j.mask.toLongArray());
                DungeonStore.write(store.backup(j.instance),backup);
                j.entry=buildingEntry(j,false);store.put(j.entry);j.journaled=true;
                j.phase=Phase.PLACE;j.cursor=0;say(j.source,"Original terrain backed up. Placing "+j.template.name+"; keep players outside until complete. Instance "+j.instance);
            }
            case PLACE -> {
                for(CompoundTag original:j.template.blockEntities){
                    CompoundTag data=original.copy();BlockPos p=j.min.offset(data.getInt("x"),data.getInt("y"),data.getInt("z"));data.putInt("x",p.getX());data.putInt("y",p.getY());data.putInt("z",p.getZ());
                    data.remove("LootrId");data.remove("lootrid");data.remove("Openers");data.remove("openers");
                    loadBlockEntity(j,p,data);
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
    public boolean protectedAt(ServerLevel level,BlockPos p){
        if(isWriting(level,p))return true;
        String dim=level.dimension().location().toString();
        for(var e:store.entries.values())if(e.dimension().equals(dim)&&e.box().intersects(new AABB(p))&&protection.contains(e,p))return true;
        return false;
    }
    public boolean isWriting(ServerLevel level,BlockPos p){
        Job j=job;
        if(j==null||!j.locked||j.level!=level||j.mask==null)return false;
        int x=p.getX()-j.min.getX(),y=p.getY()-j.min.getY(),z=p.getZ()-j.min.getZ();
        return x>=0&&x<j.sx&&y>=0&&y<j.sy&&z>=0&&z<j.sz&&j.mask.get((y*j.sz+z)*j.sx+x);
    }
    public boolean protectedBox(ServerLevel level,AABB box) {
        String dim=level.dimension().location().toString();
        Job j=job;
        if(j!=null&&j.locked&&j.level==level&&j.mask!=null&&j.box().intersects(box)) {
            int x0=Math.max(0,(int)Math.floor(box.minX)-j.min.getX()),x1=Math.min(j.sx-1,(int)Math.ceil(box.maxX)-1-j.min.getX());
            int y0=Math.max(0,(int)Math.floor(box.minY)-j.min.getY()),y1=Math.min(j.sy-1,(int)Math.ceil(box.maxY)-1-j.min.getY());
            int z0=Math.max(0,(int)Math.floor(box.minZ)-j.min.getZ()),z1=Math.min(j.sz-1,(int)Math.ceil(box.maxZ)-1-j.min.getZ());
            for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++) {
                int start=(y*j.sz+z)*j.sx+x0,found=j.mask.nextSetBit(start);
                if(found>=start&&found<=start+x1-x0)return true;
            }
        }
        for(var e:store.entries.values())if(e.dimension().equals(dim)&&e.box().intersects(box)&&protection.intersects(e,box))return true;
        return false;
    }
}
