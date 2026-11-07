package dev.unyielding.upsidedown.client;

import dev.unyielding.upsidedown.Config;
import dev.unyielding.upsidedown.Flip;
import dev.unyielding.upsidedown.Frames;
import dev.unyielding.upsidedown.Gravity;
import dev.unyielding.upsidedown.GravityEntity;
import dev.unyielding.upsidedown.GravityPayloads;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Pre;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class ClientFlip {
   public static int inRotations;
   public static boolean screenRolled;
   public static boolean viewHookRan;
   public static boolean exempt;
   private static long clientTicks;
   private static Quaternionf easeFrom;
   private static Vec3 easeEye = Vec3.ZERO;
   private static long easeStart;
   private static int easeLen;
   public static final Quaternionf lastCamera = new Quaternionf();
   public static boolean lastCameraValid;
   public static Quaternionf expected;

   private ClientFlip() {
   }

   public static void init(IEventBus modBus) {
      IEventBus bus = NeoForge.EVENT_BUS;
      bus.addListener(EventPriority.LOWEST, false, ComputeCameraAngles.class, e -> {
         Entity cam = e.getCamera().getEntity();
         if (cam != null && Flip.isFlipped(cam)) {
            e.setRoll(e.getRoll() + 180.0F);
         }
      });
      bus.addListener(MovementInputUpdateEvent.class, e -> {
         if (Flip.isFlipped(e.getEntity()) && viewRolled()) {
            e.getInput().leftImpulse = -e.getInput().leftImpulse;
         }
      });
      bus.addListener(Pre.class, e -> {
         Entity en = e.getEntity();
         if (en.level().isClientSide()) {
            if (en instanceof LocalPlayer lp && lp == Minecraft.getInstance().player) {
               localTick(lp);
               return;
            }

            Gravity.clientTickPassive(en);
         }
      });
      bus.addListener(Post.class, e -> {
         clientTicks++;
         LocalPlayer lp = Minecraft.getInstance().player;
         if (lp != null) {
            String mode = Config.shoulderOff();

            boolean special = switch (mode) {
               case "never" -> false;
               case "sideways" -> Gravity.rotated(lp);
               default -> Gravity.of(lp) != Direction.DOWN;
            };
            ShoulderCompat.tick(special);
         }
      });
      bus.addListener(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre.class, e -> inRotations = 0);
      bus.addListener(LoggingOut.class, e -> {
         Flip.setClient(List.of());
         exempt = false;
         easeFrom = null;
         lastCameraValid = false;
      });
      String test = System.getProperty("cuupsidedown.autotest", "");

      String cls = switch (test) {
         case "true" -> "AutoTest";
         case "stairs" -> "StairTest";
         case "render" -> "RenderTest";
         case "gravity" -> "GravityTest";
         case "doors" -> "DoorWalk";
         case "misc1", "misc2", "duo", "poke", "tour" -> "MiscTest";
         default -> null;
      };
      if (cls != null) {
         try {
            Class.forName("dev.unyielding.upsidedown.client." + cls).getDeclaredMethod("init", IEventBus.class).invoke(null, bus);
         } catch (ReflectiveOperationException var6) {
            throw new IllegalStateException("cuupsidedown test harness missing", var6);
         }
      }
   }

   private static void localTick(LocalPlayer p) {
      GravityEntity ge = (GravityEntity)p;
      ge.cu$setTravelDepth(0);
      if (ge.cu$grace() > 0) {
         ge.cu$setGrace(ge.cu$grace() - 1);
      }

      if (p.tickCount >= 5) {
         Direction cur = Gravity.of(p);
         if (cur != Direction.DOWN || !Flip.CLIENT.isEmpty()) {
            Direction want = Gravity.desired(p, exempt);
            if (want != cur) {
               turn(p, want);
            }
         }
      }
   }

   private static Quaternionf frameCamera(Direction g, float yaw, float pitch) {
      if (Frames.sideways(g)) {
         return Frames.camera(g, yaw, pitch);
      } else {
         float roll = g == Direction.UP ? 180.0F : 0.0F;
         return new Quaternionf()
            .rotationYXZ((float) Math.PI - yaw * (float) (Math.PI / 180.0), -pitch * (float) (Math.PI / 180.0), -roll * (float) (Math.PI / 180.0));
      }
   }

   public static boolean turn(LocalPlayer p, Direction g) {
      Direction from = Gravity.of(p);
      Quaternionf before = lastCameraValid ? new Quaternionf(lastCamera) : frameCamera(from, p.getYRot(), p.getXRot());
      Vec3 eyeBefore = p.getEyePosition();
      if (!Gravity.change(p, g)) {
         return false;
      } else {
         int len = Config.ease();
         if ((Frames.sideways(from) || Frames.sideways(g)) && len > 0) {
            Quaternionf after = frameCamera(g, p.getYRot(), p.getXRot());
            easeFrom = before.mul(new Quaternionf(after).conjugate());
            easeEye = eyeBefore.subtract(p.getEyePosition());
            easeStart = clientTicks;
            easeLen = len;
         } else {
            easeFrom = null;
         }

         PacketDistributor.sendToServer(new GravityPayloads.Change(g, p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot()), new CustomPacketPayload[0]);
         return true;
      }
   }

   private static float easeAmount(float pt) {
      if (easeFrom == null) {
         return 1.0F;
      } else {
         float t = ((float)(clientTicks - easeStart) + pt) / (float)Math.max(1, easeLen);
         if (t >= 1.0F) {
            easeFrom = null;
            return 1.0F;
         } else {
            t = Math.max(0.0F, t);
            return t * t * (3.0F - 2.0F * t);
         }
      }
   }

   public static boolean easing() {
      return easeFrom != null;
   }

   public static void applyEase(Quaternionf rot, float pt) {
      float s = easeAmount(pt);
      if (easeFrom != null && s < 1.0F) {
         Quaternionf d = new Quaternionf(easeFrom).slerp(new Quaternionf(), s);
         rot.premul(d);
      }
   }

   public static Vec3 easeEyeOffset(float pt) {
      float s = easeAmount(pt);
      return easeFrom != null && !(s >= 1.0F) ? easeEye.scale((double)(1.0F - s)) : Vec3.ZERO;
   }

   public static void onSync(GravityPayloads.Sync p) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         if ((p.flags() & 4) != 0) {
            exempt = true;
         }

         if ((p.flags() & 8) != 0) {
            exempt = false;
         }

         Entity e = mc.level.getEntity(p.id());
         if (e != null) {
            if (e == mc.player) {
               if ((p.flags() & 16) != 0) {
                  ((GravityEntity)e).cu$setGrace(60);
               }

               if ((p.flags() & 2) != 0) {
                  Gravity.setRaw(e, p.g());
               } else if ((p.flags() & 1) != 0 && Gravity.of(e) != p.g() && !turn(mc.player, p.g())) {
                  Gravity.setRaw(e, p.g());
                  PacketDistributor.sendToServer(
                     new GravityPayloads.Change(p.g(), e.getX(), e.getY(), e.getZ(), e.getYRot(), e.getXRot()), new CustomPacketPayload[0]
                  );
               }
            } else {
               Gravity.setRaw(e, p.g());
            }
         }
      }
   }

   public static boolean viewRolled() {
      return viewHookRan ? screenRolled : cameraRolled(Minecraft.getInstance().gameRenderer.getMainCamera());
   }

   public static boolean cameraRolled(Camera cam) {
      if (!cam.isInitialized()) {
         return false;
      } else {
         return Math.abs(cam.getXRot()) < 80.0F
            ? cam.rotation().transform(new Vector3f(0.0F, 1.0F, 0.0F)).y() < 0.0F
            : Math.cos(Math.toRadians((double)cam.getRoll())) < 0.0;
      }
   }

   static Minecraft mc() {
      return Minecraft.getInstance();
   }
}
