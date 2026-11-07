package dev.unyielding.upsidedown.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.unyielding.upsidedown.Flip;
import dev.unyielding.upsidedown.Frames;
import dev.unyielding.upsidedown.Gravity;
import dev.unyielding.upsidedown.GravityEntity;
import dev.unyielding.upsidedown.Sideways;
import it.unimi.dsi.fastutil.doubles.DoubleListIterator;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Entity.class})
public abstract class EntityMixin implements GravityEntity {
   @Shadow
   public boolean verticalCollision;
   @Shadow
   private float eyeHeight;
   @Shadow
   private Vec3 position;
   @Shadow
   private EntityDimensions dimensions;
   @Shadow
   public double xo;
   @Shadow
   public double yo;
   @Shadow
   public double zo;
   @Shadow
   protected Optional<BlockPos> mainSupportingBlockPos;
   @Shadow
   private boolean onGroundNoBlocks;
   @Unique
   private Direction cu$gravity = Direction.DOWN;
   @Unique
   private int cu$travelDepth;
   @Unique
   private int cu$grace;
   @Unique
   private int cu$mismatch;

   @Shadow
   public abstract AABB getBoundingBox();

   @Shadow
   public abstract Level level();

   @Shadow
   public abstract float maxUpStep();

   @Shadow
   public abstract boolean onGround();

   @Shadow
   public abstract float getBbHeight();

   @Shadow
   public abstract double getX();

   @Shadow
   public abstract double getY();

   @Shadow
   public abstract double getZ();

   @Shadow
   public abstract Vec3 getDeltaMovement();

   @Shadow
   public abstract void setDeltaMovement(Vec3 var1);

   @Shadow
   private Vec3 collide(Vec3 move) {
      throw new AssertionError();
   }

   @Shadow
   private static List<VoxelShape> collectColliders(Entity e, Level level, List<VoxelShape> extra, AABB box) {
      throw new AssertionError();
   }

   @Shadow
   private static Vec3 collideWithShapes(Vec3 move, AABB box, List<VoxelShape> shapes) {
      throw new AssertionError();
   }

   @Override
   public Direction cu$gravity() {
      return this.cu$gravity == null ? Direction.DOWN : this.cu$gravity;
   }

   @Override
   public void cu$setGravityRaw(Direction g) {
      this.cu$gravity = g;
   }

   @Override
   public int cu$travelDepth() {
      return this.cu$travelDepth;
   }

   @Override
   public void cu$setTravelDepth(int d) {
      this.cu$travelDepth = d;
   }

   @Override
   public int cu$grace() {
      return this.cu$grace;
   }

   @Override
   public void cu$setGrace(int ticks) {
      this.cu$grace = ticks;
   }

   @Override
   public int cu$mismatch() {
      return this.cu$mismatch;
   }

   @Override
   public void cu$setMismatch(int ticks) {
      this.cu$mismatch = ticks;
   }

   @Unique
   private Entity cu$self() {
      return (Entity)(Object)this;
   }

