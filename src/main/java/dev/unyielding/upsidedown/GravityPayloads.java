package dev.unyielding.upsidedown;

import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public final class GravityPayloads {
   private GravityPayloads() {
   }

   public static record Change(Direction g, double x, double y, double z, float yRot, float xRot) implements CustomPacketPayload {
      public static final Type<GravityPayloads.Change> TYPE = new Type(Flip.id("gravity_change"));
      public static final StreamCodec<FriendlyByteBuf, GravityPayloads.Change> CODEC = StreamCodec.of(
         (buf, p) -> {
            buf.writeByte(p.g().get3DDataValue());
            buf.writeDouble(p.x());
            buf.writeDouble(p.y());
            buf.writeDouble(p.z());
            buf.writeFloat(p.yRot());
            buf.writeFloat(p.xRot());
         },
         buf -> new GravityPayloads.Change(
               Direction.from3DDataValue(buf.readByte()), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat()
            )
      );

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public static record Sync(int id, Direction g, int flags) implements CustomPacketPayload {
      public static final int FORCE = 1;
      public static final int RAW = 2;
      public static final int EXEMPT_ON = 4;
      public static final int EXEMPT_OFF = 8;
      public static final int GRACE = 16;
      public static final Type<GravityPayloads.Sync> TYPE = new Type(Flip.id("gravity_sync"));
      public static final StreamCodec<FriendlyByteBuf, GravityPayloads.Sync> CODEC = StreamCodec.of((buf, p) -> {
         buf.writeVarInt(p.id());
         buf.writeByte(p.g().get3DDataValue());
         buf.writeVarInt(p.flags());
      }, buf -> new GravityPayloads.Sync(buf.readVarInt(), Direction.from3DDataValue(buf.readByte()), buf.readVarInt()));

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
