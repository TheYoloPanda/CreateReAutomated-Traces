package com.typ.traces.worldgen;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

public final class SurfaceFinder {

    private SurfaceFinder() {}

    public static Optional<BlockPos> findSurface(ServerLevel level, BlockPos nodePos) {
        return level.dimension().equals(Level.NETHER)
                ? findNetherSurface(level, nodePos)
                : findOverworldSurface(level, nodePos.getX(), nodePos.getZ());
    }

    private static Optional<BlockPos> findOverworldSurface(ServerLevel level, int x, int z) {
        BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z));
        if (level.getBlockState(top.below()).is(Blocks.GRASS_BLOCK)) {
            return Optional.of(top);
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> findNetherSurface(ServerLevel level, BlockPos nodePos) {
        int x = nodePos.getX();
        int z = nodePos.getZ();
        int maxY = Math.min(120, nodePos.getY() + 64);
        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        for (int y = nodePos.getY() + 1; y <= maxY; y++) {
            cur.set(x, y, z);
            if (level.getBlockState(cur).isAir()
                    && level.getBlockState(cur.below()).is(Blocks.NETHERRACK)) {
                return Optional.of(new BlockPos(x, y, z));
            }
        }
        return Optional.empty();
    }
}
