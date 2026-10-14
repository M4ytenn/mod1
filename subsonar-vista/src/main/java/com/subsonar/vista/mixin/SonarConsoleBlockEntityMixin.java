package com.subsonar.vista.mixin;

import com.subsonar.vista.TvWatchers;
import com.subsonar.vista.client.SubSonarVistaClient;
import java.util.List;
import java.util.UUID;
import net.mehvahdjukaar.vista.client.video_source.IVideoSource;
import net.mehvahdjukaar.vista.common.broadcast.LevelBEBroadcastLocation;
import net.mehvahdjukaar.vista.common.cassette.IBroadcastSource;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the sonar console a Vista broadcast source, like a viewfinder: it gets a saved feed id, registers itself
 * with Vista's broadcast list, and gives TVs a video source that draws the sonar. Also adds the players watching
 * it on a TV to the players it sends scans to.
 */
@Mixin(targets = "com.subsonar.block.SonarConsoleBlockEntity")
public abstract class SonarConsoleBlockEntityMixin implements IBroadcastSource {
    @Unique
    private static final String subsonarVista$FEED_KEY = "subsonar_vista:feed";

    @Unique
    @Nullable
    private UUID subsonarVista$feedId;
    @Unique
    private boolean subsonarVista$linked;
    /** Client only: the {@link IVideoSource}, typed loosely so a dedicated server never loads client classes. */
    @Unique
    @Nullable
    private Object subsonarVista$video;

    @Unique
    private BlockEntity subsonarVista$self() {
        return (BlockEntity) (Object) this;
    }

    @Inject(method = "write(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Z)V", at = @At("TAIL"))
    private void subsonarVista$writeFeedId(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        tag.putUUID(subsonarVista$FEED_KEY, this.subsonarVista$feedId());
    }

    @Inject(method = "read(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Z)V", at = @At("TAIL"))
    private void subsonarVista$readFeedId(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        if (tag.hasUUID(subsonarVista$FEED_KEY)) {
            this.subsonarVista$feedId = tag.getUUID(subsonarVista$FEED_KEY);
        }
    }

    /** Registers the console's position under its feed id once per load (and again after it moves). */
    @Inject(method = "tick()V", at = @At("HEAD"))
    private void subsonarVista$link(CallbackInfo ci) {
        BlockEntity self = this.subsonarVista$self();
        if (!this.subsonarVista$linked && self.getLevel() instanceof ServerLevel level) {
            this.subsonarVista$linked = true;
            this.ensureLinked(level, LevelBEBroadcastLocation.of(self));
        }
    }

    @Inject(method = "viewers(Lnet/minecraft/server/level/ServerLevel;)Ljava/util/List;", at = @At("RETURN"))
    private void subsonarVista$addTvWatchers(ServerLevel level, CallbackInfoReturnable<List<ServerPlayer>> cir) {
        TvWatchers.addWatchers(level, this.subsonarVista$self().getBlockPos(), cir.getReturnValue());
    }

    @Unique
    private UUID subsonarVista$feedId() {
        if (this.subsonarVista$feedId == null) {
            this.subsonarVista$feedId = UUID.randomUUID();
        }
        return this.subsonarVista$feedId;
    }

    @Override
    public UUID getBroadcastUUID() {
        boolean fresh = this.subsonarVista$feedId == null;
        UUID id = this.subsonarVista$feedId();
        if (fresh) {
            // A new id has to reach the save; write() itself only reads it, so saving never marks the chunk dirty.
            this.subsonarVista$self().setChanged();
        }
        return id;
    }

    @Override
    @Nullable
    public IVideoSource getBroadcastVideo() {
        BlockEntity self = this.subsonarVista$self();
        Level level = self.getLevel();
        if (level == null || !level.isClientSide) {
            return null;
        }
        if (this.subsonarVista$video == null) {
            this.subsonarVista$video = SubSonarVistaClient.createVideoSource(self);
        }
        return (IVideoSource) this.subsonarVista$video;
    }
}
