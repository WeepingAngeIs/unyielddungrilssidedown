package dev.unyielding.upsidedown.dungeon.block;
import com.mojang.serialization.MapCodec;
public final class FlowerPlanterBlock extends MountedDecorationBlock {
    public static final MapCodec<FlowerPlanterBlock> CODEC = simpleCodec(FlowerPlanterBlock::new);
    public FlowerPlanterBlock(Properties properties) { super(properties, DungeonGeometry.FLOWER_PLANTER); }
    @Override public MapCodec<FlowerPlanterBlock> codec() { return CODEC; }
}
