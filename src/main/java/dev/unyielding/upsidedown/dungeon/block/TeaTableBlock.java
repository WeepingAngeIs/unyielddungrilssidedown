package dev.unyielding.upsidedown.dungeon.block;
import com.mojang.serialization.MapCodec;
public final class TeaTableBlock extends MountedDecorationBlock {
    public static final MapCodec<TeaTableBlock> CODEC = simpleCodec(TeaTableBlock::new);
    public TeaTableBlock(Properties properties) { super(properties, DungeonGeometry.TEA_TABLE); }
    @Override public MapCodec<TeaTableBlock> codec() { return CODEC; }
}
