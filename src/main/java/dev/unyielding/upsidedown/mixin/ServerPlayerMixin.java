package dev.unyielding.upsidedown.mixin;

import dev.unyielding.upsidedown.Frames;
import dev.unyielding.upsidedown.Gravity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ServerPlayer.class})
public abstract class ServerPlayerMixin {
   @Inject(
      method = {"doCheckFallDamage"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cu$fallSideways(double dx, double dy, double dz, boolean onGround, CallbackInfo ci) {
      ServerPlayer self = (ServerPlayer)(Object)this;
      if (Gravity.rotated(self)) {
         ci.cancel();
         if (!self.touchingUnloadedChunk()) {
            Vec3 local = Frames.toLocal(Gravity.of(self), dx, dy, dz);
            EntityAccessor acc = (EntityAccessor)self;
            acc.cu$checkSupportingBlock(onGround, new Vec3(dx, dy, dz));
            BlockPos pos = self.getOnPosLegacy();
            BlockState state = self.level().getBlockState(pos);
            acc.cu$checkFallDamage(local.y, onGround, state, pos);
         }
      }
   }
}
