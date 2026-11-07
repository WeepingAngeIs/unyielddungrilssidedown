package dev.unyielding.upsidedown.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.unyielding.upsidedown.Frames;
import dev.unyielding.upsidedown.Gravity;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LocalPlayer.class})
public abstract class LocalPlayerMixin {
   @Inject(
      method = {"moveTowardsClosestSpace"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cu$noPush(double x, double z, CallbackInfo ci) {
      if (Gravity.rotated((LocalPlayer)(Object)this)) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"updateAutoJump"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cu$noAutoJump(float dx, float dz, CallbackInfo ci) {
      if (Gravity.rotated((LocalPlayer)(Object)this)) {
         ci.cancel();
      }
   }

   @WrapOperation(
      method = {"aiStep"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/phys/Vec3;add(DDD)Lnet/minecraft/world/phys/Vec3;"
      )},
      require = 0
   )
   private Vec3 cu$flyUp(Vec3 v, double x, double y, double z, Operation<Vec3> op) {
      LocalPlayer self = (LocalPlayer)(Object)this;
      return Gravity.rotated(self) && x == 0.0 && z == 0.0 ? v.add(Frames.up(Gravity.of(self)).scale(y)) : (Vec3)op.call(new Object[]{v, x, y, z});
   }
}
