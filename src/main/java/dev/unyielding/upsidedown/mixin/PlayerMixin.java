package dev.unyielding.upsidedown.mixin;

import dev.unyielding.upsidedown.Gravity;
import dev.unyielding.upsidedown.Sideways;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Player.class})
public abstract class PlayerMixin {
   private Player cu$self() {
      return (Player)(Object)this;
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

   @Inject(
      method = {"maybeBackOffFromEdge"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cu$edge(Vec3 move, MoverType type, CallbackInfoReturnable<Vec3> cir) {
      Player self = this.cu$self();
      if (Gravity.rotated(self)) {
         cir.setReturnValue(Sideways.backOffFromEdge(self, move, type == MoverType.SELF || type == MoverType.PLAYER));
      }
   }

   @Inject(
      method = {"canPlayerFitWithinBlocksAndEntitiesWhen"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cu$fits(Pose pose, CallbackInfoReturnable<Boolean> cir) {
      Player self = this.cu$self();
      if (Gravity.rotated(self)) {
         EntityDimensions d = self.getDimensions(pose);
         cir.setReturnValue(self.level().noCollision(self, Sideways.poseBox(self, d.width(), d.height()).deflate(1.0E-7)));
      }
   }
}
