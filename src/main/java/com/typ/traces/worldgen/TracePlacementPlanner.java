package com.typ.traces.worldgen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.typ.traces.config.Common;
import com.typ.traces.config.Config;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.common.Tags;

public final class TracePlacementPlanner {

    static final int MAX_FOUNDATION_GAP_DEPTH = 6;
    static final int MAX_CUT_DEPTH = 4;
    static final int MIN_TERRAIN_EDITS = 10;
    static final int TERRAIN_EDITS_PER_SUPPORT_COLUMN = 3;
    static final int CLEANUP_MARGIN = 1;
    static final int CLEANUP_EXTRA_HEIGHT = 2;

    private static final int DEFAULT_PLACEMENT_RADIUS = 2;
    private static final int MAX_PLACEMENT_RADIUS = 8;
    private static final int MAX_NETHER_SCAN_DISTANCE = 64;
    private static final int MAX_NETHER_SCAN_Y = 120;
    private static final int INVALID_SURFACE = Integer.MIN_VALUE;
    private static final int SURFACE_CACHE_MISS = Integer.MAX_VALUE;
    private static final Map<Integer, List<BlockPos>> OFFSETS_BY_RADIUS = new ConcurrentHashMap<>();
    private static final List<Rotation> STEEP_FALLBACK_ROTATIONS = List.of(
            Rotation.NONE,
            Rotation.CLOCKWISE_90,
            Rotation.CLOCKWISE_180,
            Rotation.COUNTERCLOCKWISE_90);
    private static final Set<Block> TREE_LOG_BLOCKS = Set.of(
            Blocks.OAK_LOG,
            Blocks.SPRUCE_LOG,
            Blocks.BIRCH_LOG,
            Blocks.JUNGLE_LOG,
            Blocks.ACACIA_LOG,
            Blocks.DARK_OAK_LOG,
            Blocks.MANGROVE_LOG,
            Blocks.CHERRY_LOG,
            Blocks.OAK_WOOD,
            Blocks.SPRUCE_WOOD,
            Blocks.BIRCH_WOOD,
            Blocks.JUNGLE_WOOD,
            Blocks.ACACIA_WOOD,
            Blocks.DARK_OAK_WOOD,
            Blocks.MANGROVE_WOOD,
            Blocks.CHERRY_WOOD,
            Blocks.STRIPPED_OAK_LOG,
            Blocks.STRIPPED_SPRUCE_LOG,
            Blocks.STRIPPED_BIRCH_LOG,
            Blocks.STRIPPED_JUNGLE_LOG,
            Blocks.STRIPPED_ACACIA_LOG,
            Blocks.STRIPPED_DARK_OAK_LOG,
            Blocks.STRIPPED_MANGROVE_LOG,
            Blocks.STRIPPED_CHERRY_LOG,
            Blocks.STRIPPED_OAK_WOOD,
            Blocks.STRIPPED_SPRUCE_WOOD,
            Blocks.STRIPPED_BIRCH_WOOD,
            Blocks.STRIPPED_JUNGLE_WOOD,
            Blocks.STRIPPED_ACACIA_WOOD,
            Blocks.STRIPPED_DARK_OAK_WOOD,
            Blocks.STRIPPED_MANGROVE_WOOD,
            Blocks.STRIPPED_CHERRY_WOOD,
            Blocks.CRIMSON_STEM,
            Blocks.WARPED_STEM,
            Blocks.CRIMSON_HYPHAE,
            Blocks.WARPED_HYPHAE,
            Blocks.STRIPPED_CRIMSON_STEM,
            Blocks.STRIPPED_WARPED_STEM,
            Blocks.STRIPPED_CRIMSON_HYPHAE,
            Blocks.STRIPPED_WARPED_HYPHAE,
            Blocks.BAMBOO_BLOCK,
            Blocks.STRIPPED_BAMBOO_BLOCK,
            Blocks.MANGROVE_ROOTS);
    private static final Set<Block> TREE_FOLIAGE_BLOCKS = Set.of(
            Blocks.OAK_LEAVES,
            Blocks.SPRUCE_LEAVES,
            Blocks.BIRCH_LEAVES,
            Blocks.JUNGLE_LEAVES,
            Blocks.ACACIA_LEAVES,
            Blocks.DARK_OAK_LEAVES,
            Blocks.MANGROVE_LEAVES,
            Blocks.CHERRY_LEAVES,
            Blocks.AZALEA_LEAVES,
            Blocks.FLOWERING_AZALEA_LEAVES,
            Blocks.VINE,
            Blocks.WEEPING_VINES,
            Blocks.WEEPING_VINES_PLANT,
            Blocks.TWISTING_VINES,
            Blocks.TWISTING_VINES_PLANT,
            Blocks.NETHER_WART_BLOCK,
            Blocks.WARPED_WART_BLOCK,
            Blocks.SHROOMLIGHT);

    private TracePlacementPlanner() {}

    static final class PlanningContext {
        private final WorldGenLevel level;
        private final int nodeY;
        private final boolean nether;
        private final int placementRadius;
        private final TreeAccess treeAccess;
        private final Long2IntOpenHashMap surfaceCache = new Long2IntOpenHashMap();

        private PlanningContext(WorldGenLevel level, BlockPos nodePos) {
            this.level = level;
            this.nodeY = nodePos.getY();
            this.nether = level.getLevel().dimension().equals(Level.NETHER);
            this.placementRadius = placementRadius();
            this.treeAccess = treeAccess(level);
            this.surfaceCache.defaultReturnValue(SURFACE_CACHE_MISS);
        }

        int placementRadius() {
            return placementRadius;
        }

        int surfaceY(int x, int z) {
            return TracePlacementPlanner.surfaceY(level, nodeY, x, z, nether, surfaceCache);
        }
    }

    interface TreeAccess {
        BlockState getBlockState(BlockPos pos);

        int minBuildHeight();

        int maxBuildHeight();
    }

    interface SurfaceLookup {
        int surfaceY(int x, int z);
    }

    private record SurfaceFailure(TracePlacementDiagnostics.SkipReason reason, String detail) {}

    private enum FoundationMode {
        NORMAL,
        STEEP_FALLBACK_FULL
    }

    private static final class Failure {
        private final boolean collectDetails;
        private TracePlacementDiagnostics.SkipReason reason;
        private String detail;

        Failure() {
            this(false);
        }

        Failure(boolean collectDetails) {
            this.collectDetails = collectDetails;
        }

        void set(TracePlacementDiagnostics.SkipReason reason) {
            if (this.reason != null) return;
            this.reason = reason;
        }

        void set(TracePlacementDiagnostics.SkipReason reason, String detail) {
            if (this.reason != null) return;
            this.reason = reason;
            this.detail = detail;
        }

