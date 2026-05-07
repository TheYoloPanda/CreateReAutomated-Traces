package com.typ.traces.worldgen;

import com.typ.traces.CreateReAutomatedTraces;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

public final class TraceTemplates {

    public static final ResourceLocation TRACE_1 =
            ResourceLocation.fromNamespaceAndPath(CreateReAutomatedTraces.MODID, "trace_1");
    public static final ResourceLocation TRACE_2 =
            ResourceLocation.fromNamespaceAndPath(CreateReAutomatedTraces.MODID, "trace_2");
    public static final ResourceLocation TRACE_3 =
            ResourceLocation.fromNamespaceAndPath(CreateReAutomatedTraces.MODID, "trace_3");

    private static final ResourceLocation[] ALL = { TRACE_1, TRACE_2, TRACE_3 };

    private TraceTemplates() {}

    public static ResourceLocation pick(RandomSource rng) {
        return ALL[rng.nextInt(ALL.length)];
    }
}
