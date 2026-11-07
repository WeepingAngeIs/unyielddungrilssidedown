package dev.unyielding.upsidedown.mixin;

import dev.unyielding.upsidedown.Flip;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({BlockStateBase.class})
public abstract class BlockStateBaseMixin {
   @Inject(
      method = {"canSurvive"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cu$survive(LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
      if (level instanceof Level l && Flip.inZone(l, pos)) {
         cir.setReturnValue(true);
      }
   }
}
