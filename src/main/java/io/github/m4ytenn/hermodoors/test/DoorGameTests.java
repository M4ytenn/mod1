package io.github.m4ytenn.hermodoors.test;

import io.github.m4ytenn.hermodoors.HermoDoors;
import io.github.m4ytenn.hermodoors.door.DoorType;
import io.github.m4ytenn.hermodoors.door.HermoDoorBlock;
import io.github.m4ytenn.hermodoors.door.HermoDoorBlockEntity;
import io.github.m4ytenn.hermodoors.door.Stage;
import io.github.m4ytenn.hermodoors.network.ConfigureDoorPayload;
import io.github.m4ytenn.hermodoors.network.DismantleDoorPayload;
import io.github.m4ytenn.hermodoors.network.PanelActionPayload;
import io.github.m4ytenn.hermodoors.network.StageCompletePayload;
import io.github.m4ytenn.hermodoors.puzzle.PuzzleSpec;
import io.github.m4ytenn.hermodoors.registry.ModBlocks;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Server-side checks run by {@code ./gradlew runGameTestServer} (and in CI). Only loaded in development
 * environments, never in a normal install.
 */
@GameTestHolder(HermoDoors.MODID)
@PrefixGameTestTemplate(false)
public final class DoorGameTests {
    private static final BlockPos CLICKED = new BlockPos(3, 1, 3);

    private DoorGameTests() {}

    /** Places a door the way its item would: the clicked part first, then the block finishes the rest. */
    private static BlockPos place(GameTestHelper helper, HermoDoorBlock block, FakePlayer player) {
        ServerLevel level = helper.getLevel();
        BlockPos clicked = helper.absolutePos(CLICKED);
        BlockState state = block.partState(Direction.SOUTH, (block.type().width - 1) / 2, 0, false);
        level.setBlock(clicked, state, 3);
        block.setPlacedBy(level, clicked, state, player, new ItemStack(block));
        return block.originOf(state, clicked);
    }

    private static List<BlockPos> parts(HermoDoorBlock block, BlockPos origin) {
        List<BlockPos> parts = new ArrayList<>();
        for (int px = 0; px < block.type().width; px++) {
            for (int py = 0; py < block.type().height; py++) {
                parts.add(HermoDoorBlock.partPos(origin, Direction.SOUTH, px, py));
            }
        }
        return parts;
    }

    private static void assertWholeDoor(GameTestHelper helper, HermoDoorBlock block, BlockPos origin) {
        for (int px = 0; px < block.type().width; px++) {
            for (int py = 0; py < block.type().height; py++) {
                BlockPos p = HermoDoorBlock.partPos(origin, Direction.SOUTH, px, py);
                if (!block.isPart(helper.getLevel().getBlockState(p), Direction.SOUTH, px, py)) {
                    helper.fail("Missing door part " + px + "," + py + " at " + p);
                }
            }
        }
        if (!(helper.getLevel().getBlockEntity(origin) instanceof HermoDoorBlockEntity)) {
            helper.fail("No door block entity on the controller at " + origin);
        }
    }

