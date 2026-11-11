package dev.unyielding.upsidedown.mixin;

import dev.unyielding.upsidedown.UpsideDown;
import dev.unyielding.upsidedown.dungeon.CommandDungeonStructure;
import dev.unyielding.upsidedown.dungeon.DungeonService;
import dev.unyielding.upsidedown.dungeon.DungeonTemplate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.commands.PlaceCommand;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlaceCommand.class)
public abstract class DungeonPlaceCommandMixin {
    @Inject(method = "placeStructure", at = @At("HEAD"), cancellable = true)
    private static void cu$placeDungeon(CommandSourceStack source, Holder.Reference<Structure> structure,
                                        BlockPos anchor, CallbackInfoReturnable<Integer> result) {
        ResourceLocation id = structure.key().location();
        if (structure.value() instanceof CommandDungeonStructure && UpsideDown.MODID.equals(id.getNamespace())
                && DungeonTemplate.IDS.contains(id.getPath())) {
            result.setReturnValue(DungeonService.placeDirect(source, id.getPath(), anchor));
        }
    }
}
