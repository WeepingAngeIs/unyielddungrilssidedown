package dev.unyielding.upsidedown.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.unyielding.upsidedown.Flip;
import dev.unyielding.upsidedown.client.ClientFlip;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({MouseHandler.class})
public abstract class MouseHandlerMixin {
   @WrapOperation(
      method = {"turnPlayer"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"
      )},
      require = 0
   )
   private void cu$turn(LocalPlayer player, double yaw, double pitch, Operation<Void> original) {
      if (Flip.isFlipped(player) && ClientFlip.viewRolled()) {
         original.call(new Object[]{player, -yaw, -pitch});
      } else {
         original.call(new Object[]{player, yaw, pitch});
      }
   }
}
