package com.typ.traces.network;

import java.util.HashSet;
import java.util.Set;

import com.typ.traces.CreateReAutomatedTraces;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

public record SelectionUpdatePayload(InteractionHand hand, Set<ResourceLocation> selected) implements CustomPacketPayload {

    public static final Type<SelectionUpdatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateReAutomatedTraces.MODID, "selection_update"));

    public static final StreamCodec<ByteBuf, SelectionUpdatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            (SelectionUpdatePayload p) -> p.hand() == InteractionHand.MAIN_HAND,
            ResourceLocation.STREAM_CODEC.<HashSet<ResourceLocation>>apply(ByteBufCodecs.collection(HashSet::new)),
            (SelectionUpdatePayload p) -> new HashSet<>(p.selected()),
            (Boolean main, HashSet<ResourceLocation> set) ->
                    new SelectionUpdatePayload(main ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, set)
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
