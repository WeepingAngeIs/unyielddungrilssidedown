package dev.unyielding.upsidedown.dungeon;

import com.mojang.serialization.MapCodec;
import dev.unyielding.upsidedown.UpsideDown;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registry entries for authored dungeons placed through the vanilla structure command. */
public final class CommandDungeonStructure extends Structure {
    private static final MapCodec<CommandDungeonStructure> CODEC = simpleCodec(CommandDungeonStructure::new);
    private static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, UpsideDown.MODID);
    private static final Supplier<StructureType<CommandDungeonStructure>> TYPE =
        TYPES.register("command_dungeon", () -> () -> CODEC);

    private CommandDungeonStructure(StructureSettings settings) {
        super(settings);
    }

    static void register(IEventBus bus) {
        TYPES.register(bus);
    }

    @Override
    public StructureType<?> type() {
        return TYPE.get();
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        // Authored geometry, loot and zones are installed together by DungeonService.
        return Optional.empty();
    }
}
