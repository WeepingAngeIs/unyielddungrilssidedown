package dev.unyielding.upsidedown;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Rotation;

public final class Layouts extends SimpleJsonResourceReloadListener {
   private static final Gson GSON = new GsonBuilder().create();
   public static volatile Map<ResourceLocation, Layouts.Layout> LAYOUTS = Map.of();

   public Layouts() {
      super(GSON, "gravity_layouts");
   }

   private static BlockPos pos(JsonArray a) {
      return new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
   }

   protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
      Map<ResourceLocation, Layouts.Layout> out = new HashMap<>();

      for (Entry<ResourceLocation, JsonElement> en : files.entrySet()) {
         try {
            JsonObject o = en.getValue().getAsJsonObject();
            List<Layouts.ZoneDef> zones = new ArrayList<>();

            for (JsonElement z : o.getAsJsonArray("zones")) {
               JsonObject zo = z.getAsJsonObject();
               Direction g = Direction.byName(zo.has("gravity") ? zo.get("gravity").getAsString() : "up");
               if (g == null) {
                  throw new IllegalArgumentException("bad gravity in zone " + zo);
               }

               zones.add(new Layouts.ZoneDef(zo.get("name").getAsString(), pos(zo.getAsJsonArray("from")), pos(zo.getAsJsonArray("to")), g));
            }

            BlockPos entrance = o.has("entrance") ? pos(o.getAsJsonArray("entrance")) : BlockPos.ZERO;
            String desc = o.has("description") ? o.get("description").getAsString() : "";
            Direction mf = null;
            if (o.has("marker") && o.getAsJsonObject("marker").has("facing")) {
               mf = Direction.byName(o.getAsJsonObject("marker").get("facing").getAsString());
            }

            out.put(en.getKey(), new Layouts.Layout(en.getKey(), desc, entrance, List.copyOf(zones), mf));
         } catch (RuntimeException var13) {
            UpsideDown.LOG.error("cuupsidedown: gravity layout {} is broken: {}", en.getKey(), var13.toString());
         }
      }

      LAYOUTS = Map.copyOf(out);
      UpsideDown.LOG.info("cuupsidedown: {} gravity layout(s) loaded", out.size());
   }

   public static BlockPos turn(BlockPos p, Rotation r) {
      return p.rotate(r);
   }

   public static Direction turn(Direction d, Rotation r) {
      return d.getAxis() == Axis.Y ? d : r.rotate(d);
   }

   public static record Layout(ResourceLocation id, String description, BlockPos entrance, List<Layouts.ZoneDef> zones, Direction markerFacing) {
   }

   public static record ZoneDef(String name, BlockPos from, BlockPos to, Direction gravity) {
   }
}
