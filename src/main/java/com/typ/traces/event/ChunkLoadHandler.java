package com.typ.traces.event;

import java.util.ArrayList;
import java.util.List;

import com.typ.traces.worldgen.TraceStructurePlacer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

public class ChunkLoadHandler {

    private static final TagKey<Block> ORE_NODES = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("createreautomated", "ore_nodes"));

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        ChunkAccess chunk = event.getChunk();
        ChunkPos cp = chunk.getPos();

        List<NodeMatch> matches = scanChunkForNodes(chunk, cp);
        if (matches.isEmpty()) return;

        long deterministicSeed = cp.toLong() ^ level.getSeed();
        MinecraftServer server = level.getServer();
        if (server == null) return;

        server.tell(new TickTask(server.getTickCount() + 1, () -> {
            if (!level.hasChunk(cp.x, cp.z)) return;
            RandomSource rng = RandomSource.create(deterministicSeed);
            for (NodeMatch m : matches) {
                TraceStructurePlacer.place(level, m.pos, m.block, m.id, rng);
            }
        }));
    }

    private static List<NodeMatch> scanChunkForNodes(ChunkAccess chunk, ChunkPos cp) {
        List<NodeMatch> result = new ArrayList<>();
        LevelChunkSection[] sections = chunk.getSections();
        int minBuildY = chunk.getMinBuildHeight();

        for (int sectionIdx = 0; sectionIdx < sections.length; sectionIdx++) {
            LevelChunkSection section = sections[sectionIdx];
            if (section == null || section.hasOnlyAir()) continue;
            if (!section.maybeHas(s -> s.is(ORE_NODES))) continue;

            int yBase = minBuildY + sectionIdx * 16;
            for (int dy = 0; dy < 16; dy++) {
                for (int dx = 0; dx < 16; dx++) {
                    for (int dz = 0; dz < 16; dz++) {
                        BlockState state = section.getBlockState(dx, dy, dz);
                        if (!state.is(ORE_NODES)) continue;
                        Block block = state.getBlock();
                        ResourceLocation nodeId = BuiltInRegistries.BLOCK.getKey(block);
                        BlockPos pos = new BlockPos(cp.getMinBlockX() + dx, yBase + dy, cp.getMinBlockZ() + dz);
                        result.add(new NodeMatch(pos, block, nodeId));
                    }
                }
            }
        }
        return result;
    }

    private record NodeMatch(BlockPos pos, Block block, ResourceLocation id) {}
}
