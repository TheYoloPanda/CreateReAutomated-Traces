package com.typ.traces.worldgen;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public final class StructureGuard {

    static final String UNKNOWN_STRUCTURE_ID = "unknown";
    private static final Set<String> TRACE_IGNORED_STRUCTURE_IDS = Set.of(
            "minecraft:mineshaft",
            "minecraft:mineshaft_mesa",
            "minecraft:buried_treasure",
            "minecraft:ancient_city",
            "minecraft:stronghold",
            "minecraft:trial_chambers",
            "minecraft:trail_ruins");

    private StructureGuard() {}

    record StructureArea(String structureId, BoundingBox box) {}

    public static List<BoundingBox> collectStructureBoxes(WorldGenLevel level, int minCX, int minCZ, int maxCX, int maxCZ) {
        return collectStructureAreas(level, minCX, minCZ, maxCX, maxCZ).stream()
                .map(StructureArea::box)
                .toList();
    }

    static List<StructureArea> collectStructureAreas(
            WorldGenLevel level,
            int minCX,
            int minCZ,
            int maxCX,
            int maxCZ) {
        Map<StructureStart, StructureArea> seen = new IdentityHashMap<>();

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                ChunkAccess chunk = level.getChunk(cx, cz);

                for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
                    addStart(seen, level, entry.getKey(), entry.getValue());
                }

                for (Map.Entry<Structure, LongSet> entry : chunk.getAllReferences().entrySet()) {
                    Structure structure = entry.getKey();
                    LongIterator it = entry.getValue().iterator();
                    while (it.hasNext()) {
                        long anchorChunkLong = it.nextLong();
                        ChunkPos anchorPos = new ChunkPos(anchorChunkLong);
                        if (!level.hasChunk(anchorPos.x, anchorPos.z)) continue;
                        ChunkAccess anchorChunk = level.getChunk(anchorPos.x, anchorPos.z);
                        StructureStart start = anchorChunk.getStartForStructure(structure);
                        addStart(seen, level, structure, start);
                    }
                }
            }
        }

        return List.copyOf(seen.values());
    }

    static List<StructureArea> unknownAreas(List<BoundingBox> boxes) {
        return boxes.stream()
                .map(box -> new StructureArea(UNKNOWN_STRUCTURE_ID, box))
                .toList();
    }

    public static boolean intersectsAny(List<BoundingBox> boxes, BoundingBox query) {
        return firstIntersecting(unknownAreas(boxes), query).isPresent();
    }

    public static boolean isInsideAny(List<BoundingBox> boxes, BlockPos pos) {
        return firstContaining(unknownAreas(boxes), pos).isPresent();
    }

    static Optional<StructureArea> firstIntersecting(Collection<StructureArea> areas, BoundingBox query) {
        for (StructureArea area : areas) {
            if (area.box().intersects(query)) return Optional.of(area);
        }
        return Optional.empty();
    }

    static Optional<StructureArea> firstContaining(Collection<StructureArea> areas, BlockPos pos) {
        for (StructureArea area : areas) {
            if (area.box().isInside(pos)) return Optional.of(area);
        }
        return Optional.empty();
    }

    private static void addStart(
            Map<StructureStart, StructureArea> seen,
            WorldGenLevel level,
            Structure structure,
            StructureStart start) {
        if (start == null || !start.isValid()) return;
        String structureId = structureId(level, structure);
        if (isTraceIgnoredStructureId(structureId)) return;
        seen.putIfAbsent(start, new StructureArea(structureId, start.getBoundingBox()));
    }

    private static String structureId(WorldGenLevel level, Structure structure) {
        ResourceLocation id = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(structure);
        return id == null ? UNKNOWN_STRUCTURE_ID : id.toString();
    }

    static boolean isTraceIgnoredStructureId(String structureId) {
        return TRACE_IGNORED_STRUCTURE_IDS.contains(structureId);
    }
}
