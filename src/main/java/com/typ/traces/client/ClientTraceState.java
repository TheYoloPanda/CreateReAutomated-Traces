package com.typ.traces.client;

import java.util.Optional;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public final class ClientTraceState {

    public static final ClientTraceState INSTANCE = new ClientTraceState();

    public static final class VisibleEntry {
        public final BlockPos pos;
        public final ResourceLocation nodeId;
        public final int colorArgb;
        public int fadeTicksRemaining;

        public VisibleEntry(BlockPos pos, ResourceLocation nodeId, int colorArgb) {
            this.pos = pos;
            this.nodeId = nodeId;
            this.colorArgb = colorArgb;
            this.fadeTicksRemaining = -1;
        }

        public boolean isFading() {
            return fadeTicksRemaining >= 0;
        }
    }

    private final Long2ObjectMap<VisibleEntry> visible = new Long2ObjectOpenHashMap<>();
    private Optional<BlockPos> target = Optional.empty();

    private ClientTraceState() {}

    public void addOrUpdate(long posLong, ResourceLocation nodeId, int colorArgb) {
        visible.put(posLong, new VisibleEntry(BlockPos.of(posLong), nodeId, colorArgb));
    }

    /** Mark a trace as fading out over the given tick count. Returns true if the entry existed. */
    public boolean markFade(long posLong, int fadeTicks) {
        VisibleEntry e = visible.get(posLong);
        if (e == null) return false;
        e.fadeTicksRemaining = fadeTicks;
        return true;
    }

    public void removeImmediately(long posLong) {
        visible.remove(posLong);
    }

    public Optional<BlockPos> target() {
        return target;
    }

    public void setTarget(Optional<BlockPos> t) {
        target = t;
    }

    public void clear() {
        visible.clear();
        target = Optional.empty();
    }

    public Iterable<VisibleEntry> entries() {
        return visible.values();
    }

    public int size() {
        return visible.size();
    }

    /** Advance fade timers; remove fully-faded entries. */
    public void tickFade() {
        if (visible.isEmpty()) return;
        var it = visible.values().iterator();
        while (it.hasNext()) {
            VisibleEntry e = it.next();
            if (e.fadeTicksRemaining < 0) continue;
            if (e.fadeTicksRemaining == 0) {
                it.remove();
                continue;
            }
            e.fadeTicksRemaining--;
        }
    }
}
