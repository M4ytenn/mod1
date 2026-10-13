package io.github.m4ytenn.hermodoors.door;

import io.github.m4ytenn.hermodoors.registry.ModBlockEntities;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * One part of a multiblock door. Every part carries its column/row inside the door; the bottom-left part
 * (as seen from the front) is the controller and owns the block entity. The leaf itself is drawn by the
 * block entity renderer, the part models only contain the steel frame.
 */
public abstract class HermoDoorBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    private final DoorType type;
    private final Map<BlockState, VoxelShape> shapes = new IdentityHashMap<>();

    protected HermoDoorBlock(DoorType type, Properties properties) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
        for (BlockState state : stateDefinition.getPossibleStates()) {
            shapes.put(state, DoorGeometry.partShape(type, state.getValue(FACING), px(state), py(state), state.getValue(OPEN)));
        }
    }

    /** Column property; each subclass declares one sized to its width. */
    protected abstract IntegerProperty partX();

    /** Row property; each subclass declares one sized to its height. */
    protected abstract IntegerProperty partY();

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, partX(), partY());
    }

    public DoorType type() {
        return type;
    }

    public int px(BlockState state) {
        return state.getValue(partX());
    }

    public int py(BlockState state) {
        return state.getValue(partY());
    }

    public boolean isController(BlockState state) {
        return px(state) == 0 && py(state) == 0;
    }

    public BlockState partState(Direction facing, int px, int py, boolean open) {
        return defaultBlockState().setValue(FACING, facing).setValue(partX(), px).setValue(partY(), py).setValue(OPEN, open);
    }

    /** Direction that points to the viewer's right when they stand in front of a door facing {@code facing}. */
    public static Direction rightOf(Direction facing) {
        return facing.getCounterClockWise();
    }

    public BlockPos originOf(BlockState state, BlockPos pos) {
        return pos.relative(rightOf(state.getValue(FACING)), -px(state)).below(py(state));
    }

    public static BlockPos partPos(BlockPos origin, Direction facing, int px, int py) {
        return origin.relative(rightOf(facing), px).above(py);
    }

    /** True if {@code state} is the part that belongs at column/row {@code px}/{@code py} of a door facing {@code facing}. */
    public boolean isPart(BlockState state, Direction facing, int px, int py) {
        return state.is(this) && state.getValue(FACING) == facing && px(state) == px && py(state) == py;
    }

    @Nullable
    public static HermoDoorBlockEntity findDoor(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof HermoDoorBlock door) {
            BlockEntity be = level.getBlockEntity(door.originOf(state, pos));
            if (be instanceof HermoDoorBlockEntity doorEntity) {
                return doorEntity;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- placement & removal

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        int clickedPx = (type.width - 1) / 2;
        BlockPos origin = ctx.getClickedPos().relative(rightOf(facing), -clickedPx);
        for (int px = 0; px < type.width; px++) {
            for (int py = 0; py < type.height; py++) {
                BlockPos p = partPos(origin, facing, px, py);
                if (!level.isInWorldBounds(p) || !level.getWorldBorder().isWithinBounds(p)) {
                    return null;
                }
                if (!level.getBlockState(p).canBeReplaced(ctx)) {
                    return null;
                }
                if (!level.isUnobstructed(partState(facing, px, py, false), p, CollisionContext.empty())) {
                    return null;
                }
            }
        }
        return partState(facing, clickedPx, 0, false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        Direction facing = state.getValue(FACING);
        BlockPos origin = originOf(state, pos);
        for (int px = 0; px < type.width; px++) {
            for (int py = 0; py < type.height; py++) {
                BlockPos p = partPos(origin, facing, px, py);
                if (!p.equals(pos)) {
                    level.setBlock(p, partState(facing, px, py, false), Block.UPDATE_ALL);
                }
            }
        }
        if (level.getBlockEntity(origin) instanceof HermoDoorBlockEntity door) {
            door.initPlaced(placer);
            if (placer instanceof ServerPlayer player && !player.isFakePlayer()) {
                door.openPassport(player, true);
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            // Take the rest of the door with this part, without drops (the door item comes back via the passport).
            Direction facing = state.getValue(FACING);
            BlockPos origin = originOf(state, pos);
            for (int px = 0; px < type.width; px++) {
                for (int py = 0; py < type.height; py++) {
                    BlockPos p = partPos(origin, facing, px, py);
                    if (!p.equals(pos) && isPart(level.getBlockState(p), facing, px, py)) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // ---------------------------------------------------------------- interaction

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        HermoDoorBlockEntity door = findDoor(level, pos);
        if (door == null || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        door.use(serverPlayer);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide) {
            HermoDoorBlockEntity door = findDoor(level, pos);
            if (door != null) {
                door.onNeighborChanged();
            }
        }
    }

    // ---------------------------------------------------------------- shape, light, misc

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state);
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        // A shut blast door keeps the light (and the dark) on its own side.
        return state.getValue(OPEN) ? 0 : 15;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(OPEN);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathType) {
        return pathType == PathComputationType.LAND && state.getValue(OPEN);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        // Rotating a single part would tear the door apart; structure templates rotate whole doors consistently.
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState rotate(BlockState state, LevelAccessor level, BlockPos pos, Rotation rotation) {
        // In-world rotation (other mods' wrenches) would break the multiblock, so ignore it.
        return state;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state;
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return false;
    }

    @Override
    public boolean canDropFromExplosion(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return false;
    }

    // ---------------------------------------------------------------- block entity

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isController(state) ? new HermoDoorBlockEntity(pos, state) : null;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || !isController(state)) {
            return null;
        }
        return createTickerHelper(blockEntityType, ModBlockEntities.DOOR.get(), HermoDoorBlockEntity::serverTick);
    }
}
