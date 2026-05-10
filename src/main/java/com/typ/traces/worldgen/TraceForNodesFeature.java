package com.typ.traces.worldgen;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.typ.traces.CreateReAutomatedTraces;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.RegisterEvent;

public class TraceForNodesFeature extends Feature<NoneFeatureConfiguration> {

    private static final TagKey<Block> ORE_NODES = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("createreautomated", "ore_nodes"));

    public TraceForNodesFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        ChunkAccess chunk = level.getChunk(origin);
        ChunkPos cp = chunk.getPos();

        List<NodeMatch> matches = scanChunkForNodes(chunk, cp);
        if (matches.isEmpty()) return false;

        boolean placedAny = false;
        for (NodeMatch m : matches) {
            TraceStructurePlacer.place(level, m.pos, m.block, m.id, ctx.random());
            placedAny = true;
        }
        return placedAny;
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

    public static void onRegister(RegisterEvent event) {
        event.register(Registries.FEATURE, helper ->
                helper.register(
                        ResourceLocation.fromNamespaceAndPath(CreateReAutomatedTraces.MODID, "trace_for_nodes"),
                        new TraceForNodesFeature(NoneFeatureConfiguration.CODEC)));
    }
}