    @GameTest(template = "empty")
    public static void dzgPlacesWholeDoor(GameTestHelper helper) {
        HermoDoorBlock block = ModBlocks.DZG_DOOR.get();
        BlockPos origin = place(helper, block, FakePlayerFactory.getMinecraft(helper.getLevel()));
        assertWholeDoor(helper, block, origin);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gatePlacesWholeDoor(GameTestHelper helper) {
        HermoDoorBlock block = ModBlocks.GERMOZATVOR.get();
        BlockPos origin = place(helper, block, FakePlayerFactory.getMinecraft(helper.getLevel()));
        assertWholeDoor(helper, block, origin);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void removingOnePartRemovesTheDoor(GameTestHelper helper) {
        HermoDoorBlock block = ModBlocks.GERMOZATVOR.get();
        BlockPos origin = place(helper, block, FakePlayerFactory.getMinecraft(helper.getLevel()));
        helper.getLevel().setBlock(HermoDoorBlock.partPos(origin, Direction.SOUTH, 3, 3), Blocks.AIR.defaultBlockState(), 3);
        for (BlockPos p : parts(block, origin)) {
            if (!helper.getLevel().getBlockState(p).isAir()) {
                helper.fail("Door part left behind at " + p);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 260)
    public static void unlockedDoorOpensAndCloses(GameTestHelper helper) {
        HermoDoorBlock block = ModBlocks.DZG_DOOR.get();
        FakePlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        BlockPos origin = place(helper, block, player);
        HermoDoorBlockEntity door = (HermoDoorBlockEntity) helper.getLevel().getBlockEntity(origin);
        // An empty procedure leaves the door unlocked: just a heavy door.
        door.applyPassport(player, 0, 1, false);
        if (!door.isUnlocked()) {
            helper.fail("Passport without steps should unlock the door");
        }
        BlockPos rel = CLICKED;
        helper.useBlock(rel, player);
        helper.runAfterDelay(DoorType.DZG.animTicks + 5, () -> {
            for (BlockPos p : parts(block, origin)) {
                if (!helper.getLevel().getBlockState(p).getValue(HermoDoorBlock.OPEN)) {
                    helper.fail("Door part still shut after the swing at " + p);
                }
            }
            helper.useBlock(rel, player);
            for (BlockPos p : parts(block, origin)) {
                if (helper.getLevel().getBlockState(p).getValue(HermoDoorBlock.OPEN)) {
                    helper.fail("Closing door should block the doorway at once, at " + p);
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void lockedDoorIgnoresUseByMachines(GameTestHelper helper) {
        HermoDoorBlock block = ModBlocks.DZG_DOOR.get();
        FakePlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        BlockPos origin = place(helper, block, player);
        HermoDoorBlockEntity door = (HermoDoorBlockEntity) helper.getLevel().getBlockEntity(origin);
        door.applyPassport(player, 0b1111, 2, false);
        helper.useBlock(CLICKED, player);
        helper.runAfterDelay(5, () -> {
            if (door.isOpen() || door.isUnlocked()) {
                helper.fail("A locked door must not open without its procedure");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void puzzleSpecsAreSolvable(GameTestHelper helper) {
        for (int difficulty = 1; difficulty <= 3; difficulty++) {
            for (long seed = 0; seed < 300; seed++) {
                PuzzleSpec spec = PuzzleSpec.create(seed * 7919L + difficulty, difficulty);
                // Breakers: closing in order of depth is always valid; a child before its feeder never is.
                List<Integer> order = new ArrayList<>();
                for (int depth = 0; depth < spec.power.count; depth++) {
                    for (int i = 0; i < spec.power.count; i++) {
                        if (spec.power.depth(i) == depth) {
                            order.add(i);
                        }
                    }
                }
                if (!spec.power.isValidOrder(order)) {
                    helper.fail("Depth order rejected for seed " + seed);
                }
                for (int i = 0; i < spec.power.count; i++) {
                    if (spec.power.parent[i] >= 0) {
                        List<Integer> bad = new ArrayList<>(order);
                        bad.remove(Integer.valueOf(i));
                        bad.add(0, i);
                        if (spec.power.isValidOrder(bad)) {
                            helper.fail("Breaker closed before its feeder was accepted, seed " + seed);
                        }
                    }
                }
                if (spec.pump.bandLow <= 1 || spec.pump.bandHigh >= PuzzleSpec.Pump.RELIEF) {
                    helper.fail("Pump band outside the gauge, seed " + seed);
                }
                for (int c = 0; c < spec.clamps.count; c++) {
                    float mark = spec.clamps.markPosition(c, 0);
                    if (mark < 0.1f || mark > 0.9f) {
                        helper.fail("Chalk mark out of reach, seed " + seed);
                    }
                }
                if (spec.wheel.speedLimit(0) <= 0) {
                    helper.fail("Wheel cannot turn, seed " + seed);
                }
            }
        }
        helper.succeed();
    }

    /** Single player hands packets over in memory, so this is the only place the payload codecs get exercised. */
    @GameTest(template = "empty")
    public static void payloadsSurviveTheWire(GameTestHelper helper) {
        BlockPos pos = new BlockPos(-29_999_000, -60, 29_999_000);
        roundTrip(helper, ConfigureDoorPayload.STREAM_CODEC, new ConfigureDoorPayload(pos, 0b1011, 3, true));
        roundTrip(helper, DismantleDoorPayload.STREAM_CODEC, new DismantleDoorPayload(pos));
        roundTrip(helper, StageCompletePayload.STREAM_CODEC, new StageCompletePayload(pos, Stage.POWER.ordinal(), List.of(2, 0, 5, 1, 4, 3)));
        roundTrip(helper, PanelActionPayload.STREAM_CODEC, new PanelActionPayload(pos, HermoDoorBlockEntity.PanelAction.WHEEL_ANGLE.ordinal(), -1440));
        helper.succeed();
    }

    private static <T> void roundTrip(GameTestHelper helper, StreamCodec<ByteBuf, T> codec, T payload) {
        ByteBuf buf = Unpooled.buffer();
        try {
            codec.encode(buf, payload);
            T back = codec.decode(buf);
            if (!payload.equals(back) || buf.isReadable()) {
                helper.fail("Payload changed on the wire: " + payload + " came back as " + back);
            }
        } finally {
            buf.release();
        }
    }
}
