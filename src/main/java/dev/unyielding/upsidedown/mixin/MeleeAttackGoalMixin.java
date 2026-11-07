package dev.unyielding.upsidedown.mixin;

import dev.unyielding.upsidedown.Flip;
import dev.unyielding.upsidedown.Gravity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({MeleeAttackGoal.class})
public abstract class MeleeAttackGoalMixin {
   @Shadow
   @Final
   protected PathfinderMob mob;

   private boolean cu$odd() {
      return Flip.isFlipped(this.mob) || Gravity.rotated(this.mob);
   }

   private boolean cu$ok() {
      LivingEntity t = this.mob.getTarget();
      return t != null && t.isAlive() && t.level() == this.mob.level() && this.mob.distanceToSqr(t) < 1024.0;
   }

   @Inject(
      method = {"canUse"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cu$canUse(CallbackInfoReturnable<Boolean> cir) {
      if (this.cu$odd()) {
         cir.setReturnValue(this.cu$ok());
      }
   }

   @Inject(
      method = {"canContinueToUse"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cu$canContinue(CallbackInfoReturnable<Boolean> cir) {
      if (this.cu$odd()) {
         cir.setReturnValue(this.cu$ok());
      }
   }
}
