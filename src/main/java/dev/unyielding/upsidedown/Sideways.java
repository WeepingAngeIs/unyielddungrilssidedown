package dev.unyielding.upsidedown;

import dev.unyielding.upsidedown.mixin.EntityAccessor;
import it.unimi.dsi.fastutil.doubles.DoubleListIterator;
import it.unimi.dsi.fastutil.floats.FloatArraySet;
import it.unimi.dsi.fastutil.floats.FloatArrays;
import it.unimi.dsi.fastutil.floats.FloatSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class Sideways {
   private Sideways() {
   }

   private static Axis axisOf(Vec3 v) {
      double ax = Math.abs(v.x);
      double ay = Math.abs(v.y);
      double az = Math.abs(v.z);
      return ax >= ay && ax >= az ? Axis.X : (ay >= az ? Axis.Y : Axis.Z);
   }

   private static double get(Vec3 v, Axis a) {
      return v.get(a);
   }

   private static Vec3 with(Vec3 v, Axis a, double d) {
      return switch (a) {
         case X -> new Vec3(d, v.y, v.z);
         case Y -> new Vec3(v.x, d, v.z);
         case Z -> new Vec3(v.x, v.y, d);
         default -> throw new MatchException(null, null);
      };
   }

   private static AABB moveAlong(AABB b, Axis a, double d) {
      return switch (a) {
         case X -> b.move(d, 0.0, 0.0);
         case Y -> b.move(0.0, d, 0.0);
         case Z -> b.move(0.0, 0.0, d);
         default -> throw new MatchException(null, null);
      };
   }

   static Vec3 collideWithShapes(Vec3 m, AABB box, List<VoxelShape> shapes, Direction g) {
      if (shapes.isEmpty()) {
         return m;
      } else {
         Axis ay = g.getAxis();
         Axis ax = axisOf(Frames.toWorld(g, 1.0, 0.0, 0.0));
         Axis az = axisOf(Frames.toWorld(g, 0.0, 0.0, 1.0));
         Vec3 r = m;
         double dy = get(m, ay);
         if (dy != 0.0) {
            dy = Shapes.collide(ay, box, shapes, dy);
            r = with(m, ay, dy);
            if (dy != 0.0) {
               box = moveAlong(box, ay, dy);
            }
         }

         double dx = get(r, ax);
         double dz = get(r, az);
         boolean zFirst = Math.abs(dx) < Math.abs(dz);
         if (zFirst && dz != 0.0) {
            dz = Shapes.collide(az, box, shapes, dz);
            if (dz != 0.0) {
               box = moveAlong(box, az, dz);
            }
         }

         if (dx != 0.0) {
            dx = Shapes.collide(ax, box, shapes, dx);
            if (!zFirst && dx != 0.0) {
               box = moveAlong(box, ax, dx);
            }
         }

         if (!zFirst && dz != 0.0) {
            dz = Shapes.collide(az, box, shapes, dz);
         }

         r = with(r, ax, dx);
         return with(r, az, dz);
      }
   }

   private static float[] stepHeights(AABB box, List<VoxelShape> shapes, float maxStep, float alreadyY, Direction g) {
      FloatSet set = new FloatArraySet(4);
      Axis a = g.getAxis();
      boolean pos = g.getAxisDirection() == AxisDirection.POSITIVE;
      double floor = Frames.floorFace(g, box);

      for (VoxelShape s : shapes) {
         DoubleListIterator var12 = s.getCoords(a).iterator();

         while (var12.hasNext()) {
            double c = (Double)var12.next();
            float f = (float)(pos ? floor - c : c - floor);
            if (f >= 0.0F && f <= maxStep && f != alreadyY) {
               set.add(f);
            }
         }
      }

      float[] arr = set.toFloatArray();
      FloatArrays.unstableSort(arr);
      return arr;
   }

   public static Vec3 collide(Entity e, Vec3 m, Direction g) {
      AABB box = e.getBoundingBox();
      Level level = e.level();
      List<VoxelShape> ents = level.getEntityCollisions(e, box.expandTowards(m));
      Vec3 v = m.lengthSqr() == 0.0 ? m : collideWithShapes(m, box, EntityAccessor.cu$collectColliders(e, level, ents, box.expandTowards(m)), g);
      Vec3 mL = Frames.toLocal(g, m);
      Vec3 vL = Frames.toLocal(g, v);
      boolean fx = mL.x != vL.x;
      boolean fz = mL.z != vL.z;
      boolean below = mL.y != vL.y && mL.y < 0.0;
      float step = e.maxUpStep();
      if (step > 0.0F && (below || e.onGround()) && (fx || fz)) {
         AABB b1 = below ? box.move(Frames.toWorld(g, 0.0, vL.y, 0.0)) : box;
         AABB b2 = b1.expandTowards(Frames.toWorld(g, mL.x, (double)step, mL.z));
         if (!below) {
            b2 = b2.expandTowards(Frames.toWorld(g, 0.0, -1.0E-5F, 0.0));
         }

         List<VoxelShape> shapes = EntityAccessor.cu$collectColliders(e, level, ents, b2);

         for (float h : stepHeights(b1, shapes, step, (float)vL.y, g)) {
            Vec3 v1 = collideWithShapes(Frames.toWorld(g, mL.x, (double)h, mL.z), b1, shapes, g);
            Vec3 v1L = Frames.toLocal(g, v1);
            if (v1L.x * v1L.x + v1L.z * v1L.z > vL.x * vL.x + vL.z * vL.z) {
               double d0 = below ? -vL.y : 0.0;
               return Frames.toWorld(g, v1L.x, v1L.y - d0, v1L.z);
            }
         }
      }

      return v;
   }

   private static boolean canFallAtLeast(Player p, Direction g, double dx, double dz, float f) {
      AABB b = p.getBoundingBox().move(Frames.toWorld(g, dx, 0.0, dz));
      AABB probe = Frames.slice(g, b, (double)(f + 1.0E-5F));
      return p.level().noCollision(p, probe);
   }

   private static boolean isAboveGround(Player p, Direction g, float f) {
      return p.onGround() || p.fallDistance < f && !canFallAtLeast(p, g, 0.0, 0.0, f - p.fallDistance);
   }

   public static Vec3 backOffFromEdge(Player p, Vec3 worldMove, boolean selfOrPlayerMove) {
      Direction g = Gravity.of(p);
      Vec3 m = Frames.toLocal(g, worldMove);
      float f = p.maxUpStep();
      if (!p.getAbilities().flying && !(m.y > 0.0) && selfOrPlayerMove && p.isShiftKeyDown() && isAboveGround(p, g, f)) {
         double d0 = m.x;
         double d1 = m.z;
         double d3 = Math.signum(d0) * 0.05;

         double d4;
         for (d4 = Math.signum(d1) * 0.05; d0 != 0.0 && canFallAtLeast(p, g, d0, 0.0, f); d0 -= d3) {
            if (Math.abs(d0) <= 0.05) {
               d0 = 0.0;
               break;
            }
         }

         while (d1 != 0.0 && canFallAtLeast(p, g, 0.0, d1, f)) {
            if (Math.abs(d1) <= 0.05) {
               d1 = 0.0;
               break;
            }

            d1 -= d4;
         }

         while (d0 != 0.0 && d1 != 0.0 && canFallAtLeast(p, g, d0, d1, f)) {
            if (Math.abs(d0) <= 0.05) {
               d0 = 0.0;
            } else {
               d0 -= d3;
            }

            if (Math.abs(d1) <= 0.05) {
               d1 = 0.0;
            } else {
               d1 -= d4;
            }
         }

         return Frames.toWorld(g, d0, m.y, d1);
      } else {
         return worldMove;
      }
   }

   @Nullable
   public static Vec3 steer(LivingEntity le) {
      if (le instanceof Mob mob) {
         Direction g = Gravity.of(le);
         LivingEntity goal = mob.getTarget();
         double stopAt = 1.0 + (double)le.getBbWidth();
         if (goal == null && le instanceof OwnableEntity own && own.getOwner() != null) {
            goal = own.getOwner();
            stopAt = 3.0;
         }

         if (goal != null && goal.isAlive() && goal.level() == le.level() && !(goal.distanceToSqr(le) > 1024.0)) {
            Vec3 d = Frames.toLocal(g, goal.position().subtract(le.position()));
            double hd = Math.sqrt(d.x * d.x + d.z * d.z);
            if (hd > 0.001) {
               float yaw = (float)(Mth.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0F;
               float turned = Mth.approachDegrees(le.getYRot(), yaw, 30.0F);
               le.setYRot(turned);
               le.setYHeadRot(turned);
               le.setYBodyRot(turned);
            }

            Vec3 eye = Frames.toLocal(g, goal.getEyePosition().subtract(le.getEyePosition()));
            le.setXRot(Mth.clamp((float)(-Mth.atan2(eye.y, Math.sqrt(eye.x * eye.x + eye.z * eye.z)) * (180.0 / Math.PI)), -60.0F, 60.0F));
            if (hd < stopAt) {
               return Vec3.ZERO;
            } else {
               le.setSpeed((float)le.getAttributeValue(Attributes.MOVEMENT_SPEED));
               if (le.horizontalCollision && le.onGround()) {
                  mob.getJumpControl().jump();
               }

               return new Vec3(0.0, 0.0, 1.0);
            }
         } else {
            return Vec3.ZERO;
         }
      } else {
         return null;
      }
   }

   public static AABB poseBox(Entity e, float w, float h) {
      return Frames.box(Gravity.of(e), e.position(), (double)w, (double)h);
   }

   public static BlockPos feetBlock(Entity e) {
      return BlockPos.containing(e.position());
   }
}
