package dev.unyielding.mimic;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry;
import net.minecraft.network.syncher.SynchedEntityData.DataValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

final class MimicNet {
   static final String HANDLER = "cumimic_disguise";
   private static final int LAST_SHARED_DATA_ID = 14;

   private MimicNet() {
   }

   static void install(ServerPlayer player) {
      try {
         Connection connection = player.connection == null ? null : player.connection.getConnection();
         Channel channel = connection == null ? null : connection.channel();
         if (channel == null) {
            return;
         }

         channel.eventLoop().execute(() -> {
            ChannelPipeline pipeline = channel.pipeline();
            if (pipeline.get("cumimic_disguise") == null) {
               if (pipeline.get("packet_handler") != null) {
                  pipeline.addBefore("packet_handler", "cumimic_disguise", new MimicNet.Handler());
               } else {
                  pipeline.addLast("cumimic_disguise", new MimicNet.Handler());
               }
            }
         });
      } catch (Throwable var3) {
         MimicMod.LOG.warn("Mimic disguises are off for {}: {}", player.getGameProfile().getName(), var3.toString());
      }
   }

   static ClientboundPlayerInfoUpdatePacket info(Mimics.Disguise d) {
      ClientboundPlayerInfoUpdatePacket info = new ClientboundPlayerInfoUpdatePacket(EnumSet.of(Action.ADD_PLAYER), List.of());
      info.entries = List.of(new Entry(d.fakeId(), d.profile(), false, 0, GameType.SURVIVAL, null, null));
      return info;
   }

   static final class Handler extends ChannelOutboundHandlerAdapter {
      private final Int2ObjectMap<UUID> shown = new Int2ObjectOpenHashMap();

      public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
         List<Packet<? super ClientGamePacketListener>> r = null;
         Object out = msg;

         try {
            if (msg instanceof ClientboundBundlePacket bundle) {
               out = this.rewriteBundle(bundle);
            } else if (msg instanceof Packet<?> packet) {
               r = this.rewrite(packet);
            }
         } catch (Throwable var8) {
            MimicMod.LOG.warn("Mimic packet rewrite failed, sending the original", var8);
            r = null;
            out = msg;
         }

         if (r == null) {
            super.write(ctx, out, promise);
         } else if (r.isEmpty()) {
            promise.trySuccess();
         } else {
            for (int i = 0; i < r.size() - 1; i++) {
               ctx.write(r.get(i), ctx.voidPromise());
            }

            super.write(ctx, r.get(r.size() - 1), promise);
         }
      }

      private Object rewriteBundle(ClientboundBundlePacket bundle) {
         List<Packet<? super ClientGamePacketListener>> out = null;
         List<Packet<? super ClientGamePacketListener>> seen = new ArrayList<>();

         for (Packet<? super ClientGamePacketListener> p : bundle.subPackets()) {
            List<Packet<? super ClientGamePacketListener>> r = this.rewrite(p);
            if (r != null && out == null) {
               out = new ArrayList<>(seen);
            }

            if (out != null) {
               if (r == null) {
                  out.add(p);
               } else {
                  out.addAll(r);
               }
            } else {
               seen.add(p);
            }
         }

         return out == null ? bundle : new ClientboundBundlePacket(out);
      }

      private List<Packet<? super ClientGamePacketListener>> rewrite(Packet<?> p) {
         if (p instanceof ClientboundAddEntityPacket add) {
            Mimics.Disguise d = Mimics.BY_ID.get(add.getId());
            if (d == null) {
               return null;
            } else {
               this.shown.put(add.getId(), d.fakeId());
               return List.of(
                  MimicNet.info(d),
                  new ClientboundAddEntityPacket(
                     add.getId(),
                     d.fakeId(),
                     add.getX(),
                     add.getY(),
                     add.getZ(),
                     add.getXRot(),
                     add.getYRot(),
                     EntityType.PLAYER,
                     0,
                     new Vec3(add.getXa(), add.getYa(), add.getZa()),
                     (double)add.getYHeadRot()
                  ),
                  new ClientboundSetEntityDataPacket(add.getId(), List.of(DataValue.create(Player.DATA_PLAYER_MODE_CUSTOMISATION, (byte)127)))
               );
            }
         } else if (this.shown.isEmpty()) {
            return null;
         } else if (p instanceof ClientboundSetEntityDataPacket data) {
            if (!this.shown.containsKey(data.id())) {
               return null;
            } else {
               List<DataValue<?>> keep = new ArrayList<>();

               for (DataValue<?> v : data.packedItems()) {
                  if (v.id() <= 14) {
                     keep.add(v);
                  }
               }

               if (keep.size() == data.packedItems().size()) {
                  return null;
               } else {
                  return keep.isEmpty() ? List.of() : List.of(new ClientboundSetEntityDataPacket(data.id(), keep));
               }
            }
         } else if (p instanceof ClientboundUpdateAttributesPacket attrs) {
            return this.shown.containsKey(attrs.getEntityId()) ? List.of() : null;
         } else if (p instanceof ClientboundRemoveEntitiesPacket remove) {
            List<UUID> gone = null;
            IntListIterator var4 = remove.getEntityIds().iterator();

            while (var4.hasNext()) {
               int id = (Integer)var4.next();
               UUID fake = (UUID)this.shown.remove(id);
               if (fake != null) {
                  if (gone == null) {
                     gone = new ArrayList<>();
                  }

                  gone.add(fake);
               }
            }

            return gone == null ? null : List.of(remove, new ClientboundPlayerInfoRemovePacket(gone));
         } else if (!(p instanceof ClientboundRespawnPacket) && !(p instanceof ClientboundLoginPacket)) {
            return null;
         } else {
            List<UUID> all = new ArrayList<>(this.shown.values());
            this.shown.clear();
            return List.of(new ClientboundPlayerInfoRemovePacket(all), (Packet<? super ClientGamePacketListener>)p);
         }
      }
   }
}
