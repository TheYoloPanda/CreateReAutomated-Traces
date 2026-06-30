package com.typ.traces.worldgen;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.typ.traces.CreateReAutomatedTraces;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

public final class TraceTemplates {

    private static final String FOLDER_PREFIX = "structure/";
    private static final String SUFFIX = ".nbt";
    private static final ResourceLocation STEEP_TERRAIN_FALLBACK_ID =
            ResourceLocation.fromNamespaceAndPath(CreateReAutomatedTraces.MODID, "small_trace_1");

    private static volatile List<TraceTemplateGroup> groups = List.of();
    private static volatile Optional<TraceTemplateProfile> steepTerrainFallback = Optional.empty();

    private TraceTemplates() {}

    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener((ResourceManagerReloadListener) TraceTemplates::reload);
    }

    private static void reload(ResourceManager resourceManager) {
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                "structure",
                id -> id.getNamespace().equals(CreateReAutomatedTraces.MODID)
                        && id.getPath().startsWith(FOLDER_PREFIX)
                        && id.getPath().endsWith(SUFFIX));

        EnumMap<TraceSize, List<TraceTemplateProfile>> loaded = new EnumMap<>(TraceSize.class);
        for (TraceSize size : TraceSize.values()) {
            loaded.put(size, new ArrayList<>());
        }
        Optional<TraceTemplateProfile> fallback = Optional.empty();
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .toList()) {
            ResourceLocation resourceId = entry.getKey();
            ResourceLocation templateId = toTemplateId(resourceId);
            try (InputStream input = entry.getValue().open()) {
                CompoundTag root = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
                TraceTemplateProfile profile = TraceTemplateProfile.fromNbt(templateId, root);
                Optional<TraceSize> size = TraceSize.fromProfile(profile);
                if (size.isEmpty()) {
                    CreateReAutomatedTraces.LOGGER.warn(
                            "Skipping Trace template {} with unsupported horizontal size {}x{}",
                            templateId,
                            profile.size().getX(),
                            profile.size().getZ());
                    continue;
                }
                loaded.get(size.get()).add(profile);
                if (STEEP_TERRAIN_FALLBACK_ID.equals(templateId)) {
                    fallback = Optional.of(profile);
                }
            } catch (IOException | RuntimeException exception) {
                CreateReAutomatedTraces.LOGGER.error(
                        "Could not load Trace template profile {}",
                        resourceId,
                        exception);
            }
        }

        List<TraceTemplateGroup> nextGroups = new ArrayList<>();
        for (TraceSize size : TraceSize.largestFirst()) {
            List<TraceTemplateProfile> profiles = loaded.get(size);
            profiles.sort(Comparator.comparing(profile -> profile.id().toString()));
            if (!profiles.isEmpty()) {
                nextGroups.add(new TraceTemplateGroup(size, profiles));
            }
        }
        groups = List.copyOf(nextGroups);
        steepTerrainFallback = fallback;
        CreateReAutomatedTraces.LOGGER.info(
                "Loaded Trace template profiles by size: extra_large={}, large={}, medium={}, small={}",
                loaded.get(TraceSize.EXTRA_LARGE).size(),
                loaded.get(TraceSize.LARGE).size(),
                loaded.get(TraceSize.MEDIUM).size(),
                loaded.get(TraceSize.SMALL).size());
    }

    private static ResourceLocation toTemplateId(ResourceLocation resourceId) {
        String path = resourceId.getPath();
        return resourceId.withPath(path.substring(FOLDER_PREFIX.length(), path.length() - SUFFIX.length()));
    }

    static List<TraceTemplateGroup> groupsFor(long worldSeed, BlockPos nodePos) {
        return groups.stream()
                .map(group -> new TraceTemplateGroup(
                        group.size(),
                        deterministicOrder(group.profiles(), worldSeed, nodePos)))
                .toList();
    }

    static Optional<TraceTemplateProfile> steepTerrainFallback() {
        return steepTerrainFallback;
    }

    static int maximumHorizontalSize() {
        return groups.stream()
                .map(TraceTemplateGroup::size)
                .mapToInt(TraceSize::horizontalSize)
                .max()
                .orElse(0);
    }

    static int deterministicIndex(long worldSeed, BlockPos nodePos, int size) {
        if (size <= 0) throw new IllegalArgumentException("size must be positive");
        long mixed = mix64(worldSeed ^ nodePos.asLong());
        return Math.floorMod(mixed, size);
    }

    static List<TraceTemplateProfile> deterministicOrder(
            List<TraceTemplateProfile> profiles,
            long worldSeed,
            BlockPos nodePos) {
        if (profiles.size() <= 1) return List.copyOf(profiles);
        int start = deterministicIndex(worldSeed, nodePos, profiles.size());
        List<TraceTemplateProfile> ordered = new ArrayList<>(profiles.size());
        for (int i = 0; i < profiles.size(); i++) {
            ordered.add(profiles.get((start + i) % profiles.size()));
        }
        return List.copyOf(ordered);
    }

    static long placementSeed(long worldSeed, BlockPos nodePos, ResourceLocation templateId) {
        return mix64(worldSeed ^ nodePos.asLong() ^ templateId.hashCode());
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    record TraceTemplateGroup(TraceSize size, List<TraceTemplateProfile> profiles) {
        TraceTemplateGroup {
            profiles = List.copyOf(profiles);
        }
    }

    enum TraceSize {
        EXTRA_LARGE(5),
        LARGE(4),
        MEDIUM(3),
        SMALL(2);

        private final int horizontalSize;

        TraceSize(int horizontalSize) {
            this.horizontalSize = horizontalSize;
        }

        int horizontalSize() {
            return horizontalSize;
        }

        static List<TraceSize> largestFirst() {
            return List.of(EXTRA_LARGE, LARGE, MEDIUM, SMALL);
        }

        static Optional<TraceSize> fromProfile(TraceTemplateProfile profile) {
            if (profile.size().getX() != profile.size().getZ()) return Optional.empty();
            int horizontalSize = profile.size().getX();
            for (TraceSize size : values()) {
                if (size.horizontalSize == horizontalSize) return Optional.of(size);
            }
            return Optional.empty();
        }
    }
}
