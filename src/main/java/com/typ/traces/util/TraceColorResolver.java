package com.typ.traces.util;

import java.util.HashMap;
import java.util.Map;

import com.typ.traces.worldgen.TraceBlockDataMap;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

public final class TraceColorResolver {

    public static final int FALLBACK_WHITE = 0xFFFFFFFF;

    private static final Map<Block, Integer> VANILLA_TRACE_COLORS = buildVanillaMap();

    private TraceColorResolver() {}

    private static Map<Block, Integer> buildVanillaMap() {
        Map<Block, Integer> m = new HashMap<>();
        put(m, 0xFFE52520, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE);
        put(m, 0xFF505050, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);
        put(m, 0xFF2050E5, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE);
        put(m, 0xFFFF8C00, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE);
        put(m, 0xFFFFB0B0, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE);
        put(m, 0xFF80E5FF, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);
        put(m, 0xFFFFEB3B, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE);
        put(m, 0xFF50E520, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE);
        put(m, 0xFFFFFFFF, Blocks.NETHER_QUARTZ_ORE);
        put(m, 0xFF6B4226, Blocks.ANCIENT_DEBRIS);
        return Map.copyOf(m);
    }

    private static void put(Map<Block, Integer> m, int color, Block... blocks) {
        for (Block b : blocks) m.put(b, color);
    }

    /**
     * Resolution order:
     * 1) hardcoded mapping by trace_block (vanilla ores)
     * 2) explicit beam_color in the data map entry for the node block
     * 3) white fallback
     */
    public static int resolve(@Nullable Block traceBlock, @Nullable Block nodeBlock) {
        if (traceBlock != null) {
            Integer v = VANILLA_TRACE_COLORS.get(traceBlock);
            if (v != null) return v;
        }
        if (nodeBlock != null) {
            return TraceBlockDataMap.beamColorFor(nodeBlock).orElse(FALLBACK_WHITE);
        }
        return FALLBACK_WHITE;
    }
}
