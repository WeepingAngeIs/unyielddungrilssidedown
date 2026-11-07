package dev.unyielding.upsidedown.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.unyielding.upsidedown.Frames;
import dev.unyielding.upsidedown.Gravity;
import dev.unyielding.upsidedown.client.ClientFlip;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Camera.class})
public abstract class CameraMixin {
   @Shadow
   @Final
   private Quaternionf rotation;
   @Shadow
   @Final
   private Vector3f forwards;
   @Shadow
   @Final
   private Vector3f up;
   @Shadow
   @Final
   private Vector3f left;
   @Shadow
   private float eyeHeight;
   @Shadow
   private float eyeHeightOld;
   @Shadow
   private Entity entity;

   private void cu$set(Quaternionf q) {
      this.rotation.set(q);
      this.forwards.set(0.0F, 0.0F, -1.0F).rotate(q);
      this.up.set(0.0F, 1.0F, 0.0F).rotate(q);
      this.left.set(-1.0F, 0.0F, 0.0F).rotate(q);
   }

   private void cu$frame(Entity e, float yaw, float pitch, float roll, float pt) {
      boolean side = Gravity.rotated(e);
      boolean mine = e == Minecraft.getInstance().player;
      if (side || mine && ClientFlip.easing()) {
         Quaternionf q;
         if (side) {
            q = Frames.quat(Gravity.of(e))
               .mul(
                  new Quaternionf()
                     .rotationYXZ((float) Math.PI - yaw * (float) (Math.PI / 180.0), -pitch * (float) (Math.PI / 180.0), -roll * (float) (Math.PI / 180.0))
               );
         } else {
            q = new Quaternionf(this.rotation);
         }

         if (mine) {
            ClientFlip.applyEase(q, pt);
         }

         this.cu$set(q);
         ClientFlip.expected = !side && (!mine || !ClientFlip.easing()) ? null : new Quaternionf(q);
      }
   }

   @WrapOperation(
      method = {"setup"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/Camera;setRotation(FFF)V",
         ordinal = 0
      )}
   )
   private void cu$rotate(
      Camera cam, float yaw, float pitch, float roll, Operation<Void> op, @Local(argsOnly = true) Entity e, @Local(argsOnly = true) float pt
   ) {
      op.call(new Object[]{cam, yaw, pitch, roll});
      ClientFlip.expected = null;
      this.cu$frame(e, yaw, pitch, roll, pt);
   }

   @WrapOperation(
      method = {"setup"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/Camera;setRotation(FFF)V",
         ordinal = 1
      )}
   )
   private void cu$rotateMirrored(
      Camera cam, float yaw, float pitch, float roll, Operation<Void> op, @Local(argsOnly = true) Entity e, @Local(argsOnly = true) float pt
   ) {
      op.call(new Object[]{cam, yaw, pitch, roll});
      this.cu$frame(e, yaw, pitch, roll, pt);
   }

   @WrapOperation(
      method = {"setup"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/Camera;setPosition(DDD)V",
         ordinal = 0
      )}
   )
   private void cu$eyes(Camera cam, double x, double y, double z, Operation<Void> op, @Local(argsOnly = true) Entity e, @Local(argsOnly = true) float pt) {
      boolean mine = e == Minecraft.getInstance().player;
      if (Gravity.rotated(e)) {
         double eh = (double)Mth.lerp(pt, this.eyeHeightOld, this.eyeHeight);
         Vec3 feet = new Vec3(Mth.lerp((double)pt, e.xo, e.getX()), Mth.lerp((double)pt, e.yo, e.getY()), Mth.lerp((double)pt, e.zo, e.getZ()));
         Vec3 eye = feet.add(Frames.up(Gravity.of(e)).scale(eh));
         x = eye.x;
         y = eye.y;
         z = eye.z;
      }

      if (mine && ClientFlip.easing()) {
         Vec3 o = ClientFlip.easeEyeOffset(pt);
         x += o.x;
         y += o.y;
         z += o.z;
      }

      op.call(new Object[]{cam, x, y, z});
   }

   @Inject(
      method = {"setup"},
      at = {@At("TAIL")}
   )
   private void cu$remember(CallbackInfo ci) {
      if (this.entity != null && this.entity == Minecraft.getInstance().player) {
         ClientFlip.lastCamera.set(this.rotation);
         ClientFlip.lastCameraValid = true;
      }
   }
}
