package com.typ.traces.worldgen;

import java.util.List;
import java.util.Optional;

import com.github.zgraund.createreautomated.block.node.OreNodeBlock;
import com.typ.traces.CreateReAutomatedTraces;
import com.typ.traces.api.TraceWorldgenExclusions;
import com.typ.traces.config.Common;
import com.typ.traces.config.Config;
import com.typ.traces.index.TraceIndex;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class TraceStructurePlacer {

    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int PLACED = 1;
    private static final int TERMINAL_FAILURE = -1;

    private TraceStructurePlacer() {}

    private static Block smoothify(Block raw) {
        if (raw == Blocks.COBBLESTONE) return Blocks.STONE;
        if (raw == Blocks.COBBLED_DEEPSLATE) return Blocks.DEEPSLATE;
        return raw;
    }

    public static boolean place(
            WorldGenLevel level,
            BlockPos nodePos,
            Block nodeBlock,
            ResourceLocation nodeId,
            RandomSource ignoredRandom) {
        TracePlacementDiagnostics diagnostics = diagnosticsFor(level, nodePos, nodeId);
        if (TraceWorldgenExclusions.consumeGeneratedTraceSuppression(level, nodePos)) {
            diagnostics.rejectGlobal(TracePlacementDiagnostics.SkipReason.SUPPRESSED);
            diagnostics.reportSkipped(level.getLevel());
            return false;
        }

        Optional<Block> traceBlockOpt = TraceBlockDataMap.traceBlockFor(nodeBlock);
        if (traceBlockOpt.isEmpty()) {
            TraceBlockDataMap.warnMissingOnce(nodeId);
            diagnostics.rejectGlobal(TracePlacementDiagnostics.SkipReason.MISSING_TRACE_BLOCK_MAPPING);
            diagnostics.reportSkipped(level.getLevel());
            return false;
        }
        Block traceBlock = traceBlockOpt.get();
        Block host = nodeBlock instanceof OreNodeBlock oreNode
                ? smoothify(oreNode.baseRock.getBlock())
                : Blocks.STONE;

        List<TraceTemplates.TraceTemplateGroup> templateGroups =
                TraceTemplates.groupsFor(level.getSeed(), nodePos);
        if (templateGroups.isEmpty()) {
            CreateReAutomatedTraces.LOGGER.warn("No valid Trace template profiles are loaded for {}", nodeId);
            diagnostics.rejectGlobal(TracePlacementDiagnostics.SkipReason.NO_TEMPLATE_PROFILES);
            diagnostics.reportSkipped(level.getLevel());
            return false;
        }

        TracePlacementPlanner.PlanningContext planningContext =
                TracePlacementPlanner.createContext(level, nodePos);
        List<StructureGuard.StructureArea> structureAreas =
                collectStructureAreas(
                        level,
                        nodePos,
                        TraceTemplates.maximumHorizontalSize(),
                        planningContext.placementRadius());
        StructureTemplateManager templateManager = level.getLevel().getStructureManager();

        for (TraceTemplates.TraceTemplateGroup group : templateGroups) {
            Optional<PlacementChoice> choice =
                    findBestChoice(
                            planningContext,
                            nodePos,
                            group.size(),
                            group.profiles(),
                            structureAreas,
                            diagnostics);
            if (choice.isEmpty()) continue;
            int result = placeChoice(
                    level, nodePos, nodeId, traceBlock, host, choice.get(), templateManager);
            if (result == PLACED) return true;
            diagnostics.rejectGlobal(TracePlacementDiagnostics.SkipReason.TEMPLATE_PLACE_FAILED);
            diagnostics.reportSkipped(level.getLevel());
            return false;
        }

        if (diagnostics.hasSteepTerrainFallbackReason()) {
            Optional<PlacementChoice> fallbackChoice =
                    findSteepTerrainFallbackChoice(
                            planningContext,
                            nodePos,
                            structureAreas,
                            diagnostics);
            if (fallbackChoice.isPresent()) {
                int result = placeChoice(
                        level, nodePos, nodeId, traceBlock, host, fallbackChoice.get(), templateManager);
                if (result == PLACED) return true;
                diagnostics.rejectGlobal(TracePlacementDiagnostics.SkipReason.TEMPLATE_PLACE_FAILED);
                diagnostics.reportSkipped(level.getLevel());
                return false;
            }
        }

        diagnostics.reportSkipped(level.getLevel());
        return false;
    }

    private static TracePlacementDiagnostics diagnosticsFor(
            WorldGenLevel level,
            BlockPos nodePos,
            ResourceLocation nodeId) {
        Common common = Config.common();
        if (common == null || !common.worldgen.tracePlacementDiagnostics.get()) {
            return TracePlacementDiagnostics.disabled();
        }
        return TracePlacementDiagnostics.enabled(nodeId, level.getLevel().dimension().location(), nodePos);
    }

    private static Optional<PlacementChoice> findBestChoice(
            TracePlacementPlanner.PlanningContext planningContext,
            BlockPos nodePos,
            TraceTemplates.TraceSize size,
            List<TraceTemplateProfile> profiles,
            List<StructureGuard.StructureArea> structureAreas,
            TracePlacementDiagnostics diagnostics) {
        PlacementChoice best = null;
        for (int variantOrder = 0; variantOrder < profiles.size(); variantOrder++) {
            TraceTemplateProfile profile = profiles.get(variantOrder);
            Optional<TracePlacementPlan> plan =
                    TracePlacementPlanner.findPlan(
                            planningContext,
                            nodePos,
                            profile,
                            structureAreas,
                            size,
                            diagnostics);
            if (plan.isEmpty()) continue;
            PlacementChoice candidate = new PlacementChoice(profile, plan.get(), variantOrder);
            if (isBetterChoice(candidate, best)) {
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    private static Optional<PlacementChoice> findSteepTerrainFallbackChoice(
            TracePlacementPlanner.PlanningContext planningContext,
            BlockPos nodePos,
            List<StructureGuard.StructureArea> structureAreas,
            TracePlacementDiagnostics diagnostics) {
        Optional<TraceTemplateProfile> profile = TraceTemplates.steepTerrainFallback();
        if (profile.isEmpty()) return Optional.empty();
        Optional<TracePlacementPlan> plan =
                TracePlacementPlanner.findSteepTerrainFallbackPlan(
                        planningContext,
                        nodePos,
                        profile.get(),
                        structureAreas,
                        diagnostics);
        return plan.map(tracePlacementPlan -> new PlacementChoice(
                profile.get(),
                tracePlacementPlan,
                Integer.MAX_VALUE));
    }

    private static int placeChoice(
            WorldGenLevel level,
            BlockPos nodePos,
            ResourceLocation nodeId,
            Block traceBlock,
            Block host,
            PlacementChoice choice,
            StructureTemplateManager templateManager) {
        TraceTemplateProfile profile = choice.profile();
        Optional<StructureTemplate> templateOpt = templateManager.get(profile.id());
        if (templateOpt.isEmpty()) {
            CreateReAutomatedTraces.LOGGER.warn("Trace template missing: {}", profile.id());
            return TERMINAL_FAILURE;
        }

        TracePlacementPlan plan = choice.plan();
        applyPlan(level, plan, host);
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(plan.rotation())
                .setRotationPivot(BlockPos.ZERO)
                .addProcessor(new TracePlaceholderProcessor(traceBlock, host));
        RandomSource placementRandom = RandomSource.create(
                TraceTemplates.placementSeed(level.getSeed(), nodePos, profile.id()));
        boolean placed = templateOpt.get().placeInWorld(
                level,
                plan.anchor(),
                plan.anchor(),
                settings,
                placementRandom,
                PLACE_FLAGS);
        if (!placed) {
            CreateReAutomatedTraces.LOGGER.warn(
                    "Trace template placement failed for {} using {}; terrain plan was not retried",
                    nodeId,
                    profile.id());
            return TERMINAL_FAILURE;
        }

        recordTrace(level, plan.tracePos(), nodeId);
        return PLACED;
    }

    private static boolean isBetterChoice(PlacementChoice candidate, PlacementChoice current) {
        if (current == null) return true;
        TracePlacementPlan candidatePlan = candidate.plan();
        TracePlacementPlan currentPlan = current.plan();
        int costComparison = TracePlacementPlanner.comparePlanCost(candidatePlan, currentPlan);
        if (costComparison != 0) return costComparison < 0;
        if (candidate.variantOrder() != current.variantOrder()) {
            return candidate.variantOrder() < current.variantOrder();
        }
        return TracePlacementPlanner.comparePlanAnchor(candidatePlan, currentPlan) < 0;
    }

    private static void applyPlan(WorldGenLevel level, TracePlacementPlan plan, Block host) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState hostState = host.defaultBlockState();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();

        for (BlockPos pos : plan.cutBlocks()) {
            BlockState previous = level.getBlockState(pos);
            level.setBlock(pos, air, PLACE_FLAGS);
            if (previous.is(Blocks.SNOW)) {
                fixSnowyGroundBelow(level, pos, below);
            }
        }
        for (BlockPos pos : plan.cleanupBlocks()) {
            BlockState previous = level.getBlockState(pos);
            level.setBlock(pos, air, PLACE_FLAGS);
            if (previous.is(Blocks.SNOW)) {
                fixSnowyGroundBelow(level, pos, below);
            }
        }
        for (BlockPos pos : plan.fillBlocks()) {
            level.setBlock(pos, hostState, PLACE_FLAGS);
        }
    }

    private static void fixSnowyGroundBelow(
            WorldGenLevel level,
            BlockPos snowPos,
            BlockPos.MutableBlockPos below) {
        int y = snowPos.getY() - 1;
        if (y < level.getMinBuildHeight()) return;
        below.set(snowPos.getX(), y, snowPos.getZ());
        BlockState state = level.getBlockState(below);
        if (!state.hasProperty(SnowyDirtBlock.SNOWY) || !state.getValue(SnowyDirtBlock.SNOWY)) return;
        level.setBlock(below, state.setValue(SnowyDirtBlock.SNOWY, Boolean.FALSE), Block.UPDATE_CLIENTS);
    }

    private static List<StructureGuard.StructureArea> collectStructureAreas(
            WorldGenLevel level,
            BlockPos nodePos,
            int maximumTemplateSize,
            int placementRadius) {
        int reach = placementRadius
                + maximumTemplateSize
                + TracePlacementPlanner.requiredHorizontalMargin();
        int minChunkX = (nodePos.getX() - reach) >> 4;
        int maxChunkX = (nodePos.getX() + reach) >> 4;
        int minChunkZ = (nodePos.getZ() - reach) >> 4;
        int maxChunkZ = (nodePos.getZ() + reach) >> 4;
        return StructureGuard.collectStructureAreas(level, minChunkX, minChunkZ, maxChunkX, maxChunkZ);
    }

    private static void recordTrace(WorldGenLevel level, BlockPos tracePos, ResourceLocation nodeId) {
        ServerLevel serverLevel = level.getLevel();
        MinecraftServer server = serverLevel.getServer();
        server.execute(() -> TraceIndex.record(serverLevel, tracePos, nodeId));
    }

    private record PlacementChoice(
            TraceTemplateProfile profile,
            TracePlacementPlan plan,
            int variantOrder) {}
}
