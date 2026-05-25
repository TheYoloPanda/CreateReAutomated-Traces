package com.typ.traces.worldgen;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.typ.traces.CreateReAutomatedTraces;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

public final class TraceBlockDataMap {

    private static final Codec<Integer> HEX_COLOR_CODEC = Codec.STRING.comapFlatMap(
            s -> {
                if (s.length() == 7 && s.charAt(0) == '#') {
                    try {
                        int rgb = Integer.parseInt(s.substring(1), 16);
                        return DataResult.success(0xFF000000 | (rgb & 0xFFFFFF));
                    } catch (NumberFormatException ignored) {
                        return DataResult.error(() -> "invalid hex color: " + s);
                    }
                }
                return DataResult.error(() -> "expected #RRGGBB, got: " + s);
            },
            rgb -> String.format("#%06X", rgb & 0xFFFFFF));

    private static final Codec<Integer> ARRAY_COLOR_CODEC = Codec.INT.listOf().comapFlatMap(
            list -> {
                if (list.size() != 3) {
                    return DataResult.error(() -> "expected [r,g,b], got " + list.size() + " elements");
                }
                for (int v : list) {
                    if (v < 0 || v > 255) return DataResult.error(() -> "color component out of range: " + v);
                }
                return DataResult.success(0xFF000000 | (list.get(0) << 16) | (list.get(1) << 8) | list.get(2));
            },
            rgb -> List.of((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF));

    private static final Codec<Integer> BEAM_COLOR_CODEC = Codec.either(HEX_COLOR_CODEC, ARRAY_COLOR_CODEC)
            .xmap(e -> e.map(i -> i, i -> i), Either::left);

    public record TraceBlockEntry(Block block, Optional<Integer> beamColor) {
        public static final Codec<TraceBlockEntry> CODEC = RecordCodecBuilder.create(inst ->
                inst.group(
                        BuiltInRegistries.BLOCK.byNameCodec().fieldOf("trace_block").forGetter(TraceBlockEntry::block),
                        BEAM_COLOR_CODEC.optionalFieldOf("beam_color").forGetter(TraceBlockEntry::beamColor)
                ).apply(inst, TraceBlockEntry::new));
    }

    public static final DataMapType<Block, TraceBlockEntry> TYPE = DataMapType.builder(
            ResourceLocation.fromNamespaceAndPath(CreateReAutomatedTraces.MODID, "trace_block_for_node"),
            Registries.BLOCK,
            TraceBlockEntry.CODEC
    ).synced(TraceBlockEntry.CODEC, false).build();

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

    public static Optional<Integer> beamColorFor(Block nodeBlock) {
        TraceBlockEntry data = nodeBlock.builtInRegistryHolder().getData(TYPE);
        return data == null ? Optional.empty() : data.beamColor();
    }

    public static void warnMissingOnce(ResourceLocation nodeId) {
        if (WARNED_MISSING.size() >= WARN_CAP) return;
        if (WARNED_MISSING.add(nodeId)) {
            CreateReAutomatedTraces.LOGGER.warn(
                    "no trace_block_for_node mapping for {}, skipping trace placement", nodeId);
        }
    }
}
