package dev.unyielding.upsidedown;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import net.minecraft.world.phys.AABB;

public final class ZoneData extends SavedData {
   final List<Flip.Zone> zones = new ArrayList<>();
   final List<ZoneData.Anchor> anchors = new ArrayList<>();

   static ZoneData get(MinecraftServer server) {
      return (ZoneData)server.overworld().getDataStorage().computeIfAbsent(new Factory<ZoneData>(ZoneData::new, ZoneData::load, null), "cuupsidedown_zones");
   }

   static ZoneData load(CompoundTag tag, Provider reg) {
      ZoneData d = new ZoneData();
      ListTag l = tag.getList("zones", 10);

      for (int i = 0; i < l.size(); i++) {
         CompoundTag z = l.getCompound(i);
         Direction g = z.contains("gravity") ? Direction.byName(z.getString("gravity")) : Direction.UP;
         d.zones
            .add(
               new Flip.Zone(
                  z.getString("name"),
                  ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(z.getString("dim"))),
                  new AABB(z.getDouble("x0"), z.getDouble("y0"), z.getDouble("z0"), z.getDouble("x1"), z.getDouble("y1"), z.getDouble("z1")),
                  g == null ? Direction.UP : g
               )
            );
      }

      ListTag a = tag.getList("anchors", 10);

      for (int i = 0; i < a.size(); i++) {
         CompoundTag c = a.getCompound(i);
         d.anchors
            .add(
               new ZoneData.Anchor(
                  c.getString("name"),
                  ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(c.getString("dim"))),
                  new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z"))
               )
            );
      }

      return d;
   }

   public CompoundTag save(CompoundTag tag, Provider reg) {
      ListTag l = new ListTag();

      for (Flip.Zone z : this.zones) {
         CompoundTag c = new CompoundTag();
         c.putString("name", z.name());
         c.putString("dim", z.dim().location().toString());
         c.putDouble("x0", z.box().minX);
         c.putDouble("y0", z.box().minY);
         c.putDouble("z0", z.box().minZ);
         c.putDouble("x1", z.box().maxX);
         c.putDouble("y1", z.box().maxY);
         c.putDouble("z1", z.box().maxZ);
         c.putString("gravity", z.gravity().getSerializedName());
         l.add(c);
      }

      tag.put("zones", l);
      ListTag a = new ListTag();

      for (ZoneData.Anchor an : this.anchors) {
         CompoundTag c = new CompoundTag();
         c.putString("name", an.name());
         c.putString("dim", an.dim().location().toString());
         c.putInt("x", an.entrance().getX());
         c.putInt("y", an.entrance().getY());
         c.putInt("z", an.entrance().getZ());
         a.add(c);
      }

      tag.put("anchors", a);
      return tag;
   }

   public static record Anchor(String name, ResourceKey<Level> dim, BlockPos entrance) {
   }
}
