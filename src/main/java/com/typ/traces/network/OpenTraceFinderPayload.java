package com.typ.traces.network;

import com.typ.traces.CreateReAutomatedTraces;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

public record OpenTraceFinderPayload(InteractionHand hand) implements CustomPacketPayload {

    public static final Type<OpenTraceFinderPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateReAutomatedTraces.MODID, "open_trace_finder"));

    public static final StreamCodec<ByteBuf, OpenTraceFinderPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            (OpenTraceFinderPayload p) -> p.hand() == InteractionHand.MAIN_HAND,
            (Boolean main) -> new OpenTraceFinderPayload(main ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND)
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
