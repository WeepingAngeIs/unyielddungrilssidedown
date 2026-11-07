package dev.unyielding.upsidedown.mixin;

import dev.unyielding.upsidedown.Flip;
import dev.unyielding.upsidedown.Gravity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({PathNavigation.class})
public abstract class PathNavigationMixin {
   @Shadow
   protected Mob mob;
   @Shadow
   protected double speedModifier;

   @Inject(
      method = {"tick"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cu$chase(CallbackInfo ci) {
      if (Gravity.rotated(this.mob)) {
         ci.cancel();
      } else {
         if (Flip.isFlipped(this.mob)) {
            ci.cancel();
            LivingEntity t = this.mob.getTarget();
            if (t != null && t.isAlive()) {
               this.mob.getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), this.speedModifier > 0.0 ? this.speedModifier : 1.0);
            }
         }
      }
   }
}
