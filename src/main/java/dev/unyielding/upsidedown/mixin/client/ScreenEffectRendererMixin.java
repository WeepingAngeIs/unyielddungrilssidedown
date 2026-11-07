package dev.unyielding.upsidedown.mixin.client;

import dev.unyielding.upsidedown.Frames;
import dev.unyielding.upsidedown.Gravity;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ScreenEffectRenderer.class})
public abstract class ScreenEffectRendererMixin {
   @Inject(
      method = {"getOverlayBlock"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private static void cu$overlay(Player p, CallbackInfoReturnable<Pair<BlockState, BlockPos>> cir) {
      if (Gravity.rotated(p)) {
         Direction g = Gravity.of(p);
         Vec3 eye = p.getEyePosition();
         MutableBlockPos m = new MutableBlockPos();

         for (int i = 0; i < 8; i++) {
            double lx = (double)(((float)((i >> 0) % 2) - 0.5F) * p.getBbWidth() * 0.8F);
            double ly = (double)(((float)((i >> 1) % 2) - 0.5F) * 0.1F * p.getScale());
            double lz = (double)(((float)((i >> 2) % 2) - 0.5F) * p.getBbWidth() * 0.8F);
            Vec3 w = eye.add(Frames.toWorld(g, lx, ly, lz));
            m.set(w.x, w.y, w.z);
            BlockState s = p.level().getBlockState(m);
            if (s.getRenderShape() != RenderShape.INVISIBLE && s.isViewBlocking(p.level(), m)) {
               cir.setReturnValue(Pair.of(s, m.immutable()));
               return;
            }
         }

         cir.setReturnValue(null);
      }
   }
}
