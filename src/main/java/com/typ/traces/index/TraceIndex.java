package com.typ.traces.index;

import java.util.function.Consumer;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public final class TraceIndex {

    private TraceIndex() {}

    public static boolean record(ServerLevel level, BlockPos pos, ResourceLocation nodeId) {
        return TraceIndexSavedData.get(level).record(nodeId, pos);
    }

    public static boolean remove(ServerLevel level, BlockPos pos) {
        return TraceIndexSavedData.get(level).remove(pos);
    }

    public static boolean isScanned(ServerLevel level, long chunkLong) {
        return TraceIndexSavedData.get(level).isScanned(chunkLong);
    }

    public static boolean markScanned(ServerLevel level, long chunkLong) {
        return TraceIndexSavedData.get(level).markScanned(chunkLong);
    }

    public static boolean isRecorded(ServerLevel level, BlockPos pos) {
        return TraceIndexSavedData.get(level).isRecorded(pos);
    }

    public static int size(ServerLevel level) {
        return TraceIndexSavedData.get(level).size();
    }

    public static void forEachInRange(ServerLevel level, ChunkPos center, int radiusChunks,
                                       Predicate<ResourceLocation> nodeFilter,
                                       Consumer<TraceIndexSavedData.TraceRecord> consumer) {
        TraceIndexSavedData.get(level).forEachInRange(center.x, center.z, radiusChunks, nodeFilter, consumer);
    }
}
