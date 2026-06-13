package com.typ.traces.api;

import java.util.Optional;

import com.typ.traces.index.TraceIndex;
import com.typ.traces.worldgen.TraceBlockDataMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class TraceApi {

    private static final TagKey<Block> ORE_NODES = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("createreautomated", "ore_nodes"));

    private TraceApi() {}

    public static boolean recordExternalNode(ServerLevel level, BlockPos nodePos, ResourceLocation nodeId) {
        return TraceIndex.record(level, nodePos, nodeId) || TraceIndex.isRecorded(level, nodePos);
    }

    public static boolean removeExternalRecord(ServerLevel level, BlockPos pos) {
        return TraceIndex.remove(level, pos);
    }

    public static boolean isRecorded(ServerLevel level, BlockPos pos) {
        return TraceIndex.isRecorded(level, pos);
    }

    public static Optional<Block> traceBlockForNode(Block nodeBlock) {
        return TraceBlockDataMap.traceBlockFor(nodeBlock);
    }

    public static Optional<Block> traceBlockForNode(BlockState nodeState) {
        return traceBlockForNode(nodeState.getBlock());
    }

    public static boolean isOreNode(BlockState state) {
        return state.is(ORE_NODES);
    }
}
