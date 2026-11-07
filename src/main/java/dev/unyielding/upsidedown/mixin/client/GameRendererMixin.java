package dev.unyielding.upsidedown.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.unyielding.upsidedown.Flip;
import dev.unyielding.upsidedown.Gravity;
import dev.unyielding.upsidedown.client.ClientFlip;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({GameRenderer.class})
public abstract class GameRendererMixin {
   @Shadow
   public abstract Camera getMainCamera();

   @ModifyExpressionValue(
      method = {"renderLevel"},
      at = {@At(
         value = "INVOKE",
         target = "Lorg/joml/Quaternionf;conjugate(Lorg/joml/Quaternionf;)Lorg/joml/Quaternionf;",
         remap = false
      )},
      require = 0
   )
   private Quaternionf cu$turnView(Quaternionf view) {
      Camera cam = this.getMainCamera();
      Entity e = cam.getEntity();
      ClientFlip.viewHookRan = true;
      Quaternionf want = ClientFlip.expected;
      if (e == null || want == null || !Gravity.rotated(e) && !ClientFlip.easing()) {
         boolean rolled = ClientFlip.cameraRolled(cam);
         if (e != null && !rolled && Flip.isFlipped(e)) {
            view.premul(new Quaternionf().rotationZ((float) Math.PI));
            rolled = true;
         }

         ClientFlip.screenRolled = rolled;
         return view;
      } else {
         if (Math.abs(cam.rotation().dot(want)) < 0.9999F) {
            view.set(want).conjugate();
         }

         ClientFlip.screenRolled = Flip.isFlipped(e);
         return view;
      }
   }
}
