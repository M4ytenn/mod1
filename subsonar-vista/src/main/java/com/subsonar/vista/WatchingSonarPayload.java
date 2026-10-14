package com.subsonar.vista;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server, once a second: "I am looking at the sonar feed on the TV at this position". */
public record WatchingSonarPayload(BlockPos tv) implements CustomPacketPayload {
    public static final Type<WatchingSonarPayload> TYPE = new Type<>(SubSonarVista.id("watching_sonar"));
    public static final StreamCodec<ByteBuf, WatchingSonarPayload> STREAM_CODEC =
            BlockPos.STREAM_CODEC.map(WatchingSonarPayload::new, WatchingSonarPayload::tv);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
