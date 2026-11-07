package dev.unyielding.upsidedown.mixin;

import dev.unyielding.upsidedown.Frames;
import dev.unyielding.upsidedown.Gravity;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Projectile.class})
public abstract class ProjectileMixin {
   @Shadow
   public abstract void shoot(double var1, double var3, double var5, float var7, float var8);

   @Inject(
      method = {"shootFromRotation"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cu$shootSideways(Entity shooter, float xRot, float yRot, float roll, float velocity, float inaccuracy, CallbackInfo ci) {
      if (Gravity.rotated(shooter)) {
         ci.cancel();
         Direction g = Gravity.of(shooter);
         float f = -Mth.sin(yRot * (float) (Math.PI / 180.0)) * Mth.cos(xRot * (float) (Math.PI / 180.0));
         float f1 = -Mth.sin((xRot + roll) * (float) (Math.PI / 180.0));
         float f2 = Mth.cos(yRot * (float) (Math.PI / 180.0)) * Mth.cos(xRot * (float) (Math.PI / 180.0));
         Vec3 dir = Frames.toWorld(g, (double)f, (double)f1, (double)f2);
         this.shoot(dir.x, dir.y, dir.z, velocity, inaccuracy);
         Vec3 km = Frames.toLocal(g, shooter.getKnownMovement());
         if (shooter.onGround()) {
            km = new Vec3(km.x, 0.0, km.z);
         }

         Projectile self = (Projectile)(Object)this;
         self.setDeltaMovement(self.getDeltaMovement().add(Frames.toWorld(g, km)));
      }
   }
}
