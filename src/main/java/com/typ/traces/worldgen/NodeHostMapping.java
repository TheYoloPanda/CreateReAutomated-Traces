package com.typ.traces.worldgen;

import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class NodeHostMapping {

    private NodeHostMapping() {}

    public static Optional<Block> hostFor(ResourceLocation nodeId) {
        if (!"createreautomated".equals(nodeId.getNamespace())) return Optional.empty();
        String path = nodeId.getPath();
        if (!path.endsWith("_node")) return Optional.empty();
        if (path.startsWith("infinite_")) return Optional.empty();

        if (path.startsWith("deepslate_")) return Optional.of(Blocks.DEEPSLATE);
        if (path.startsWith("nether_")) return Optional.of(Blocks.NETHERRACK);
        return Optional.of(Blocks.STONE);
    }
}
