package dev.unyielding.upsidedown.mixin;

import dev.unyielding.upsidedown.Frames;
import dev.unyielding.upsidedown.Gravity;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Direction.class})
public abstract class DirectionMixin {
   @Inject(
      method = {"orderedByNearest"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private static void cu$ordered(Entity e, CallbackInfoReturnable<Direction[]> cir) {
      if (Gravity.rotated(e)) {
         Direction g = Gravity.of(e);
         Direction[] in = (Direction[])cir.getReturnValue();
         Direction[] out = new Direction[in.length];

         for (int i = 0; i < in.length; i++) {
            out[i] = Frames.toWorld(g, in[i]);
         }

         cir.setReturnValue(out);
      }
   }
}
