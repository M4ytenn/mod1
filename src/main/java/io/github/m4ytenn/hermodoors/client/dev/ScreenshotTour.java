package io.github.m4ytenn.hermodoors.client.dev;

import io.github.m4ytenn.hermodoors.HermoDoors;
import io.github.m4ytenn.hermodoors.client.gui.DoorPanelScreen;
import io.github.m4ytenn.hermodoors.client.gui.PassportScreen;
import io.github.m4ytenn.hermodoors.client.gui.stage.ClampsView;
import io.github.m4ytenn.hermodoors.client.gui.stage.PowerView;
import io.github.m4ytenn.hermodoors.client.gui.stage.PumpView;
import io.github.m4ytenn.hermodoors.client.gui.stage.WheelView;
import io.github.m4ytenn.hermodoors.door.HermoDoorBlock;
import io.github.m4ytenn.hermodoors.door.HermoDoorBlockEntity;
import io.github.m4ytenn.hermodoors.door.Stage;
import io.github.m4ytenn.hermodoors.puzzle.PuzzleSpec;
import io.github.m4ytenn.hermodoors.registry.ModBlocks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * Development aid, only active with {@code -Dhermodoors.tour=true} (the {@code runTourClient} task): creates a
 * flat world, builds a wall with both doors, works through the passport and every panel stage (the breaker panel
 * is solved through real clicks on the screen) and saves screenshots to {@code run/screenshots}, then quits.
 * CI runs it under a virtual display so the screens can be checked without a person at the keyboard.
 */
public final class ScreenshotTour {
    private static final String WORLD = "hermodoors_tour";
    private static final int GIVE_UP_TICKS = 20 * 60 * 6;

    private final Minecraft mc = Minecraft.getInstance();
    private final Deque<Step> steps = new ArrayDeque<>();
    private int ticks;
    private int wait;
    private boolean worldRequested;
    private boolean sceneBuilt;
    private int inWorldTicks;
    private BlockPos dzg = BlockPos.ZERO;
    private BlockPos gate = BlockPos.ZERO;
    private float mouseX;
    private float mouseY;

    private record Step(String name, int delay, Runnable action, BooleanSupplier ready) {}

