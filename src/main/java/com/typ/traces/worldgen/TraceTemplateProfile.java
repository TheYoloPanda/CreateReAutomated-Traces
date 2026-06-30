package com.typ.traces.worldgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class TraceTemplateProfile {

    private static final String AIR = "minecraft:air";
    private static final String CAVE_AIR = "minecraft:cave_air";
    private static final String VOID_AIR = "minecraft:void_air";
    private static final String TRACE_PLACEHOLDER = "minecraft:black_concrete";
    private static final Direction[] DIRECTIONS = Direction.values();

    private final ResourceLocation id;
    private final Vec3i size;
    private final List<BlockPos> occupiedBlocks;
    private final long[] traceContactKeys;
    private final List<BlockPos> supportColumns;
    private final Map<Long, Integer> supportBottomByColumn;
    private final BlockPos traceOffset;
    private final int minOccupiedX;
    private final int maxOccupiedX;
    private final int minOccupiedY;
    private final int maxOccupiedY;
    private final int minOccupiedZ;
    private final int maxOccupiedZ;
    private final int minSupportY;

    private TraceTemplateProfile(
            ResourceLocation id,
            Vec3i size,
            List<BlockPos> occupiedBlocks,
            List<BlockPos> supportColumns,
            Map<Long, Integer> supportBottomByColumn,
            BlockPos traceOffset,
            int minOccupiedX,
            int maxOccupiedX,
            int minOccupiedY,
            int maxOccupiedY,
            int minOccupiedZ,
            int maxOccupiedZ,
            int minSupportY) {
        this.id = id;
        this.size = size;
        this.occupiedBlocks = List.copyOf(occupiedBlocks);
        this.traceContactKeys = traceContactKeys(this.occupiedBlocks);
        this.supportColumns = List.copyOf(supportColumns);
        this.supportBottomByColumn = Map.copyOf(supportBottomByColumn);
        this.traceOffset = traceOffset;
        this.minOccupiedX = minOccupiedX;
        this.maxOccupiedX = maxOccupiedX;
        this.minOccupiedY = minOccupiedY;
        this.maxOccupiedY = maxOccupiedY;
        this.minOccupiedZ = minOccupiedZ;
        this.maxOccupiedZ = maxOccupiedZ;
        this.minSupportY = minSupportY;
    }

    static TraceTemplateProfile fromNbt(ResourceLocation id, CompoundTag root) {
        ListTag sizeTag = root.getList("size", Tag.TAG_INT);
        if (sizeTag.size() != 3) {
            throw new IllegalArgumentException("template has no valid size");
        }
        Vec3i size = new Vec3i(sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2));

        ListTag palette = firstPalette(root);
        if (palette.isEmpty()) {
            throw new IllegalArgumentException("template has no palette");
        }
        List<String> stateNames = new ArrayList<>(palette.size());
        for (int i = 0; i < palette.size(); i++) {
            stateNames.add(palette.getCompound(i).getString("Name"));
        }

        List<BlockPos> occupied = new ArrayList<>();
        List<BlockPos> tracePlaceholders = new ArrayList<>();
        Map<Long, Integer> supportBottoms = new HashMap<>();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag blockTag = blocks.getCompound(i);
            int stateIndex = blockTag.getInt("state");
            if (stateIndex < 0 || stateIndex >= stateNames.size()) {
                throw new IllegalArgumentException("template block references invalid palette state " + stateIndex);
            }

            ListTag posTag = blockTag.getList("pos", Tag.TAG_INT);
            if (posTag.size() != 3) {
                throw new IllegalArgumentException("template block has no valid position");
            }
            BlockPos pos = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
            String stateName = stateNames.get(stateIndex);
            if (isAir(stateName)) continue;

            occupied.add(pos);
            supportBottoms.merge(columnKey(pos.getX(), pos.getZ()), pos.getY(), Math::min);
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
            if (TRACE_PLACEHOLDER.equals(stateName)) {
                tracePlaceholders.add(pos);
            }
        }

        if (occupied.isEmpty()) {
            throw new IllegalArgumentException("template has no occupied blocks");
        }
        if (tracePlaceholders.isEmpty()) {
            throw new IllegalArgumentException("template has no black concrete trace placeholder");
        }

        List<BlockPos> supportColumns = supportBottoms.entrySet().stream()
                .map(entry -> new BlockPos(
                        columnX(entry.getKey()),
                        entry.getValue(),
                        columnZ(entry.getKey())))
                .sorted((a, b) -> {
                    int x = Integer.compare(a.getX(), b.getX());
                    return x != 0 ? x : Integer.compare(a.getZ(), b.getZ());
                })
                .toList();

        int minSupportY = supportColumns.stream().mapToInt(BlockPos::getY).min().orElseThrow();
        BlockPos traceOffset = selectTraceOffset(tracePlaceholders, size);
        return new TraceTemplateProfile(
                id,
                size,
                occupied,
                supportColumns,
                supportBottoms,
                traceOffset,
                minX,
                maxX,
                minY,
                maxY,
                minZ,
                maxZ,
                minSupportY);
    }

    private static ListTag firstPalette(CompoundTag root) {
        if (root.contains("palette", Tag.TAG_LIST)) {
            return root.getList("palette", Tag.TAG_COMPOUND);
        }
        ListTag palettes = root.getList("palettes", Tag.TAG_LIST);
        if (palettes.isEmpty() || !(palettes.get(0) instanceof ListTag first)) {
            return new ListTag();
        }
        return first;
    }

    private static boolean isAir(String stateName) {
        return AIR.equals(stateName) || CAVE_AIR.equals(stateName) || VOID_AIR.equals(stateName);
    }

    private static BlockPos selectTraceOffset(List<BlockPos> placeholders, Vec3i size) {
        BlockPos best = null;
        int bestY = Integer.MIN_VALUE;
        double bestDistance = Double.MAX_VALUE;
        double centerX = (size.getX() - 1) * 0.5D;
        double centerZ = (size.getZ() - 1) * 0.5D;
        for (BlockPos pos : placeholders) {
            double dx = pos.getX() - centerX;
            double dz = pos.getZ() - centerZ;
            double distance = dx * dx + dz * dz;
            if (pos.getY() > bestY || (pos.getY() == bestY && distance < bestDistance)) {
                best = pos;
                bestY = pos.getY();
                bestDistance = distance;
            }
        }
        return best;
    }

    private static long[] traceContactKeys(List<BlockPos> occupiedBlocks) {
        LongOpenHashSet keys = new LongOpenHashSet(occupiedBlocks.size() * (DIRECTIONS.length + 1));
        for (BlockPos occupied : occupiedBlocks) {
            addTraceContactKey(keys, occupied.getX(), occupied.getY(), occupied.getZ());
            for (Direction direction : DIRECTIONS) {
                addTraceContactKey(
                        keys,
                        occupied.getX() + direction.getStepX(),
                        occupied.getY() + direction.getStepY(),
                        occupied.getZ() + direction.getStepZ());
            }
        }
        long[] result = keys.toLongArray();
        Arrays.sort(result);
        return result;
    }

    private static void addTraceContactKey(LongOpenHashSet keys, int x, int y, int z) {
        keys.add(BlockPos.asLong(x, y, z));
    }

    static long columnKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    static int columnX(long key) {
        return (int) (key >> 32);
    }

    static int columnZ(long key) {
        return (int) key;
    }

    TraceTemplateProfile oriented(Rotation rotation) {
        if (rotation == Rotation.NONE) return this;

        Vec3i orientedSize = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90
                ? new Vec3i(size.getZ(), size.getY(), size.getX())
                : size;
        List<BlockPos> orientedOccupied = new ArrayList<>(occupiedBlocks.size());
        Map<Long, Integer> orientedSupportBottoms = new HashMap<>();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (BlockPos occupied : occupiedBlocks) {
            BlockPos transformed = StructureTemplate.transform(occupied, Mirror.NONE, rotation, BlockPos.ZERO);
            orientedOccupied.add(transformed);
            orientedSupportBottoms.merge(columnKey(transformed.getX(), transformed.getZ()), transformed.getY(), Math::min);
            minX = Math.min(minX, transformed.getX());
            maxX = Math.max(maxX, transformed.getX());
            minY = Math.min(minY, transformed.getY());
            maxY = Math.max(maxY, transformed.getY());
            minZ = Math.min(minZ, transformed.getZ());
            maxZ = Math.max(maxZ, transformed.getZ());
        }

        List<BlockPos> orientedSupportColumns = orientedSupportBottoms.entrySet().stream()
                .map(entry -> new BlockPos(
                        columnX(entry.getKey()),
                        entry.getValue(),
                        columnZ(entry.getKey())))
                .sorted((a, b) -> {
                    int x = Integer.compare(a.getX(), b.getX());
                    return x != 0 ? x : Integer.compare(a.getZ(), b.getZ());
                })
                .toList();
        int orientedMinSupportY = orientedSupportColumns.stream().mapToInt(BlockPos::getY).min().orElseThrow();
        BlockPos orientedTraceOffset =
                StructureTemplate.transform(traceOffset, Mirror.NONE, rotation, BlockPos.ZERO);

        return new TraceTemplateProfile(
                id,
                orientedSize,
                orientedOccupied,
                orientedSupportColumns,
                orientedSupportBottoms,
                orientedTraceOffset,
                minX,
                maxX,
                minY,
                maxY,
                minZ,
                maxZ,
                orientedMinSupportY);
    }

    public ResourceLocation id() {
        return id;
    }

    public Vec3i size() {
        return size;
    }

    public List<BlockPos> occupiedBlocks() {
        return occupiedBlocks;
    }

    boolean containsTraceContact(int localX, int localY, int localZ) {
        return Arrays.binarySearch(traceContactKeys, BlockPos.asLong(localX, localY, localZ)) >= 0;
    }

    /**
     * Local X/Z are stored in X/Z and the lowest occupied local Y is stored in Y.
     */
    public List<BlockPos> supportColumns() {
        return supportColumns;
    }

    public OptionalInt supportBottomY(int localX, int localZ) {
        Integer value = supportBottomByColumn.get(columnKey(localX, localZ));
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }

    public BlockPos traceOffset() {
        return traceOffset;
    }

    public int minOccupiedX() {
        return minOccupiedX;
    }

    public int maxOccupiedX() {
        return maxOccupiedX;
    }

    public int minOccupiedY() {
        return minOccupiedY;
    }

    public int maxOccupiedY() {
        return maxOccupiedY;
    }

    public int minOccupiedZ() {
        return minOccupiedZ;
    }

    public int maxOccupiedZ() {
        return maxOccupiedZ;
    }

    public int minSupportY() {
        return minSupportY;
    }

}
