package dev.unyielding.upsidedown.mixin;

import dev.unyielding.upsidedown.dungeon.DungeonModule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Regions remain intact under explosions, fluids, fire and ordinary third-party world edits. */
@Mixin(Level.class)
public abstract class DungeonLevelMixin {
    @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("HEAD"),cancellable=true)
    private void cu$protectChange(BlockPos pos,BlockState state,int flags,int recursion,CallbackInfoReturnable<Boolean> cir){
        if(DungeonModule.denyChange((Level)(Object)this,pos,state))cir.setReturnValue(false);
    }
    @Inject(method="destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",at=@At("HEAD"),cancellable=true)
    private void cu$protectDestroy(BlockPos pos,boolean drops,Entity entity,int recursion,CallbackInfoReturnable<Boolean> cir){
        if(DungeonModule.denyChange((Level)(Object)this,pos,null))cir.setReturnValue(false);
    }
}