    public static void start() {
        ScreenshotTour tour = new ScreenshotTour();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tour.tick());
        HermoDoors.LOGGER.info("[tour] screenshot tour armed");
    }

    private void tick() {
        ticks++;
        if (ticks > GIVE_UP_TICKS) {
            log("giving up after " + ticks + " ticks with " + steps.size() + " steps left");
            mc.stop();
            return;
        }
        if (!worldRequested) {
            if (ticks > 60 && mc.getOverlay() == null && mc.level == null && mc.screen != null) {
                createWorld();
            }
            return;
        }
        if (!sceneBuilt) {
            if (mc.player != null && mc.level != null && mc.getSingleplayerServer() != null) {
                if (++inWorldTicks == 80) {
                    buildScene();
                    planSteps();
                    sceneBuilt = true;
                    wait = steps.peekFirst().delay();
                }
            }
            return;
        }
        if (mc.screen != null) {
            // Keep the real cursor out of the way so hover tooltips never cover a shot.
            GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 2, 2);
        }
        if (wait > 0) {
            wait--;
            return;
        }
        Step step = steps.peekFirst();
        if (step == null) {
            return;
        }
        if (step.ready() != null && !step.ready().getAsBoolean()) {
            return;
        }
        steps.removeFirst();
        log(step.name());
        try {
            step.action().run();
        } catch (RuntimeException e) {
            HermoDoors.LOGGER.error("[tour] step '{}' failed", step.name(), e);
        }
        Step next = steps.peekFirst();
        wait = next == null ? 0 : next.delay();
    }

    private void createWorld() {
        worldRequested = true;
        log("creating flat world");
        mc.options.onboardAccessibility = false;
        mc.options.tutorialStep = TutorialSteps.NONE;
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(1974L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                new TitleScreen());
    }

    // ---------------------------------------------------------------- server side

    private void server(Consumer<ServerContext> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            log("no integrated server");
            return;
        }
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (player != null) {
                action.accept(new ServerContext(server.overworld(), player));
            }
        });
    }

    private record ServerContext(ServerLevel level, ServerPlayer player) {}

    private void buildScene() {
        BlockPos spawn = mc.player.blockPosition();
        int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
        // A brick wall across z = wallZ with the ДЗГ on the left and the gate on the right (as seen by the player,
        // who looks south). Doors face north, towards the player.
        int wallZ = spawn.getZ() + 6;
        dzg = new BlockPos(spawn.getX() + 2, ground, wallZ);
        gate = new BlockPos(spawn.getX() - 2, ground, wallZ);
        BlockPos dzgPos = dzg;
        BlockPos gatePos = gate;
        server(ctx -> {
            ServerLevel level = ctx.level();
            MinecraftServer server = level.getServer();
            level.setDayTime(6000);
            level.setWeatherParameters(12000, 0, false, false);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            for (int x = gatePos.getX() - 9; x <= dzgPos.getX() + 4; x++) {
                for (int y = ground; y < ground + 6; y++) {
                    BlockState brick = ((x * 31 + y * 17) % 7 == 0) ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                            : ((x * 13 + y * 5) % 9 == 0) ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                            : Blocks.STONE_BRICKS.defaultBlockState();
                    level.setBlock(new BlockPos(x, y, wallZ), brick, 3);
                }
            }
            for (int x = gatePos.getX() - 9; x <= dzgPos.getX() + 4; x++) {
                for (int z = wallZ - 7; z < wallZ; z++) {
                    level.setBlock(new BlockPos(x, ground - 1, z), ((x + z) & 1) == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState(), 3);
                }
            }
            level.setBlock(new BlockPos(spawn.getX(), ground, wallZ - 1), Blocks.LANTERN.defaultBlockState(), 3);
            place(level, ctx.player(), ModBlocks.GERMOZATVOR.get(), gatePos);
            place(level, ctx.player(), ModBlocks.DZG_DOOR.get(), dzgPos);
            ctx.player().teleportTo(level, spawn.getX() + 0.5, ground, spawn.getZ() + 1.5, 0f, 8f);
        });
    }

    /** Places a door as the item would, with the player as placer (which opens its passport). */
    private static void place(ServerLevel level, ServerPlayer player, HermoDoorBlock block, BlockPos origin) {
        int clickedPx = (block.type().width - 1) / 2;
        BlockPos clicked = HermoDoorBlock.partPos(origin, Direction.NORTH, clickedPx, 0);
        for (int px = 0; px < block.type().width; px++) {
            for (int py = 0; py < block.type().height; py++) {
                level.setBlock(HermoDoorBlock.partPos(origin, Direction.NORTH, px, py), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        BlockState state = block.partState(Direction.NORTH, clickedPx, 0, false);
        level.setBlock(clicked, state, 3);
        block.setPlacedBy(level, clicked, state, player, new ItemStack(block));
    }

    // ---------------------------------------------------------------- the tour

    private void planSteps() {
        add("passport after placement", 60, () -> shot("01_passport_first_placement"), () -> mc.screen instanceof PassportScreen);
        add("close passport", 10, () -> mc.screen.onClose(), null);
        add("doors closed", 30, () -> {
            mc.options.hideGui = true;
        }, null);
        add("shot doors closed", 5, () -> shot("02_doors_closed"), null);
        add("show gui", 2, () -> mc.options.hideGui = false, null);
        add("open ДЗГ panel", 5, () -> useDoor(dzg), null);
        add("power panel", 30, () -> shot("03_power_initial"), () -> mc.screen instanceof DoorPanelScreen);
        add("main switch", 10, () -> clickStage(PowerView.knifeCenter()[0], PowerView.knifeCenter()[1]), null);
        add("plan breakers", 2, this::planBreakers, null);
        add("pump panel", 40, () -> shot("05_pump_initial"), () -> stageIs(Stage.PUMP));
        for (int k = 0; k < 9; k++) {
            add("pump down", 3, () -> {
                float[] up = PumpView.knobAt(PumpView.LEVER_UP);
                pressStage(up[0], up[1]);
                float[] mid = PumpView.knobAt((PumpView.LEVER_UP + PumpView.LEVER_DOWN) / 2);
                dragStage(mid[0], mid[1]);
            }, null);
            add("pump bottom", 2, () -> {
                float[] down = PumpView.knobAt(PumpView.LEVER_DOWN);
                dragStage(down[0], down[1]);
                releaseStage();
            }, null);
            add("pump up", 3, () -> {
                float[] down = PumpView.knobAt(PumpView.LEVER_DOWN);
                pressStage(down[0], down[1]);
                float[] up = PumpView.knobAt(PumpView.LEVER_UP);
                dragStage(up[0], up[1]);
                releaseStage();
            }, null);
        }
        add("pump pressure", 5, () -> shot("06_pump_pressure"), null);
        add("finish pump", 70, () -> complete(Stage.PUMP), null);
        add("clamps panel", 30, () -> shot("07_clamps_initial"), () -> stageIs(Stage.CLAMPS));
        add("strike", 5, () -> clickStage(ClampsView.strikePoint()[0], ClampsView.strikePoint()[1]), null);
        add("clamps strike", 3, () -> shot("08_clamps_strike"), null);
        add("finish clamps", 90, () -> complete(Stage.CLAMPS), null);
        add("wheel panel", 30, () -> shot("09_wheel_initial"), () -> stageIs(Stage.WHEEL));
        add("grab wheel", 5, () -> {
            float[] p = WheelView.rimPoint(0);
            pressStage(p[0], p[1]);
        }, null);
        for (int k = 1; k <= 18; k++) {
            float angle = -k * 13f;
            add("turn wheel", 1, () -> {
                float[] p = WheelView.rimPoint(angle);
                dragStage(p[0], p[1]);
            }, null);
        }
        add("wheel turning", 2, () -> shot("10_wheel_turning"), null);
        add("let go", 1, this::releaseStage, null);
        add("finish wheel", 60, () -> complete(Stage.WHEEL), null);
        add("ДЗГ swinging", 30, () -> mc.options.hideGui = true, () -> mc.screen == null);
        add("shot ДЗГ swinging", 2, () -> shot("11_dzg_swinging"), null);
        add("shot ДЗГ open", 70, () -> shot("12_dzg_open"), null);
        add("unlock gate", 5, () -> server(ctx -> {
            if (ctx.level().getBlockEntity(gate) instanceof HermoDoorBlockEntity door) {
                door.applyPassport(ctx.player(), 0, 2, false);
            }
        }), null);
        add("open gate", 10, () -> useDoor(gate), null);
        add("shot gate sliding", 80, () -> shot("13_gate_sliding"), null);
        add("shot gate open", 110, () -> shot("14_gate_open"), null);
        add("side view", 5, () -> server(ctx -> ctx.player().teleportTo(ctx.level(), dzg.getX() - 1.5, dzg.getY(), dzg.getZ() - 3.2, -35f, 10f)), null);
        add("shot side view", 30, () -> shot("15_dzg_side"), null);
        add("switch to Russian", 5, () -> {
            mc.options.languageCode = "ru_ru";
            mc.getLanguageManager().setSelected("ru_ru");
            mc.reloadResourcePacks();
        }, null);
        add("passport in Russian", 40, () -> server(ctx -> {
            if (ctx.level().getBlockEntity(dzg) instanceof HermoDoorBlockEntity door) {
                door.openPassport(ctx.player(), true);
            }
        }), () -> mc.getOverlay() == null);
        add("shot passport ru", 30, () -> shot("16_passport_ru"), () -> mc.screen instanceof PassportScreen);
        add("untick clamps", 5, () -> clickPassport(PassportScreen.stepPoint(Stage.CLAMPS)), null);
        add("stamp", 10, () -> clickPassport(PassportScreen.stampPoint()), null);
        add("shot stamped", 6, () -> shot("17_passport_stamped"), null);
        add("quit", 40, () -> {
            log("done");
            mc.stop();
        }, null);
    }

    private void add(String name, int delay, Runnable action, BooleanSupplier ready) {
        steps.addLast(new Step(name, delay, action, ready));
    }

    /** Closes the breakers in a valid order through real clicks; the order is only known once the door synced. */
    private void planBreakers() {
        List<Integer> order = breakerOrder();
        List<Step> plan = new ArrayList<>();
        for (int k = 0; k < order.size(); k++) {
            int index = order.get(k);
            int count = order.size();
            plan.add(new Step("breaker А" + (index + 1), 9, () -> {
                int[] c = PowerView.breakerCenter(count, index);
                clickStage(c[0], c[1]);
            }, null));
            if (k == 1) {
                plan.add(new Step("power in progress", 4, () -> shot("04_power_in_progress"), null));
            }
        }
        for (int i = plan.size() - 1; i >= 0; i--) {
            steps.addFirst(plan.get(i));
        }
    }

    private void log(String message) {
        HermoDoors.LOGGER.info("[tour] {}", message);
    }

    private void shot(String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> log(message.getString()));
    }

    private boolean stageIs(Stage stage) {
        return mc.screen instanceof DoorPanelScreen && mc.level != null
                && mc.level.getBlockEntity(dzg) instanceof HermoDoorBlockEntity door && door.currentStage() == stage;
    }

    private List<Integer> breakerOrder() {
        List<Integer> order = new ArrayList<>();
        PuzzleSpec spec = mc.level != null && mc.level.getBlockEntity(dzg) instanceof HermoDoorBlockEntity door ? door.spec() : null;
        if (spec == null) {
            log("door not synced yet, breaker order unknown");
            return order;
        }
        for (int depth = 0; depth < spec.power.count; depth++) {
            for (int i = 0; i < spec.power.count; i++) {
                if (spec.power.depth(i) == depth) {
                    order.add(i);
                }
            }
        }
        return order;
    }

    private void useDoor(BlockPos origin) {
        server(ctx -> {
            BlockState state = ctx.level().getBlockState(origin);
            state.useWithoutItem(ctx.level(), ctx.player(), new BlockHitResult(Vec3.atCenterOf(origin), Direction.NORTH, origin, false));
        });
    }

    private void complete(Stage stage) {
        server(ctx -> {
            if (ctx.level().getBlockEntity(dzg) instanceof HermoDoorBlockEntity door) {
                boolean ok = door.completeStage(ctx.player(), stage, List.of());
                HermoDoors.LOGGER.info("[tour] server accepted {}: {}", stage.id, ok);
            }
        });
    }

    private void clickPassport(int[] local) {
        Screen screen = mc.screen;
        if (screen != null) {
            double x = (screen.width - PassportScreen.WIDTH) / 2 + local[0];
            double y = (screen.height - PassportScreen.HEIGHT) / 2 + local[1];
            screen.mouseClicked(x, y, 0);
            screen.mouseReleased(x, y, 0);
        }
    }

    // ---------------------------------------------------------------- mouse on the panel

    private double[] toScreen(float stageX, float stageY) {
        Screen screen = mc.screen;
        if (screen == null) {
            return new double[] {0, 0};
        }
        int left = (screen.width - DoorPanelScreen.WIDTH) / 2;
        int top = (screen.height - DoorPanelScreen.HEIGHT) / 2;
        return new double[] {left + DoorPanelScreen.STAGE_X + stageX, top + DoorPanelScreen.STAGE_Y + stageY};
    }

    private void clickStage(float x, float y) {
        pressStage(x, y);
        releaseStage();
    }

    private void pressStage(float x, float y) {
        if (mc.screen != null) {
            double[] p = toScreen(x, y);
            mouseX = (float) p[0];
            mouseY = (float) p[1];
            mc.screen.mouseClicked(p[0], p[1], 0);
        }
    }

    private void dragStage(float x, float y) {
        if (mc.screen != null) {
            double[] p = toScreen(x, y);
            mc.screen.mouseDragged(p[0], p[1], 0, p[0] - mouseX, p[1] - mouseY);
            mouseX = (float) p[0];
            mouseY = (float) p[1];
        }
    }

    private void releaseStage() {
        if (mc.screen != null) {
            mc.screen.mouseReleased(mouseX, mouseY, 0);
        }
    }
}
