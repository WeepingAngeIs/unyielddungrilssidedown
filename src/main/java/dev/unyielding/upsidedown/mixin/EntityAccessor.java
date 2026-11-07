package dev.unyielding.upsidedown.mixin;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({Entity.class})
public interface EntityAccessor {
   @Invoker("collectColliders")
   static List<VoxelShape> cu$collectColliders(@Nullable Entity e, Level level, List<VoxelShape> extra, AABB box) {
      throw new AssertionError();
   }

   @Invoker("checkSupportingBlock")
   void cu$checkSupportingBlock(boolean var1, @Nullable Vec3 var2);

   @Invoker("checkFallDamage")
   void cu$checkFallDamage(double var1, boolean var3, BlockState var4, BlockPos var5);
}
