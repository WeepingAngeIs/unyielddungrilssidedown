package dev.unyielding.upsidedown.mixin;

import dev.unyielding.upsidedown.Flip;
import dev.unyielding.upsidedown.Frames;
import dev.unyielding.upsidedown.Gravity;
import dev.unyielding.upsidedown.Sideways;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin({LivingEntity.class})
public abstract class LivingEntityMixin {
   @Shadow
   public float yBodyRot;
   @Shadow
   public float attackAnim;

   @Shadow
   protected abstract float getJumpPower();

   @Shadow
   protected abstract void updateWalkAnimation(float var1);

   private LivingEntity cu$self() {
      return (LivingEntity)(Object)this;
   }

   @Inject(
      method = {"jumpFromGround"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cu$jumpSideways(CallbackInfo ci) {
      LivingEntity self = this.cu$self();
      if (Gravity.rotated(self)) {
         ci.cancel();
         float f = this.getJumpPower();
         if (!(f <= 1.0E-5F)) {
            Direction g = Gravity.of(self);
            boolean local = Gravity.localTravel(self);
            Vec3 v = local ? self.getDeltaMovement() : Frames.toLocal(g, self.getDeltaMovement());
            v = new Vec3(v.x, (double)f, v.z);
            if (self.isSprinting()) {
               float r = self.getYRot() * (float) (Math.PI / 180.0);
               v = v.add((double)(-Mth.sin(r)) * 0.2, 0.0, (double)Mth.cos(r) * 0.2);
            }

            self.setDeltaMovement(local ? v : Frames.toWorld(g, v));
            self.hasImpulse = true;
            CommonHooks.onLivingJump(self);
         }
      }
   }

   @Inject(
      method = {"jumpFromGround"},
      at = {@At("TAIL")}
   )
   private void cu$jumpDown(CallbackInfo ci) {
      LivingEntity self = this.cu$self();
      Vec3 v = self.getDeltaMovement();
      if (v.y > 0.0 && Flip.isFlipped(self)) {
         self.setDeltaMovement(v.x, -v.y, v.z);
      }
   }

   @Inject(
      method = {"travel"},
      at = {@At("HEAD")}
   )
   private void cu$travelIn(Vec3 input, CallbackInfo ci) {
      Gravity.enterTravel(this.cu$self());
   }

   @Inject(
      method = {"travel"},
      at = {@At("RETURN")}
   )
   private void cu$travelOut(Vec3 input, CallbackInfo ci) {
      Gravity.exitTravel(this.cu$self());
   }

   @ModifyVariable(
      method = {"travel"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private Vec3 cu$steer(Vec3 input) {
      LivingEntity self = this.cu$self();
      if (!(self instanceof Player) && !self.level().isClientSide() && Gravity.rotated(self)) {
         Vec3 s = Sideways.steer(self);
         if (s != null) {
            return s;
         }
      }

      return input;
   }

   @ModifyArgs(
      method = {"tick"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/LivingEntity;tickHeadTurn(FF)F"
      )
   )
   private void cu$bodyTurn(Args args) {
      LivingEntity self = this.cu$self();
      if (Gravity.rotated(self)) {
         Vec3 d = Frames.toLocal(Gravity.of(self), new Vec3(self.getX() - self.xo, self.getY() - self.yo, self.getZ() - self.zo));
         float f = (float)(d.x * d.x + d.z * d.z);
         float f1 = this.yBodyRot;
         float f2 = 0.0F;
         if (f > 0.0025000002F) {
            f2 = (float)Math.sqrt((double)f) * 3.0F;
            float f4 = (float)Mth.atan2(d.z, d.x) * (180.0F / (float)Math.PI) - 90.0F;
            float f5 = Mth.abs(Mth.wrapDegrees(self.getYRot()) - f4);
            f1 = 95.0F < f5 && f5 < 265.0F ? f4 - 180.0F : f4;
         }

         if (this.attackAnim > 0.0F) {
            f1 = self.getYRot();
         }

         args.set(0, f1);
         args.set(1, f2);
      }
   }

   @Inject(
      method = {"calculateEntityAnimation"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cu$walkAnim(boolean flying, CallbackInfo ci) {
      LivingEntity self = this.cu$self();
      if (Gravity.rotated(self)) {
         ci.cancel();
         Vec3 d = Frames.toLocal(Gravity.of(self), new Vec3(self.getX() - self.xo, self.getY() - self.yo, self.getZ() - self.zo));
         this.updateWalkAnimation((float)Mth.length(d.x, flying ? d.y : 0.0, d.z));
      }
   }
}
