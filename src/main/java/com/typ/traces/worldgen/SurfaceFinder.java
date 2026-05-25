package com.typ.traces.worldgen;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class SurfaceFinder {

    private static final int MAX_FOOTPRINT_VARIANCE = 4;
    static final int SEARCH_RADIUS = 8;
    private static final int DISTANCE_WEIGHT = 6;
    private static final int HEIGHT_VARIANCE_WEIGHT = 48;
    private static final int EXTRA_VARIANCE_WEIGHT = 180;
    private static final int FLUID_COLUMN_PENALTY = 18;
    private static final int SOFT_VEGETATION_PENALTY = 12;
    private static final int LEAF_PENALTY = 28;
    private static final int LOG_PENALTY = 1200;
    private static final int COLUMNAR_PLANT_PENALTY = 360;
    private static final int HARD_OBSTACLE_PENALTY = 120;
    private static final int NO_GROUND_PENALTY = 20000;
    private static final int STRUCTURE_OVERLAP_PENALTY = 1_000_000;
    private static final int[][] SEARCH_OFFSETS;

    static {
        int side = SEARCH_RADIUS * 2 + 1;
        int[][] offsets = new int[side * side][2];
        int idx = 0;
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                offsets[idx][0] = dx;
                offsets[idx][1] = dz;
                idx++;
            }
        }
        Arrays.sort(offsets, Comparator
                .comparingInt((int[] o) -> o[0] * o[0] + o[1] * o[1])
                .thenComparingDouble(o -> Math.atan2(o[1], o[0])));
        SEARCH_OFFSETS = offsets;
    }

    private SurfaceFinder() {}

    public static Optional<BlockPos> findAnchor(WorldGenLevel level, BlockPos nodePos, Vec3i footprint, List<BoundingBox> structureBoxes) {
        int originX = nodePos.getX() - Math.floorDiv(footprint.getX(), 2);
        int originZ = nodePos.getZ() - Math.floorDiv(footprint.getZ(), 2);
        boolean nether = level.getLevel().dimension().equals(Level.NETHER);
        AnchorCandidate best = null;

        for (int[] off : SEARCH_OFFSETS) {
            int ox = originX + off[0];
            int oz = originZ + off[1];
            Optional<AnchorCandidate> result = nether
                    ? scoreNetherAnchor(level, ox, oz, footprint, nodePos.getY(), off, structureBoxes)
                    : scoreOverworldAnchor(level, ox, oz, footprint, off, structureBoxes);
            if (result.isEmpty()) continue;
            AnchorCandidate candidate = result.get();
            if (best == null || candidate.score() < best.score()) {
                best = candidate;
            }
        }

        if (best != null) return Optional.of(best.anchor());
        return Optional.of(fallbackAnchor(level, nodePos, footprint, structureBoxes));
    }

    private static Optional<AnchorCandidate> scoreOverworldAnchor(WorldGenLevel level, int originX, int originZ, Vec3i footprint,
                                                                  int[] offset, List<BoundingBox> structureBoxes) {
        if (!chunksLoaded(level, originX, originZ, footprint)) return Optional.empty();

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int penalty = distancePenalty(offset);
        for (int dx = 0; dx < footprint.getX(); dx++) {
            for (int dz = 0; dz < footprint.getZ(); dz++) {
                ColumnProbe probe = findOpenGroundY(level, originX + dx, originZ + dz);
                int yi = probe.y();
                if (yi < minY) minY = yi;
                if (yi > maxY) maxY = yi;
                penalty += probe.penalty();
            }
        }
        int variance = maxY - minY;
        penalty += variance * HEIGHT_VARIANCE_WEIGHT;
        if (variance > MAX_FOOTPRINT_VARIANCE) {
            penalty += (variance - MAX_FOOTPRINT_VARIANCE) * EXTRA_VARIANCE_WEIGHT;
        }
        if (overlapsStructure(originX, maxY, originZ, footprint, structureBoxes)) {
            penalty += STRUCTURE_OVERLAP_PENALTY;
        }
        return Optional.of(new AnchorCandidate(new BlockPos(originX, maxY, originZ), penalty));
    }

    private static ColumnProbe findOpenGroundY(WorldGenLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        int worldMinY = level.getMinBuildHeight();
        int penalty = 0;
        for (int y = top; y >= worldMinY; y--) {
            cur.set(x, y, z);
            BlockState state = level.getBlockState(cur);
            if (!state.getFluidState().isEmpty()) {
                penalty += FLUID_COLUMN_PENALTY;
                continue;
            }
            if (isFrozenWaterSurface(level, cur, state)) {
                penalty += FLUID_COLUMN_PENALTY;
                continue;
            }
            int passthroughPenalty = passthroughPenalty(state);
            if (passthroughPenalty >= 0) {
                penalty += passthroughPenalty;
                continue;
            }
            if (state.is(BlockTags.LOGS)) {
                penalty += LOG_PENALTY;
                continue;
            }
            if (isColumnarPlant(state)) {
                penalty += COLUMNAR_PLANT_PENALTY;
                continue;
            }
            if (!isValidGround(state)) {
                penalty += HARD_OBSTACLE_PENALTY;
            }
            return new ColumnProbe(y + 1, penalty);
        }
        return new ColumnProbe(worldMinY + 1, penalty + NO_GROUND_PENALTY);
    }

    private static int passthroughPenalty(BlockState state) {
        if (state.isAir()) return 0;
        if (state.is(BlockTags.LEAVES)) return LEAF_PENALTY;
        if (state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(Blocks.SNOW)) {
            return SOFT_VEGETATION_PENALTY;
        }
        if (state.is(BlockTags.REPLACEABLE)) return SOFT_VEGETATION_PENALTY;
        return -1;
    }

    private static boolean isValidGround(BlockState state) {
        return !state.is(BlockTags.LOGS)
                && !state.is(BlockTags.LEAVES)
                && !state.is(BlockTags.REPLACEABLE);
    }

    private static boolean isFrozenWaterSurface(WorldGenLevel level, BlockPos pos, BlockState state) {
        if (!state.is(Blocks.ICE)
                && !state.is(Blocks.FROSTED_ICE)
                && !state.is(Blocks.PACKED_ICE)
                && !state.is(Blocks.BLUE_ICE)) return false;
        if (pos.getY() - 1 < level.getMinBuildHeight()) return false;
        return level.getFluidState(pos.below()).is(FluidTags.WATER);
    }

    private static Optional<AnchorCandidate> scoreNetherAnchor(WorldGenLevel level, int originX, int originZ, Vec3i footprint,
                                                               int nodeY, int[] offset, List<BoundingBox> structureBoxes) {
        if (!chunksLoaded(level, originX, originZ, footprint)) return Optional.empty();

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int penalty = distancePenalty(offset);
        for (int dx = 0; dx < footprint.getX(); dx++) {
            for (int dz = 0; dz < footprint.getZ(); dz++) {
                ColumnProbe probe = scanNetherUp(level, originX + dx, nodeY, originZ + dz);
                int yi = probe.y();
                if (yi < minY) minY = yi;
                if (yi > maxY) maxY = yi;
                penalty += probe.penalty();
            }
        }
        int variance = maxY - minY;
        penalty += variance * HEIGHT_VARIANCE_WEIGHT;
        if (variance > MAX_FOOTPRINT_VARIANCE) {
            penalty += (variance - MAX_FOOTPRINT_VARIANCE) * EXTRA_VARIANCE_WEIGHT;
        }
        if (overlapsStructure(originX, maxY, originZ, footprint, structureBoxes)) {
            penalty += STRUCTURE_OVERLAP_PENALTY;
        }
        return Optional.of(new AnchorCandidate(new BlockPos(originX, maxY, originZ), penalty));
    }

    private static boolean overlapsStructure(int originX, int anchorY, int originZ, Vec3i footprint, List<BoundingBox> structureBoxes) {
        if (structureBoxes.isEmpty()) return false;
        BoundingBox candidate = new BoundingBox(
                originX, anchorY, originZ,
                originX + footprint.getX() - 1,
                anchorY + footprint.getY() - 1,
                originZ + footprint.getZ() - 1);
        return StructureGuard.intersectsAny(structureBoxes, candidate);
    }

    private static ColumnProbe scanNetherUp(WorldGenLevel level, int x, int startY, int z) {
        int maxScanY = Math.min(120, startY + 64);
        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        int penalty = 0;
        for (int y = startY + 1; y <= maxScanY; y++) {
            cur.set(x, y, z);
            BlockState state = level.getBlockState(cur);
            BlockState below = level.getBlockState(cur.below());
            int passthroughPenalty = passthroughPenalty(state);
            boolean open = state.isAir() || passthroughPenalty >= 0 || !state.getFluidState().isEmpty();
            if (open && isNetherGround(below)) {
                int groundPenalty = below.is(Blocks.NETHERRACK) ? 0 : HARD_OBSTACLE_PENALTY;
                int fluidPenalty = state.getFluidState().isEmpty() ? 0 : FLUID_COLUMN_PENALTY;
                return new ColumnProbe(y, penalty + Math.max(0, passthroughPenalty) + groundPenalty + fluidPenalty);
            }
            if (state.is(BlockTags.LOGS)) {
                penalty += LOG_PENALTY;
            } else if (isColumnarPlant(state)) {
                penalty += COLUMNAR_PLANT_PENALTY;
            } else if (!state.isAir()) {
                penalty += HARD_OBSTACLE_PENALTY / 4;
            }
        }
        int fallbackY = Math.max(level.getMinBuildHeight() + 1,
                Math.min(level.getMaxBuildHeight() - 2, startY + 1));
        return new ColumnProbe(fallbackY, penalty + NO_GROUND_PENALTY);
    }

    private static boolean isNetherGround(BlockState state) {
        return state.is(Blocks.NETHERRACK)
                || (!state.canBeReplaced() && state.getFluidState().isEmpty());
    }

    private static boolean isColumnarPlant(BlockState state) {
        return state.is(Blocks.BAMBOO)
                || state.is(Blocks.BAMBOO_SAPLING)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.SUGAR_CANE);
    }

    private static boolean chunksLoaded(WorldGenLevel level, int originX, int originZ, Vec3i footprint) {
        int x0 = originX >> 4;
        int z0 = originZ >> 4;
        int x1 = (originX + footprint.getX() - 1) >> 4;
        int z1 = (originZ + footprint.getZ() - 1) >> 4;
        return level.hasChunk(x0, z0)
                && level.hasChunk(x1, z0)
                && level.hasChunk(x0, z1)
                && level.hasChunk(x1, z1);
    }

    private static int distancePenalty(int[] offset) {
        return (offset[0] * offset[0] + offset[1] * offset[1]) * DISTANCE_WEIGHT;
    }

    private static BlockPos fallbackAnchor(WorldGenLevel level, BlockPos nodePos, Vec3i footprint, List<BoundingBox> structureBoxes) {
        int originX = nodePos.getX() - Math.floorDiv(footprint.getX(), 2);
        int originZ = nodePos.getZ() - Math.floorDiv(footprint.getZ(), 2);
        boolean nether = level.getLevel().dimension().equals(Level.NETHER);
        ColumnProbe probe = nether
                ? scanNetherUp(level, nodePos.getX(), nodePos.getY(), nodePos.getZ())
                : findOpenGroundY(level, nodePos.getX(), nodePos.getZ());
        int y = probe.y();
        if (overlapsStructure(originX, y, originZ, footprint, structureBoxes)) {
            y = Math.min(level.getMaxBuildHeight() - footprint.getY() - 1, y + footprint.getY());
        }
        return new BlockPos(originX, y, originZ);
    }

    private record AnchorCandidate(BlockPos anchor, int score) {}
    private record ColumnProbe(int y, int penalty) {}
}
