package dev.unyielding.mimic;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType;

final class MimicAwards {
   static final String KILLS = "mimic_kills";

   private MimicAwards() {
   }

   static void ensureObjective(MinecraftServer server) {
      Scoreboard board = server.getScoreboard();
      if (board.getObjective("mimic_kills") == null) {
         board.addObjective("mimic_kills", ObjectiveCriteria.DUMMY, Component.literal("Mimic Kills"), RenderType.INTEGER, true, null);
      }
   }

   static void award(ServerPlayer p, String path) {
      MinecraftServer server = p.getServer();
      if (server != null) {
         AdvancementHolder adv = server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("cumimic", path));
         if (adv != null && !p.getAdvancements().getOrStartProgress(adv).isDone()) {
            AdvancementHolder root = server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("cumimic", "root"));
            if (root != null && !path.equals("root")) {
               p.getAdvancements().award(root, "trigger");
            }

            p.getAdvancements().award(adv, "trigger");
         }
      }
   }

   static void onFloor(ServerPlayer p, int floor, boolean top) {
      award(p, "root");
      if (floor >= 3) {
         award(p, "lights_out");
      }

      if (floor >= 13) {
         award(p, "halfway");
      }

      if (top) {
         award(p, "summit");
      }
   }

   static void onKill(ServerPlayer p, Mob mimic) {
      award(p, "first_blood");
      int kills = addKill(p);
      if (kills >= 25) {
         award(p, "hunter");
      }

      if (kills >= 100) {
         award(p, "exterminator");
      }

      if (p.getUUID().equals(Mimics.sourceOf(mimic))) {
         award(p, "mirror_match");
      }

      if (mimic.getItemBySlot(EquipmentSlot.HEAD).is(Items.NETHERITE_HELMET)
         && mimic.getItemBySlot(EquipmentSlot.CHEST).is(Items.NETHERITE_CHESTPLATE)
         && mimic.getItemBySlot(EquipmentSlot.LEGS).is(Items.NETHERITE_LEGGINGS)
         && mimic.getItemBySlot(EquipmentSlot.FEET).is(Items.NETHERITE_BOOTS)) {
         award(p, "dressed_to_kill");
      }
   }

   private static int addKill(ServerPlayer p) {
      MinecraftServer server = p.getServer();
      if (server == null) {
         return 0;
      } else {
         ensureObjective(server);
         Scoreboard board = server.getScoreboard();
         Objective obj = board.getObjective("mimic_kills");
         return obj == null ? 0 : board.getOrCreatePlayerScore(p, obj).increment();
      }
   }
}
