package com.typ.traces.network;

import java.util.ArrayList;
import java.util.List;

import com.typ.traces.CreateReAutomatedTraces;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VisibleTracesPayload(List<Entry> added, List<Long> removed) implements CustomPacketPayload {

    public record Entry(long posLong, ResourceLocation nodeId, int colorArgb) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Entry::posLong,
                ResourceLocation.STREAM_CODEC, Entry::nodeId,
                ByteBufCodecs.INT, Entry::colorArgb,
                Entry::new
        );
    }

    public static final Type<VisibleTracesPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateReAutomatedTraces.MODID, "visible_traces"));

    public static final StreamCodec<ByteBuf, VisibleTracesPayload> STREAM_CODEC = StreamCodec.composite(
            Entry.STREAM_CODEC.<List<Entry>>apply(ByteBufCodecs.collection(ArrayList::new)),
            VisibleTracesPayload::added,
            ByteBufCodecs.VAR_LONG.<List<Long>>apply(ByteBufCodecs.collection(ArrayList::new)),
            VisibleTracesPayload::removed,
            VisibleTracesPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