        void set(TracePlacementDiagnostics.SkipReason reason, Supplier<String> detail) {
            if (this.reason != null) return;
            this.reason = reason;
            if (collectDetails) {
                this.detail = detail.get();
            }
        }

        TracePlacementDiagnostics.SkipReason or(TracePlacementDiagnostics.SkipReason fallback) {
            return reason == null ? fallback : reason;
        }

        TracePlacementDiagnostics.DetailSample sample(TraceTemplates.TraceSize size) {
            return detail == null ? null : TracePlacementDiagnostics.DetailSample.of(size, detail);
        }
    }

    public static Optional<TracePlacementPlan> findPlan(
            WorldGenLevel level,
            BlockPos nodePos,
            TraceTemplateProfile profile,
            List<BoundingBox> structureBoxes) {
        return findPlan(createContext(level, nodePos), nodePos, profile, structureBoxes);
    }

    static PlanningContext createContext(WorldGenLevel level, BlockPos nodePos) {
        return new PlanningContext(level, nodePos);
    }

    static Optional<TracePlacementPlan> findPlan(
            PlanningContext context,
            BlockPos nodePos,
            TraceTemplateProfile profile,
            List<BoundingBox> structureBoxes) {
        return findPlan(context, nodePos, profile, StructureGuard.unknownAreas(structureBoxes));
    }

    static Optional<TracePlacementPlan> findPlan(
            PlanningContext context,
            BlockPos nodePos,
            TraceTemplateProfile profile,
            Collection<StructureGuard.StructureArea> structureAreas) {
        return findPlan(
                context,
                nodePos,
                profile,
                structureAreas,
                TraceTemplates.TraceSize.MEDIUM,
                TracePlacementDiagnostics.disabled());
    }

    static Optional<TracePlacementPlan> findPlan(
            WorldGenLevel level,
            BlockPos nodePos,
            TraceTemplateProfile profile,
            List<BoundingBox> structureBoxes,
            TraceTemplates.TraceSize size,
            TracePlacementDiagnostics diagnostics) {
        return findPlan(
                createContext(level, nodePos),
                nodePos,
                profile,
                StructureGuard.unknownAreas(structureBoxes),
                size,
                diagnostics);
    }

    static Optional<TracePlacementPlan> findPlan(
            PlanningContext context,
            BlockPos nodePos,
            TraceTemplateProfile profile,
            Collection<StructureGuard.StructureArea> structureAreas,
            TraceTemplates.TraceSize size,
            TracePlacementDiagnostics diagnostics) {
        TracePlacementPlan best = null;

        for (BlockPos offset : offsetsForRadius(context.placementRadius())) {
            int originX = nodePos.getX() + offset.getX() - Math.floorDiv(profile.size().getX(), 2);
            int originZ = nodePos.getZ() + offset.getZ() - Math.floorDiv(profile.size().getZ(), 2);
            if (!requiredChunksAvailable(context.level, originX, originZ, profile)) {
                diagnostics.reject(
                        size,
                        TracePlacementDiagnostics.SkipReason.REQUIRED_CHUNKS_UNAVAILABLE,
                        diagnostics.sample(
                                size,
                                () -> "profile=" + profile.id()
                                        + " originX=" + originX
                                        + " originZ=" + originZ));
                continue;
            }

            int naturalAnchorY = naturalAnchorY(context, profile, originX, originZ);
            if (naturalAnchorY == INVALID_SURFACE) {
                diagnostics.reject(
                        size,
                        TracePlacementDiagnostics.SkipReason.NO_SURFACE,
                        diagnostics.sample(
                                size,
                                () -> "profile=" + profile.id()
                                        + " originX=" + originX
                                        + " originZ=" + originZ
                                        + " no surface for support columns"));
                continue;
            }

            int distanceSq = offset.getX() * offset.getX() + offset.getZ() * offset.getZ();
            for (int lowering = 0; lowering <= MAX_CUT_DEPTH; lowering++) {
                diagnostics.recordAttempt(size);
                Optional<TracePlacementPlan> plan = buildPlan(
                        context,
                        profile,
                        structureAreas,
                        size,
                        diagnostics,
                        originX,
                        naturalAnchorY - lowering,
                        originZ,
                        lowering,
                        distanceSq,
                        lowering > 0,
                        false,
                        Rotation.NONE,
                        FoundationMode.NORMAL);
                if (plan.isEmpty()) continue;
                TracePlacementPlan candidate = plan.get();
                if (isBetterPlan(candidate, best)) {
                    best = candidate;
                }
            }
        }

        return Optional.ofNullable(best);
    }

    static Optional<TracePlacementPlan> findSteepTerrainFallbackPlan(
            PlanningContext context,
            BlockPos nodePos,
            TraceTemplateProfile profile,
            Collection<StructureGuard.StructureArea> structureAreas,
            TracePlacementDiagnostics diagnostics) {
        int nodeSurfaceY = context.surfaceY(nodePos.getX(), nodePos.getZ());
        TracePlacementPlan best = null;
        BlockPos firstAnchor = nodeAlignedFallbackAnchor(nodePos, profile, nodeSurfaceY);
        if (firstAnchor.getY() == INVALID_SURFACE) {
            diagnostics.reject(
                    TraceTemplates.TraceSize.SMALL,
                    TracePlacementDiagnostics.SkipReason.NO_SURFACE,
                    diagnostics.sample(
                            TraceTemplates.TraceSize.SMALL,
                            () -> "profile=" + profile.id()
                                    + " nodeX=" + nodePos.getX()
                                    + " nodeZ=" + nodePos.getZ()
                                    + " no surface above node"));
            return Optional.empty();
        }
        for (Rotation rotation : STEEP_FALLBACK_ROTATIONS) {
            TraceTemplateProfile orientedProfile = profile.oriented(rotation);
            BlockPos anchor = nodeAlignedFallbackAnchor(nodePos, orientedProfile, nodeSurfaceY);
            if (!requiredChunksAvailable(context.level, anchor.getX(), anchor.getZ(), orientedProfile)) {
                diagnostics.reject(
                        TraceTemplates.TraceSize.SMALL,
                        TracePlacementDiagnostics.SkipReason.REQUIRED_CHUNKS_UNAVAILABLE,
                        diagnostics.sample(
                                TraceTemplates.TraceSize.SMALL,
                                () -> "profile=" + profile.id()
                                        + " rotation=" + rotation
                                        + " originX=" + anchor.getX()
                                        + " originZ=" + anchor.getZ()));
                continue;
            }

            diagnostics.recordAttempt(TraceTemplates.TraceSize.SMALL);
            Optional<TracePlacementPlan> plan = buildPlan(
                    context,
                    orientedProfile,
                    structureAreas,
                    TraceTemplates.TraceSize.SMALL,
                    diagnostics,
                    anchor.getX(),
                    anchor.getY(),
                    anchor.getZ(),
                    0,
                    0,
                    true,
                    true,
                    rotation,
                    FoundationMode.STEEP_FALLBACK_FULL);
            if (plan.isPresent() && isBetterSteepFallbackPlan(plan.get(), best)) {
                best = plan.get();
            }
        }

        return Optional.ofNullable(best);
    }

