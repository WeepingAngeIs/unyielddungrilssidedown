package dev.unyielding.mimic;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

final class MimicFloors {
   private static final MimicFloors.Floor NONE = new MimicFloors.Floor(-1, false, false);
   private static final Map<ResourceKey<Level>, Map<Long, MimicFloors.Floor>> CACHE = new HashMap<>();

   private MimicFloors() {
   }

   static void clear() {
      CACHE.clear();
   }

   static MimicFloors.Floor at(ServerPlayer p) {
      ServerLevel level = p.serverLevel();
      BlockPos pos = p.blockPosition();
      int cx = pos.getX() >> 4;
      int cz = pos.getZ() >> 4;
      MimicFloors.Floor best = null;
      double bestScore = Double.MAX_VALUE;

      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
            if (chunk != null) {
               for (BlockEntity be : chunk.getBlockEntities().values()) {
                  if (be instanceof SpawnerBlockEntity) {
                     SpawnerBlockEntity spawner = (SpawnerBlockEntity)be;
                     BlockPos sp = be.getBlockPos();
                     double flat = Math.sqrt(sp.distSqr(new BlockPos(pos.getX(), sp.getY(), pos.getZ())));
                     if (!(flat > 24.0)) {
                        MimicFloors.Floor f = floorOf(level, spawner);
                        if (f != null) {
                           int up = f.upside() ? sp.getY() + 1 - (int)Math.floor(p.getBoundingBox().maxY + 1.0E-4) : pos.getY() - sp.getY();
                           if (up >= -2 && up <= 15) {
                              double score = flat + (double)Math.abs(up) * 0.5;
                              if (score < bestScore) {
                                 bestScore = score;
                                 best = f;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return best;
   }

   private static MimicFloors.Floor floorOf(ServerLevel level, SpawnerBlockEntity spawner) {
      Map<Long, MimicFloors.Floor> byPos = CACHE.computeIfAbsent(level.dimension(), k -> new HashMap<>());
      long key = spawner.getBlockPos().asLong();
      MimicFloors.Floor f = byPos.get(key);
      if (f == null) {
         f = NONE;
         CompoundTag saved = spawner.getSpawner().save(new CompoundTag());
         ListTag tags = saved.getCompound("SpawnData").getCompound("entity").getList("Tags", 8);
         boolean mimic = false;
         boolean top = false;
         boolean upside = false;
         int number = -1;

         for (int i = 0; i < tags.size(); i++) {
            String t = tags.getString(i);
            mimic |= t.equals("cu_mimic");
            top |= t.equals("cu_top");
            upside |= t.equals("cu_upside");
            if (t.startsWith("cu_floor_")) {
               try {
                  number = Integer.parseInt(t.substring("cu_floor_".length()));
               } catch (NumberFormatException var15) {
                  number = -1;
               }
            }
         }

         if (mimic && number > 0) {
            f = new MimicFloors.Floor(number, top, upside);
         }

         byPos.put(key, f);
      }

      return f == NONE ? null : f;
   }

   static record Floor(int number, boolean top, boolean upside) {
   }
}
