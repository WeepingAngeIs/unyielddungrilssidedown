package dev.unyielding.mimic;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

final class MimicCommands {
   private MimicCommands() {
   }

   static void register(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("mimic").requires(s -> s.hasPermission(2)))
            .then(
               ((LiteralArgumentBuilder)Commands.literal("summon").executes(c -> summon(c, 0)))
                  .then(Commands.argument("floor", IntegerArgumentType.integer(1, 99)).executes(c -> summon(c, IntegerArgumentType.getInteger(c, "floor"))))
            )
      );
   }

   private static int summon(CommandContext<CommandSourceStack> c, int floor) {
      CommandSourceStack src = (CommandSourceStack)c.getSource();
      ServerLevel level = src.getLevel();
      Vec3 at = src.getPosition();
      Entity e = EntityType.loadEntityRecursive(Mimics.entityTag(floor, false), level, x -> {
         x.moveTo(at.x, at.y, at.z, x.getYRot(), x.getXRot());
         return x;
      });
      if (e != null && level.tryAddFreshEntityWithPassengers(e)) {
         src.sendSuccess(() -> Component.literal("A Mimic appeared."), true);
         return 1;
      } else {
         src.sendFailure(Component.literal("Could not summon a Mimic here."));
         return 0;
      }
   }
}
