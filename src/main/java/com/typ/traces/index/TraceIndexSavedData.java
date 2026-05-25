package com.typ.traces.index;

import java.util.function.Consumer;
import java.util.function.Predicate;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

public class TraceIndexSavedData extends SavedData {

    public static final String DATA_NAME = "createreautomatedtraces_trace_index";
    private static final int SCAN_VERSION = 2;

    public record TraceRecord(ResourceLocation nodeId, long posLong) {
        public BlockPos pos() { return BlockPos.of(posLong); }
    }

    private final Long2ObjectOpenHashMap<TraceRecord> byPos = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<LongOpenHashSet> chunkIndex = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet scannedChunks = new LongOpenHashSet();

    public TraceIndexSavedData() {}

    public static SavedData.Factory<TraceIndexSavedData> factory() {
        return new SavedData.Factory<>(TraceIndexSavedData::new, TraceIndexSavedData::load, null);
    }

    public static TraceIndexSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public boolean record(ResourceLocation nodeId, BlockPos pos) {
        long pk = pos.asLong();
        if (byPos.containsKey(pk)) return false;
        byPos.put(pk, new TraceRecord(nodeId, pk));
        addToChunkIndex(pos.getX() >> 4, pos.getZ() >> 4, pk);
        setDirty();
        return true;
    }

    public boolean remove(BlockPos pos) {
        long pk = pos.asLong();
        TraceRecord prev = byPos.remove(pk);
        if (prev == null) return false;
        removeFromChunkIndex(pos.getX() >> 4, pos.getZ() >> 4, pk);
        setDirty();
        return true;
    }

    public boolean markScanned(long chunkLong) {
        boolean added = scannedChunks.add(chunkLong);
        if (added) setDirty();
        return added;
    }

    public boolean isScanned(long chunkLong) {
        return scannedChunks.contains(chunkLong);
    }

    public boolean isRecorded(BlockPos pos) {
        return byPos.containsKey(pos.asLong());
    }

    public int size() {
        return byPos.size();
    }

    /**
     * Iterate trace records whose position falls inside the square of chunks
     * centered on (centerChunkX, centerChunkZ) with the given radius (inclusive).
     */
    public void forEachInRange(int centerChunkX, int centerChunkZ, int radiusChunks,
                                Predicate<ResourceLocation> nodeFilter,
                                Consumer<TraceRecord> consumer) {
        int minCX = centerChunkX - radiusChunks;
        int maxCX = centerChunkX + radiusChunks;
        int minCZ = centerChunkZ - radiusChunks;
        int maxCZ = centerChunkZ + radiusChunks;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                LongOpenHashSet bucket = chunkIndex.get(ChunkPos.asLong(cx, cz));
                if (bucket == null || bucket.isEmpty()) continue;
                LongIterator it = bucket.iterator();
                while (it.hasNext()) {
                    long posLong = it.nextLong();
                    TraceRecord rec = byPos.get(posLong);
                    if (rec == null) continue;
                    if (!nodeFilter.test(rec.nodeId())) continue;
                    consumer.accept(rec);
                }
            }
        }
    }

    private void addToChunkIndex(int chunkX, int chunkZ, long posLong) {
        long ckey = ChunkPos.asLong(chunkX, chunkZ);
        chunkIndex.computeIfAbsent(ckey, k -> new LongOpenHashSet()).add(posLong);
    }

    private void removeFromChunkIndex(int chunkX, int chunkZ, long posLong) {
        long ckey = ChunkPos.asLong(chunkX, chunkZ);
        LongOpenHashSet bucket = chunkIndex.get(ckey);
        if (bucket != null) {
            bucket.remove(posLong);
            if (bucket.isEmpty()) chunkIndex.remove(ckey);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        ListTag entries = new ListTag();
        for (Long2ObjectMap.Entry<TraceRecord> e : byPos.long2ObjectEntrySet()) {
            CompoundTag t = new CompoundTag();
            TraceRecord r = e.getValue();
            t.putString("node", r.nodeId().toString());
            t.putLong("pos", r.posLong());
            entries.add(t);
        }
        tag.put("entries", entries);
        tag.putInt("scan_version", SCAN_VERSION);
        tag.putLongArray("scanned", scannedChunks.toLongArray());
        return tag;
    }

    public static TraceIndexSavedData load(CompoundTag tag, HolderLookup.Provider lookup) {
        TraceIndexSavedData data = new TraceIndexSavedData();
        ListTag entries = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag t = entries.getCompound(i);
            ResourceLocation nodeId = ResourceLocation.tryParse(t.getString("node"));
            if (nodeId == null) continue;
            long posLong = t.getLong("pos");
            BlockPos p = BlockPos.of(posLong);
            data.byPos.put(posLong, new TraceRecord(nodeId, posLong));
            data.addToChunkIndex(p.getX() >> 4, p.getZ() >> 4, posLong);
        }
        if (tag.getInt("scan_version") >= SCAN_VERSION) {
            long[] scanned = tag.getLongArray("scanned");
            for (long ck : scanned) data.scannedChunks.add(ck);
        }
        return data;
    }
}
