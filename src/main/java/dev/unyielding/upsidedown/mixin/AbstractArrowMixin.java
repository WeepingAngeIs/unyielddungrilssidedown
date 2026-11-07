package dev.unyielding.upsidedown.mixin;

import dev.unyielding.upsidedown.Frames;
import dev.unyielding.upsidedown.Gravity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({AbstractArrow.class})
public abstract class AbstractArrowMixin {
   @Inject(
      method = {"<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V"},
      at = {@At("RETURN")}
   )
   private void cu$fromEyes(EntityType<?> type, LivingEntity owner, Level level, ItemStack pickup, ItemStack weapon, CallbackInfo ci) {
      if (Gravity.rotated(owner)) {
         Vec3 p = owner.getEyePosition().add(Frames.up(Gravity.of(owner)).scale(-0.1));
         ((AbstractArrow)(Object)this).setPos(p.x, p.y, p.z);
      }
   }
}
