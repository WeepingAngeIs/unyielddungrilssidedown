package dev.unyielding.upsidedown.dungeon.block;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Indestructible architecture, with bespoke models/textures and no survival drops. */
public final class DungeonBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("cuupsidedown");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("cuupsidedown");
    private static final List<Supplier<? extends Block>> ALL = new ArrayList<>();
    public static final Supplier<Block> SAKURA_WALL = full("sakura_wall",MapColor.COLOR_PINK);
    public static final Supplier<Block> DARK_WALL = full("dark_wall",MapColor.COLOR_BLACK);
    public static final Supplier<Block> ZEN_WALL = full("zen_wall",MapColor.COLOR_GREEN);
    public static final Supplier<Block> MOON_WALL = full("moon_wall",MapColor.COLOR_BLUE);
    public static final Supplier<Block> BLOSSOM_FLOOR = full("blossom_floor",MapColor.TERRACOTTA_PINK);
    public static final Supplier<Block> DARK_FLOOR = full("dark_floor",MapColor.COLOR_BLACK);
    public static final Supplier<ShojiLanternBlock> LANTERN = register("lantern", () -> new ShojiLanternBlock(properties(MapColor.COLOR_YELLOW).lightLevel(s -> 15).noOcclusion()));
    public static final Supplier<StairBlock> TEMPLE_STAIRS = register("temple_stairs", () -> new StairBlock(DARK_FLOOR.get().defaultBlockState(), properties(MapColor.COLOR_BLACK)));
    public static final Supplier<TeaTableBlock> TEA_TABLE = register("tea_table", () -> new TeaTableBlock(properties(MapColor.COLOR_PINK).noOcclusion()));
    public static final Supplier<FlowerPlanterBlock> FLOWER_PLANTER = register("flower_planter", () -> new FlowerPlanterBlock(properties(MapColor.COLOR_PINK).noOcclusion()));
    private DungeonBlocks() {}
    private static BlockBehaviour.Properties properties(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).sound(SoundType.DEEPSLATE_TILES)
                .strength(-1.0F,3600000.0F).pushReaction(PushReaction.BLOCK).noLootTable();
    }
    private static Supplier<Block> full(String name, MapColor color) { return register(name, () -> new Block(properties(color))); }
    private static <T extends Block> Supplier<T> register(String name, Supplier<T> supplier) {
        var block = BLOCKS.register(name, supplier);
        ITEMS.registerSimpleBlockItem(name, block);
        ALL.add(block);
        return block;
    }
    public static void init(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        bus.addListener(BuildCreativeModeTabContentsEvent.class, event -> {
            if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) ALL.forEach(block -> event.accept(block.get()));
        });
    }
}
