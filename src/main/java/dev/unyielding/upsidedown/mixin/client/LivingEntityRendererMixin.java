package dev.unyielding.upsidedown.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.unyielding.upsidedown.Flip;
import dev.unyielding.upsidedown.Frames;
import dev.unyielding.upsidedown.Gravity;
import dev.unyielding.upsidedown.client.ClientFlip;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LivingEntityRenderer.class})
public abstract class LivingEntityRendererMixin {
   @Inject(
      method = {"isEntityUpsideDown"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void cu$upsideDown(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
      if (ClientFlip.inRotations == 0 && Flip.isFlipped(entity)) {
         cir.setReturnValue(true);
      }
   }

   @Inject(
      method = {"render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V"
      )},
      require = 0
   )
   private void cu$beforeRotations(LivingEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
      ClientFlip.inRotations++;
      if (Gravity.rotated(entity)) {
         pose.mulPose(Frames.quat(Gravity.of(entity)));
      }
   }

   @Inject(
      method = {"render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
         shift = Shift.AFTER
      )},
      require = 0
   )
   private void cu$afterRotations(LivingEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
      if (ClientFlip.inRotations > 0) {
         ClientFlip.inRotations--;
      }

      if (Flip.isFlipped(entity)) {
         float scale = entity.getScale();
         pose.translate(0.0F, entity.getBbHeight() / (scale == 0.0F ? 1.0F : scale), 0.0F);
         pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
      }
   }
}
