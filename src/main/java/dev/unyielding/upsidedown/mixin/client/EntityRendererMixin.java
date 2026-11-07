package dev.unyielding.upsidedown.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.unyielding.upsidedown.Frames;
import dev.unyielding.upsidedown.Gravity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({EntityRenderer.class})
public abstract class EntityRendererMixin {
   @WrapOperation(
      method = {"renderNameTag"},
      at = {@At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V",
         ordinal = 0
      )},
      require = 0
   )
   private void cu$tag(
      PoseStack pose, double x, double y, double z, Operation<Void> op, Entity e, Component name, PoseStack p2, MultiBufferSource b, int light, float pt
   ) {
      if (Gravity.rotated(e)) {
         Vec3 r = Frames.toWorld(Gravity.of(e), x, y, z);
         op.call(new Object[]{pose, r.x, r.y, r.z});
      } else {
         op.call(new Object[]{pose, x, y, z});
      }
   }
}
