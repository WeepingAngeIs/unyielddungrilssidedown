package dev.unyielding.upsidedown.mixin;

import dev.unyielding.upsidedown.Flip;
import dev.unyielding.upsidedown.Gravity;
import net.minecraft.core.Direction;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ServerGamePacketListenerImpl.class})
public abstract class ServerGamePacketListenerImplMixin {
   @Inject(
      method = {"noBlocksAround"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cu$noBlocksAbove(Entity e, CallbackInfoReturnable<Boolean> cir) {
      if (Gravity.rotated(e)) {
         Direction g = Gravity.of(e);
         cir.setReturnValue(
            e.level()
               .getBlockStates(
                  e.getBoundingBox().inflate(0.0625).expandTowards((double)g.getStepX() * 0.55, (double)g.getStepY() * 0.55, (double)g.getStepZ() * 0.55)
               )
               .allMatch(BlockStateBase::isAir)
         );
      } else {
         if (Flip.isFlipped(e)) {
            double roof = Flip.roofOf(e);
            if (!Double.isNaN(roof) && roof - e.getBoundingBox().maxY < 0.0625) {
               cir.setReturnValue(false);
               return;
            }

            cir.setReturnValue(e.level().getBlockStates(e.getBoundingBox().inflate(0.0625).expandTowards(0.0, 0.55, 0.0)).allMatch(BlockStateBase::isAir));
         }
      }
   }
}
