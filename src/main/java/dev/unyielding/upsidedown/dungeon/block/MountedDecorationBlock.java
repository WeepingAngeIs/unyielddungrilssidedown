package dev.unyielding.upsidedown.dungeon.block;

import java.util.EnumMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Mount is the outward normal of the attachment surface, NOT gravity direction. */
public abstract class MountedDecorationBlock extends Block {
    public static final EnumProperty<Direction> MOUNT = EnumProperty.create("mount", Direction.class);
    private final EnumMap<Direction,VoxelShape> shapes = new EnumMap<>(Direction.class);
    protected MountedDecorationBlock(Properties properties, double[][] geometry) {
        super(properties);
        for (Direction direction : Direction.values()) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : geometry) {
                double[] r = MountGeometry.box(b, direction.getSerializedName());
                shape = Shapes.or(shape, Block.box(r[0],r[1],r[2],r[3],r[4],r[5]));
            }
            shapes.put(direction, shape.optimize());
        }
        registerDefaultState(stateDefinition.any().setValue(MOUNT, Direction.UP));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(MOUNT); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(MOUNT, context.getClickedFace()); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) { return shapes.get(state.getValue(MOUNT)); }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) { return shapes.get(state.getValue(MOUNT)); }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(MOUNT, rotation.rotate(state.getValue(MOUNT))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.setValue(MOUNT, mirror.mirror(state.getValue(MOUNT))); }
}
