package dev.unyielding.mimic;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod("cumimic")
public final class MimicMod {
   public static final String MODID = "cumimic";
   public static final Logger LOG = LogUtils.getLogger();

   public MimicMod(IEventBus modBus) {
      Mimics.register(NeoForge.EVENT_BUS);
   }
}
