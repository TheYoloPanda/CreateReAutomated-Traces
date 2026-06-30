package com.typ.traces.network;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import com.typ.traces.CreateReAutomatedTraces;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

public record SelectionUpdatePayload(InteractionHand hand, Set<ResourceLocation> selected) implements CustomPacketPayload {

    public static final int MAX_SELECTED_NODES = 512;

    public static final Type<SelectionUpdatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateReAutomatedTraces.MODID, "selection_update"));

    public static final StreamCodec<ByteBuf, SelectionUpdatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            (SelectionUpdatePayload p) -> p.hand() == InteractionHand.MAIN_HAND,
            ByteBufCodecs.collection(LinkedHashSet::new, ResourceLocation.STREAM_CODEC, MAX_SELECTED_NODES),
            (SelectionUpdatePayload p) -> new LinkedHashSet<>(p.selected()),
            (Boolean main, LinkedHashSet<ResourceLocation> set) ->
                    new SelectionUpdatePayload(main ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, set)
    );

    public SelectionUpdatePayload {
        hand = Objects.requireNonNull(hand, "hand");
        selected = Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(selected, "selected")));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
