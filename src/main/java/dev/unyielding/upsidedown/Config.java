package dev.unyielding.upsidedown;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

public final class Config {
   public static final ModConfigSpec SERVER_SPEC;
   public static final ModConfigSpec CLIENT_SPEC;
   public static final BooleanValue SIDEWAYS_ENABLED;
   public static final BooleanValue SIDEWAYS_MOBS;
   public static final BooleanValue SIDEWAYS_ITEMS;
   public static final IntValue FAILSAFE_TICKS;
   public static final IntValue CAMERA_EASE_TICKS;
   public static final ConfigValue<String> SHOULDER_OFF;

   private Config() {
   }

   static boolean get(BooleanValue v, boolean def) {
      try {
         return (Boolean)v.get();
      } catch (RuntimeException var3) {
         return def;
      }
   }

   static int get(IntValue v, int def) {
      try {
         return (Integer)v.get();
      } catch (RuntimeException var3) {
         return def;
      }
   }

   public static boolean sideways() {
      return get(SIDEWAYS_ENABLED, true);
   }

   public static boolean mobs() {
      return get(SIDEWAYS_MOBS, true);
   }

   public static boolean items() {
      return get(SIDEWAYS_ITEMS, true);
   }

   public static int failsafe() {
      return get(FAILSAFE_TICKS, 60);
   }

   public static String shoulderOff() {
      try {
         return (String)SHOULDER_OFF.get();
      } catch (RuntimeException var1) {
         return "all";
      }
   }

   public static int ease() {
      return get(CAMERA_EASE_TICKS, 10);
   }

   static {
      Builder s = new Builder();
      SIDEWAYS_ENABLED = s.comment("Sideways gravity zones (north/south/east/west). Off = they behave like normal gravity.").define("sidewaysEnabled", true);
      SIDEWAYS_MOBS = s.comment("Mobs and Pokemon get sideways gravity too (they walk on the wall, heading straight for their target or owner).")
         .define("sidewaysMobs", true);
      SIDEWAYS_ITEMS = s.comment("Dropped items, XP and thrown things fall sideways in sideways zones.").define("sidewaysItems", true);
      FAILSAFE_TICKS = s.comment("If a player's gravity disagrees with the zones this long (ticks), the server corrects it.")
         .defineInRange("failsafeTicks", 60, 10, 1200);
      SERVER_SPEC = s.build();
      Builder c = new Builder();
      CAMERA_EASE_TICKS = c.comment("How long (ticks, 20 = 1 second) the view takes to turn when your gravity changes. 0 = instant turn.")
         .defineInRange("cameraEaseTicks", 10, 0, 40);
      SHOULDER_OFF = c.comment(
            new String[]{
               "Shoulder Surfing's over-the-shoulder view can't follow special gravity, so it switches to first person while you have it:",
               "all = upside down and sideways (default), sideways = only sideways (upside down keeps the shoulder view), never = don't touch it."
            }
         )
         .define("shoulderSurfingOff", "all", o -> {
            if (o instanceof String str && (str.equals("all") || str.equals("sideways") || str.equals("never"))) {
               return true;
            }

            return false;
         });
      CLIENT_SPEC = c.build();
   }
}
