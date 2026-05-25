package com.typ.traces.component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public record TrackedNodesComponent(Set<ResourceLocation> selected,
                                     Set<GlobalPos> discovered) {

    public static final TrackedNodesComponent EMPTY =
            new TrackedNodesComponent(Set.of(), Set.of());

    public TrackedNodesComponent {
        selected = Set.copyOf(selected);
        discovered = Set.copyOf(discovered);
    }

    private static final Codec<GlobalPos> DISCOVERED_ENTRY_CODEC =
            Codec.either(GlobalPos.CODEC, Codec.LONG)
                    .xmap(e -> e.map(pos -> pos, posLong ->
                                    GlobalPos.of(Level.OVERWORLD, BlockPos.of(posLong))),
                            Either::left);

    public static final Codec<TrackedNodesComponent> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ResourceLocation.CODEC.listOf()
                    .xmap((List<ResourceLocation> l) -> (Set<ResourceLocation>) Set.copyOf(l),
                          (Set<ResourceLocation> s) -> List.copyOf(s))
                    .optionalFieldOf("selected", Set.of())
                    .forGetter(TrackedNodesComponent::selected),
            DISCOVERED_ENTRY_CODEC.listOf()
                    .xmap((List<GlobalPos> l) -> (Set<GlobalPos>) Set.copyOf(l),
                          (Set<GlobalPos> s) -> List.copyOf(s))
                    .optionalFieldOf("discovered", Set.of())
                    .forGetter(TrackedNodesComponent::discovered)
    ).apply(inst, TrackedNodesComponent::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrackedNodesComponent> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.collection(HashSet::new)),
            (TrackedNodesComponent c) -> new HashSet<>(c.selected()),
            GlobalPos.STREAM_CODEC.apply(ByteBufCodecs.collection(HashSet::new)),
            (TrackedNodesComponent c) -> new HashSet<>(c.discovered()),
            TrackedNodesComponent::new
    );

    public TrackedNodesComponent withSelected(Set<ResourceLocation> newSelected) {
        return new TrackedNodesComponent(newSelected, discovered);
    }

    public TrackedNodesComponent withDiscovered(Set<GlobalPos> newDiscovered) {
        return new TrackedNodesComponent(selected, newDiscovered);
    }

    public boolean isTracking(ResourceLocation nodeId) {
        return selected.contains(nodeId);
    }

    public boolean isDiscovered(GlobalPos pos) {
        return discovered.contains(pos);
    }
}
