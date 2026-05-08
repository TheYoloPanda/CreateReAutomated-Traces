package com.typ.traces.worldgen;

import java.util.Optional;
import java.util.OptionalInt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public final class SurfaceFinder {

    private static final int MAX_FOOTPRINT_VARIANCE = 4;

    private SurfaceFinder() {}

    public static Optional<BlockPos> findAnchor(ServerLevel level, BlockPos nodePos, Vec3i footprint) {
        return level.dimension().equals(Level.NETHER)
                ? findNetherAnchor(level, nodePos, footprint)
                : findOverworldAnchor(level, nodePos, footprint);
    }

    private static Optional<BlockPos> findOverworldAnchor(ServerLevel level, BlockPos nodePos, Vec3i footprint) {
        int sx = nodePos.getX();
        int sz = nodePos.getZ();

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int dx = 0; dx < footprint.getX(); dx++) {
            for (int dz = 0; dz < footprint.getZ(); dz++) {
                OptionalInt y = findOpenGroundY(level, sx + dx, sz + dz);
                if (y.isEmpty()) return Optional.empty();
                int yi = y.getAsInt();
                if (yi < minY) minY = yi;
                if (yi > maxY) maxY = yi;
            }
        }
        if (maxY - minY > MAX_FOOTPRINT_VARIANCE) return Optional.empty();
        return Optional.of(new BlockPos(sx, maxY, sz));
    }

    private static OptionalInt findOpenGroundY(ServerLevel level, int x, int z) {
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

    private static Optional<BlockPos> findNetherAnchor(ServerLevel level, BlockPos nodePos, Vec3i footprint) {
        int sx = nodePos.getX();
        int sz = nodePos.getZ();

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int dx = 0; dx < footprint.getX(); dx++) {
            for (int dz = 0; dz < footprint.getZ(); dz++) {
                OptionalInt y = scanNetherUp(level, sx + dx, nodePos.getY(), sz + dz);
                if (y.isEmpty()) return Optional.empty();
                int yi = y.getAsInt();
                if (yi < minY) minY = yi;
                if (yi > maxY) maxY = yi;
            }
        }
        if (maxY - minY > MAX_FOOTPRINT_VARIANCE) return Optional.empty();
        return Optional.of(new BlockPos(sx, maxY, sz));
    }

    private static OptionalInt scanNetherUp(ServerLevel level, int x, int startY, int z) {
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
}
