package com.typ.traces.api;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

public final class TraceWorldgenExclusions {

    private static final ConcurrentMap<ResourceKey<Level>, Set<Long>> SUPPRESSED = new ConcurrentHashMap<>();

    private TraceWorldgenExclusions() {}

    public static void suppressGeneratedTrace(WorldGenLevel level, BlockPos nodePos) {
        suppress(level.getLevel().dimension(), nodePos);
    }

    public static void suppressGeneratedTrace(ServerLevel level, BlockPos nodePos) {
        suppress(level.dimension(), nodePos);
    }

    public static boolean isGeneratedTraceSuppressed(WorldGenLevel level, BlockPos nodePos) {
        return isSuppressed(level.getLevel().dimension(), nodePos);
    }

    public static boolean isGeneratedTraceSuppressed(ServerLevel level, BlockPos nodePos) {
        return isSuppressed(level.dimension(), nodePos);
    }

    public static void clearGeneratedTraceSuppression(WorldGenLevel level, BlockPos nodePos) {
        clear(level.getLevel().dimension(), nodePos);
    }

    public static void clearGeneratedTraceSuppression(ServerLevel level, BlockPos nodePos) {
        clear(level.dimension(), nodePos);
    }

    public static void clearAllGeneratedTraceSuppressions() {
        SUPPRESSED.clear();
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        clearAllGeneratedTraceSuppressions();
    }

    private static void suppress(ResourceKey<Level> dimension, BlockPos nodePos) {
        SUPPRESSED.computeIfAbsent(dimension, key -> ConcurrentHashMap.newKeySet()).add(nodePos.asLong());
    }

    private static boolean isSuppressed(ResourceKey<Level> dimension, BlockPos nodePos) {
        Set<Long> positions = SUPPRESSED.get(dimension);
        return positions != null && positions.contains(nodePos.asLong());
    }

    private static void clear(ResourceKey<Level> dimension, BlockPos nodePos) {
        Set<Long> positions = SUPPRESSED.get(dimension);
        if (positions == null) return;
        positions.remove(nodePos.asLong());
        if (positions.isEmpty()) {
            SUPPRESSED.remove(dimension, positions);
        }
    }
}
