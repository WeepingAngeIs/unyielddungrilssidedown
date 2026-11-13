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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
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
            if(!(e.getLevel() instanceof ServerLevel l))return;
            DungeonService service=service(l.getServer());
            var helper=e.getStructureHelper();
            if(service.protectedAt(l,e.getPos())||service.protectedAt(l,e.getFaceOffsetPos())){e.setCanceled(true);return;}
            if(helper!=null&&helper.resolve()) {
                var move=e.getPistonMoveType().isExtend?e.getDirection():e.getDirection().getOpposite();
                if(helper.getToPush().stream().anyMatch(p->service.protectedAt(l,p)||service.protectedAt(l,p.relative(move)))
                        ||helper.getToDestroy().stream().anyMatch(p->service.protectedAt(l,p))) e.setCanceled(true);
            }
        });
        bus.addListener(ExplosionEvent.Detonate.class,e->{if(e.getLevel() instanceof ServerLevel l)e.getAffectedBlocks().removeIf(p->service(l.getServer()).protectedAt(l,p));});
        bus.addListener(LivingDestroyBlockEvent.class,e->{if(e.getEntity().level() instanceof ServerLevel l&&service(l.getServer()).protectedAt(l,e.getPos()))e.setCanceled(true);});
        bus.addListener(PlayerInteractEvent.RightClickBlock.class,e->{
            if(!(e.getLevel() instanceof ServerLevel l))return;
            DungeonService service=service(l.getServer());
            BlockPos clicked=e.getPos(),target=e.getFace()==null?clicked:clicked.relative(e.getFace());
            boolean clickedProtected=service.protectedAt(l,clicked),targetProtected=service.protectedAt(l,target);
            String item=BuiltInRegistries.ITEM.getKey(e.getItemStack().getItem()).getPath();
            // A protected block can support a new block or fluid on its exterior face.
            // The affected destination, not merely the clicked support, determines protection.
            boolean addsBeside=e.getItemStack().getItem() instanceof BlockItem
                    ||(item.endsWith("bucket")&&!item.equals("bucket"))||item.equals("flint_and_steel")||item.equals("fire_charge");
            boolean writing=service.isWriting(l,target)||(service.isWriting(l,clicked)&&!addsBeside);
            boolean targeting=item.equals("bucket")?clickedProtected:
                    (item.endsWith("bucket")||item.equals("flint_and_steel")||item.equals("fire_charge"))&&targetProtected;
            if(writing||targeting||(item.equals("bone_meal")&&clickedProtected)){
                e.setCancellationResult(InteractionResult.FAIL);e.setCanceled(true);
            }
        });
        UpsideDown.LOG.info("Command-only dungeons enabled; no structures are added to natural world generation");
    }
}
