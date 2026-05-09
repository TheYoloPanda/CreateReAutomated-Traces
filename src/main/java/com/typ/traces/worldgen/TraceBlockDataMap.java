package com.typ.traces.worldgen;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.typ.traces.CreateReAutomatedTraces;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

public final class TraceBlockDataMap {

    public record TraceBlockEntry(Block block) {
        public static final Codec<TraceBlockEntry> CODEC = RecordCodecBuilder.create(inst ->
                inst.group(
                        BuiltInRegistries.BLOCK.byNameCodec().fieldOf("trace_block").forGetter(TraceBlockEntry::block)
                ).apply(inst, TraceBlockEntry::new));
    }

    public static final DataMapType<Block, TraceBlockEntry> TYPE = DataMapType.builder(
            ResourceLocation.fromNamespaceAndPath(CreateReAutomatedTraces.MODID, "trace_block_for_node"),
            Registries.BLOCK,
            TraceBlockEntry.CODEC
    ).build();

    private static final Set<ResourceLocation> WARNED_MISSING = ConcurrentHashMap.newKeySet();
    private static final int WARN_CAP = 1024;

    private TraceBlockDataMap() {}

    public static void register(RegisterDataMapTypesEvent event) {
        event.register(TYPE);
    }

    public static Optional<Block> traceBlockFor(Block nodeBlock) {
        TraceBlockEntry data = nodeBlock.builtInRegistryHolder().getData(TYPE);
        return data == null ? Optional.empty() : Optional.of(data.block());
    }

    public static void warnMissingOnce(ResourceLocation nodeId) {
        if (WARNED_MISSING.size() >= WARN_CAP) return;
        if (WARNED_MISSING.add(nodeId)) {
            CreateReAutomatedTraces.LOGGER.warn(
                    "no trace_block_for_node mapping for {}, skipping trace placement", nodeId);
        }
    }
}
