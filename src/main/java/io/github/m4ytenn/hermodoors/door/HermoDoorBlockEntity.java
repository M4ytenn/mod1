package io.github.m4ytenn.hermodoors.door;

import io.github.m4ytenn.hermodoors.HermoConfig;
import io.github.m4ytenn.hermodoors.HermoDoors;
import io.github.m4ytenn.hermodoors.menu.DoorPanelMenu;
import io.github.m4ytenn.hermodoors.menu.DoorPassportMenu;
import io.github.m4ytenn.hermodoors.puzzle.PuzzleSpec;
import io.github.m4ytenn.hermodoors.registry.ModBlockEntities;
import io.github.m4ytenn.hermodoors.registry.ModSounds;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Lives in the controller part. Holds the door's passport (which stages it needs, difficulty, hinge side),
 * progress through the stages, the open/close animation and who is currently working the control panel.
 * The server is authoritative; clients get the whole state through the vanilla block entity sync.
 */
public class HermoDoorBlockEntity extends BlockEntity {
    private static final double REACH = 9.0;

    // Passport
    private int stageMask = Stage.ALL;
    private int difficulty = 2;
    private boolean mirrored;
    private long seed;
    private int serial;
    private int year;
    @Nullable
    private UUID owner;
    private String ownerName = "";

    // Progress
    private int completed;
    private boolean unlocked;
    /** Clamps knocked loose so far, for the renderer. */
    private int clampsFreed;
    /** Handwheel angle as last reported by whoever is turning it, for the renderer. */
    private float wheelAngle;

    // Motion
    private boolean open;
    private long animStart;
    private float animFrom;
    private boolean powered;

    // Not saved
    @Nullable
    private UUID operator;
    private long stageStartTick;
    private long lastClampTick;
    private boolean wasMoving;
    private long actionWindowStart;
    private int actionsInWindow;
    private float wheelShown;
    private float wheelShownPrev;
    private long wheelShownTick = Long.MIN_VALUE;
    @Nullable
    private PuzzleSpec spec;
    private long specSeed;
    private int specDifficulty;

