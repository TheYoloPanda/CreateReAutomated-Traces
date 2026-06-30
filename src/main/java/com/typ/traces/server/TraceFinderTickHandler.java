package com.typ.traces.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import com.typ.traces.api.TraceApi;
import com.typ.traces.component.TrackedNodesComponent;
import com.typ.traces.config.Common;
import com.typ.traces.config.Config;
import com.typ.traces.index.TraceIndex;
import com.typ.traces.item.TraceFinderItem;
import com.typ.traces.network.TargetUpdatePayload;
import com.typ.traces.network.VisibleTracesPayload;
import com.typ.traces.registry.ModDataComponents;
import com.typ.traces.util.TraceColorResolver;
import com.typ.traces.worldgen.TraceBlockDataMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TraceFinderTickHandler {

    private static final int TICK_INTERVAL = 10;
    private static final int DEFAULT_RENDER_RADIUS_CHUNKS = 12;
    private static final int MIN_RENDER_RADIUS_CHUNKS = 2;
    private static final int MAX_RENDER_RADIUS_CHUNKS = 12;
    private static final int DEFAULT_MAX_VISIBLE_TRACE_BEAMS = 64;
    private static final int MAX_VISIBLE_TRACE_BEAMS = 256;
    private static final int DISCOVERY_RADIUS_BLOCKS = 8;
    private static final int DISCOVERY_RADIUS_SQ = DISCOVERY_RADIUS_BLOCKS * DISCOVERY_RADIUS_BLOCKS;
    private static final int TRACE_BLOCK_SEARCH_RADIUS_XZ = 4;
    private static final int TRACE_BLOCK_SEARCH_RADIUS_Y = 6;

    private record PlayerSnapshot(BlockPos target, LongOpenHashSet visible) {}
    private record TraceMove(BlockPos from, BlockPos to, ResourceLocation nodeId) {}
    private record BeamCandidate(long posLong, ResourceLocation nodeId, int horizontalDistanceSq,
                                 Block traceBlock, Block nodeBlock) {}
    record TraceRecordLookup(Block nodeBlock, Block traceBlock) {}

    enum TraceResolutionStatus {
        FOUND,
        MISSING,
        UNAVAILABLE
    }

    record TraceResolution(TraceResolutionStatus status, BlockPos pos) {
        private static TraceResolution found(BlockPos pos) {
            return new TraceResolution(TraceResolutionStatus.FOUND, pos);
        }

        private static TraceResolution missing() {
            return new TraceResolution(TraceResolutionStatus.MISSING, null);
        }

        private static TraceResolution unavailable() {
            return new TraceResolution(TraceResolutionStatus.UNAVAILABLE, null);
        }
    }

    private static final class NearestTarget {
        private BlockPos pos;
        private int horizontalDistanceSq = Integer.MAX_VALUE;

        void consider(BlockPos candidatePos, int candidateDistanceSq) {
            if (candidateDistanceSq >= horizontalDistanceSq) return;
            pos = candidatePos;
            horizontalDistanceSq = candidateDistanceSq;
        }

        BlockPos pos() {
            return pos;
        }
    }

    private static final Map<UUID, PlayerSnapshot> SNAPSHOTS = new HashMap<>();
    private static int tickCounter = 0;

    private TraceFinderTickHandler() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter % TICK_INTERVAL != 0) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            processPlayer(player);
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SNAPSHOTS.remove(event.getEntity().getUUID());
    }

    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        SNAPSHOTS.remove(event.getEntity().getUUID());
    }

    private record InvState(Set<ResourceLocation> selectedUnion,
                             Set<GlobalPos> discoveredUnion,
                             List<ItemStack> finders) {}

    private static void processPlayer(ServerPlayer player) {
        InvState invState = collectInventoryState(player);
        Set<ResourceLocation> selected = invState.selectedUnion();
        Set<GlobalPos> discoveredUnion = invState.discoveredUnion();
        PlayerSnapshot prev = SNAPSHOTS.get(player.getUUID());

        if (selected.isEmpty()) {
            if (prev == null) return;
            if (prev.target != null) {
                PacketDistributor.sendToPlayer(player, new TargetUpdatePayload(Optional.empty()));
            }
            if (!prev.visible.isEmpty()) {
                List<Long> rm = new ArrayList<>(prev.visible.size());
                for (long l : prev.visible) rm.add(l);
                PacketDistributor.sendToPlayer(player, new VisibleTracesPayload(List.of(), rm));
            }
            SNAPSHOTS.remove(player.getUUID());
            return;
        }

        ServerLevel level = player.serverLevel();
        ChunkPos pp = player.chunkPosition();
        int renderRadiusChunks = renderRadiusChunks();
        int maxVisibleTraceBeams = maxVisibleTraceBeams();
        List<BeamCandidate> beamCandidates = new ArrayList<>();
        List<VisibleTracesPayload.Entry> currentEntries;
        LongOpenHashSet currentSet = new LongOpenHashSet();
        NearestTarget nearestTarget = new NearestTarget();
        Map<ResourceLocation, Integer> colorCache = new HashMap<>();
        Map<GlobalPos, ResourceLocation> toDiscoverIds = new HashMap<>();
        List<BlockPos> staleTraces = new ArrayList<>();
        List<TraceMove> traceMoves = new ArrayList<>();

        TraceIndex.forEachInRange(level, pp, renderRadiusChunks, selected::contains, rec -> {
            BlockPos indexedPos = rec.pos();
            GlobalPos indexedGlobalPos = GlobalPos.of(level.dimension(), indexedPos);
            // Already discovered by some Finder in inventory → never beam, never target.
            if (discoveredUnion.contains(indexedGlobalPos)) return;

            Optional<TraceRecordLookup> lookup = traceLookupForRecord(rec.nodeId());
            if (lookup.isEmpty()) return;
            TraceRecordLookup traceLookup = lookup.get();

            TraceResolution resolved = resolveTracePos(level, indexedPos, rec.nodeId(), traceLookup.traceBlock());
            if (resolved.status() == TraceResolutionStatus.UNAVAILABLE) return;
            if (shouldRemoveResolvedRecord(resolved)) {
                staleTraces.add(indexedPos);
                return;
            }
            BlockPos p = resolved.pos();
            if (!p.equals(indexedPos)) {
                traceMoves.add(new TraceMove(indexedPos, p, rec.nodeId()));
            }
            long pl = p.asLong();
            GlobalPos tracePos = GlobalPos.of(level.dimension(), p);
            if (discoveredUnion.contains(tracePos)) return;

            int dx = p.getX() - player.getBlockX();
            int dz = p.getZ() - player.getBlockZ();
            int horizSq = dx * dx + dz * dz;
            if (horizSq <= DISCOVERY_RADIUS_SQ) {
                // First-time entry into discovery radius — schedule marking on relevant Finders.
                toDiscoverIds.putIfAbsent(tracePos, rec.nodeId());
                return;
            }

            beamCandidates.add(new BeamCandidate(
                    pl,
                    rec.nodeId(),
                    horizSq,
                    traceLookup.traceBlock(),
                    traceLookup.nodeBlock()));
            nearestTarget.consider(p, horizSq);
        });

        if (!traceMoves.isEmpty()) {
            for (TraceMove move : traceMoves) {
                TraceIndex.remove(level, move.from());
                TraceIndex.record(level, move.to(), move.nodeId());
            }
        }
        if (!staleTraces.isEmpty()) {
            for (BlockPos stale : staleTraces) {
                TraceIndex.remove(level, stale);
            }
        }

        // Apply discovery mutations to the relevant Finder stacks.
        if (!toDiscoverIds.isEmpty()) {
            markDiscovered(invState.finders(), toDiscoverIds);
        }

        BlockPos newTarget = nearestTarget.pos();
        currentEntries = visibleEntries(beamCandidates, currentSet, colorCache, maxVisibleTraceBeams);

        List<VisibleTracesPayload.Entry> added = new ArrayList<>();
        List<Long> removed = new ArrayList<>();
        if (prev == null) {
            added.addAll(currentEntries);
        } else {
            for (VisibleTracesPayload.Entry entry : currentEntries) {
                if (!prev.visible.contains(entry.posLong())) added.add(entry);
            }
            for (long l : prev.visible) {
                if (!currentSet.contains(l)) removed.add(l);
            }
        }

        boolean visibleChanged = !added.isEmpty() || !removed.isEmpty();
        boolean targetChanged = !Objects.equals(prev == null ? null : prev.target, newTarget);

        if (visibleChanged) {
            PacketDistributor.sendToPlayer(player, new VisibleTracesPayload(added, removed));
        }
        if (targetChanged) {
            PacketDistributor.sendToPlayer(player, new TargetUpdatePayload(Optional.ofNullable(newTarget)));
        }

        SNAPSHOTS.put(player.getUUID(), new PlayerSnapshot(newTarget, currentSet));
    }

    private static List<VisibleTracesPayload.Entry> visibleEntries(List<BeamCandidate> candidates,
                                                                   LongOpenHashSet currentSet,
                                                                   Map<ResourceLocation, Integer> colorCache,
                                                                   int maxBeams) {
        if (candidates.isEmpty()) return List.of();

        if (maxBeams > 0 && candidates.size() > maxBeams) {
            candidates.sort(Comparator
                    .comparingInt(BeamCandidate::horizontalDistanceSq)
                    .thenComparingLong(BeamCandidate::posLong));
        }

        int count = maxBeams <= 0 ? candidates.size() : Math.min(maxBeams, candidates.size());
        List<VisibleTracesPayload.Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BeamCandidate candidate = candidates.get(i);
            int color = colorCache.computeIfAbsent(candidate.nodeId(), id ->
                    TraceColorResolver.resolve(candidate.traceBlock(), candidate.nodeBlock()));
            entries.add(new VisibleTracesPayload.Entry(candidate.posLong(), candidate.nodeId(), color));
            currentSet.add(candidate.posLong());
        }
        return entries;
    }

    private static int renderRadiusChunks() {
        Common common = Config.common();
        if (common == null) return DEFAULT_RENDER_RADIUS_CHUNKS;
        int configured = common.finder.traceFinderRenderRadiusChunks.get();
        return Math.max(MIN_RENDER_RADIUS_CHUNKS, Math.min(MAX_RENDER_RADIUS_CHUNKS, configured));
    }

    private static int maxVisibleTraceBeams() {
        Common common = Config.common();
        if (common == null) return DEFAULT_MAX_VISIBLE_TRACE_BEAMS;
        int configured = common.finder.maxVisibleTraceBeams.get();
        return Math.max(0, Math.min(MAX_VISIBLE_TRACE_BEAMS, configured));
    }

    static Optional<TraceRecordLookup> traceLookupForRecord(ResourceLocation nodeId) {
        return BuiltInRegistries.BLOCK.getOptional(nodeId)
                .flatMap(nodeBlock -> TraceBlockDataMap.traceBlockFor(nodeBlock)
                        .map(traceBlock -> new TraceRecordLookup(nodeBlock, traceBlock)));
    }

    static boolean shouldRemoveResolvedRecord(TraceResolution resolved) {
        return resolved.status() == TraceResolutionStatus.MISSING;
    }

    private static TraceResolution resolveTracePos(ServerLevel level, BlockPos indexedPos,
                                                   ResourceLocation nodeId, Block traceBlock) {
        BlockState indexedState = loadedBlockState(level, indexedPos);
        if (indexedState == null) return TraceResolution.unavailable();
        if (indexedState.is(traceBlock)) return TraceResolution.found(indexedPos);
        if (isCompatibleIndexedNode(indexedState, nodeId, traceBlock)) return TraceResolution.found(indexedPos);

        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        boolean completeSearch = true;
        int minY = Math.max(level.getMinBuildHeight(), indexedPos.getY() - TRACE_BLOCK_SEARCH_RADIUS_Y);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, indexedPos.getY() + TRACE_BLOCK_SEARCH_RADIUS_Y);

        for (int dx = -TRACE_BLOCK_SEARCH_RADIUS_XZ; dx <= TRACE_BLOCK_SEARCH_RADIUS_XZ; dx++) {
            for (int dz = -TRACE_BLOCK_SEARCH_RADIUS_XZ; dz <= TRACE_BLOCK_SEARCH_RADIUS_XZ; dz++) {
                int x = indexedPos.getX() + dx;
                int z = indexedPos.getZ() + dz;
                LevelChunk chunk = loadedChunk(level, x >> 4, z >> 4);
                if (chunk == null) {
                    completeSearch = false;
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    cur.set(x, y, z);
                    if (!chunk.getBlockState(cur).is(traceBlock)) continue;
                    double dist = cur.distSqr(indexedPos);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = cur.immutable();
                    }
                }
            }
        }
        if (!completeSearch) return TraceResolution.unavailable();
        return best == null ? TraceResolution.missing() : TraceResolution.found(best);
    }

    private static LevelChunk loadedChunk(ServerLevel level, int chunkX, int chunkZ) {
        return level.getChunkSource().getChunkNow(chunkX, chunkZ);
    }

    private static BlockState loadedBlockState(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = loadedChunk(level, pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockState(pos);
    }

    private static boolean isCompatibleIndexedNode(BlockState state, ResourceLocation nodeId, Block traceBlock) {
        if (!TraceApi.isOreNode(state)) return false;
        if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(nodeId)) return false;
        return TraceBlockDataMap.traceBlockFor(state.getBlock())
                .map(mappedTraceBlock -> mappedTraceBlock == traceBlock)
                .orElse(false);
    }

    private static void markDiscovered(List<ItemStack> finders, Map<GlobalPos, ResourceLocation> discoveredIds) {
        for (ItemStack stack : finders) {
            TrackedNodesComponent comp = stack.get(ModDataComponents.TRACKED_NODES.get());
            if (comp == null) continue;
            Set<GlobalPos> updated = null;
            for (Map.Entry<GlobalPos, ResourceLocation> entry : discoveredIds.entrySet()) {
                GlobalPos tracePos = entry.getKey();
                ResourceLocation nodeId = entry.getValue();
                if (!comp.isTracking(nodeId)) continue;
                if (comp.isDiscovered(tracePos)) continue;
                if (updated == null) {
                    updated = new HashSet<>(comp.discovered());
                }
                updated.add(tracePos);
            }
            if (updated != null) {
                stack.set(ModDataComponents.TRACKED_NODES.get(), comp.withDiscovered(updated));
            }
        }
    }

    private static InvState collectInventoryState(ServerPlayer player) {
        Set<ResourceLocation> selected = new HashSet<>();
        Set<GlobalPos> discovered = new HashSet<>();
        List<ItemStack> finders = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) {
            collectFrom(stack, selected, discovered, finders);
        }
        for (ItemStack stack : player.getInventory().offhand) {
            collectFrom(stack, selected, discovered, finders);
        }
        return new InvState(selected, discovered, finders);
    }

    private static void collectFrom(ItemStack stack,
                                     Set<ResourceLocation> selectedOut,
                                     Set<GlobalPos> discoveredOut,
                                     List<ItemStack> findersOut) {
        if (stack.isEmpty() || !(stack.getItem() instanceof TraceFinderItem)) return;
        TrackedNodesComponent comp = stack.get(ModDataComponents.TRACKED_NODES.get());
        if (comp == null) return;
        selectedOut.addAll(comp.selected());
        discoveredOut.addAll(comp.discovered());
        findersOut.add(stack);
    }
}
