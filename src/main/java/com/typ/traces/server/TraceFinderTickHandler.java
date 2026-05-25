package com.typ.traces.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import com.typ.traces.component.TrackedNodesComponent;
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
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TraceFinderTickHandler {

    private static final int TICK_INTERVAL = 10;
    private static final int RENDER_RADIUS_CHUNKS = 12;
    private static final int DISCOVERY_RADIUS_BLOCKS = 8;
    private static final int DISCOVERY_RADIUS_SQ = DISCOVERY_RADIUS_BLOCKS * DISCOVERY_RADIUS_BLOCKS;
    private static final int TRACE_BLOCK_SEARCH_RADIUS_XZ = 4;
    private static final int TRACE_BLOCK_SEARCH_RADIUS_Y = 6;

    private record PlayerSnapshot(BlockPos target, LongOpenHashSet visible) {}
    private record TraceMove(BlockPos from, BlockPos to, ResourceLocation nodeId) {}

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
        List<VisibleTracesPayload.Entry> currentEntries = new ArrayList<>();
        LongOpenHashSet currentSet = new LongOpenHashSet();
        BlockPos[] nearestHolder = new BlockPos[1];
        double[] nearestSqHolder = { Double.MAX_VALUE };
        Map<ResourceLocation, Integer> colorCache = new HashMap<>();
        Map<GlobalPos, ResourceLocation> toDiscoverIds = new HashMap<>();
        List<BlockPos> staleTraces = new ArrayList<>();
        List<TraceMove> traceMoves = new ArrayList<>();

        TraceIndex.forEachInRange(level, pp, RENDER_RADIUS_CHUNKS, selected::contains, rec -> {
            BlockPos indexedPos = rec.pos();
            GlobalPos indexedGlobalPos = GlobalPos.of(level.dimension(), indexedPos);
            // Already discovered by some Finder in inventory → never beam, never target.
            if (discoveredUnion.contains(indexedGlobalPos)) return;

            Block nodeBlock = BuiltInRegistries.BLOCK.get(rec.nodeId());
            Block traceBlock = TraceBlockDataMap.traceBlockFor(nodeBlock).orElse(null);
            Optional<BlockPos> resolvedPos = resolveTracePos(level, indexedPos, traceBlock);
            if (traceBlock == null || resolvedPos.isEmpty()) {
                staleTraces.add(indexedPos);
                return;
            }
            BlockPos p = resolvedPos.get();
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

            int color = colorCache.computeIfAbsent(rec.nodeId(), id -> {
                return TraceColorResolver.resolve(traceBlock, nodeBlock);
            });
            currentEntries.add(new VisibleTracesPayload.Entry(pl, rec.nodeId(), color));
            currentSet.add(pl);
            if (horizSq < nearestSqHolder[0]) {
                nearestSqHolder[0] = horizSq;
                nearestHolder[0] = p;
            }
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
            for (Map.Entry<GlobalPos, ResourceLocation> e : toDiscoverIds.entrySet()) {
                markDiscovered(invState.finders(), e.getValue(), e.getKey());
            }
        }

        BlockPos newTarget = nearestHolder[0];

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

    private static Optional<BlockPos> resolveTracePos(ServerLevel level, BlockPos indexedPos, Block traceBlock) {
        if (traceBlock == null) return Optional.empty();
        if (level.getBlockState(indexedPos).is(traceBlock)) return Optional.of(indexedPos);

        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        int minY = Math.max(level.getMinBuildHeight(), indexedPos.getY() - TRACE_BLOCK_SEARCH_RADIUS_Y);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, indexedPos.getY() + TRACE_BLOCK_SEARCH_RADIUS_Y);

        for (int dx = -TRACE_BLOCK_SEARCH_RADIUS_XZ; dx <= TRACE_BLOCK_SEARCH_RADIUS_XZ; dx++) {
            for (int dz = -TRACE_BLOCK_SEARCH_RADIUS_XZ; dz <= TRACE_BLOCK_SEARCH_RADIUS_XZ; dz++) {
                int x = indexedPos.getX() + dx;
                int z = indexedPos.getZ() + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                for (int y = minY; y <= maxY; y++) {
                    cur.set(x, y, z);
                    if (!level.getBlockState(cur).is(traceBlock)) continue;
                    double dist = cur.distSqr(indexedPos);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = cur.immutable();
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static void markDiscovered(List<ItemStack> finders, ResourceLocation nodeId, GlobalPos tracePos) {
        for (ItemStack stack : finders) {
            TrackedNodesComponent comp = stack.get(ModDataComponents.TRACKED_NODES.get());
            if (comp == null || !comp.isTracking(nodeId)) continue;
            if (comp.isDiscovered(tracePos)) continue;
            Set<GlobalPos> updated = new HashSet<>(comp.discovered());
            updated.add(tracePos);
            stack.set(ModDataComponents.TRACKED_NODES.get(), comp.withDiscovered(updated));
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
