package com.typ.traces.worldgen;

import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class NodeOreMapping {

    private NodeOreMapping() {}

    public static Optional<Block> oreFor(ResourceLocation nodeId) {
        if (!"createreautomated".equals(nodeId.getNamespace())) return Optional.empty();
        String path = nodeId.getPath();
        if (!path.endsWith("_node")) return Optional.empty();
        if (path.startsWith("infinite_")) return Optional.empty();

        String orePath = oreNameFor(path);
        if (orePath == null) return Optional.empty();

        String oreNamespace = isZinc(path) ? "create" : "minecraft";
        ResourceLocation oreId = ResourceLocation.fromNamespaceAndPath(oreNamespace, orePath);
        Block ore = BuiltInRegistries.BLOCK.get(oreId);
        return ore == Blocks.AIR ? Optional.empty() : Optional.of(ore);
    }

    private static String oreNameFor(String nodePath) {
        String base = nodePath.substring(0, nodePath.length() - "_node".length());
        if (base.isEmpty()) return null;
        return base + "_ore";
    }

    private static boolean isZinc(String nodePath) {
        return nodePath.equals("zinc_node") || nodePath.equals("deepslate_zinc_node");
    }
}
