package dev.unyielding.upsidedown.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.unyielding.upsidedown.Gravity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({EntityRenderDispatcher.class})
public abstract class EntityRenderDispatcherMixin {
   @Inject(
      method = {"renderShadow"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private static void cu$noShadow(PoseStack pose, MultiBufferSource buffers, Entity e, float alpha, float pt, LevelReader level, float size, CallbackInfo ci) {
      if (Gravity.rotated(e)) {
         ci.cancel();
      }
   }
}
