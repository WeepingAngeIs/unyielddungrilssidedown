package dev.unyielding.upsidedown.dungeon;

import dev.unyielding.upsidedown.UpsideDown;
import dev.unyielding.upsidedown.dungeon.block.DungeonBlocks;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Protection applies to native pack blocks and Lootr too; their own container logic remains intact. */
public final class DungeonModule {
    private static final Map<MinecraftServer,DungeonService> SERVICES=new WeakHashMap<>();
    private static final ThreadLocal<Integer> EDIT_DEPTH=ThreadLocal.withInitial(()->0);
    private DungeonModule(){}
    public static boolean withEdits(BooleanSupplier edit){
        EDIT_DEPTH.set(EDIT_DEPTH.get()+1);
        try{return edit.getAsBoolean();}finally{EDIT_DEPTH.set(EDIT_DEPTH.get()-1);}
    }
    /** Common mutation guard also catches fire, flowing fluids and mod edits that bypass player events. */
    public static boolean denyChange(Level level,BlockPos pos,BlockState replacement){
        if(EDIT_DEPTH.get()>0||!(level instanceof ServerLevel serverLevel))return false;
        DungeonService service=SERVICES.get(serverLevel.getServer());
        if(service==null||!service.protectedAt(serverLevel,pos))return false;
        return replacement==null||level.getBlockState(pos).getBlock()!=replacement.getBlock();
    }
    public static DungeonService service(MinecraftServer server){
        DungeonService found=SERVICES.get(server);
        if(found==null)throw new IllegalStateException("Dungeon journal unavailable; check server log before placing anything");
        return found;
    }
    /** Called after persisted gravity is loaded, before the server accepts normal play. */
    public static void afterZonesLoaded(MinecraftServer server){
        try{service(server).reconcileInterruptedZones();}
        catch(Exception failure){throw new IllegalStateException("Cannot reconcile unfinished dungeon gravity; refusing unsafe server startup",failure);}
    }
    public static void init(IEventBus modBus) {
        DungeonBlocks.init(modBus);
        CommandDungeonStructure.register(modBus);
        IEventBus bus=NeoForge.EVENT_BUS;
        bus.addListener(ServerAboutToStartEvent.class,e->{
            try{SERVICES.put(e.getServer(),new DungeonService(e.getServer()));}
            catch(Exception failure){throw new IllegalStateException("Cannot load dungeon protection journal; refusing unsafe server startup",failure);}
        });
        bus.addListener(ServerStoppedEvent.class,e->{DungeonService s=SERVICES.remove(e.getServer());if(s!=null)s.stop();});
        bus.addListener(ServerTickEvent.Post.class,e->{DungeonService s=SERVICES.get(e.getServer());if(s!=null)s.tick();});
        bus.addListener(RegisterCommandsEvent.class,e->DungeonService.register(e.getDispatcher()));
        bus.addListener(BlockEvent.BreakEvent.class,e->{if(e.getLevel() instanceof ServerLevel l&&service(l.getServer()).protectedAt(l,e.getPos()))e.setCanceled(true);});
        bus.addListener(BlockEvent.EntityPlaceEvent.class,e->{if(e.getLevel() instanceof ServerLevel l&&service(l.getServer()).protectedAt(l,e.getPos()))e.setCanceled(true);});
        bus.addListener(BlockEvent.EntityMultiPlaceEvent.class,e->{if(e.getLevel() instanceof ServerLevel l&&e.getReplacedBlockSnapshots().stream().anyMatch(b->service(l.getServer()).protectedAt(l,b.getPos())))e.setCanceled(true);});
        bus.addListener(BlockEvent.FluidPlaceBlockEvent.class,e->{if(e.getLevel() instanceof ServerLevel l&&service(l.getServer()).protectedAt(l,e.getPos()))e.setCanceled(true);});
        bus.addListener(BlockEvent.FarmlandTrampleEvent.class,e->{if(e.getLevel() instanceof ServerLevel l&&service(l.getServer()).protectedAt(l,e.getPos()))e.setCanceled(true);});
        bus.addListener(BlockEvent.BlockToolModificationEvent.class,e->{if(e.getLevel() instanceof ServerLevel l&&service(l.getServer()).protectedAt(l,e.getPos()))e.setCanceled(true);});
        bus.addListener(PistonEvent.Pre.class,e->{
            if(e.getLevel() instanceof ServerLevel l&&service(l.getServer()).protectedBox(l,new AABB(e.getPos()).inflate(13)))e.setCanceled(true);
        });
        bus.addListener(ExplosionEvent.Detonate.class,e->{if(e.getLevel() instanceof ServerLevel l)e.getAffectedBlocks().removeIf(p->service(l.getServer()).protectedAt(l,p));});
        bus.addListener(EntityMobGriefingEvent.class,e->{if(e.getEntity().level() instanceof ServerLevel l&&service(l.getServer()).protectedBox(l,e.getEntity().getBoundingBox().inflate(4)))e.setCanGrief(false);});
        bus.addListener(LivingDestroyBlockEvent.class,e->{if(e.getEntity().level() instanceof ServerLevel l&&service(l.getServer()).protectedAt(l,e.getPos()))e.setCanceled(true);});
        bus.addListener(PlayerInteractEvent.RightClickBlock.class,e->{
            if(!(e.getLevel() instanceof ServerLevel l))return;
            DungeonService service=service(l.getServer());
            boolean within=service.protectedAt(l,e.getPos())||(e.getFace()!=null&&service.protectedAt(l,e.getPos().relative(e.getFace())));
            if(!within)return;
            String item=BuiltInRegistries.ITEM.getKey(e.getItemStack().getItem()).getPath();
            // Native Lootr opening is deliberately not cancelled. Placement/tool/block-break hooks handle other edits.
            if(service.isWriting(l,e.getPos())||item.endsWith("bucket")||item.equals("flint_and_steel")||item.equals("fire_charge")||item.equals("bone_meal")){
                e.setCancellationResult(InteractionResult.FAIL);e.setCanceled(true);
            }
        });
        UpsideDown.LOG.info("Command-only dungeons enabled; no structures are added to natural world generation");
    }
}
