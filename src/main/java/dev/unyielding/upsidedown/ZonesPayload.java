package dev.unyielding.upsidedown;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record ZonesPayload(List<Flip.Zone> zones) implements CustomPacketPayload {
   public static final Type<ZonesPayload> TYPE = new Type(Flip.id("zones"));
   public static final StreamCodec<FriendlyByteBuf, ZonesPayload> CODEC = StreamCodec.of((buf, p) -> {
      buf.writeVarInt(p.zones().size());

      for (Flip.Zone z : p.zones()) {
         z.write(buf);
      }
   }, buf -> {
      int n = buf.readVarInt();
      List<Flip.Zone> l = new ArrayList<>(n);

      for (int i = 0; i < n; i++) {
         l.add(Flip.Zone.read(buf));
      }

      return new ZonesPayload(List.copyOf(l));
   });

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
