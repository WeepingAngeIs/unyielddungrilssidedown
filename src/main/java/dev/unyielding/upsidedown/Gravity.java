package dev.unyielding.upsidedown;

import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Gravity {
   private Gravity() {
   }

   public static Direction of(Entity e) {
      Direction g = ((GravityEntity)e).cu$gravity();
      return g == null ? Direction.DOWN : g;
   }

   public static boolean rotated(Entity e) {
      return e instanceof LivingEntity && Frames.sideways(of(e));
   }

   public static boolean localTravel(Entity e) {
      return ((GravityEntity)e).cu$travelDepth() > 0 && rotated(e);
   }

   public static Vec3 centre(Entity e) {
      return e.getBoundingBox().getCenter();
   }

   public static void enterTravel(Entity e) {
      GravityEntity ge = (GravityEntity)e;
      int d = ge.cu$travelDepth();
      if (d > 0) {
         ge.cu$setTravelDepth(d + 1);
      } else if (rotated(e)) {
         e.setDeltaMovement(Frames.toLocal(of(e), e.getDeltaMovement()));
         ge.cu$setTravelDepth(1);
      }
   }

   public static void exitTravel(Entity e) {
      GravityEntity ge = (GravityEntity)e;
      int d = ge.cu$travelDepth();
      if (d == 1) {
         ge.cu$setTravelDepth(0);
         e.setDeltaMovement(Frames.toWorld(of(e), e.getDeltaMovement()));
      } else if (d > 1) {
         ge.cu$setTravelDepth(d - 1);
      }
   }

   public static Vec3 worldLook(Direction g, float pitch, float yaw) {
      return Frames.view(Frames.sideways(g) ? g : Direction.DOWN, pitch, yaw);
   }

   public static float[] anglesFor(Direction g, Vec3 dir, float fallbackYaw) {
      return Frames.anglesOf(Frames.sideways(g) ? g : Direction.DOWN, dir, fallbackYaw);
   }

   public static AABB boxFor(Entity e, Direction g, Vec3 feet, double w, double h) {
      return e instanceof LivingEntity && Frames.sideways(g)
         ? Frames.box(g, feet, w, h)
         : new AABB(feet.x - w / 2.0, feet.y, feet.z - w / 2.0, feet.x + w / 2.0, feet.y + h, feet.z + w / 2.0);
   }

   private static boolean free(Entity e, AABB b) {
      return e.level().noCollision(e, b.deflate(1.0E-7));
   }

   static Vec3 placeFeet(Entity e, Direction from, Direction to) {
      double w = (double)e.getBbWidth();
      double h = (double)e.getBbHeight();
      Vec3 feet = Frames.feetForCentre(to, centre(e), h);
      AABB base = boxFor(e, to, feet, w, h);
      if (free(e, base)) {
         return feet;
      } else {
         Vec3 up = Frames.up(to);
         Vec3 upOld = Frames.up(from);
         Axis third = null;

         for (Axis a : Axis.values()) {
            if (a != to.getAxis() && a != from.getAxis()) {
               third = a;
            }
         }

         Vec3 t = third == null ? null : new Vec3(third == Axis.X ? 1.0 : 0.0, third == Axis.Y ? 1.0 : 0.0, third == Axis.Z ? 1.0 : 0.0);
         Vec3[] dirs = t == null ? new Vec3[]{up, up.reverse(), upOld, upOld.reverse()} : new Vec3[]{up, up.reverse(), upOld, upOld.reverse(), t, t.reverse()};

         for (int i = 1; i <= 24; i++) {
            double s = (double)i * 0.05;

            for (Vec3 d : dirs) {
               if (free(e, base.move(d.scale(s)))) {
                  return feet.add(d.scale(s));
               }
            }
         }

         return null;
      }
   }

   public static boolean change(Entity e, Direction to) {
      GravityEntity ge = (GravityEntity)e;
      Direction from = of(e);
      if (from == to) {
         return true;
      } else {
         boolean turns = e instanceof LivingEntity && (Frames.sideways(from) || Frames.sideways(to));
         if (!turns) {
            ge.cu$setGravityRaw(to);
            persist(e);
            return true;
         } else {
            LivingEntity le = (LivingEntity)e;
            Vec3 feet = placeFeet(e, from, to);
            if (feet == null) {
               return false;
            } else {
               Vec3 look = worldLook(from, e.getXRot(), e.getYRot());
               Vec3 head = worldLook(from, e.getXRot(), le.getYHeadRot());
               ge.cu$setGravityRaw(to);
               e.setPos(feet);
               e.xo = feet.x;
               e.yo = feet.y;
               e.zo = feet.z;
               e.xOld = feet.x;
               e.yOld = feet.y;
               e.zOld = feet.z;
               float[] a = anglesFor(to, look, e.getYRot());
               float[] hh = anglesFor(to, head, le.getYHeadRot());
               e.setYRot(a[0]);
               e.setXRot(a[1]);
               e.yRotO = a[0];
               e.xRotO = a[1];
               le.setYHeadRot(hh[0]);
               le.yHeadRotO = hh[0];
               le.setYBodyRot(hh[0]);
               le.yBodyRotO = hh[0];
               e.resetFallDistance();
               persist(e);
               return true;
            }
         }
      }
   }

   public static void setRaw(Entity e, Direction g) {
      if (of(e) != g) {
         ((GravityEntity)e).cu$setGravityRaw(g);
         e.setPos(e.position());
         persist(e);
      }
   }

   static void persist(Entity e) {
      if (!e.level().isClientSide()) {
         Direction g = of(e);
         if (g == Direction.DOWN) {
            e.getPersistentData().remove("cu_gravity");
         } else {
            e.getPersistentData().putString("cu_gravity", g.getSerializedName());
         }
      }
   }

   static Direction saved(Entity e) {
      String s = e.getPersistentData().getString("cu_gravity");
      Direction d = s.isEmpty() ? null : Direction.byName(s);
      return d == null ? Direction.DOWN : d;
   }

   public static boolean exempt(Entity e) {
      return e.getPersistentData().getCompound("PlayerPersisted").getBoolean("cu_gravity_exempt");
   }

   public static void setExempt(Entity e, boolean on) {
      CompoundTag t = e.getPersistentData().getCompound("PlayerPersisted");
      t.putBoolean("cu_gravity_exempt", on);
      e.getPersistentData().put("PlayerPersisted", t);
   }

   public static Direction desired(Entity e, boolean clientExempt) {
      GravityEntity ge = (GravityEntity)e;
      if (ge.cu$grace() <= 0 && !e.isPassenger() && !e.isSpectator() && !e.noPhysics) {
         if (e instanceof Player p && (clientExempt || exempt(p))) {
            return Direction.DOWN;
         }

         Level level = e.level();
         Vec3 c = centre(e);
         Direction g = Flip.select(level, c.x, c.y, c.z, of(e));
         if (Frames.sideways(g)) {
            if (!Config.sideways()) {
               return Direction.DOWN;
            }

            if (e instanceof LivingEntity) {
               if (!(e instanceof Player) && !Config.mobs()) {
                  return Direction.DOWN;
               }
            } else if (!Config.items()) {
               return Direction.DOWN;
            }
         }

         return g;
      } else {
         return Direction.DOWN;
      }
   }

   static void serverTick(Entity e) {
      GravityEntity ge = (GravityEntity)e;
      if (ge.cu$grace() > 0) {
         ge.cu$setGrace(ge.cu$grace() - 1);
      }

      Direction cur = of(e);
      if (cur != Direction.DOWN || !Flip.SERVER.isEmpty()) {
         Direction want = desired(e, false);
         if (e instanceof ServerPlayer sp) {
            if (want != cur) {
               int m = ge.cu$mismatch() + 1;
               ge.cu$setMismatch(m);
               if (m >= Config.failsafe()) {
                  ge.cu$setMismatch(-Config.failsafe());
                  PacketDistributor.sendToPlayer(sp, new GravityPayloads.Sync(sp.getId(), want, 1), new CustomPacketPayload[0]);
                  UpsideDown.LOG
                     .info(
                        "cuupsidedown: corrected {}'s gravity {} -> {}",
                        new Object[]{sp.getName().getString(), cur.getSerializedName(), want.getSerializedName()}
                     );
               }
            } else if (ge.cu$mismatch() > 0) {
               ge.cu$setMismatch(0);
            } else if (ge.cu$mismatch() < 0) {
               ge.cu$setMismatch(ge.cu$mismatch() + 1);
            }
         } else if (want != cur) {
            if (e instanceof LivingEntity le) {
               if (change(e, want)) {
                  if (Frames.sideways(cur) || Frames.sideways(want)) {
                     le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false, false));
                  }

                  PacketDistributor.sendToPlayersTrackingEntity(e, new GravityPayloads.Sync(e.getId(), want, 2), new CustomPacketPayload[0]);
               }
            } else {
               ge.cu$setGravityRaw(want);
            }
         }
      }
   }

   public static void clientTickPassive(Entity e) {
      if (!(e instanceof LivingEntity)) {
         Direction cur = of(e);
         if (cur != Direction.DOWN || !Flip.CLIENT.isEmpty()) {
            Direction want = desired(e, false);
            if (want != cur) {
               ((GravityEntity)e).cu$setGravityRaw(want);
            }
         }
      }
   }

   static void onChange(ServerPlayer sp, GravityPayloads.Change p) {
      Vec3 pos = new Vec3(p.x(), p.y(), p.z());
      Direction g = p.g();
      Direction cur = of(sp);
      double h = (double)sp.getBbHeight();
      AABB claimed = boxFor(sp, g, pos, (double)sp.getBbWidth(), h);
      Vec3 c = claimed.getCenter();
      boolean ok = pos.distanceToSqr(sp.position()) < 16.0 && sp.isAlive() && !sp.isPassenger();
      if (ok && g != Direction.DOWN) {
         ok = !exempt(sp) && (!Frames.sideways(g) || Config.sideways()) && Flip.zoneNear(sp.level(), c.x, c.y, c.z, g, 1.5);
      }

      if (!ok) {
         Direction want = desired(sp, false);
         PacketDistributor.sendToPlayer(sp, new GravityPayloads.Sync(sp.getId(), want, 1), new CustomPacketPayload[0]);
      } else {
         ((GravityEntity)sp).cu$setGravityRaw(g);
         ((GravityEntity)sp).cu$setMismatch(0);
         sp.absMoveTo(pos.x, pos.y, pos.z, p.yRot(), p.xRot());
         sp.setYHeadRot(p.yRot());
         sp.connection.resetPosition();
         sp.resetFallDistance();
         if (Frames.sideways(cur) || Frames.sideways(g)) {
            sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false, false));
         }

         persist(sp);
         PacketDistributor.sendToPlayersTrackingEntity(sp, new GravityPayloads.Sync(sp.getId(), g, 2), new CustomPacketPayload[0]);
      }
   }

   static void sendState(ServerPlayer sp, int extraFlags) {
      PacketDistributor.sendToPlayer(sp, new GravityPayloads.Sync(sp.getId(), of(sp), 2 | extraFlags), new CustomPacketPayload[0]);
      PacketDistributor.sendToPlayersTrackingEntity(sp, new GravityPayloads.Sync(sp.getId(), of(sp), 2), new CustomPacketPayload[0]);
   }
}
