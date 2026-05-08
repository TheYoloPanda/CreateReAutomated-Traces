package com.typ.traces.worldgen;

import java.util.List;
import java.util.Optional;

import com.typ.traces.CreateReAutomatedTraces;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

public final class TraceTemplates {

    private static final String FOLDER_PREFIX = "structure/";
    private static final String NAME_PREFIX = "trace_";
    private static final String SUFFIX = ".nbt";

    private static volatile List<ResourceLocation> templates = List.of();

    private TraceTemplates() {}

    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener((ResourceManagerReloadListener) TraceTemplates::reload);
    }

    private static void reload(ResourceManager rm) {
        templates = rm.listResources("structure", rl ->
                        rl.getNamespace().equals(CreateReAutomatedTraces.MODID)
                                && rl.getPath().startsWith(FOLDER_PREFIX + NAME_PREFIX)
                                && rl.getPath().endsWith(SUFFIX))
                .keySet().stream()
                .map(rl -> rl.withPath(rl.getPath().substring(
                        FOLDER_PREFIX.length(),
                        rl.getPath().length() - SUFFIX.length())))
                .toList();
        CreateReAutomatedTraces.LOGGER.info("Loaded {} trace templates: {}", templates.size(), templates);
    }

    public static Optional<ResourceLocation> pick(RandomSource rng) {
        List<ResourceLocation> snap = templates;
        if (snap.isEmpty()) return Optional.empty();
        return Optional.of(snap.get(rng.nextInt(snap.size())));
    }
}
