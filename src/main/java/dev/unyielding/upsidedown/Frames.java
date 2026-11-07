package dev.unyielding.upsidedown;

import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public final class Frames {
   private static final int[][][] M = new int[6][][];
   private static final Quaternionf[] Q = new Quaternionf[6];
   public static final double SHIFT = 5.0E-8;

   private Frames() {
   }

   public static boolean sideways(Direction g) {
      return g != null && g.getAxis() != Axis.Y;
   }

   public static Vec3 toWorld(Direction g, double x, double y, double z) {
      int[][] m = M[g.ordinal()];
      return new Vec3(
         (double)m[0][0] * x + (double)m[0][1] * y + (double)m[0][2] * z,
         (double)m[1][0] * x + (double)m[1][1] * y + (double)m[1][2] * z,
         (double)m[2][0] * x + (double)m[2][1] * y + (double)m[2][2] * z
      );
   }

   public static Vec3 toWorld(Direction g, Vec3 v) {
      return g == Direction.DOWN ? v : toWorld(g, v.x, v.y, v.z);
   }

   public static Vec3 toLocal(Direction g, double x, double y, double z) {
      int[][] m = M[g.ordinal()];
      return new Vec3(
         (double)m[0][0] * x + (double)m[1][0] * y + (double)m[2][0] * z,
         (double)m[0][1] * x + (double)m[1][1] * y + (double)m[2][1] * z,
         (double)m[0][2] * x + (double)m[1][2] * y + (double)m[2][2] * z
      );
   }

   public static Vec3 toLocal(Direction g, Vec3 v) {
      return g == Direction.DOWN ? v : toLocal(g, v.x, v.y, v.z);
   }

   public static Direction toWorld(Direction g, Direction local) {
      Vec3 v = toWorld(g, (double)local.getStepX(), (double)local.getStepY(), (double)local.getStepZ());
      return Direction.getNearest(v.x, v.y, v.z);
   }

   public static Direction toLocal(Direction g, Direction world) {
      Vec3 v = toLocal(g, (double)world.getStepX(), (double)world.getStepY(), (double)world.getStepZ());
      return Direction.getNearest(v.x, v.y, v.z);
   }

   public static Quaternionf quat(Direction g) {
      return new Quaternionf(Q[g.ordinal()]);
   }

   public static Vec3 up(Direction g) {
      return new Vec3((double)(-g.getStepX()), (double)(-g.getStepY()), (double)(-g.getStepZ()));
   }

   public static AABB box(Direction g, Vec3 feet, double w, double h) {
      double hw = w / 2.0;
      double lo = 0.0;
      double hi = h;
      if (g.getAxisDirection() == AxisDirection.POSITIVE) {
         lo -= 5.0E-8;
         hi = h - 5.0E-8;
      }

      Vec3 a = toWorld(g, -hw, lo, -hw);
      Vec3 b = toWorld(g, hw, hi, hw);
      return new AABB(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z), Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z)).move(feet);
   }

   public static Vec3 feetForCentre(Direction g, Vec3 centre, double h) {
      if (!sideways(g)) {
         return new Vec3(centre.x, centre.y - h / 2.0, centre.z);
      } else {
         Vec3 f = centre.add((double)g.getStepX() * h / 2.0, (double)g.getStepY() * h / 2.0, (double)g.getStepZ() * h / 2.0);
         if (g.getAxisDirection() == AxisDirection.POSITIVE) {
            f = f.add((double)(-g.getStepX()) * 5.0E-8, 0.0, (double)(-g.getStepZ()) * 5.0E-8);
         }

         return f;
      }
   }

   public static double floorFace(Direction g, AABB b) {
      return g.getAxisDirection() == AxisDirection.POSITIVE ? b.max(g.getAxis()) : b.min(g.getAxis());
   }

   public static AABB slice(Direction g, AABB b, double d) {
      Axis a = g.getAxis();
      double f = floorFace(g, b);
      double lo = g.getAxisDirection() == AxisDirection.POSITIVE ? f : f - d;
      double hi = lo + d;

      return switch (a) {
         case X -> new AABB(lo, b.minY, b.minZ, hi, b.maxY, b.maxZ);
         case Y -> new AABB(b.minX, lo, b.minZ, b.maxX, hi, b.maxZ);
         case Z -> new AABB(b.minX, b.minY, lo, b.maxX, b.maxY, hi);
         default -> throw new MatchException(null, null);
      };
   }

   public static float[] anglesOf(Direction g, Vec3 worldDir, float fallbackYaw) {
      Vec3 l = toLocal(g, worldDir).normalize();
      double hz = Math.sqrt(l.x * l.x + l.z * l.z);
      float pitch = (float)(-Math.toDegrees(Math.atan2(l.y, hz)));
      float yaw = hz < 1.0E-4 ? fallbackYaw : (float)Math.toDegrees(Math.atan2(-l.x, l.z));
      return new float[]{yaw, pitch};
   }

   public static Vec3 view(Direction g, float pitch, float yaw) {
      double f = Math.toRadians((double)pitch);
      double f1 = Math.toRadians((double)(-yaw));
      double c = Math.cos(f1);
      double s = Math.sin(f1);
      double cp = Math.cos(f);
      double sp = Math.sin(f);
      return toWorld(g, s * cp, -sp, c * cp);
   }

   public static Quaternionf camera(Direction g, float yaw, float pitch) {
      Quaternionf local = new Quaternionf().rotationYXZ((float) Math.PI - yaw * (float) (Math.PI / 180.0), -pitch * (float) (Math.PI / 180.0), 0.0F);
      return quat(g).mul(local);
   }

   static {
      M[Direction.DOWN.ordinal()] = new int[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
      M[Direction.UP.ordinal()] = new int[][]{{-1, 0, 0}, {0, -1, 0}, {0, 0, 1}};
      M[Direction.EAST.ordinal()] = new int[][]{{0, -1, 0}, {1, 0, 0}, {0, 0, 1}};
      M[Direction.WEST.ordinal()] = new int[][]{{0, 1, 0}, {-1, 0, 0}, {0, 0, 1}};
      M[Direction.SOUTH.ordinal()] = new int[][]{{1, 0, 0}, {0, 0, 1}, {0, -1, 0}};
      M[Direction.NORTH.ordinal()] = new int[][]{{1, 0, 0}, {0, 0, -1}, {0, 1, 0}};
      Q[Direction.DOWN.ordinal()] = new Quaternionf();
      Q[Direction.UP.ordinal()] = new Quaternionf().rotationZ((float) Math.PI);
      Q[Direction.EAST.ordinal()] = new Quaternionf().rotationZ((float) (Math.PI / 2));
      Q[Direction.WEST.ordinal()] = new Quaternionf().rotationZ((float) (-Math.PI / 2));
      Q[Direction.SOUTH.ordinal()] = new Quaternionf().rotationX((float) (-Math.PI / 2));
      Q[Direction.NORTH.ordinal()] = new Quaternionf().rotationX((float) (Math.PI / 2));
   }
}
