package com.typ.traces.worldgen;

import java.util.Optional;

import com.typ.traces.CreateReAutomatedTraces;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class TraceStructurePlacer {

    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private TraceStructurePlacer() {}

    public static void place(ServerLevel level, BlockPos nodePos, ResourceLocation nodeId, RandomSource rng) {
        Optional<Block> oreOpt = NodeOreMapping.oreFor(nodeId);
        if (oreOpt.isEmpty()) return;

        Optional<BlockPos> surfaceOpt = SurfaceFinder.findSurface(level, nodePos);
        if (surfaceOpt.isEmpty()) return;

        StructureTemplateManager mgr = level.getStructureManager();
        ResourceLocation tmplId = TraceTemplates.pick(rng);
        Optional<StructureTemplate> tmplOpt = mgr.get(tmplId);
        if (tmplOpt.isEmpty()) {
            CreateReAutomatedTraces.LOGGER.warn("Trace template missing: {}", tmplId);
            return;
        }

        StructureTemplate tmpl = tmplOpt.get();
        BlockPos placePos = surfaceOpt.get();
        Vec3i size = tmpl.getSize();

        BlockPos farCorner = placePos.offset(Math.max(0, size.getX() - 1), 0, Math.max(0, size.getZ() - 1));
        if (!level.hasChunkAt(placePos) || !level.hasChunkAt(farCorner)) return;
        if (placePos.getY() + size.getY() >= level.getMaxBuildHeight()) return;

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .addProcessor(new OrePlaceholderProcessor(oreOpt.get()));

        tmpl.placeInWorld(level, placePos, placePos, settings, rng, PLACE_FLAGS);
    }
}
