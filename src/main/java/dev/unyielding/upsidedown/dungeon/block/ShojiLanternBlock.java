package dev.unyielding.upsidedown.dungeon.block;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
public final class ShojiLanternBlock extends Block {
    public static final MapCodec<ShojiLanternBlock> CODEC = simpleCodec(ShojiLanternBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(3,0,3,13,2,13), Block.box(4,2,4,12,14,12), Block.box(2,14,2,14,16,14));
    public ShojiLanternBlock(Properties properties) { super(properties); }
    @Override public MapCodec<ShojiLanternBlock> codec() { return CODEC; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) { return SHAPE; }
}
