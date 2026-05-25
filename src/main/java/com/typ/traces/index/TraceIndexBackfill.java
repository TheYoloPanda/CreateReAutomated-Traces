package com.typ.traces.index;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.typ.traces.worldgen.TraceBlockDataMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class TraceIndexBackfill {

    private static final TagKey<Block> ORE_NODES = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("createreautomated", "ore_nodes"));

    private static final int CHUNKS_PER_TICK = 4;
    private static final int MAX_DEPTH_BELOW = 96;
    private static final int MAX_DEPTH_FROM_SURFACE = 6;
    private static final int MAX_HORIZONTAL_FROM_TRACE = 8;

    private static final Map<ResourceKey<Level>, Deque<Long>> PENDING = new HashMap<>();

    private TraceIndexBackfill() {}

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide()) return;
        if (event.isNewChunk()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long ckey = event.getChunk().getPos().toLong();
        if (TraceIndex.isScanned(level, ckey)) return;
        PENDING.computeIfAbsent(level.dimension(), k -> new ArrayDeque<>()).addLast(ckey);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            Deque<Long> queue = PENDING.get(level.dimension());
            if (queue == null || queue.isEmpty()) continue;
            Set<Block> traceBlocks = collectTraceBlocks();
            if (traceBlocks.isEmpty()) {
                queue.clear();
                continue;
            }
            int budget = CHUNKS_PER_TICK;
            while (budget-- > 0 && !queue.isEmpty()) {
                long ckey = queue.pollFirst();
                if (TraceIndex.isScanned(level, ckey)) continue;
                scanChunk(level, ckey, traceBlocks);
                TraceIndex.markScanned(level, ckey);
            }
        }
    }

    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        PENDING.clear();
    }

    private static Set<Block> collectTraceBlocks() {
        Set<Block> set = new HashSet<>();
        for (Block b : BuiltInRegistries.BLOCK) {
            TraceBlockDataMap.TraceBlockEntry e =
                    b.builtInRegistryHolder().getData(TraceBlockDataMap.TYPE);
            if (e != null) set.add(e.block());
        }
        return set;
    }

    private static void scanChunk(ServerLevel level, long chunkLong, Set<Block> traceBlocks) {
        ChunkPos cp = new ChunkPos(chunkLong);
        if (!level.hasChunk(cp.x, cp.z)) return;
        ChunkAccess chunk = level.getChunk(cp.x, cp.z);
        LevelChunkSection[] sections = chunk.getSections();
        int minBuildY = chunk.getMinBuildHeight();
        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();

        for (int si = 0; si < sections.length; si++) {
            LevelChunkSection section = sections[si];
            if (section == null || section.hasOnlyAir()) continue;
            if (!section.maybeHas(state -> traceBlocks.contains(state.getBlock()))) continue;

            int yBase = minBuildY + si * 16;
            for (int dy = 0; dy < 16; dy++) {
                for (int dx = 0; dx < 16; dx++) {
                    for (int dz = 0; dz < 16; dz++) {
                        BlockState state = section.getBlockState(dx, dy, dz);
                        if (!traceBlocks.contains(state.getBlock())) continue;
                        int wy = yBase + dy;
                        int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, dx, dz);
                        if (wy < surfaceY - MAX_DEPTH_FROM_SURFACE) continue;
                        int wx = cp.getMinBlockX() + dx;
                        int wz = cp.getMinBlockZ() + dz;
                        cur.set(wx, wy, wz);
                        ResourceLocation nodeId = findNodeBelow(level, chunk, cur, state.getBlock());
                        if (nodeId == null) continue;
                        TraceIndex.record(level, cur.immutable(), nodeId);
                    }
                }
            }
        }
    }

    private static ResourceLocation findNodeBelow(ServerLevel level, ChunkAccess chunk, BlockPos candidate, Block traceBlock) {
        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        int minY = chunk.getMinBuildHeight();
        for (int dy = 1; dy <= MAX_DEPTH_BELOW; dy++) {
            int y = candidate.getY() - dy;
            if (y < minY) break;
            for (int dx = -MAX_HORIZONTAL_FROM_TRACE; dx <= MAX_HORIZONTAL_FROM_TRACE; dx++) {
                for (int dz = -MAX_HORIZONTAL_FROM_TRACE; dz <= MAX_HORIZONTAL_FROM_TRACE; dz++) {
                    int x = candidate.getX() + dx;
                    int z = candidate.getZ() + dz;
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;
                    cur.set(x, y, z);
                    BlockState s = level.getBlockState(cur);
                    if (!s.is(ORE_NODES)) continue;
                    Optional<Block> mapped = TraceBlockDataMap.traceBlockFor(s.getBlock());
                    if (mapped.isEmpty() || mapped.get() != traceBlock) continue;
                    return BuiltInRegistries.BLOCK.getKey(s.getBlock());
                }
            }
        }
        return null;
    }
}
