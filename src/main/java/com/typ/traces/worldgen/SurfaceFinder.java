package com.typ.traces.worldgen;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class SurfaceFinder {

    private static final int MAX_FOOTPRINT_VARIANCE = 4;
    static final int SEARCH_RADIUS = 8;
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

        for (int[] off : SEARCH_OFFSETS) {
            int ox = originX + off[0];
            int oz = originZ + off[1];
            Optional<BlockPos> result = nether
                    ? tryNetherAnchor(level, ox, oz, footprint, nodePos.getY(), structureBoxes)
                    : tryOverworldAnchor(level, ox, oz, footprint, structureBoxes);
            if (result.isPresent()) return result;
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> tryOverworldAnchor(WorldGenLevel level, int originX, int originZ, Vec3i footprint, List<BoundingBox> structureBoxes) {
        if (!chunksLoaded(level, originX, originZ, footprint)) return Optional.empty();

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int dx = 0; dx < footprint.getX(); dx++) {
            for (int dz = 0; dz < footprint.getZ(); dz++) {
                OptionalInt y = findOpenGroundY(level, originX + dx, originZ + dz);
                if (y.isEmpty()) return Optional.empty();
                int yi = y.getAsInt();
                if (yi < minY) minY = yi;
                if (yi > maxY) maxY = yi;
            }
        }
        if (maxY - minY > MAX_FOOTPRINT_VARIANCE) return Optional.empty();
        if (overlapsStructure(originX, maxY, originZ, footprint, structureBoxes)) return Optional.empty();
        return Optional.of(new BlockPos(originX, maxY, originZ));
    }

    private static OptionalInt findOpenGroundY(WorldGenLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        int worldMinY = level.getMinBuildHeight();
        for (int y = top; y >= worldMinY; y--) {
            cur.set(x, y, z);
            BlockState state = level.getBlockState(cur);
            if (isPassthrough(state)) continue;
            return OptionalInt.of(y + 1);
        }
        return OptionalInt.empty();
    }

    private static boolean isPassthrough(BlockState state) {
        return state.isAir()
                || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.LOGS)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.REPLACEABLE)
                || state.is(Blocks.SNOW);
    }

    private static Optional<BlockPos> tryNetherAnchor(WorldGenLevel level, int originX, int originZ, Vec3i footprint, int nodeY, List<BoundingBox> structureBoxes) {
        if (!chunksLoaded(level, originX, originZ, footprint)) return Optional.empty();

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int dx = 0; dx < footprint.getX(); dx++) {
            for (int dz = 0; dz < footprint.getZ(); dz++) {
                OptionalInt y = scanNetherUp(level, originX + dx, nodeY, originZ + dz);
                if (y.isEmpty()) return Optional.empty();
                int yi = y.getAsInt();
                if (yi < minY) minY = yi;
                if (yi > maxY) maxY = yi;
            }
        }
        if (maxY - minY > MAX_FOOTPRINT_VARIANCE) return Optional.empty();
        if (overlapsStructure(originX, maxY, originZ, footprint, structureBoxes)) return Optional.empty();
        return Optional.of(new BlockPos(originX, maxY, originZ));
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

    private static OptionalInt scanNetherUp(WorldGenLevel level, int x, int startY, int z) {
        int maxScanY = Math.min(120, startY + 64);
        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        for (int y = startY + 1; y <= maxScanY; y++) {
            cur.set(x, y, z);
            if (level.getBlockState(cur).isAir()
                    && level.getBlockState(cur.below()).is(Blocks.NETHERRACK)) {
                return OptionalInt.of(y);
            }
        }
        return OptionalInt.empty();
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
}
