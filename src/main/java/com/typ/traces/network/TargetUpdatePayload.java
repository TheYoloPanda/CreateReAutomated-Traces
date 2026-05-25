package com.typ.traces.network;

import java.util.Optional;

import com.typ.traces.CreateReAutomatedTraces;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TargetUpdatePayload(Optional<BlockPos> target) implements CustomPacketPayload {

    public static final Type<TargetUpdatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateReAutomatedTraces.MODID, "target_update"));

    public static final StreamCodec<ByteBuf, TargetUpdatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC),
            TargetUpdatePayload::target,
            TargetUpdatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
