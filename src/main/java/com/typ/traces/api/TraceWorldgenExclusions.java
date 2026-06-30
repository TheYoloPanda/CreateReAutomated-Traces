package com.typ.traces.api;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

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

    public static boolean consumeGeneratedTraceSuppression(WorldGenLevel level, BlockPos nodePos) {
        return consume(level.getLevel().dimension(), nodePos);
    }

    public static boolean consumeGeneratedTraceSuppression(ServerLevel level, BlockPos nodePos) {
        return consume(level.dimension(), nodePos);
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

    static void suppress(ResourceKey<Level> dimension, BlockPos nodePos) {
        long packedPos = nodePos.asLong();
        SUPPRESSED.compute(dimension, (key, positions) -> {
            Set<Long> next = positions == null ? ConcurrentHashMap.<Long>newKeySet() : positions;
            next.add(packedPos);
            return next;
        });
    }

    static boolean isSuppressed(ResourceKey<Level> dimension, BlockPos nodePos) {
        Set<Long> positions = SUPPRESSED.get(dimension);
        return positions != null && positions.contains(nodePos.asLong());
    }

    static boolean consume(ResourceKey<Level> dimension, BlockPos nodePos) {
        return remove(dimension, nodePos);
    }

    static void clear(ResourceKey<Level> dimension, BlockPos nodePos) {
        remove(dimension, nodePos);
    }

    private static boolean remove(ResourceKey<Level> dimension, BlockPos nodePos) {
        long packedPos = nodePos.asLong();
        AtomicBoolean removed = new AtomicBoolean(false);
        SUPPRESSED.computeIfPresent(dimension, (key, positions) -> {
            removed.set(positions.remove(packedPos));
            return positions.isEmpty() ? null : positions;
        });
        return removed.get();
    }
}