    static int placementRadius() {
        Common common = Config.common();
        if (common == null) return DEFAULT_PLACEMENT_RADIUS;
        int configured = common.worldgen.tracePlacementRadius.get();
        return Math.max(0, Math.min(MAX_PLACEMENT_RADIUS, configured));
    }

    static int requiredHorizontalMargin() {
        return CLEANUP_MARGIN + 1;
    }

    static List<BlockPos> offsetsForRadius(int radius) {
        int clamped = Math.max(0, Math.min(MAX_PLACEMENT_RADIUS, radius));
        return OFFSETS_BY_RADIUS.computeIfAbsent(clamped, TracePlacementPlanner::buildOffsets);
    }

    private static List<BlockPos> buildOffsets(int radius) {
        List<BlockPos> offsets = new ArrayList<>();
        int radiusSq = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radiusSq) continue;
                offsets.add(new BlockPos(dx, 0, dz));
            }
        }
        offsets.sort(Comparator
                .comparingInt((BlockPos pos) -> pos.getX() * pos.getX() + pos.getZ() * pos.getZ())
                .thenComparingInt(TracePlacementPlanner::offsetTieBreak)
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
        return List.copyOf(offsets);
    }

    private static int offsetTieBreak(BlockPos offset) {
        int dx = offset.getX();
        int dz = offset.getZ();
        if (dx > 0 && dz == 0) return 0;
        if (dx < 0 && dz == 0) return 1;
        if (dx == 0 && dz > 0) return 2;
        if (dx == 0 && dz < 0) return 3;
        if (dx > 0 && dz > 0) return 4;
        if (dx > 0 && dz < 0) return 5;
        if (dx < 0 && dz > 0) return 6;
        if (dx < 0 && dz < 0) return 7;
        return 8;
    }

    private static int naturalAnchorY(
            PlanningContext context,
            TraceTemplateProfile profile,
            int originX,
            int originZ) {
        int anchorY = Integer.MIN_VALUE;
        for (BlockPos support : profile.supportColumns()) {
            int surfaceY = context.surfaceY(originX + support.getX(), originZ + support.getZ());
            if (surfaceY == INVALID_SURFACE) return INVALID_SURFACE;
            anchorY = Math.max(anchorY, surfaceY - support.getY());
        }
        return anchorY;
    }

    static BlockPos nodeAlignedFallbackAnchor(
            BlockPos nodePos,
            TraceTemplateProfile profile,
            int nodeSurfaceY) {
        BlockPos traceOffset = profile.traceOffset();
        int originX = nodePos.getX() - traceOffset.getX();
        int originZ = nodePos.getZ() - traceOffset.getZ();
        if (nodeSurfaceY == INVALID_SURFACE) {
            return new BlockPos(originX, INVALID_SURFACE, originZ);
        }

        int supportY = profile.supportBottomY(traceOffset.getX(), traceOffset.getZ())
                .orElse(profile.minSupportY());
        return new BlockPos(originX, nodeSurfaceY - supportY, originZ);
    }

    private static Optional<TracePlacementPlan> buildPlan(
            PlanningContext context,
            TraceTemplateProfile profile,
            Collection<StructureGuard.StructureArea> structureAreas,
            TraceTemplates.TraceSize sizeGroup,
            TracePlacementDiagnostics diagnostics,
            int originX,
            int anchorY,
            int originZ,
            int lowering,
            int distanceSq,
            boolean allowTerrainCut,
            boolean allowEmbeddedFootprint,
            Rotation rotation,
            FoundationMode foundationMode) {
        WorldGenLevel level = context.level;
        TreeAccess treeAccess = context.treeAccess;
        Vec3i size = profile.size();
        int minTemplateY = foundationMode == FoundationMode.STEEP_FALLBACK_FULL
                ? anchorY + profile.minOccupiedY()
                : anchorY;
        int maxTemplateY = foundationMode == FoundationMode.STEEP_FALLBACK_FULL
                ? anchorY + profile.maxOccupiedY()
                : anchorY + size.getY() - 1;
        if (minTemplateY < level.getMinBuildHeight()
                || maxTemplateY >= level.getMaxBuildHeight()) {
            diagnostics.reject(sizeGroup, TracePlacementDiagnostics.SkipReason.OUT_OF_BUILD_HEIGHT);
            return Optional.empty();
        }

        BlockPos anchor = new BlockPos(originX, anchorY, originZ);
        BoundingBox templateBox = templateBoundingBox(profile, anchor, foundationMode);
        Optional<StructureGuard.StructureArea> intersectingStructure =
                StructureGuard.firstIntersecting(structureAreas, templateBox);
        if (intersectingStructure.isPresent()) {
            diagnostics.reject(
                    sizeGroup,
                    TracePlacementDiagnostics.SkipReason.STRUCTURE_INTERSECTION,
                    diagnostics.sample(
                            sizeGroup,
                            () -> TracePlacementDiagnostics.structureIntersectionDetail(
                                    profile.id(),
                                    anchor,
                                    templateBox,
                                    intersectingStructure.get())));
            return Optional.empty();
        }

        LinkedHashSet<BlockPos> cutBlocks = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> fillBlocks = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> cleanupBlocks = new LinkedHashSet<>();
        Failure failure = new Failure(diagnostics.isEnabled());

        if (!collectOccupiedCollisions(
                level,
                profile,
                originX,
                anchorY,
                originZ,
                structureAreas,
                context.nether,
                allowTerrainCut,
                cutBlocks,
                cleanupBlocks,
                failure)) {
            diagnostics.reject(
                    sizeGroup,
                    failure.or(TracePlacementDiagnostics.SkipReason.NON_TERRAIN_BLOCK),
                    failure.sample(sizeGroup));
            return Optional.empty();
        }

        int totalMissing = 0;
        int maxGap = 0;
        int unsupportedColumns = 0;
        for (BlockPos support : profile.supportColumns()) {
            int worldX = originX + support.getX();
            int worldZ = originZ + support.getZ();
            int supportBottomY = anchorY + support.getY();
            int gap = foundationMode == FoundationMode.STEEP_FALLBACK_FULL
                    ? collectSteepFallbackFoundationColumn(
                            treeAccess,
                            structureAreas,
                            context.nether,
                            worldX,
                            supportBottomY,
                            worldZ,
                            fillBlocks,
                            failure)
                    : collectFoundationColumn(
                            level,
                            structureAreas,
                            worldX,
                            supportBottomY,
                            worldZ,
                            fillBlocks,
                            failure);
            if (gap < 0) {
                diagnostics.reject(
                        sizeGroup,
                        failure.or(TracePlacementDiagnostics.SkipReason.FOUNDATION_BLOCKER),
                        failure.sample(sizeGroup));
                return Optional.empty();
            }
            if (gap > 0) unsupportedColumns++;
            totalMissing += gap;
            maxGap = Math.max(maxGap, gap);
            if (foundationMode == FoundationMode.NORMAL && maxGap > MAX_FOUNDATION_GAP_DEPTH) {
                diagnostics.reject(sizeGroup, TracePlacementDiagnostics.SkipReason.FOUNDATION_GAP_TOO_DEEP);
                return Optional.empty();
            }
        }

        if (foundationMode == FoundationMode.NORMAL
                && terrainEditCount(cutBlocks, fillBlocks) > maxTerrainEdits(profile)) {
            diagnostics.reject(sizeGroup, TracePlacementDiagnostics.SkipReason.TERRAIN_EDIT_LIMIT);
            return Optional.empty();
        }

        if (!collectCleanup(
                level,
                profile,
                structureAreas,
                anchor,
                cleanupBlocks,
                failure)) {
            diagnostics.reject(
                    sizeGroup,
                    failure.or(TracePlacementDiagnostics.SkipReason.CLEANUP_STRUCTURE),
                    failure.sample(sizeGroup));
            return Optional.empty();
        }
        SurfaceFailure footprintFailure = footprintSurfaceFailureDetail(
                profile,
                anchor,
                context::surfaceY,
                diagnostics.isEnabled());
        if (footprintFailure != null) {
            if (allowEmbeddedFootprint
                    && allowsSteepTerrainFallbackFootprintFailure(footprintFailure.reason())) {
                footprintFailure = null;
            }
        }
        if (footprintFailure != null) {
            diagnostics.reject(
                    sizeGroup,
                    footprintFailure.reason(),
                    footprintFailure.detail() == null
                            ? null
                            : diagnostics.sample(sizeGroup, footprintFailure::detail));
            return Optional.empty();
        }
        cleanupBlocks.removeAll(cutBlocks);
        cleanupBlocks.removeAll(fillBlocks);
        BlockPos tracePos = anchor.offset(profile.traceOffset());
        int traceSurfaceY = context.surfaceY(tracePos.getX(), tracePos.getZ());
        if (!isTraceVisibleAtSurface(tracePos.getY(), traceSurfaceY)) {
            TracePlacementDiagnostics.SkipReason reason = traceSurfaceY == INVALID_SURFACE
                    ? TracePlacementDiagnostics.SkipReason.NO_SURFACE
                    : TracePlacementDiagnostics.SkipReason.TRACE_BURIED;
            diagnostics.reject(
                    sizeGroup,
                    reason,
                    diagnostics.sample(
                            sizeGroup,
                            () -> TracePlacementDiagnostics.surfaceDetail(
                                    profile.id(),
                                    "trace",
                                    tracePos,
                                    tracePos.getY(),
                                    traceSurfaceY)));
            return Optional.empty();
        }
        return Optional.of(new TracePlacementPlan(
                anchor,
                tracePos,
                new ArrayList<>(cutBlocks),
                new ArrayList<>(fillBlocks),
                new ArrayList<>(cleanupBlocks),
                lowering == 0,
                lowering,
                distanceSq,
                totalMissing,
                maxGap,
                unsupportedColumns,
                rotation));
    }

    private static BoundingBox templateBoundingBox(
            TraceTemplateProfile profile,
            BlockPos anchor,
            FoundationMode foundationMode) {
        if (foundationMode == FoundationMode.STEEP_FALLBACK_FULL) {
            return new BoundingBox(
                    anchor.getX() + profile.minOccupiedX(),
                    anchor.getY() + profile.minOccupiedY(),
                    anchor.getZ() + profile.minOccupiedZ(),
                    anchor.getX() + profile.maxOccupiedX(),
                    anchor.getY() + profile.maxOccupiedY(),
                    anchor.getZ() + profile.maxOccupiedZ());
        }
        Vec3i size = profile.size();
        return new BoundingBox(
                anchor.getX(),
                anchor.getY(),
                anchor.getZ(),
                anchor.getX() + size.getX() - 1,
                anchor.getY() + size.getY() - 1,
                anchor.getZ() + size.getZ() - 1);
    }

    static boolean collectOccupiedCollisions(
            WorldGenLevel level,
            Iterable<BlockPos> occupiedBlocks,
            List<BoundingBox> structureBoxes,
            boolean nether,
            boolean allowTerrainCut,
            Set<BlockPos> cutBlocks,
            Set<BlockPos> cleanupBlocks) {
        return collectOccupiedCollisions(
                level,
                occupiedBlocks,
                StructureGuard.unknownAreas(structureBoxes),
                nether,
                allowTerrainCut,
                cutBlocks,
                cleanupBlocks,
                new Failure());
    }

    private static boolean collectOccupiedCollisions(
            WorldGenLevel level,
            Iterable<BlockPos> occupiedBlocks,
            Collection<StructureGuard.StructureArea> structureAreas,
            boolean nether,
            boolean allowTerrainCut,
            Set<BlockPos> cutBlocks,
            Set<BlockPos> cleanupBlocks,
            Failure failure) {
        for (BlockPos pos : occupiedBlocks) {
            if (!collectOccupiedCollision(
                    level,
                    pos,
                    structureAreas,
                    nether,
                    allowTerrainCut,
                    cutBlocks,
                    cleanupBlocks,
                    failure)) {
                return false;
            }
        }
        return validateCutDepth(cutBlocks, failure);
    }

    private static boolean collectOccupiedCollisions(
            WorldGenLevel level,
            TraceTemplateProfile profile,
            int originX,
            int anchorY,
            int originZ,
            Collection<StructureGuard.StructureArea> structureAreas,
            boolean nether,
            boolean allowTerrainCut,
            Set<BlockPos> cutBlocks,
            Set<BlockPos> cleanupBlocks,
            Failure failure) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (BlockPos occupied : profile.occupiedBlocks()) {
            pos.set(
                    originX + occupied.getX(),
                    anchorY + occupied.getY(),
                    originZ + occupied.getZ());
            if (!collectOccupiedCollision(
                    level,
                    pos,
                    structureAreas,
                    nether,
                    allowTerrainCut,
                    cutBlocks,
                    cleanupBlocks,
                    failure)) {
                return false;
            }
        }
        return validateCutDepth(cutBlocks, failure);
    }

    private static boolean collectOccupiedCollision(
            WorldGenLevel level,
            BlockPos pos,
            Collection<StructureGuard.StructureArea> structureAreas,
            boolean nether,
            boolean allowTerrainCut,
            Set<BlockPos> cutBlocks,
            Set<BlockPos> cleanupBlocks,
            Failure failure) {
        Optional<StructureGuard.StructureArea> containingStructure =
                StructureGuard.firstContaining(structureAreas, pos);
        if (containingStructure.isPresent()) {
            BlockPos failurePos = pos.immutable();
            failure.set(
                    TracePlacementDiagnostics.SkipReason.OCCUPIED_STRUCTURE,
                    () -> TracePlacementDiagnostics.structureContainmentDetail(
                            "occupied footprint",
                            failurePos,
                            containingStructure.get()));
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        if (isFluidPlacementSpace(state)) return true;
        if (state.hasBlockEntity()) {
            BlockPos failurePos = pos.immutable();
            failure.set(
                    TracePlacementDiagnostics.SkipReason.BLOCK_ENTITY_COLLISION,
                    () -> TracePlacementDiagnostics.blockDetail("occupied footprint", failurePos, state));
            return false;
        }
        if (isOreBlock(state)) {
            BlockPos failurePos = pos.immutable();
            failure.set(
                    TracePlacementDiagnostics.SkipReason.ORE_OR_PROTECTED_BLOCK,
                    () -> TracePlacementDiagnostics.blockDetail("occupied footprint", failurePos, state));
            return false;
        }
        if (isTreeLog(state)) {
            BlockPos failurePos = pos.immutable();
            failure.set(
                    TracePlacementDiagnostics.SkipReason.NON_TERRAIN_BLOCK,
                    () -> TracePlacementDiagnostics.blockDetail("occupied footprint", failurePos, state));
            return false;
        }
        if (isTreeFoliage(state) || isCleanupRemovable(state)) {
            return addCleanupBlock(structureAreas, pos, cleanupBlocks, failure);
        }
        if (!state.getFluidState().isEmpty()) {
            BlockPos failurePos = pos.immutable();
            failure.set(
                    TracePlacementDiagnostics.SkipReason.NON_TERRAIN_BLOCK,
                    () -> TracePlacementDiagnostics.blockDetail("occupied footprint", failurePos, state));
            return false;
        }
        if (!allowTerrainCut) {
            BlockPos failurePos = pos.immutable();
            failure.set(
                    TracePlacementDiagnostics.SkipReason.TERRAIN_CUT_NOT_ALLOWED,
                    () -> TracePlacementDiagnostics.blockDetail("occupied footprint", failurePos, state));
            return false;
        }
        if (!isCuttableTerrain(state, nether)) {
            BlockPos failurePos = pos.immutable();
            failure.set(
                    TracePlacementDiagnostics.SkipReason.NON_TERRAIN_BLOCK,
                    () -> TracePlacementDiagnostics.blockDetail("occupied footprint", failurePos, state));
            return false;
        }
        cutBlocks.add(pos.immutable());
        return true;
    }

    private static boolean validateCutDepth(Set<BlockPos> cutBlocks, Failure failure) {
        if (!respectsCutDepth(cutBlocks)) {
            failure.set(
                    TracePlacementDiagnostics.SkipReason.CUT_DEPTH_LIMIT,
                    () -> "occupied footprint cutDepthBlocks=" + cutBlocks.size());
            return false;
        }
        return true;
    }

    private static int collectFoundationColumn(
            WorldGenLevel level,
            Collection<StructureGuard.StructureArea> structureAreas,
            int x,
            int supportBottomY,
            int z,
            Set<BlockPos> fillBlocks,
            Failure failure) {
        int gap = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = supportBottomY - 1; y >= level.getMinBuildHeight(); y--) {
            pos.set(x, y, z);
            Optional<StructureGuard.StructureArea> containingStructure =
                    StructureGuard.firstContaining(structureAreas, pos);
            if (containingStructure.isPresent()) {
                BlockPos failurePos = pos.immutable();
                failure.set(
                        TracePlacementDiagnostics.SkipReason.FOUNDATION_STRUCTURE,
                        () -> TracePlacementDiagnostics.structureContainmentDetail(
                                "foundation",
                                failurePos,
                                containingStructure.get()));
                return -1;
            }
            BlockState state = level.getBlockState(pos);
            if (state.hasBlockEntity()
                    || isOreBlock(state)
                    || isTreeLog(state)) {
                BlockPos failurePos = pos.immutable();
                failure.set(
                        TracePlacementDiagnostics.SkipReason.FOUNDATION_BLOCKER,
                        () -> TracePlacementDiagnostics.blockDetail("foundation", failurePos, state));
                return -1;
            }
            if (isFoundationGapState(state)) {
                gap++;
                if (gap > MAX_FOUNDATION_GAP_DEPTH) {
                    int gapDepth = gap;
                    BlockPos failurePos = pos.immutable();
                    failure.set(
                            TracePlacementDiagnostics.SkipReason.FOUNDATION_GAP_TOO_DEEP,
                            () -> "foundation pos=" + TracePlacementDiagnostics.formatPos(failurePos)
                                    + " gap=" + gapDepth
                                    + " max=" + MAX_FOUNDATION_GAP_DEPTH);
                    return -1;
                }
                fillBlocks.add(pos.immutable());
                continue;
            }
            return gap;
        }
        failure.set(
                TracePlacementDiagnostics.SkipReason.FOUNDATION_GAP_TOO_DEEP,
                () -> "foundation x=" + x + " z=" + z + " reached min build height");
        return -1;
    }

    static int collectSteepFallbackFoundationColumn(
            TreeAccess access,
            List<BoundingBox> structureBoxes,
            boolean nether,
            int x,
            int supportBottomY,
            int z,
            Set<BlockPos> fillBlocks) {
        return collectSteepFallbackFoundationColumn(
                access,
                StructureGuard.unknownAreas(structureBoxes),
                nether,
                x,
                supportBottomY,
                z,
                fillBlocks,
                new Failure());
    }

    private static int collectSteepFallbackFoundationColumn(
            TreeAccess access,
            Collection<StructureGuard.StructureArea> structureAreas,
            boolean nether,
            int x,
            int supportBottomY,
            int z,
            Set<BlockPos> fillBlocks,
            Failure failure) {
        int gap = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = supportBottomY - 1; y >= access.minBuildHeight(); y--) {
            pos.set(x, y, z);
            Optional<StructureGuard.StructureArea> containingStructure =
                    StructureGuard.firstContaining(structureAreas, pos);
            if (containingStructure.isPresent()) {
                BlockPos failurePos = pos.immutable();
                failure.set(
                        TracePlacementDiagnostics.SkipReason.FOUNDATION_STRUCTURE,
                        () -> TracePlacementDiagnostics.structureContainmentDetail(
                                "fallback foundation",
                                failurePos,
                                containingStructure.get()));
                return -1;
            }

            BlockState state = access.getBlockState(pos);
            if (state.hasBlockEntity() || isOreBlock(state) || isTreeLog(state)) {
                BlockPos failurePos = pos.immutable();
                failure.set(
                        TracePlacementDiagnostics.SkipReason.FOUNDATION_BLOCKER,
                        () -> TracePlacementDiagnostics.blockDetail("fallback foundation", failurePos, state));
                return -1;
            }
            if (isFoundationGapState(state)) {
                gap++;
                fillBlocks.add(pos.immutable());
                continue;
            }
            if (!isCuttableTerrain(state, nether)) {
                BlockPos failurePos = pos.immutable();
                failure.set(
                        TracePlacementDiagnostics.SkipReason.FOUNDATION_BLOCKER,
                        () -> TracePlacementDiagnostics.blockDetail("fallback foundation", failurePos, state));
                return -1;
            }
            return gap;
        }

        failure.set(
                TracePlacementDiagnostics.SkipReason.FOUNDATION_GAP_TOO_DEEP,
                () -> "fallback foundation x=" + x + " z=" + z + " reached min build height");
        return -1;
    }

    static boolean collectCleanup(
            WorldGenLevel level,
            TraceTemplateProfile profile,
            List<BoundingBox> structureBoxes,
            BlockPos anchor,
            Set<BlockPos> cleanupBlocks) {
        return collectCleanup(
                level,
                profile,
                StructureGuard.unknownAreas(structureBoxes),
                anchor,
                cleanupBlocks,
                new Failure());
    }

    private static boolean collectCleanup(
            WorldGenLevel level,
            TraceTemplateProfile profile,
            Collection<StructureGuard.StructureArea> structureAreas,
            BlockPos anchor,
            Set<BlockPos> cleanupBlocks,
            Failure failure) {
        int anchorX = anchor.getX();
        int anchorY = anchor.getY();
        int anchorZ = anchor.getZ();
        int minX = anchorX + profile.minOccupiedX() - CLEANUP_MARGIN;
        int maxX = anchorX + profile.maxOccupiedX() + CLEANUP_MARGIN;
        int minZ = anchorZ + profile.minOccupiedZ() - CLEANUP_MARGIN;
        int maxZ = anchorZ + profile.maxOccupiedZ() + CLEANUP_MARGIN;
        int minY = anchorY;
        int maxY = Math.min(
                level.getMaxBuildHeight() - 1,
                anchorY + profile.size().getY() - 1 + CLEANUP_EXTRA_HEIGHT);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || !state.getFluidState().isEmpty()) continue;
                    if (isTreeLog(state)) continue;
                    if (isColumnPlant(state)) {
                        if (!collectWholePlantColumn(level, structureAreas, pos, cleanupBlocks, failure)) {
                            return false;
                        }
                    } else if (isTreeFoliageBlock(state)) {
                        if (!profile.containsTraceContact(x - anchorX, y - anchorY, z - anchorZ)) continue;
                        if (!addCleanupBlock(structureAreas, pos, cleanupBlocks, failure)) return false;
                    } else if (isCleanupRemovable(state)) {
                        if (!addCleanupBlock(structureAreas, pos, cleanupBlocks, failure)) return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean addCleanupBlock(
            Collection<StructureGuard.StructureArea> structureAreas,
            BlockPos pos,
            Set<BlockPos> cleanupBlocks,
            Failure failure) {
        Optional<StructureGuard.StructureArea> containingStructure =
                StructureGuard.firstContaining(structureAreas, pos);
        if (containingStructure.isPresent()) {
            BlockPos failurePos = pos.immutable();
            failure.set(
                    TracePlacementDiagnostics.SkipReason.CLEANUP_STRUCTURE,
                    () -> TracePlacementDiagnostics.structureContainmentDetail(
                            "cleanup",
                            failurePos,
                            containingStructure.get()));
            return false;
        }
        cleanupBlocks.add(pos.immutable());
        return true;
    }

    private static boolean collectWholePlantColumn(
            WorldGenLevel level,
            Collection<StructureGuard.StructureArea> structureAreas,
            BlockPos origin,
            Set<BlockPos> cleanupBlocks,
            Failure failure) {
        BlockState originState = level.getBlockState(origin);
        int minY = origin.getY();
        int maxY = origin.getY();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        while (minY > level.getMinBuildHeight()
                && sameColumnPlant(originState, level.getBlockState(cursor.set(origin.getX(), minY - 1, origin.getZ())))) {
            minY--;
        }
        while (maxY + 1 < level.getMaxBuildHeight()
                && sameColumnPlant(originState, level.getBlockState(cursor.set(origin.getX(), maxY + 1, origin.getZ())))) {
            maxY++;
        }
        for (int y = minY; y <= maxY; y++) {
            cursor.set(origin.getX(), y, origin.getZ());
            Optional<StructureGuard.StructureArea> containingStructure =
                    StructureGuard.firstContaining(structureAreas, cursor);
            if (containingStructure.isPresent()) {
                BlockPos failurePos = cursor.immutable();
                failure.set(
                        TracePlacementDiagnostics.SkipReason.COLUMN_PLANT_IN_STRUCTURE,
                        () -> TracePlacementDiagnostics.structureContainmentDetail(
                                "plant column cleanup",
                                failurePos,
                                containingStructure.get()));
                return false;
            }
            cleanupBlocks.add(cursor.immutable());
        }
        return true;
    }

    private static boolean sameColumnPlant(BlockState first, BlockState second) {
        if (isBamboo(first)) return isBamboo(second);
        if (first.is(Blocks.CACTUS)) return second.is(Blocks.CACTUS);
        if (first.is(Blocks.SUGAR_CANE)) return second.is(Blocks.SUGAR_CANE);
        return false;
    }

    private static boolean isBamboo(BlockState state) {
        return state.is(Blocks.BAMBOO) || state.is(Blocks.BAMBOO_SAPLING);
    }

    private static boolean isColumnPlant(BlockState state) {
        return isBamboo(state) || state.is(Blocks.CACTUS) || state.is(Blocks.SUGAR_CANE);
    }

    private static boolean isAquaticPlant(BlockState state) {
        return state.is(Blocks.SEAGRASS)
                || state.is(Blocks.TALL_SEAGRASS)
                || state.is(Blocks.KELP)
                || state.is(Blocks.KELP_PLANT);
    }

    static boolean isTreeLog(BlockState state) {
        return state.is(BlockTags.LOGS) || TREE_LOG_BLOCKS.contains(state.getBlock());
    }

    static boolean isTreeFoliage(BlockState state) {
        return isTreeFoliageBlock(state)
                || (!isAquaticPlant(state) && state.is(BlockTags.REPLACEABLE));
    }

    private static boolean isTreeFoliageBlock(BlockState state) {
        return !isAquaticPlant(state)
                && (state.is(BlockTags.LEAVES)
                || TREE_FOLIAGE_BLOCKS.contains(state.getBlock()));
    }

    static boolean isCleanupRemovable(BlockState state) {
        return isColumnPlant(state)
                || isAquaticPlant(state)
                || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.REPLACEABLE)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.RED_MUSHROOM)
                || state.is(Blocks.BROWN_MUSHROOM)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.GLOW_LICHEN);
    }

    static boolean isFluidPlacementSpace(BlockState state) {
        return state.getBlock() instanceof LiquidBlock && !state.getFluidState().isEmpty();
    }

    static boolean isFoundationGapState(BlockState state) {
        return state.isAir() || isFluidPlacementSpace(state) || isCleanupRemovable(state);
    }

    static boolean isCuttableTerrain(BlockState state, boolean nether) {
        if (isOreBlock(state)) return false;
        if (nether) {
            return state.is(Blocks.NETHERRACK)
                    || isNylium(state)
                    || state.is(Blocks.SOUL_SAND)
                    || state.is(Blocks.SOUL_SOIL)
                    || state.is(Blocks.BASALT)
                    || state.is(Blocks.BLACKSTONE)
                    || state.is(Blocks.MAGMA_BLOCK)
                    || state.is(Blocks.GRAVEL);
        }
        return isDirt(state)
                || isSand(state)
                || state.is(Blocks.GRAVEL)
                || isOverworldRock(state);
    }

    private static boolean isOreBlock(BlockState state) {
        return state.is(Tags.Blocks.ORES)
                || state.is(BlockTags.COAL_ORES)
                || state.is(BlockTags.COPPER_ORES)
                || state.is(BlockTags.DIAMOND_ORES)
                || state.is(BlockTags.EMERALD_ORES)
                || state.is(BlockTags.GOLD_ORES)
                || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.REDSTONE_ORES)
                || state.is(Blocks.NETHER_GOLD_ORE)
                || state.is(Blocks.NETHER_QUARTZ_ORE)
                || state.is(Blocks.ANCIENT_DEBRIS);
    }

    private static boolean isDirt(BlockState state) {
        return state.is(BlockTags.DIRT)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.MUD)
                || state.is(Blocks.MUDDY_MANGROVE_ROOTS);
    }

    private static boolean isSand(BlockState state) {
        return state.is(BlockTags.SAND)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND)
                || state.is(Blocks.SUSPICIOUS_SAND);
    }

    private static boolean isNylium(BlockState state) {
        return state.is(BlockTags.NYLIUM)
                || state.is(Blocks.CRIMSON_NYLIUM)
                || state.is(Blocks.WARPED_NYLIUM);
    }

    private static boolean isOverworldRock(BlockState state) {
        return state.is(Blocks.STONE)
                || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.TUFF)
                || state.is(Blocks.CALCITE)
                || state.is(Blocks.GRANITE)
                || state.is(Blocks.DIORITE)
                || state.is(Blocks.ANDESITE);
    }

    static List<BlockPos> worldOccupiedBlocks(TraceTemplateProfile profile, BlockPos anchor) {
        List<BlockPos> result = new ArrayList<>(profile.occupiedBlocks().size());
        for (BlockPos occupied : profile.occupiedBlocks()) {
            result.add(anchor.offset(occupied));
        }
        return List.copyOf(result);
    }

    static Set<BlockPos> traceContactBlocks(TraceTemplateProfile profile, BlockPos anchor) {
        return traceContactBlocks(worldOccupiedBlocks(profile, anchor));
    }

    static Set<BlockPos> traceContactBlocks(Iterable<BlockPos> occupiedBlocks) {
        LinkedHashSet<BlockPos> contactBlocks = new LinkedHashSet<>();
        for (BlockPos occupied : occupiedBlocks) {
            contactBlocks.add(occupied.immutable());
            for (Direction direction : Direction.values()) {
                contactBlocks.add(occupied.relative(direction).immutable());
            }
        }
        return Set.copyOf(contactBlocks);
    }

    static boolean respectsCutDepth(Iterable<BlockPos> cutBlocks) {
        Map<Long, Integer> cutsByColumn = new HashMap<>();
        for (BlockPos pos : cutBlocks) {
            int count = cutsByColumn.merge(worldColumnKey(pos.getX(), pos.getZ()), 1, Integer::sum);
            if (count > MAX_CUT_DEPTH) return false;
        }
        return true;
    }

    static int maxTerrainEdits(TraceTemplateProfile profile) {
        return Math.max(MIN_TERRAIN_EDITS, profile.supportColumns().size() * TERRAIN_EDITS_PER_SUPPORT_COLUMN);
    }

    static boolean isTraceVisibleAtSurface(TraceTemplateProfile profile, int anchorY, int surfaceY) {
        return isTraceVisibleAtSurface(anchorY + profile.traceOffset().getY(), surfaceY);
    }

    static boolean isTraceVisibleAtSurface(int traceY, int surfaceY) {
        return surfaceY != INVALID_SURFACE && traceY >= surfaceY;
    }

    static TracePlacementDiagnostics.SkipReason footprintSurfaceFailure(
            TraceTemplateProfile profile,
            BlockPos anchor,
            SurfaceLookup surfaceLookup) {
        SurfaceFailure failure = footprintSurfaceFailureDetail(profile, anchor, surfaceLookup, false);
        return failure == null ? null : failure.reason();
    }

    static boolean allowsSteepTerrainFallbackFootprintFailure(
            TracePlacementDiagnostics.SkipReason reason) {
        return reason == TracePlacementDiagnostics.SkipReason.TRACE_EMBEDDED;
    }

    private static SurfaceFailure footprintSurfaceFailureDetail(
            TraceTemplateProfile profile,
            BlockPos anchor,
            SurfaceLookup surfaceLookup,
            boolean includeDetail) {
        for (BlockPos support : profile.supportColumns()) {
            int worldX = anchor.getX() + support.getX();
            int worldZ = anchor.getZ() + support.getZ();
            int supportBottomY = anchor.getY() + support.getY();
            int surfaceY = surfaceLookup.surfaceY(worldX, worldZ);
            BlockPos supportPos = new BlockPos(worldX, supportBottomY, worldZ);
            if (surfaceY == INVALID_SURFACE) {
                return new SurfaceFailure(
                        TracePlacementDiagnostics.SkipReason.NO_SURFACE,
                        includeDetail
                                ? TracePlacementDiagnostics.surfaceDetail(
                                        profile.id(),
                                        "support",
                                        supportPos,
                                        supportBottomY,
                                        surfaceY)
                                : null);
            }
            if (supportBottomY < surfaceY) {
                return new SurfaceFailure(
                        TracePlacementDiagnostics.SkipReason.TRACE_EMBEDDED,
                        includeDetail
                                ? TracePlacementDiagnostics.surfaceDetail(
                                        profile.id(),
                                        "support",
                                        supportPos,
                                        supportBottomY,
                                        surfaceY)
                                : null);
            }
        }
        return null;
    }

    private static TreeAccess treeAccess(WorldGenLevel level) {
        return new TreeAccess() {
            @Override
            public BlockState getBlockState(BlockPos pos) {
                return level.getBlockState(pos);
            }

            @Override
            public int minBuildHeight() {
                return level.getMinBuildHeight();
            }

            @Override
            public int maxBuildHeight() {
                return level.getMaxBuildHeight();
            }
        };
    }

    private static int terrainEditCount(Set<BlockPos> cutBlocks, Set<BlockPos> fillBlocks) {
        return cutBlocks.size() + fillBlocks.size();
    }

    private static int surfaceY(
            WorldGenLevel level,
            int nodeY,
            int x,
            int z,
            boolean nether,
            Long2IntOpenHashMap cache) {
        long key = worldColumnKey(x, z);
        int cached = cache.get(key);
        if (cached != SURFACE_CACHE_MISS) return cached;
        int result = nether
                ? findNetherSurface(level, nodeY, x, z)
                : findOverworldSurface(level, x, z);
        cache.put(key, result);
        return result;
    }

    private static int findOverworldSurface(WorldGenLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = top; y >= level.getMinBuildHeight(); y--) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (isSurfacePassThrough(level, pos, state)) continue;
            return y + 1;
        }
        return INVALID_SURFACE;
    }

    private static int findNetherSurface(WorldGenLevel level, int nodeY, int x, int z) {
        int maxY = Math.min(
                level.getMaxBuildHeight() - 1,
                Math.min(MAX_NETHER_SCAN_Y, nodeY + MAX_NETHER_SCAN_DISTANCE));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos belowPos = new BlockPos.MutableBlockPos();
        for (int y = nodeY + 1; y <= maxY; y++) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (!isOpenPlacementState(state)) continue;
            BlockState below = level.getBlockState(belowPos.set(x, y - 1, z));
            if (isSolidGround(below)) return y;
        }
        return INVALID_SURFACE;
    }

    private static boolean isSurfacePassThrough(WorldGenLevel level, BlockPos pos, BlockState state) {
        return state.isAir()
                || isFluidPlacementSpace(state)
                || isFrozenWaterSurface(level, pos, state)
                || isCleanupRemovable(state)
                || isTreeLog(state);
    }

    static boolean isOpenPlacementState(BlockState state) {
        return state.isAir() || isFluidPlacementSpace(state) || isCleanupRemovable(state);
    }

    static boolean isSolidGround(BlockState state) {
        return !state.isAir()
                && state.getFluidState().isEmpty()
                && !isCleanupRemovable(state)
                && !isTreeLog(state);
    }

    private static boolean isFrozenWaterSurface(WorldGenLevel level, BlockPos pos, BlockState state) {
        if (!state.is(Blocks.ICE)
                && !state.is(Blocks.FROSTED_ICE)
                && !state.is(Blocks.PACKED_ICE)
                && !state.is(Blocks.BLUE_ICE)) {
            return false;
        }
        if (pos.getY() - 1 < level.getMinBuildHeight()) return false;
        return level.getFluidState(pos.below()).is(FluidTags.WATER);
    }

    private static boolean requiredChunksAvailable(
            WorldGenLevel level,
            int originX,
            int originZ,
            TraceTemplateProfile profile) {
        int minX = originX + profile.minOccupiedX() - CLEANUP_MARGIN;
        int maxX = originX + profile.maxOccupiedX() + CLEANUP_MARGIN;
        int minZ = originZ + profile.minOccupiedZ() - CLEANUP_MARGIN;
        int maxZ = originZ + profile.maxOccupiedZ() + CLEANUP_MARGIN;
        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) return false;
            }
        }
        return true;
    }

    static boolean isBetterPlan(TracePlacementPlan candidate, TracePlacementPlan current) {
        if (current == null) return true;
        int costComparison = comparePlanCost(candidate, current);
        if (costComparison != 0) return costComparison < 0;
        return comparePlanAnchor(candidate, current) < 0;
    }

    static boolean isBetterSteepFallbackPlan(TracePlacementPlan candidate, TracePlacementPlan current) {
        if (current == null) return true;
        int costComparison = compareSteepFallbackPlanCost(candidate, current);
        if (costComparison != 0) return costComparison < 0;
        return comparePlanAnchor(candidate, current) < 0;
    }

    static int comparePlanCost(TracePlacementPlan left, TracePlacementPlan right) {
        int terrainEdits = Integer.compare(left.terrainEditCount(), right.terrainEditCount());
        if (terrainEdits != 0) return terrainEdits;
        int modifiedBlocks = Integer.compare(left.modifiedBlockCount(), right.modifiedBlockCount());
        if (modifiedBlocks != 0) return modifiedBlocks;
        int distance = Integer.compare(left.distanceSq(), right.distanceSq());
        if (distance != 0) return distance;
        return Integer.compare(left.lowering(), right.lowering());
    }

    static int compareSteepFallbackPlanCost(TracePlacementPlan left, TracePlacementPlan right) {
        int fillBlocks = Integer.compare(left.fillBlocks().size(), right.fillBlocks().size());
        if (fillBlocks != 0) return fillBlocks;
        int cutBlocks = Integer.compare(left.cutBlocks().size(), right.cutBlocks().size());
        if (cutBlocks != 0) return cutBlocks;
        int cleanupBlocks = Integer.compare(left.cleanupBlocks().size(), right.cleanupBlocks().size());
        if (cleanupBlocks != 0) return cleanupBlocks;
        int modifiedBlocks = Integer.compare(left.modifiedBlockCount(), right.modifiedBlockCount());
        if (modifiedBlocks != 0) return modifiedBlocks;
        return Integer.compare(rotationOrder(left.rotation()), rotationOrder(right.rotation()));
    }

    private static int rotationOrder(Rotation rotation) {
        int index = STEEP_FALLBACK_ROTATIONS.indexOf(rotation);
        return index < 0 ? STEEP_FALLBACK_ROTATIONS.size() : index;
    }

    static int comparePlanAnchor(TracePlacementPlan left, TracePlacementPlan right) {
        TracePlacementPlan candidate = left;
        TracePlacementPlan current = right;
        if (candidate.anchor().getX() != current.anchor().getX()) {
            return Integer.compare(candidate.anchor().getX(), current.anchor().getX());
        }
        if (candidate.anchor().getZ() != current.anchor().getZ()) {
            return Integer.compare(candidate.anchor().getZ(), current.anchor().getZ());
        }
        return Integer.compare(candidate.anchor().getY(), current.anchor().getY());
    }

    private static long worldColumnKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
