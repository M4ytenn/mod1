package io.github.m4ytenn.hermodoors.network;

import io.github.m4ytenn.hermodoors.door.HermoDoorBlockEntity;
import io.github.m4ytenn.hermodoors.door.Stage;
import io.github.m4ytenn.hermodoors.menu.DoorPanelMenu;
import io.github.m4ytenn.hermodoors.menu.DoorPassportMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

/**
 * All packets go client to server. Door state flows back through the regular block entity sync, and the
 * screens are opened through vanilla menus, so a client can only talk about a door it has open in front of it.
 */
public final class ModNetwork {
    private static final String VERSION = "1";

    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(ConfigureDoorPayload.TYPE, ConfigureDoorPayload.STREAM_CODEC, ModNetwork::onConfigure);
        registrar.playToServer(DismantleDoorPayload.TYPE, DismantleDoorPayload.STREAM_CODEC, ModNetwork::onDismantle);
        registrar.playToServer(StageCompletePayload.TYPE, StageCompletePayload.STREAM_CODEC, ModNetwork::onStageComplete);
        registrar.playToServer(PanelActionPayload.TYPE, PanelActionPayload.STREAM_CODEC, ModNetwork::onPanelAction);
    }

    private static void onConfigure(ConfigureDoorPayload msg, IPayloadContext ctx) {
        HermoDoorBlockEntity door = passportDoor(ctx, msg.pos());
        if (door != null) {
            door.applyPassport((ServerPlayer) ctx.player(), msg.stageMask(), msg.difficulty(), msg.mirrored());
            ctx.player().closeContainer();
        }
    }

    private static void onDismantle(DismantleDoorPayload msg, IPayloadContext ctx) {
        HermoDoorBlockEntity door = passportDoor(ctx, msg.pos());
        if (door != null) {
            door.dismantle((ServerPlayer) ctx.player());
        }
    }

    private static void onStageComplete(StageCompletePayload msg, IPayloadContext ctx) {
        HermoDoorBlockEntity door = panelDoor(ctx, msg.pos());
        Stage stage = Stage.byIndex(msg.stage());
        if (door != null && stage != null) {
            door.completeStage((ServerPlayer) ctx.player(), stage, msg.proof());
        }
    }

    private static void onPanelAction(PanelActionPayload msg, IPayloadContext ctx) {
        HermoDoorBlockEntity door = panelDoor(ctx, msg.pos());
        HermoDoorBlockEntity.PanelAction action = HermoDoorBlockEntity.PanelAction.byId(msg.action());
        if (door != null && action != null) {
            door.panelAction((ServerPlayer) ctx.player(), action, msg.value());
        }
    }

    /** The door, if the sender really has its control panel open and is standing next to it. */
    @Nullable
    private static HermoDoorBlockEntity panelDoor(IPayloadContext ctx, BlockPos pos) {
        if (ctx.player() instanceof ServerPlayer player
                && player.containerMenu instanceof DoorPanelMenu menu
                && menu.pos().equals(pos)
                && menu.stillValid(player)) {
            return menu.door(player);
        }
        return null;
    }

    /** The door, if the sender really has its passport open and is standing next to it. */
    @Nullable
    private static HermoDoorBlockEntity passportDoor(IPayloadContext ctx, BlockPos pos) {
        if (ctx.player() instanceof ServerPlayer player
                && player.containerMenu instanceof DoorPassportMenu menu
                && menu.pos().equals(pos)
                && menu.stillValid(player)) {
            return menu.door(player);
        }
        return null;
    }
}
