package dev.unyielding.upsidedown;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class Flip {
   public static final double KEEP_BONUS = 0.3;
   private static volatile Flip.Index serverIndex = new Flip.Index(List.of());
   private static volatile Flip.Index clientIndex = new Flip.Index(List.of());
   public static volatile List<Flip.Zone> SERVER = List.of();
   public static volatile List<Flip.Zone> CLIENT = List.of();

   private Flip() {
   }

   public static void setServer(List<Flip.Zone> zones) {
      serverIndex = new Flip.Index(zones);
      SERVER = serverIndex.all;
   }

   public static void setClient(List<Flip.Zone> zones) {
      clientIndex = new Flip.Index(zones);
      CLIENT = clientIndex.all;
   }

   private static Flip.Index index(Level level) {
      return level.isClientSide() ? clientIndex : serverIndex;
   }

   public static Flip.Zone zoneAt(Level level, double x, double y, double z, Direction current) {
      Flip.Index idx = index(level);
      if (idx.all.isEmpty()) {
         return null;
      } else {
         List<Flip.Zone> c = new ArrayList<>(2);
         idx.candidates(level.dimension(), x, y, z, c);
         if (c.isEmpty()) {
            return null;
         } else if (c.size() == 1) {
            return c.get(0);
         } else {
            Flip.Zone best = null;
            double bestD = Double.MAX_VALUE;

            for (Flip.Zone zone : c) {
               double d = zone.floorDistance(x, y, z);
               if (zone.gravity() == current) {
                  d -= 0.3;
               }

               if (d < bestD) {
                  bestD = d;
                  best = zone;
               }
            }

            return best;
         }
      }
   }

   public static Direction select(Level level, double x, double y, double z, Direction current) {
      Flip.Zone zone = zoneAt(level, x, y, z, current);
      return zone == null ? Direction.DOWN : zone.gravity();
   }

   public static boolean isFlipped(Entity e) {
      return Gravity.of(e) == Direction.UP;
   }

   public static Flip.Zone zoneOf(Entity e) {
      Level level = e.level();
      if (level == null) {
         return null;
      } else {
         AABB b = e.getBoundingBox();
         return zoneAt(level, (b.minX + b.maxX) * 0.5, (b.minY + b.maxY) * 0.5, (b.minZ + b.maxZ) * 0.5, Gravity.of(e));
      }
   }

   public static double roofOf(Entity e) {
      if (!isFlipped(e)) {
         return Double.NaN;
      } else {
         Flip.Zone zone = zoneOf(e);
         if (zone != null && zone.gravity() == Direction.UP) {
            double top = zone.box().maxY;
            AABB b = e.getBoundingBox();
            double x = (b.minX + b.maxX) * 0.5;
            double z = (b.minZ + b.maxZ) * 0.5;
            List<Flip.Zone> c = new ArrayList<>(2);
            index(e.level()).candidates(zone.dim(), x, top + 0.01, z, c);

            for (Flip.Zone other : c) {
               if (other != zone) {
                  return Double.NaN;
               }
            }

            return top;
         } else {
            return Double.NaN;
         }
      }
   }

   public static boolean inZone(Level level, BlockPos pos) {
      Flip.Index idx = index(level);
      if (idx.all.isEmpty()) {
         return false;
      } else {
         List<Flip.Zone> c = new ArrayList<>(2);
         idx.candidates(level.dimension(), (double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5, c);
         return !c.isEmpty();
      }
   }

   public static boolean zoneNear(Level level, double x, double y, double z, Direction g, double margin) {
      for (Flip.Zone zone : (level.isClientSide() ? clientIndex : serverIndex).all) {
         if (zone.gravity() == g && zone.dim().equals(level.dimension()) && zone.box().inflate(margin).contains(x, y, z)) {
            return true;
         }
      }

      return false;
   }

   static ResourceLocation id(String path) {
      return ResourceLocation.fromNamespaceAndPath("cuupsidedown", path);
   }

   static final class Index {
      final List<Flip.Zone> all;
      final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<List<Flip.Zone>>> sections = new HashMap<>();
      final Map<ResourceKey<Level>, List<Flip.Zone>> big = new HashMap<>();

      Index(List<Flip.Zone> zones) {
         this.all = List.copyOf(zones);

         for (Flip.Zone z : this.all) {
            AABB b = z.box();
            int x0 = SectionPos.blockToSectionCoord(Math.floor(b.minX));
            int x1 = SectionPos.blockToSectionCoord(Math.ceil(b.maxX) - 1.0);
            int y0 = SectionPos.blockToSectionCoord(Math.floor(b.minY));
            int y1 = SectionPos.blockToSectionCoord(Math.ceil(b.maxY) - 1.0);
            int z0 = SectionPos.blockToSectionCoord(Math.floor(b.minZ));
            int z1 = SectionPos.blockToSectionCoord(Math.ceil(b.maxZ) - 1.0);
            long count = (long)(x1 - x0 + 1) * (long)(y1 - y0 + 1) * (long)(z1 - z0 + 1);
            if (count > 4096L) {
               this.big.computeIfAbsent(z.dim(), k -> new ArrayList<>()).add(z);
            } else {
               Long2ObjectOpenHashMap<List<Flip.Zone>> m = this.sections.computeIfAbsent(z.dim(), k -> new Long2ObjectOpenHashMap());

               for (int sx = x0; sx <= x1; sx++) {
                  for (int sy = y0; sy <= y1; sy++) {
                     for (int sz = z0; sz <= z1; sz++) {
                        ((List)m.computeIfAbsent(SectionPos.asLong(sx, sy, sz), k -> new ArrayList(2))).add(z);
                     }
                  }
               }
            }
         }
      }

      void candidates(ResourceKey<Level> dim, double x, double y, double z, List<Flip.Zone> out) {
         Long2ObjectOpenHashMap<List<Flip.Zone>> m = this.sections.get(dim);
         if (m != null) {
            List<Flip.Zone> l = (List<Flip.Zone>)m.get(
               SectionPos.asLong(
                  SectionPos.blockToSectionCoord(Math.floor(x)), SectionPos.blockToSectionCoord(Math.floor(y)), SectionPos.blockToSectionCoord(Math.floor(z))
               )
            );
            if (l != null) {
               for (Flip.Zone zone : l) {
                  if (zone.box().contains(x, y, z)) {
                     out.add(zone);
                  }
               }
            }
         }

         List<Flip.Zone> b = this.big.get(dim);
         if (b != null) {
            for (Flip.Zone zonex : b) {
               if (zonex.box().contains(x, y, z)) {
                  out.add(zonex);
               }
            }
         }
      }
   }

   public static record Zone(String name, ResourceKey<Level> dim, AABB box, Direction gravity) {
      public Zone(String name, ResourceKey<Level> dim, AABB box) {
         this(name, dim, box, Direction.UP);
      }

      void write(FriendlyByteBuf buf) {
         buf.writeUtf(this.name);
         buf.writeResourceLocation(this.dim.location());
         buf.writeDouble(this.box.minX);
         buf.writeDouble(this.box.minY);
         buf.writeDouble(this.box.minZ);
         buf.writeDouble(this.box.maxX);
         buf.writeDouble(this.box.maxY);
         buf.writeDouble(this.box.maxZ);
         buf.writeByte(this.gravity.get3DDataValue());
      }

      static Flip.Zone read(FriendlyByteBuf buf) {
         String n = buf.readUtf();
         ResourceKey<Level> d = ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation());
         AABB b = new AABB(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
         return new Flip.Zone(n, d, b, Direction.from3DDataValue(buf.readByte()));
      }

      double floorDistance(double x, double y, double z) {
         return switch (this.gravity) {
            case DOWN -> y - this.box.minY;
            case UP -> this.box.maxY - y;
            case EAST -> this.box.maxX - x;
            case WEST -> x - this.box.minX;
            case SOUTH -> this.box.maxZ - z;
            case NORTH -> z - this.box.minZ;
            default -> throw new MatchException(null, null);
         };
      }
   }
}
