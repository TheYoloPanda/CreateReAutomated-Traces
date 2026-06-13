package com.typ.traces.worldgen;

import java.util.List;
import java.util.Optional;

import com.github.zgraund.createreautomated.block.node.OreNodeBlock;
import com.typ.traces.CreateReAutomatedTraces;
import com.typ.traces.api.TraceWorldgenExclusions;
import com.typ.traces.index.TraceIndex;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class TraceStructurePlacer {

    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int MAX_FOUNDATION_DEPTH = 4;
    private static final int CLEAR_MARGIN = 1;
    private static final int VISIBILITY_CLEAR_HEIGHT = 5;
    private static final int FALLBACK_TRACE_RADIUS = 2;
    private static final Vec3i FALLBACK_TRACE_SIZE = new Vec3i(5, 3, 5);

    private TraceStructurePlacer() {}

    private static Block smoothify(Block raw) {
        if (raw == Blocks.COBBLESTONE) return Blocks.STONE;
        if (raw == Blocks.COBBLED_DEEPSLATE) return Blocks.DEEPSLATE;
        return raw;
    }

    public static boolean place(WorldGenLevel level, BlockPos nodePos, Block nodeBlock, ResourceLocation nodeId, RandomSource rng) {
        if (TraceWorldgenExclusions.isGeneratedTraceSuppressed(level, nodePos)) {
            return false;
        }

        Optional<Block> traceBlockOpt = TraceBlockDataMap.traceBlockFor(nodeBlock);
        if (traceBlockOpt.isEmpty()) {
            TraceBlockDataMap.warnMissingOnce(nodeId);
            return false;
        }
        Block traceBlock = traceBlockOpt.get();
        Block host = (nodeBlock instanceof OreNodeBlock onb)
                ? smoothify(onb.baseRock.getBlock())
                : Blocks.STONE;

        Optional<ResourceLocation> tmplIdOpt = TraceTemplates.pick(rng);
        if (tmplIdOpt.isEmpty()) {
            CreateReAutomatedTraces.LOGGER.warn("No trace templates loaded, using procedural fallback for {}", nodeId);
            return placeProceduralFallback(level, nodePos, nodeId, traceBlock, host);
        }
        ResourceLocation tmplId = tmplIdOpt.get();

        StructureTemplateManager mgr = level.getLevel().getStructureManager();
        Optional<StructureTemplate> tmplOpt = mgr.get(tmplId);
        if (tmplOpt.isEmpty()) {
            CreateReAutomatedTraces.LOGGER.warn("Trace template missing: {}", tmplId);
            return placeProceduralFallback(level, nodePos, nodeId, traceBlock, host);
        }
        StructureTemplate tmpl = tmplOpt.get();
        Vec3i size = tmpl.getSize();

        List<BoundingBox> structureBoxes = collectStructureBoxes(level, nodePos, size);

        Optional<BlockPos> anchorOpt = SurfaceFinder.findAnchor(level, nodePos, size, structureBoxes);
        BlockPos anchor = clampAnchorToBuildHeight(level, anchorOpt.orElse(nodePos), size);

        clearFoliage(level, anchor, size, structureBoxes);

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .addProcessor(new TracePlaceholderProcessor(traceBlock, host));

        boolean placed = tmpl.placeInWorld(level, anchor, anchor, settings, rng, PLACE_FLAGS);
        if (!placed) {
            CreateReAutomatedTraces.LOGGER.warn("Trace template placement failed for {}, using procedural fallback", nodeId);
            return placeProceduralFallback(level, nodePos, nodeId, traceBlock, host);
        }

        fillFoundation(level, anchor, size, host, traceBlock, structureBoxes);

        recordTrace(level, anchor, size, nodeId, traceBlock);
        return true;
    }

    private static List<BoundingBox> collectStructureBoxes(WorldGenLevel level, BlockPos nodePos, Vec3i size) {
        int reach = SurfaceFinder.placementRadius() + Math.max(size.getX(), size.getZ()) + CLEAR_MARGIN;
        int minCX = (nodePos.getX() - reach) >> 4;
        int maxCX = (nodePos.getX() + reach) >> 4;
        int minCZ = (nodePos.getZ() - reach) >> 4;
        int maxCZ = (nodePos.getZ() + reach) >> 4;
        return StructureGuard.collectStructureBoxes(level, minCX, minCZ, maxCX, maxCZ);
    }

    private static BlockPos clampAnchorToBuildHeight(WorldGenLevel level, BlockPos anchor, Vec3i size) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - size.getY() - 1;
        if (maxY < minY) maxY = minY;
        int y = Math.max(minY, Math.min(anchor.getY(), maxY));
        return y == anchor.getY() ? anchor : new BlockPos(anchor.getX(), y, anchor.getZ());
    }

    private static boolean placeProceduralFallback(WorldGenLevel level, BlockPos nodePos, ResourceLocation nodeId,
                                                   Block traceBlock, Block host) {
        Vec3i size = FALLBACK_TRACE_SIZE;
        List<BoundingBox> structureBoxes = collectStructureBoxes(level, nodePos, size);
        Optional<BlockPos> anchorOpt = SurfaceFinder.findAnchor(level, nodePos, size, structureBoxes);
        BlockPos anchor = clampAnchorToBuildHeight(level, anchorOpt.orElse(nodePos), size);

        clearFoliage(level, anchor, size, structureBoxes);

        BlockState hostState = host.defaultBlockState();
        BlockState traceState = traceBlock.defaultBlockState();
        int centerX = anchor.getX() + size.getX() / 2;
        int centerZ = anchor.getZ() + size.getZ() / 2;
        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();

        for (int dx = -FALLBACK_TRACE_RADIUS; dx <= FALLBACK_TRACE_RADIUS; dx++) {
            for (int dz = -FALLBACK_TRACE_RADIUS; dz <= FALLBACK_TRACE_RADIUS; dz++) {
                int wx = centerX + dx;
                int wz = centerZ + dz;
                double distSq = dx * dx + dz * dz;
                double edge = FALLBACK_TRACE_RADIUS * FALLBACK_TRACE_RADIUS
                        + signedNoise(wx, anchor.getY(), wz) * 1.15D;
                if (distSq > edge) continue;
                int height = distSq <= 1.5D ? 1 : 0;
                for (int dy = 0; dy <= height; dy++) {
                    cur.set(wx, anchor.getY() + dy, wz);
                    if (StructureGuard.isInsideAny(structureBoxes, cur)) continue;
                    level.setBlock(cur, hostState, PLACE_FLAGS);
                }
            }
        }

        BlockPos tracePos = new BlockPos(centerX, anchor.getY() + 2, centerZ);
        if (!StructureGuard.isInsideAny(structureBoxes, tracePos)) {
            level.setBlock(tracePos, traceState, PLACE_FLAGS);
        } else {
            tracePos = new BlockPos(centerX, anchor.getY() + 1, centerZ);
            level.setBlock(tracePos, traceState, PLACE_FLAGS);
        }

        fillFoundation(level, anchor, size, host, traceBlock, structureBoxes);
        recordTrace(level, anchor, size, nodeId, traceBlock);
        return true;
    }

    private static void recordTrace(WorldGenLevel level, BlockPos anchor, Vec3i size, ResourceLocation nodeId, Block traceBlock) {
        ServerLevel serverLevel = level.getLevel();
        MinecraftServer server = serverLevel.getServer();
        BlockPos visiblePos = findVisibleTraceBlock(level, anchor, size, traceBlock);
        long chunkLong = net.minecraft.world.level.ChunkPos.asLong(visiblePos.getX() >> 4, visiblePos.getZ() >> 4);
        // Trace placement happens on a worldgen thread; defer the SavedData write
        // to the main server thread to avoid racing on DimensionDataStorage.
        server.execute(() -> {
            TraceIndex.record(serverLevel, visiblePos, nodeId);
            TraceIndex.markScanned(serverLevel, chunkLong);
        });
    }

    private static BlockPos findVisibleTraceBlock(WorldGenLevel level, BlockPos anchor, Vec3i size, Block traceBlock) {
        BlockPos fallback = new BlockPos(
                anchor.getX() + size.getX() / 2,
                anchor.getY() + size.getY() - 1,
                anchor.getZ() + size.getZ() / 2);
        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        int bestY = Integer.MIN_VALUE;
        double bestDist = Double.MAX_VALUE;
        int centerX = anchor.getX() + size.getX() / 2;
        int centerZ = anchor.getZ() + size.getZ() / 2;

        for (int dx = 0; dx < size.getX(); dx++) {
            for (int dy = 0; dy < size.getY(); dy++) {
                for (int dz = 0; dz < size.getZ(); dz++) {
                    cur.set(anchor.getX() + dx, anchor.getY() + dy, anchor.getZ() + dz);
                    if (!level.getBlockState(cur).is(traceBlock)) continue;
                    double xzDist = cur.distToCenterSqr(centerX + 0.5D, cur.getY() + 0.5D, centerZ + 0.5D);
                    if (cur.getY() > bestY || (cur.getY() == bestY && xzDist < bestDist)) {
                        bestY = cur.getY();
                        bestDist = xzDist;
                        best = cur.immutable();
                    }
                }
            }
        }
        return best == null ? fallback : best;
    }

    private static void clearFoliage(WorldGenLevel level, BlockPos anchor, Vec3i size, List<BoundingBox> structureBoxes) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();

        int minX = anchor.getX() - CLEAR_MARGIN;
        int maxX = anchor.getX() + size.getX() - 1 + CLEAR_MARGIN;
        int minZ = anchor.getZ() - CLEAR_MARGIN;
        int maxZ = anchor.getZ() + size.getZ() - 1 + CLEAR_MARGIN;
        int bodyTop = anchor.getY() + size.getY() - 1;
        int yMax = Math.min(bodyTop + VISIBILITY_CLEAR_HEIGHT, level.getMaxBuildHeight() - 1);

        for (int wx = minX; wx <= maxX; wx++) {
            for (int wz = minZ; wz <= maxZ; wz++) {
                for (int y = anchor.getY(); y <= yMax; y++) {
                    cur.set(wx, y, wz);
                    if (StructureGuard.isInsideAny(structureBoxes, cur)) continue;
                    BlockState existing = level.getBlockState(cur);
                    if (!isClearable(existing)) continue;
                    if (!shouldClearAt(anchor, size, wx, y, wz, bodyTop)) continue;
                    level.setBlock(cur, air, PLACE_FLAGS);
                    if (existing.is(Blocks.SNOW)) {
                        fixSnowyGroundBelow(level, cur, below);
                    }
                }
            }
        }
    }

    private static void fixSnowyGroundBelow(WorldGenLevel level, BlockPos snowPos, BlockPos.MutableBlockPos below) {
        int y = snowPos.getY() - 1;
        if (y < level.getMinBuildHeight()) return;
        below.set(snowPos.getX(), y, snowPos.getZ());
        BlockState state = level.getBlockState(below);
        if (!state.hasProperty(SnowyDirtBlock.SNOWY) || !state.getValue(SnowyDirtBlock.SNOWY)) return;
        level.setBlock(below, state.setValue(SnowyDirtBlock.SNOWY, Boolean.FALSE), Block.UPDATE_CLIENTS);
    }

    private static boolean isClearable(BlockState state) {
        if (state.isAir()) return false;
        if (state.is(Blocks.WATER)) return false;
        if (state.is(Blocks.LAVA)) return false;
        if (state.is(BlockTags.LOGS)) return false;
        if (state.is(Blocks.BAMBOO)
                || state.is(Blocks.BAMBOO_SAPLING)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.SUGAR_CANE)) {
            return false;
        }
        return state.is(BlockTags.LEAVES)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.REPLACEABLE)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.RED_MUSHROOM)
                || state.is(Blocks.BROWN_MUSHROOM)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.GLOW_LICHEN);
    }

    private static boolean shouldClearAt(BlockPos anchor, Vec3i size, int wx, int y, int wz, int bodyTop) {
        if (y <= bodyTop) return isInsideOrganicFootprint(anchor, size, wx, y, wz);
        return isInsideVisibilityClearance(anchor, size, wx, wz);
    }

    private static boolean isInsideOrganicFootprint(BlockPos anchor, Vec3i size, int wx, int y, int wz) {
        double centerX = anchor.getX() + (size.getX() - 1) * 0.5D;
        double centerZ = anchor.getZ() + (size.getZ() - 1) * 0.5D;
        double radiusX = Math.max(1.0D, size.getX() * 0.5D);
        double radiusZ = Math.max(1.0D, size.getZ() * 0.5D);
        double nx = (wx - centerX) / radiusX;
        double nz = (wz - centerZ) / radiusZ;
        double dist = nx * nx + nz * nz;
        double height = (double) (y - anchor.getY()) / Math.max(1, size.getY());
        double edge = 1.0D + signedNoise(wx, y, wz) * 0.22D - height * 0.12D;
        return dist <= edge;
    }

    private static boolean isInsideVisibilityClearance(BlockPos anchor, Vec3i size, int wx, int wz) {
        double centerX = anchor.getX() + (size.getX() - 1) * 0.5D;
        double centerZ = anchor.getZ() + (size.getZ() - 1) * 0.5D;
        double dx = wx - centerX;
        double dz = wz - centerZ;
        double radius = Math.max(1.35D, Math.min(size.getX(), size.getZ()) * 0.25D);
        double edge = radius * radius + signedNoise(wx, anchor.getY(), wz) * 0.55D;
        return dx * dx + dz * dz <= edge;
    }

    private static double signedNoise(int x, int y, int z) {
        long h = 0x9E3779B97F4A7C15L;
        h ^= (long) x * 0xBF58476D1CE4E5B9L;
        h = Long.rotateLeft(h, 21);
        h ^= (long) y * 0x94D049BB133111EBL;
        h = Long.rotateLeft(h, 17);
        h ^= (long) z * 0xD6E8FEB86659FD93L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return ((h & 0xFFFFL) / 32767.5D) - 1.0D;
    }

    private static void fillFoundation(WorldGenLevel level, BlockPos anchor, Vec3i size, Block host, Block traceBlock,
                                       List<BoundingBox> structureBoxes) {
        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        BlockState hostState = host.defaultBlockState();
        int worldMinY = level.getMinBuildHeight();

        for (int dx = 0; dx < size.getX(); dx++) {
            for (int dz = 0; dz < size.getZ(); dz++) {
                int wx = anchor.getX() + dx;
                int wz = anchor.getZ() + dz;

                int structBottomY = -1;
                for (int dy = 0; dy < size.getY(); dy++) {
                    cur.set(wx, anchor.getY() + dy, wz);
                    BlockState state = level.getBlockState(cur);
                    if (state.is(host) || state.is(traceBlock)) {
                        structBottomY = anchor.getY() + dy;
                        break;
                    }
                }
                if (structBottomY < 0) continue;

                for (int depth = 0; depth < MAX_FOUNDATION_DEPTH; depth++) {
                    int y = structBottomY - 1 - depth;
                    if (y < worldMinY) break;
                    cur.set(wx, y, wz);
                    if (StructureGuard.isInsideAny(structureBoxes, cur)) break;
                    BlockState existing = level.getBlockState(cur);
                    if (!existing.canBeReplaced() && existing.getFluidState().isEmpty()) break;
                    level.setBlock(cur, hostState, PLACE_FLAGS);
                }
            }
        }
    }
}
