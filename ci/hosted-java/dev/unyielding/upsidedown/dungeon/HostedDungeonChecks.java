package dev.unyielding.upsidedown.dungeon;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.nio.file.Files;
import java.util.BitSet;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Disposable GitHub-only fake-player checks. Not included in the release jar. */
@EventBusSubscriber(modid="cuupsidedown")
public final class HostedDungeonChecks {
    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cu_ci_protection").requires(s->s.hasPermission(4))
            .then(Commands.argument("template",StringArgumentType.word())
                .then(Commands.argument("anchor",BlockPosArgument.blockPos()).executes(ctx->{
                    String id=StringArgumentType.getString(ctx,"template");
                    try {
                        check(ctx.getSource().getLevel(),id,BlockPosArgument.getBlockPos(ctx,"anchor"));
                        ctx.getSource().sendSuccess(()->Component.literal("CI exterior and tree checks passed: "+id),false);
                        return 1;
                    } catch(Exception ex) { throw new IllegalStateException("CI exterior test failed for "+id,ex); }
                }))));
        event.getDispatcher().register(Commands.literal("cu_ci_gap").requires(s->s.hasPermission(4))
            .then(Commands.argument("mode",StringArgumentType.word())
                .then(Commands.argument("anchor",BlockPosArgument.blockPos()).executes(ctx->{
                    String mode=StringArgumentType.getString(ctx,"mode");
                    try {
                        gap(ctx.getSource().getLevel(),BlockPosArgument.getBlockPos(ctx,"anchor"),mode);
                        ctx.getSource().sendSuccess(()->Component.literal("CI maze gap fixture: "+mode),false);return 1;
                    } catch(Exception ex) { throw new IllegalStateException("Could not prepare CI maze gap",ex); }
                }))));
    }
    private static void require(boolean ok,String message) {
        if(!ok)throw new IllegalStateException(message);
    }
    private static void check(ServerLevel level,String id,BlockPos anchor)throws Exception {
        DungeonTemplate t=DungeonTemplate.load(id);
        BlockPos min=anchor.offset(t.offset);
        boolean maze=id.equals("sakura_labyrinth");
        BlockPos wall=min.offset(maze?2:21,maze?9:327,maze?50:48);
        BlockPos outside=wall.west(),above=outside.above();
        BlockPos tree=min.offset(maze?6:10,maze?6:324,maze?10:12);
        DungeonService service=DungeonModule.service(level.getServer());
        require(service.protectedAt(level,wall),"structural wall must be protected");
        require(!service.protectedAt(level,outside)&&!service.protectedAt(level,above),"adjacent exterior air must be free");
        require(level.getBlockState(outside).isAir()&&level.getBlockState(above).isAir(),"expected open exterior air");
        require(level.getBlockState(tree).is(Blocks.CHERRY_LOG)&&!service.protectedAt(level,tree),"tree trunk must be removable");

        FakePlayer player=FakePlayerFactory.getMinecraft(level);
        player.setPos(outside.getX()-2.5,outside.getY(),outside.getZ()+0.5);
        ItemStack stone=new ItemStack(Items.STONE,4);
        player.setItemInHand(InteractionHand.MAIN_HAND,stone);
        BlockHitResult hit=new BlockHitResult(Vec3.atCenterOf(wall),Direction.WEST,wall,false);
        try {
            player.gameMode.useItemOn(player,level,stone,InteractionHand.MAIN_HAND,hit);
            require(level.getBlockState(outside).is(Blocks.STONE),"fake player could not place against outside wall");
            BlockHitResult bridgeHit=new BlockHitResult(Vec3.atCenterOf(outside),Direction.UP,outside,false);
            player.gameMode.useItemOn(player,level,stone,InteractionHand.MAIN_HAND,bridgeHit);
            require(level.getBlockState(above).is(Blocks.STONE),"fake player could not extend the exterior bridge");
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WATER_BUCKET));
            PlayerInteractEvent.RightClickBlock bucket=new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,wall,hit);
            NeoForge.EVENT_BUS.post(bucket);
            require(!bucket.isCanceled(),"bucket on the outward face was incorrectly blocked");
            BlockState original=level.getBlockState(wall);
            BlockEvent.BreakEvent wallBreak=new BlockEvent.BreakEvent(level,wall,original,player);
            NeoForge.EVENT_BUS.post(wallBreak);
            require(wallBreak.isCanceled(),"protected wall may be broken");
            BlockState treeState=level.getBlockState(tree);
            BlockEvent.BreakEvent treeBreak=new BlockEvent.BreakEvent(level,tree,treeState,player);
            NeoForge.EVENT_BUS.post(treeBreak);
            require(!treeBreak.isCanceled() && level.setBlock(tree,Blocks.AIR.defaultBlockState(),3),"tree cannot be removed");
            require(level.setBlock(tree,treeState,3),"tree fixture could not be restored");
            checkOldMasks(level,id,min,t,wall,tree);
        } finally {
            level.setBlock(above,Blocks.AIR.defaultBlockState(),3);
            level.setBlock(outside,Blocks.AIR.defaultBlockState(),3);
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        }
    }
    private static void checkOldMasks(ServerLevel level,String id,BlockPos min,DungeonTemplate t,BlockPos wall,BlockPos tree)throws Exception {
        DungeonStore store=new DungeonStore(level.getServer());
        String fixture="c1c1c1c1c1c1";
        DungeonStore.Entry e=new DungeonStore.Entry(fixture,id,level.dimension().location().toString(),min,t.sx,t.sy,t.sz,min.offset(t.spawn),"PLACED",true);
        var path=store.directory.resolve(fixture+".protection.nbt");
        require(!Files.exists(path),"test fixture already exists");
        try {
            DungeonProtection beta3=new DungeonProtection(store);
            require(beta3.contains(e,wall)&&!beta3.contains(e,tree),"beta.3 migration did not free old tree or protect wall");
            BitSet old=new BitSet(t.volume);
            old.set(t.index(wall.subtract(min)));old.set(t.index(tree.subtract(min)));
            CompoundTag saved=new CompoundTag();saved.putInt("Version",1);
            saved.putIntArray("Size",new int[]{t.sx,t.sy,t.sz});saved.putLongArray("Blocks",old.toLongArray());
            DungeonStore.write(path,saved);
            DungeonProtection beta4=new DungeonProtection(store);
            require(beta4.contains(e,wall)&&!beta4.contains(e,tree),"beta.4 migration did not free old tree or protect wall");
        } finally { Files.deleteIfExists(path); }
    }
    private static void gap(ServerLevel level,BlockPos anchor,String mode)throws Exception {
        DungeonTemplate t=DungeonTemplate.load("sakura_labyrinth");BlockPos min=anchor.offset(t.offset);
        if(mode.equals("prepare")) {
            DungeonStore store=new DungeonStore(level.getServer());
            var e=store.entries.values().stream().filter(v->v.template().equals("sakura_labyrinth")&&v.min().equals(min)).findFirst().orElseThrow();
            // Simulate beta.4's direct backup mask, which did not include the gap.
            // Direct backup segments already contain these original cells.
            if(e.direct()) {
                var path=store.backup(e.id());CompoundTag tag=DungeonStore.read(path);
                BitSet mask=BitSet.valueOf(tag.getLongArray("PlacementMask"));
                for(int z=219;z<=220;z++)for(int x=2;x<=202;x++)mask.clear((5*t.sz+z)*t.sx+x);
                tag.putLongArray("PlacementMask",mask.toLongArray());DungeonStore.write(path,tag);
            }
            for(int z=219;z<=220;z++)for(int x=2;x<=202;x++) {
                BlockPos p=min.offset(x,5,z);
                require(level.getBlockState(p).is(dev.unyielding.upsidedown.dungeon.block.DungeonBlocks.BLOSSOM_FLOOR.get()),"expected intact maze strip");
                require(DungeonModule.withEdits(()->level.setBlock(p,Blocks.AIR.defaultBlockState(),2)),"could not make gap");
            }
            BlockPos obstacle=min.offset(2,5,219);
            require(DungeonModule.withEdits(()->level.setBlock(obstacle,Blocks.DIAMOND_BLOCK.defaultBlockState(),2)),"could not seed obstruction");
        } else if(mode.equals("clear")) {
            BlockPos obstacle=min.offset(2,5,219);
            require(level.getBlockState(obstacle).is(Blocks.DIAMOND_BLOCK),"missing repair obstruction");
            require(DungeonModule.withEdits(()->level.setBlock(obstacle,Blocks.AIR.defaultBlockState(),2)),"could not clear repair obstruction");
        } else throw new IllegalArgumentException("Unknown CI gap mode");
    }
}
