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

public final class OreForNodeDataMap {

    public record OreForNode(Block ore) {
        public static final Codec<OreForNode> CODEC = RecordCodecBuilder.create(inst ->
                inst.group(
                        BuiltInRegistries.BLOCK.byNameCodec().fieldOf("ore").forGetter(OreForNode::ore)
                ).apply(inst, OreForNode::new));
    }

    public static final DataMapType<Block, OreForNode> TYPE = DataMapType.builder(
            ResourceLocation.fromNamespaceAndPath(CreateReAutomatedTraces.MODID, "ore_for_node"),
            Registries.BLOCK,
            OreForNode.CODEC
    ).build();

    private static final Set<ResourceLocation> WARNED_MISSING = ConcurrentHashMap.newKeySet();
    private static final int WARN_CAP = 1024;

    private OreForNodeDataMap() {}

    public static void register(RegisterDataMapTypesEvent event) {
        event.register(TYPE);
    }

    public static Optional<Block> oreFor(Block nodeBlock) {
        OreForNode data = nodeBlock.builtInRegistryHolder().getData(TYPE);
        return data == null ? Optional.empty() : Optional.of(data.ore());
    }

    public static void warnMissingOnce(ResourceLocation nodeId) {
        if (WARNED_MISSING.size() >= WARN_CAP) return;
        if (WARNED_MISSING.add(nodeId)) {
            CreateReAutomatedTraces.LOGGER.warn(
                    "no ore_for_node mapping for {}, skipping trace placement", nodeId);
        }
    }
}
