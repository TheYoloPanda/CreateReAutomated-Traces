package com.typ.traces.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public final class StructureGuard {

    private StructureGuard() {}

    public static List<BoundingBox> collectStructureBoxes(WorldGenLevel level, int minCX, int minCZ, int maxCX, int maxCZ) {
        Set<StructureStart> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                ChunkAccess chunk = level.getChunk(cx, cz);

                for (StructureStart start : chunk.getAllStarts().values()) {
                    if (start.isValid()) seen.add(start);
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
                        if (start != null && start.isValid()) seen.add(start);
                    }
                }
            }
        }

        List<BoundingBox> result = new ArrayList<>(seen.size());
        for (StructureStart start : seen) {
            result.add(start.getBoundingBox());
        }
        return result;
    }

    public static boolean intersectsAny(List<BoundingBox> boxes, BoundingBox query) {
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i).intersects(query)) return true;
        }
        return false;
    }

    public static boolean isInsideAny(List<BoundingBox> boxes, BlockPos pos) {
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i).isInside(pos)) return true;
        }
        return false;
    }
}
