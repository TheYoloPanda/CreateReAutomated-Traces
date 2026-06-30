package com.typ.traces.worldgen;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;

public final class TracePlacementPlan {

    private final BlockPos anchor;
    private final BlockPos tracePos;
    private final List<BlockPos> cutBlocks;
    private final List<BlockPos> fillBlocks;
    private final List<BlockPos> cleanupBlocks;
    private final boolean natural;
    private final int lowering;
    private final int distanceSq;
    private final int totalMissingBlocks;
    private final int maxGapDepth;
    private final int unsupportedColumns;
    private final Rotation rotation;

    TracePlacementPlan(
            BlockPos anchor,
            BlockPos tracePos,
            List<BlockPos> cutBlocks,
            List<BlockPos> fillBlocks,
            List<BlockPos> cleanupBlocks,
            boolean natural,
            int lowering,
            int distanceSq,
            int totalMissingBlocks,
            int maxGapDepth,
            int unsupportedColumns) {
        this(
                anchor,
                tracePos,
                cutBlocks,
                fillBlocks,
                cleanupBlocks,
                natural,
                lowering,
                distanceSq,
                totalMissingBlocks,
                maxGapDepth,
                unsupportedColumns,
                Rotation.NONE);
    }

    TracePlacementPlan(
            BlockPos anchor,
            BlockPos tracePos,
            List<BlockPos> cutBlocks,
            List<BlockPos> fillBlocks,
            List<BlockPos> cleanupBlocks,
            boolean natural,
            int lowering,
            int distanceSq,
            int totalMissingBlocks,
            int maxGapDepth,
            int unsupportedColumns,
            Rotation rotation) {
        this.anchor = anchor.immutable();
        this.tracePos = tracePos.immutable();
        this.cutBlocks = immutablePositions(cutBlocks);
        this.fillBlocks = immutablePositions(fillBlocks);
        this.cleanupBlocks = immutablePositions(cleanupBlocks);
        this.natural = natural;
        this.lowering = lowering;
        this.distanceSq = distanceSq;
        this.totalMissingBlocks = totalMissingBlocks;
        this.maxGapDepth = maxGapDepth;
        this.unsupportedColumns = unsupportedColumns;
        this.rotation = rotation;
    }

    private static List<BlockPos> immutablePositions(List<BlockPos> positions) {
        return positions.stream().map(BlockPos::immutable).toList();
    }

    public BlockPos anchor() {
        return anchor;
    }

    public BlockPos tracePos() {
        return tracePos;
    }

    public List<BlockPos> cutBlocks() {
        return cutBlocks;
    }

    public List<BlockPos> fillBlocks() {
        return fillBlocks;
    }

    public List<BlockPos> cleanupBlocks() {
        return cleanupBlocks;
    }

    public boolean natural() {
        return natural;
    }

    public int lowering() {
        return lowering;
    }

    public int distanceSq() {
        return distanceSq;
    }

    public int totalMissingBlocks() {
        return totalMissingBlocks;
    }

    public int maxGapDepth() {
        return maxGapDepth;
    }

    public int unsupportedColumns() {
        return unsupportedColumns;
    }

    public int treeCleanupBlockCount() {
        return 0;
    }

    public Rotation rotation() {
        return rotation;
    }

    public int modifiedBlockCount() {
        return cutBlocks.size() + fillBlocks.size() + cleanupBlocks.size();
    }

    public int terrainEditCount() {
        return cutBlocks.size() + fillBlocks.size();
    }
}
