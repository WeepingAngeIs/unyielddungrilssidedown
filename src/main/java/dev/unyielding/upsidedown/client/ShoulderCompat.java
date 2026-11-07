package dev.unyielding.upsidedown.client;

import java.lang.reflect.Method;
import net.neoforged.fml.ModList;

final class ShoulderCompat {
   private static boolean tried;
   private static Object api;
   private static Method isShoulder;
   private static Method change;
   private static Object first;
   private static Object shoulder;
   private static boolean swapped;

   private ShoulderCompat() {
   }

   private static boolean load() {
      if (tried) {
         return api != null;
      } else {
         tried = true;
         if (!ModList.get().isLoaded("shouldersurfing")) {
            return false;
         } else {
            try {
               Class<?> ss = Class.forName("com.github.exopandora.shouldersurfing.api.client.ShoulderSurfing");
               api = ss.getMethod("getInstance").invoke(null);
               Class<?> iface = Class.forName("com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing");
               Class<?> persp = Class.forName("com.github.exopandora.shouldersurfing.api.client.Perspective");
               isShoulder = iface.getMethod("isShoulderSurfing");
               change = iface.getMethod("changePerspective", persp);
               first = persp.getMethod("valueOf", String.class).invoke(null, "FIRST_PERSON");
               shoulder = persp.getMethod("valueOf", String.class).invoke(null, "SHOULDER_SURFING");
               return true;
            } catch (RuntimeException | LinkageError | ReflectiveOperationException var3) {
               api = null;
               return false;
            }
         }
      }
   }

   static void tick(boolean sideways) {
      if (load()) {
         try {
            boolean on = (Boolean)isShoulder.invoke(api);
            if (sideways && on) {
               change.invoke(api, first);
               swapped = true;
            } else if (!sideways && swapped) {
               swapped = false;
               if (!on) {
                  change.invoke(api, shoulder);
               }
            }
         } catch (RuntimeException | ReflectiveOperationException var2) {
            api = null;
         }
      }
   }
}