   @Inject(
      method = {"makeBoundingBox"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void cu$turnedBox(CallbackInfoReturnable<AABB> cir) {
      Direction g = this.cu$gravity();
      if (Frames.sideways(g) && this.cu$self() instanceof LivingEntity) {
         cir.setReturnValue(Frames.box(g, this.position, (double)(Object)this.dimensions.width(), (double)(Object)this.dimensions.height()));
      }
   }

   @Inject(
      method = {"getEyePosition()Lnet/minecraft/world/phys/Vec3;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cu$eyePos(CallbackInfoReturnable<Vec3> cir) {
      if (Gravity.rotated(this.cu$self())) {
         cir.setReturnValue(this.position.add(Frames.up(this.cu$gravity()).scale((double)(Object)this.eyeHeight)));
      }
   }

   @Inject(
      method = {"getEyePosition(F)Lnet/minecraft/world/phys/Vec3;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cu$eyePosLerp(float pt, CallbackInfoReturnable<Vec3> cir) {
      if (Gravity.rotated(this.cu$self())) {
         Vec3 feet = new Vec3(
            Mth.lerp((double)pt, this.xo, this.getX()), Mth.lerp((double)pt, this.yo, this.getY()), Mth.lerp((double)pt, this.zo, this.getZ())
         );
         cir.setReturnValue(feet.add(Frames.up(this.cu$gravity()).scale((double)(Object)this.eyeHeight)));
      }
   }

   @Inject(
      method = {"calculateViewVector"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void cu$view(float pitch, float yaw, CallbackInfoReturnable<Vec3> cir) {
      if (Gravity.rotated(this.cu$self())) {
         cir.setReturnValue(Frames.toWorld(this.cu$gravity(), (Vec3)cir.getReturnValue()));
      }
   }

   @Inject(
      method = {"getDirection"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void cu$facing(CallbackInfoReturnable<Direction> cir) {
      if (Gravity.rotated(this.cu$self())) {
         Direction d = Frames.toWorld(this.cu$gravity(), (Direction)cir.getReturnValue());
         if (d.getAxis() == Axis.Y) {
            Vec3 look = this.cu$self().getLookAngle();
            d = Direction.getNearest(look.x, 0.0, look.z);
            if (d.getAxis() == Axis.Y) {
               d = Direction.NORTH;
            }
         }

         cir.setReturnValue(d);
      }
   }

   @ModifyVariable(
      method = {"move"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private Vec3 cu$moveToWorld(Vec3 move) {
      return Gravity.localTravel(this.cu$self()) ? Frames.toWorld(this.cu$gravity(), move) : move;
   }

   @ModifyVariable(
      method = {"move"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/util/profiling/ProfilerFiller;pop()V",
         ordinal = 0
      ),
      ordinal = 0,
      argsOnly = true
   )
   private Vec3 cu$moveArgToLocal(Vec3 move) {
      return Gravity.rotated(this.cu$self()) ? Frames.toLocal(this.cu$gravity(), move) : move;
   }

   @ModifyVariable(
      method = {"move"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/util/profiling/ProfilerFiller;pop()V",
         ordinal = 0
      ),
      ordinal = 1
   )
   private Vec3 cu$movedToLocal(Vec3 moved) {
      return Gravity.rotated(this.cu$self()) ? Frames.toLocal(this.cu$gravity(), moved) : moved;
   }

   @Inject(
      method = {"checkSupportingBlock"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cu$supportSideways(boolean onGround, Vec3 movement, CallbackInfo ci) {
      if (Gravity.rotated(this.cu$self())) {
         ci.cancel();
         if (onGround) {
            AABB slice = Frames.slice(this.cu$gravity(), this.getBoundingBox(), 1.0E-6);
            Optional<BlockPos> opt = this.level().findSupportingBlock(this.cu$self(), slice);
            this.mainSupportingBlockPos = opt;
            this.onGroundNoBlocks = opt.isEmpty();
         } else {
            this.onGroundNoBlocks = false;
            if (this.mainSupportingBlockPos.isPresent()) {
               this.mainSupportingBlockPos = Optional.empty();
            }
         }
      }
   }

   @Inject(
      method = {"applyGravity"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cu$sideGravity(CallbackInfo ci) {
      Direction g = this.cu$gravity();
      if (Frames.sideways(g) && !(this.cu$self() instanceof LivingEntity)) {
         ci.cancel();
         double d = this.cu$self().getGravity();
         if (d != 0.0) {
            this.setDeltaMovement(this.getDeltaMovement().add((double)g.getStepX() * d, (double)g.getStepY() * d, (double)g.getStepZ() * d));
         }
      }
   }

   @Inject(
      method = {"getGravity"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void cu$gravity(CallbackInfoReturnable<Double> cir) {
      if ((Double)cir.getReturnValue() != 0.0 && Flip.isFlipped(this.cu$self())) {
         cir.setReturnValue(-(Double)cir.getReturnValue());
      }
   }

   @WrapOperation(
      method = {"move"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/world/entity/Entity;verticalCollisionBelow:Z",
         opcode = 181
      )}
   )
   private void cu$groundAbove(Entity self, boolean value, Operation<Void> op, @Local(argsOnly = true) Vec3 movement) {
      if (Flip.isFlipped(self)) {
         value = this.verticalCollision && movement.y > 0.0;
      }

      op.call(new Object[]{self, value});
   }

   @ModifyVariable(
      method = {"checkFallDamage"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private double cu$fallUp(double dy) {
      return Flip.isFlipped(this.cu$self()) ? -dy : dy;
   }

   @Inject(
      method = {"getOnPos(F)Lnet/minecraft/core/BlockPos;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cu$onPos(float offset, CallbackInfoReturnable<BlockPos> cir) {
      Direction g = this.cu$gravity();
      if (g == Direction.UP) {
         cir.setReturnValue(BlockPos.containing(this.position.x, this.getBoundingBox().maxY + (double)offset, this.position.z));
      } else if (Frames.sideways(g) && this.cu$self() instanceof LivingEntity) {
         cir.setReturnValue(
            BlockPos.containing(
               this.position.add((double)((float)g.getStepX() * offset), (double)((float)g.getStepY() * offset), (double)((float)g.getStepZ() * offset))
            )
         );
      }
   }

   @Inject(
      method = {"getEyeHeight()F"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void cu$eyeHeight(CallbackInfoReturnable<Float> cir) {
      if (Flip.isFlipped(this.cu$self())) {
         cir.setReturnValue(this.getBbHeight() - (Float)cir.getReturnValue());
      }
   }

   @Inject(
      method = {"getEyeY"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void cu$eyeY(CallbackInfoReturnable<Double> cir) {
      Direction g = this.cu$gravity();
      if (g == Direction.UP) {
         cir.setReturnValue(this.position.y + (double)(Object)this.getBbHeight() - (double)(Object)this.eyeHeight);
      } else if (Frames.sideways(g) && this.cu$self() instanceof LivingEntity) {
         cir.setReturnValue(this.position.y + Frames.up(g).y * (double)(Object)this.eyeHeight);
      }
   }

   @Inject(
      method = {"collide"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cu$collide(Vec3 m, CallbackInfoReturnable<Vec3> cir) {
      Entity self = this.cu$self();
      if (Gravity.rotated(self)) {
         cir.setReturnValue(Sideways.collide(self, m, this.cu$gravity()));
      } else if (Flip.isFlipped(self)) {
         AABB box;
         box = this.getBoundingBox();
         label112:
         if (m.y > 0.0) {
            if (self instanceof Player p && p.getAbilities().flying) {
               break label112;
            }

            double roof = Flip.roofOf(self);
            if (!Double.isNaN(roof) && box.maxY <= roof + 1.0E-7 && box.maxY + m.y > roof) {
               cir.setReturnValue(this.collide(new Vec3(m.x, Math.max(0.0, roof - box.maxY), m.z)));
               return;
            }
         }

         float step = this.maxUpStep();
         if (!(step <= 0.0F)) {
            Level level = this.level();
            List<VoxelShape> list = level.getEntityCollisions(self, box.expandTowards(m));
            Vec3 v = m.lengthSqr() == 0.0 ? m : Entity.collideBoundingBox(self, m, box, level, list);
            boolean fx = m.x != v.x;
            boolean fz = m.z != v.z;
            boolean hitAbove = m.y != v.y && m.y > 0.0;
            if ((hitAbove || this.onGround()) && (fx || fz)) {
               AABB b1 = hitAbove ? box.move(0.0, v.y, 0.0) : box;
               AABB b2 = b1.expandTowards(m.x, (double)(-step), m.z);
               if (!hitAbove) {
                  b2 = b2.expandTowards(0.0, 1.0E-5F, 0.0);
               }

               List<VoxelShape> shapes = collectColliders(self, level, list, b2);
               FloatArrayList cand = new FloatArrayList();

               for (VoxelShape s : shapes) {
                  DoubleListIterator d0 = s.getCoords(Axis.Y).iterator();

                  while (d0.hasNext()) {
                     double c = (Double)d0.next();
                     float f = (float)(b1.maxY - c);
                     if (f >= 0.0F && f <= step && f != (float)(-v.y) && !cand.contains(f)) {
                        cand.add(f);
                     }
                  }
               }

               cand.sort(null);

               for (int i = 0; i < cand.size(); i++) {
                  Vec3 v1 = collideWithShapes(new Vec3(m.x, (double)(-cand.getFloat(i)), m.z), b1, shapes);
                  if (v1.horizontalDistanceSqr() > v.horizontalDistanceSqr()) {
                     double d0 = box.minY - b1.minY;
                     cir.setReturnValue(v1.add(0.0, -d0, 0.0));
                     return;
                  }
               }
            }

            cir.setReturnValue(v);
         }
      }
   }
}
