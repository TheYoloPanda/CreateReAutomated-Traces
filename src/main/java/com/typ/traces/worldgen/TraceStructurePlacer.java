package com.typ.traces.worldgen;

import java.util.List;
import java.util.Optional;

import com.github.zgraund.createreautomated.block.node.OreNodeBlock;
import com.typ.traces.CreateReAutomatedTraces;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class TraceStructurePlacer {

    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int MAX_FOUNDATION_DEPTH = 4;
    private static final int CLEAR_MARGIN = 3;
    private static final int CANOPY_EXTRA_HEIGHT = 24;

    private TraceStructurePlacer() {}

    private static Block smoothify(Block raw) {
        if (raw == Blocks.COBBLESTONE) return Blocks.STONE;
        if (raw == Blocks.COBBLED_DEEPSLATE) return Blocks.DEEPSLATE;
        return raw;
    }

    public static void place(WorldGenLevel level, BlockPos nodePos, Block nodeBlock, ResourceLocation nodeId, RandomSource rng) {
        Optional<Block> traceBlockOpt = TraceBlockDataMap.traceBlockFor(nodeBlock);
        if (traceBlockOpt.isEmpty()) {
            TraceBlockDataMap.warnMissingOnce(nodeId);
            return;
        }
        Block traceBlock = traceBlockOpt.get();
        Block host = (nodeBlock instanceof OreNodeBlock onb)
                ? smoothify(onb.baseRock.getBlock())
                : Blocks.STONE;

        Optional<ResourceLocation> tmplIdOpt = TraceTemplates.pick(rng);
        if (tmplIdOpt.isEmpty()) return;
        ResourceLocation tmplId = tmplIdOpt.get();

        StructureTemplateManager mgr = level.getLevel().getStructureManager();
        Optional<StructureTemplate> tmplOpt = mgr.get(tmplId);
        if (tmplOpt.isEmpty()) {
            CreateReAutomatedTraces.LOGGER.warn("Trace template missing: {}", tmplId);
            return;
        }
        StructureTemplate tmpl = tmplOpt.get();
        Vec3i size = tmpl.getSize();

        int reach = SurfaceFinder.SEARCH_RADIUS + Math.max(size.getX(), size.getZ()) + CLEAR_MARGIN;
        int minCX = (nodePos.getX() - reach) >> 4;
        int maxCX = (nodePos.getX() + reach) >> 4;
        int minCZ = (nodePos.getZ() - reach) >> 4;
        int maxCZ = (nodePos.getZ() + reach) >> 4;
        List<BoundingBox> structureBoxes = StructureGuard.collectStructureBoxes(level, minCX, minCZ, maxCX, maxCZ);

        Optional<BlockPos> anchorOpt = SurfaceFinder.findAnchor(level, nodePos, size, structureBoxes);
        if (anchorOpt.isEmpty()) return;
        BlockPos anchor = anchorOpt.get();

        if (anchor.getY() + size.getY() >= level.getMaxBuildHeight()) return;

        clearFoliage(level, anchor, size, structureBoxes);

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .addProcessor(new TracePlaceholderProcessor(traceBlock, host));

        boolean placed = tmpl.placeInWorld(level, anchor, anchor, settings, rng, PLACE_FLAGS);
        if (!placed) return;

        fillFoundation(level, anchor, size, host, structureBoxes);
    }

    private static void clearFoliage(WorldGenLevel level, BlockPos anchor, Vec3i size, List<BoundingBox> structureBoxes) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();

        int minX = anchor.getX() - CLEAR_MARGIN;
        int maxX = anchor.getX() + size.getX() - 1 + CLEAR_MARGIN;
        int minZ = anchor.getZ() - CLEAR_MARGIN;
        int maxZ = anchor.getZ() + size.getZ() - 1 + CLEAR_MARGIN;
        int yMax = Math.min(anchor.getY() + size.getY() + CANOPY_EXTRA_HEIGHT, level.getMaxBuildHeight() - 1);

        for (int wx = minX; wx <= maxX; wx++) {
            for (int wz = minZ; wz <= maxZ; wz++) {
                for (int y = anchor.getY(); y <= yMax; y++) {
                    cur.set(wx, y, wz);
                    if (StructureGuard.isInsideAny(structureBoxes, cur)) continue;
                    BlockState existing = level.getBlockState(cur);
                    if (!isClearable(existing)) continue;
                    BlockState replacement = existing.getFluidState().is(FluidTags.WATER) ? water : air;
                    level.setBlock(cur, replacement, PLACE_FLAGS);
                }
            }
        }
    }

    private static boolean isClearable(BlockState state) {
        if (state.isAir()) return false;
        if (state.is(Blocks.WATER)) return false;
        if (state.is(Blocks.LAVA)) return false;
        return state.is(BlockTags.LEAVES)
                || state.is(BlockTags.LOGS)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.REPLACEABLE)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.RED_MUSHROOM)
                || state.is(Blocks.BROWN_MUSHROOM)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.SUGAR_CANE)
                || state.is(Blocks.BAMBOO)
                || state.is(Blocks.BAMBOO_SAPLING)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.GLOW_LICHEN);
    }

    private static void fillFoundation(WorldGenLevel level, BlockPos anchor, Vec3i size, Block host, List<BoundingBox> structureBoxes) {
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
                    if (!level.getBlockState(cur).isAir()) {
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
                    if (!existing.canBeReplaced()) break;
                    level.setBlock(cur, hostState, PLACE_FLAGS);
                }
            }
        }
    }
}