    public HermoDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DOOR.get(), pos, state);
    }

    // ---------------------------------------------------------------- accessors

    public DoorType type() {
        return getBlockState().getBlock() instanceof HermoDoorBlock door ? door.type() : DoorType.DZG;
    }

    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(HermoDoorBlock.FACING) ? state.getValue(HermoDoorBlock.FACING) : Direction.NORTH;
    }

    public int stageMask() {
        return stageMask;
    }

    public int difficulty() {
        return difficulty;
    }

    public boolean mirrored() {
        return mirrored;
    }

    public int serial() {
        return serial;
    }

    public int year() {
        return year;
    }

    public String ownerName() {
        return ownerName;
    }

    public int completed() {
        return completed;
    }

    public boolean isUnlocked() {
        return unlocked;
    }

    public int clampsFreed() {
        return clampsFreed;
    }

    public float wheelAngle() {
        return wheelAngle;
    }

    /**
     * Client-side, for the renderer: the handwheel eases toward the last synced angle so other players see it
     * turn smoothly rather than in jumps.
     */
    public float wheelDisplay(float partialTick) {
        if (level != null && level.getGameTime() != wheelShownTick) {
            wheelShownTick = level.getGameTime();
            wheelShownPrev = wheelShown;
            wheelShown += (wheelAngle - wheelShown) * 0.35f;
        }
        return Mth.lerp(partialTick, wheelShownPrev, wheelShown);
    }

    public boolean isOpen() {
        return open;
    }

    @Nullable
    public Stage currentStage() {
        return unlocked ? null : Stage.next(stageMask, completed);
    }

    public PuzzleSpec spec() {
        if (spec == null || specSeed != seed || specDifficulty != difficulty) {
            spec = PuzzleSpec.create(seed, difficulty);
            specSeed = seed;
            specDifficulty = difficulty;
        }
        return spec;
    }

    /** 0 = shut, 1 = fully open. Clients pass the frame's partial tick for smooth motion. */
    public float openProgress(float partialTick) {
        if (level == null) {
            return open ? 1f : 0f;
        }
        float elapsed = (level.getGameTime() - animStart + partialTick) / (float) type().animTicks;
        return Mth.clamp(open ? animFrom + elapsed : animFrom - elapsed, 0f, 1f);
    }

    public boolean isMoving() {
        float p = openProgress(0);
        return open ? p < 1f : p > 0f;
    }

    public Vec3 center() {
        DoorType type = type();
        Direction right = HermoDoorBlock.rightOf(facing());
        return Vec3.atLowerCornerOf(worldPosition)
                .add(0.5, 0, 0.5)
                .add(right.getStepX() * (type.width - 1) / 2.0, type.height / 2.0, right.getStepZ() * (type.width - 1) / 2.0);
    }

    public boolean isInReach(Player player) {
        return !isRemoved() && player.distanceToSqr(center()) <= REACH * REACH;
    }

    /** Box that covers the frame and everywhere the leaf can travel, for culling the renderer. */
    public AABB renderBounds() {
        DoorType type = type();
        Direction facing = facing();
        Direction right = HermoDoorBlock.rightOf(facing);
        BlockPos far = HermoDoorBlock.partPos(worldPosition, facing, type.width - 1, type.height - 1);
        AABB box = new AABB(Vec3.atLowerCornerOf(worldPosition), Vec3.atLowerCornerOf(far).add(1, 1, 1));
        if (type.sliding) {
            Direction side = mirrored ? right.getOpposite() : right;
            box = box.expandTowards(side.getStepX() * type.width, 0, side.getStepZ() * type.width);
        } else {
            box = box.expandTowards(facing.getStepX() * type.width, 0, facing.getStepZ() * type.width);
        }
        return box.inflate(0.5);
    }

    // ---------------------------------------------------------------- placement & passport

    void initPlaced(@Nullable LivingEntity placer) {
        if (level == null) {
            return;
        }
        var random = level.getRandom();
        seed = random.nextLong();
        serial = 100 + random.nextInt(9900);
        year = 1961 + random.nextInt(28);
        stageMask = HermoConfig.SERVER.defaultStageMask();
        difficulty = HermoConfig.SERVER.defaultDifficulty.get();
        unlocked = stageMask == 0;
        if (placer instanceof Player player) {
            owner = player.getUUID();
            ownerName = player.getGameProfile().getName();
        }
        sync();
    }

    public boolean mayConfigure(Player player) {
        if (player.getAbilities().instabuild || player.hasPermissions(2)) {
            return true;
        }
        if (!HermoConfig.ownerOnlyPassport()) {
            return true;
        }
        return owner != null && owner.equals(player.getUUID());
    }

    public void openPassport(ServerPlayer player, boolean firstPlacement) {
        if (!mayConfigure(player)) {
            player.displayClientMessage(Component.translatable("message.hermodoors.not_owner", ownerName), true);
            return;
        }
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new DoorPassportMenu(id, inventory, worldPosition, firstPlacement),
                Component.translatable("screen.hermodoors.passport")), buf -> {
                    buf.writeBlockPos(worldPosition);
                    buf.writeBoolean(firstPlacement);
                });
    }

    public void applyPassport(ServerPlayer player, int mask, int newDifficulty, boolean newMirrored) {
        if (level == null || !mayConfigure(player)) {
            return;
        }
        kickOperator();
        stageMask = mask & Stage.ALL;
        difficulty = Mth.clamp(newDifficulty, 1, 3);
        mirrored = newMirrored;
        seed = level.getRandom().nextLong();
        completed = 0;
        clampsFreed = 0;
        wheelAngle = 0;
        unlocked = stageMask == 0;
        if (!unlocked && open) {
            setOpen(false);
        }
        level.playSound(null, worldPosition, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 0.6f, 0.8f);
        sync();
    }

    public void dismantle(ServerPlayer player) {
        if (level == null || !mayConfigure(player)) {
            return;
        }
        ItemStack stack = new ItemStack(getBlockState().getBlock().asItem());
        Vec3 c = center();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.7f, 0.6f);
        player.closeContainer();
        level.setBlock(worldPosition, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        if (!player.getAbilities().instabuild && !player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // ---------------------------------------------------------------- use

    void use(ServerPlayer player) {
        if (level == null) {
            return;
        }
        if (player.isSecondaryUseActive() && !player.isFakePlayer() && mayConfigure(player)) {
            openPassport(player, false);
            return;
        }
        if (!unlocked && Stage.next(stageMask, completed) == null) {
            unlocked = true;
            sync();
        }
        if (unlocked) {
            setOpen(!open);
            return;
        }
        if (player.isFakePlayer()) {
            return;
        }
        ServerPlayer current = activeOperator();
        if (current != null && current != player) {
            player.displayClientMessage(Component.translatable("message.hermodoors.busy", current.getGameProfile().getName()), true);
            return;
        }
        operator = player.getUUID();
        stageStartTick = level.getGameTime();
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new DoorPanelMenu(id, inventory, worldPosition),
                Component.translatable(type().translationKey())), worldPosition);
        level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.5f, 0.7f);
    }

    @Nullable
    private ServerPlayer activeOperator() {
        if (operator == null || level == null) {
            return null;
        }
        Player player = level.getPlayerByUUID(operator);
        if (player instanceof ServerPlayer serverPlayer
                && serverPlayer.containerMenu instanceof DoorPanelMenu menu
                && menu.pos().equals(worldPosition)) {
            return serverPlayer;
        }
        operator = null;
        return null;
    }

    private void kickOperator() {
        ServerPlayer current = activeOperator();
        if (current != null) {
            current.closeContainer();
        }
        operator = null;
    }

    public void releaseOperator(Player player) {
        if (player.getUUID().equals(operator)) {
            operator = null;
            if (currentStage() == Stage.WHEEL && wheelAngle != 0) {
                // Let go of the wheel and the bolt springs push it back.
                wheelAngle = 0;
                sync();
            }
        }
    }

    public boolean isOperator(Player player) {
        return player.getUUID().equals(operator);
    }

    // ---------------------------------------------------------------- stages

    /** Called when the operator's client reports a finished stage. Returns false if the claim was rejected. */
    public boolean completeStage(ServerPlayer player, Stage stage, List<Integer> proof) {
        if (level == null || unlocked || !isOperator(player)) {
            return false;
        }
        if (Stage.next(stageMask, completed) != stage) {
            return false;
        }
        long elapsed = level.getGameTime() - stageStartTick;
        int minTicks = stage == Stage.CLAMPS ? spec().clamps.hitsFrom(clampsFreed) * 5 : spec().minTicks(stage);
        if (HermoConfig.SERVER.enforceMinimumTimes.get() && elapsed < minTicks) {
            HermoDoors.LOGGER.warn("Rejected {} stage of door at {} from {}: finished in {} ticks", stage.id, worldPosition, player.getGameProfile().getName(), elapsed);
            return false;
        }
        if (stage == Stage.POWER && !spec().power.isValidOrder(proof)) {
            HermoDoors.LOGGER.warn("Rejected power stage of door at {} from {}: bad switching order", worldPosition, player.getGameProfile().getName());
            return false;
        }
        completed |= stage.bit;
        stageStartTick = level.getGameTime();
        Vec3 c = center();
        switch (stage) {
            case POWER -> {
                level.playSound(null, c.x, c.y, c.z, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.8f, 0.5f);
                level.playSound(null, c.x, c.y, c.z, ModSounds.PANEL_HUM.get(), SoundSource.BLOCKS, 0.6f, 1.0f);
            }
            case PUMP -> level.playSound(null, c.x, c.y, c.z, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.7f, 0.5f);
            case CLAMPS -> {
                clampsFreed = spec().clamps.count;
                level.playSound(null, c.x, c.y, c.z, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0f, 0.6f);
            }
            case WHEEL -> level.playSound(null, c.x, c.y, c.z, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0f, 0.5f);
        }
        if (Stage.next(stageMask, completed) == null) {
            unlocked = true;
            operator = null;
            player.closeContainer();
            level.playSound(null, c.x, c.y, c.z, ModSounds.DOOR_UNSEAL.get(), SoundSource.BLOCKS, 1.2f, 1.0f);
            player.displayClientMessage(Component.translatable("message.hermodoors.unlocked"), true);
            setOpen(true);
        }
        sync();
        return true;
    }

    /**
     * Cosmetic events from the operator's panel, replayed in the world so everyone nearby hears the work
     * (and sees the clamps and the handwheel move). Rate limited.
     */
    public void panelAction(ServerPlayer player, PanelAction action, int value) {
        if (level == null || unlocked || !isOperator(player)) {
            return;
        }
        long now = level.getGameTime();
        if (now - actionWindowStart >= 20) {
            actionWindowStart = now;
            actionsInWindow = 0;
        }
        if (++actionsInWindow > 12) {
            return;
        }
        Vec3 c = center();
        switch (action) {
            case BREAKER -> playNear(player, c, SoundEvents.LEVER_CLICK, 0.5f, 0.7f);
            case TRIP -> playNear(player, c, ModSounds.PANEL_ARC.get(), 0.8f, 1.0f);
            case PUMP_STROKE -> playNear(player, c, SoundEvents.PISTON_CONTRACT, 0.35f, 1.5f);
            case RELIEF -> playNear(player, c, ModSounds.PANEL_HISS.get(), 0.8f, 1.0f);
            case HAMMER_HIT -> playNear(player, c, SoundEvents.ANVIL_PLACE, 0.6f, 1.3f);
            case HAMMER_MISS -> playNear(player, c, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, 0.5f, 0.6f);
            case CLAMP_FREED -> {
                // Clamps come loose one at a time, and no faster than their blows could have been struck.
                PuzzleSpec.Clamps clamps = spec().clamps;
                if (currentStage() == Stage.CLAMPS && value == clampsFreed + 1 && clampsFreed < clamps.count
                        && now - Math.max(lastClampTick, stageStartTick) >= clamps.hits[clampsFreed] * 4L) {
                    clampsFreed = value;
                    lastClampTick = now;
                    playNear(player, c, SoundEvents.IRON_TRAPDOOR_OPEN, 0.7f, 0.6f);
                    sync();
                }
            }
            case WHEEL_ANGLE -> {
                wheelAngle = value;
                sync();
            }
            case WHEEL_SLIP -> playNear(player, c, SoundEvents.CHAIN_BREAK, 0.6f, 0.7f);
        }
    }

    private void playNear(ServerPlayer except, Vec3 c, SoundEvent sound, float volume, float pitch) {
        // The operator already hears the panel version of this sound.
        level.playSound(except, c.x, c.y, c.z, sound, SoundSource.BLOCKS, volume, pitch);
    }

    // ---------------------------------------------------------------- motion

    public void setOpen(boolean newOpen) {
        if (level == null || newOpen == open) {
            return;
        }
        float current = openProgress(0);
        open = newOpen;
        animFrom = current;
        animStart = level.getGameTime();
        if (!newOpen) {
            applyOpenState(false);
        }
        Vec3 c = center();
        level.playSound(null, c.x, c.y, c.z, newOpen ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 0.5f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.6f, 0.5f);
        sync();
    }

    /** Collision follows the leaf: the doorway frees up three quarters of the way open, and blocks at once on closing. */
    private void applyOpenState(boolean value) {
        if (level == null) {
            return;
        }
        BlockState controller = getBlockState();
        if (!(controller.getBlock() instanceof HermoDoorBlock block)) {
            return;
        }
        DoorType type = block.type();
        Direction facing = controller.getValue(HermoDoorBlock.FACING);
        for (int px = 0; px < type.width; px++) {
            for (int py = 0; py < type.height; py++) {
                BlockPos p = HermoDoorBlock.partPos(worldPosition, facing, px, py);
                BlockState s = level.getBlockState(p);
                if (block.isPart(s, facing, px, py) && s.getValue(HermoDoorBlock.OPEN) != value) {
                    level.setBlock(p, s.setValue(HermoDoorBlock.OPEN, value), Block.UPDATE_ALL);
                }
            }
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HermoDoorBlockEntity door) {
        door.tickServer(state);
    }

    private void tickServer(BlockState state) {
        if (level == null) {
            return;
        }
        float progress = openProgress(0);
        boolean moving = open ? progress < 1f : progress > 0f;
        if (open && progress >= 0.75f && !state.getValue(HermoDoorBlock.OPEN)) {
            applyOpenState(true);
        }
        Vec3 c = center();
        if (moving && (level.getGameTime() - animStart) % 16 == 0) {
            level.playSound(null, c.x, c.y, c.z, ModSounds.DOOR_RUMBLE.get(), SoundSource.BLOCKS, type().sliding ? 0.9f : 0.6f, type().sliding ? 0.8f : 1.1f);
            if (type().sliding) {
                level.playSound(null, c.x, c.y, c.z, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.4f, 0.5f);
            }
        }
        if (wasMoving && !moving) {
            level.playSound(null, c.x, c.y, c.z, open ? SoundEvents.ANVIL_LAND : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, open ? 0.35f : 1.0f, 0.5f);
        }
        wasMoving = moving;
        if (operator != null && level.getGameTime() % 20 == 0) {
            activeOperator();
        }
    }

    void onNeighborChanged() {
        if (level == null) {
            return;
        }
        DoorType type = type();
        Direction facing = facing();
        boolean nowPowered = false;
        for (int px = 0; px < type.width && !nowPowered; px++) {
            for (int py = 0; py < type.height && !nowPowered; py++) {
                nowPowered = level.hasNeighborSignal(HermoDoorBlock.partPos(worldPosition, facing, px, py));
            }
        }
        if (nowPowered != powered) {
            powered = nowPowered;
            setChanged();
            if (unlocked && HermoConfig.SERVER.redstoneOpensUnlocked.get()) {
                setOpen(nowPowered);
            }
        }
    }

    // ---------------------------------------------------------------- persistence & sync

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("StageMask", stageMask);
        tag.putInt("Difficulty", difficulty);
        tag.putBoolean("Mirrored", mirrored);
        tag.putLong("Seed", seed);
        tag.putInt("Serial", serial);
        tag.putInt("Year", year);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
        tag.putInt("Completed", completed);
        tag.putBoolean("Unlocked", unlocked);
        tag.putInt("ClampsFreed", clampsFreed);
        tag.putFloat("WheelAngle", wheelAngle);
        tag.putBoolean("Open", open);
        tag.putLong("AnimStart", animStart);
        tag.putFloat("AnimFrom", animFrom);
        tag.putBoolean("Powered", powered);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stageMask = tag.contains("StageMask") ? tag.getInt("StageMask") & Stage.ALL : Stage.ALL;
        difficulty = tag.contains("Difficulty") ? Mth.clamp(tag.getInt("Difficulty"), 1, 3) : 2;
        mirrored = tag.getBoolean("Mirrored");
        seed = tag.getLong("Seed");
        serial = tag.getInt("Serial");
        year = tag.getInt("Year");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        completed = tag.getInt("Completed") & Stage.ALL;
        unlocked = tag.getBoolean("Unlocked");
        clampsFreed = tag.getInt("ClampsFreed");
        wheelAngle = tag.getFloat("WheelAngle");
        open = tag.getBoolean("Open");
        animStart = tag.getLong("AnimStart");
        animFrom = tag.getFloat("AnimFrom");
        powered = tag.getBoolean("Powered");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Cosmetic panel events that are replayed in the world for other players. */
    public enum PanelAction {
        BREAKER, TRIP, PUMP_STROKE, RELIEF, HAMMER_HIT, HAMMER_MISS, CLAMP_FREED, WHEEL_ANGLE, WHEEL_SLIP;

        @Nullable
        public static PanelAction byId(int id) {
            PanelAction[] values = values();
            return id >= 0 && id < values.length ? values[id] : null;
        }
    }
}
