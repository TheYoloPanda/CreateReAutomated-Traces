package com.typ.traces.worldgen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Supplier;

import com.typ.traces.CreateReAutomatedTraces;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

final class TracePlacementDiagnostics {

    enum SkipReason {
        SUPPRESSED,
        MISSING_TRACE_BLOCK_MAPPING,
        NO_TEMPLATE_PROFILES,
        TEMPLATE_PLACE_FAILED,
        REQUIRED_CHUNKS_UNAVAILABLE,
        NO_SURFACE,
        OUT_OF_BUILD_HEIGHT,
        STRUCTURE_INTERSECTION,
        OCCUPIED_STRUCTURE,
        BLOCK_ENTITY_COLLISION,
        ORE_OR_PROTECTED_BLOCK,
        NON_TERRAIN_BLOCK,
        TERRAIN_CUT_NOT_ALLOWED,
        CUT_DEPTH_LIMIT,
        FOUNDATION_STRUCTURE,
        FOUNDATION_BLOCKER,
        FOUNDATION_GAP_TOO_DEEP,
        TERRAIN_EDIT_LIMIT,
        CLEANUP_STRUCTURE,
        COLUMN_PLANT_IN_STRUCTURE,
        TRACE_BURIED,
        TRACE_EMBEDDED
    }

    private static final int TOP_REASON_LIMIT = 3;

    private final boolean enabled;
    private final ResourceLocation nodeId;
    private final ResourceLocation dimensionId;
    private final BlockPos nodePos;
    private final EnumMap<TraceTemplates.TraceSize, Integer> attempts =
            new EnumMap<>(TraceTemplates.TraceSize.class);
    private final EnumMap<TraceTemplates.TraceSize, EnumMap<SkipReason, Integer>> reasonsBySize =
            new EnumMap<>(TraceTemplates.TraceSize.class);
    private final EnumMap<SkipReason, Integer> globalReasons = new EnumMap<>(SkipReason.class);
    private final EnumMap<SkipReason, DetailSample> detailSamples = new EnumMap<>(SkipReason.class);

    private boolean logged;
    private boolean hasSteepTerrainFallbackReason;

    record DetailSample(TraceTemplates.TraceSize size, String detail) {
        static DetailSample of(TraceTemplates.TraceSize size, String detail) {
            return new DetailSample(size, detail);
        }

        String summary() {
            return sizeLabel(size) + " " + detail;
        }
    }

    private TracePlacementDiagnostics(
            boolean enabled,
            ResourceLocation nodeId,
            ResourceLocation dimensionId,
            BlockPos nodePos) {
        this.enabled = enabled;
        this.nodeId = nodeId;
        this.dimensionId = dimensionId;
        this.nodePos = nodePos.immutable();
    }

    static TracePlacementDiagnostics disabled() {
        return new TracePlacementDiagnostics(false, null, null, BlockPos.ZERO);
    }

    static TracePlacementDiagnostics enabled(
            ResourceLocation nodeId,
            ResourceLocation dimensionId,
            BlockPos nodePos) {
        return new TracePlacementDiagnostics(true, nodeId, dimensionId, nodePos);
    }

    boolean isEnabled() {
        return enabled;
    }

    void recordAttempt(TraceTemplates.TraceSize size) {
        if (!enabled) return;
        attempts.merge(size, 1, Integer::sum);
    }

    void reject(TraceTemplates.TraceSize size, SkipReason reason) {
        reject(size, reason, null);
    }

    void reject(TraceTemplates.TraceSize size, SkipReason reason, DetailSample sample) {
        if (isSteepTerrainFallbackReason(reason)) {
            hasSteepTerrainFallbackReason = true;
        }
        if (!enabled) return;
        reasonsBySize
                .computeIfAbsent(size, ignored -> new EnumMap<>(SkipReason.class))
                .merge(reason, 1, Integer::sum);
        if (sample != null) {
            detailSamples.putIfAbsent(reason, sample);
        }
    }

    DetailSample sample(TraceTemplates.TraceSize size, Supplier<String> detail) {
        return enabled ? DetailSample.of(size, detail.get()) : null;
    }

    void rejectGlobal(SkipReason reason) {
        if (!enabled) return;
        globalReasons.merge(reason, 1, Integer::sum);
    }

    boolean hasSteepTerrainFallbackReason() {
        return hasSteepTerrainFallbackReason;
    }

    void reportSkipped(ServerLevel level) {
        if (!enabled || logged) return;
        logged = true;
        CreateReAutomatedTraces.LOGGER.info(summary());
        String details = detailsSummary();
        if (!details.isEmpty()) {
            CreateReAutomatedTraces.LOGGER.info(
                    "Trace placement skipped details for {} at {} x={} z={}: {}",
                    nodeId,
                    dimensionId,
                    nodePos.getX(),
                    nodePos.getZ(),
                    details);
        }
        level.getServer().execute(() -> sendChatMessageToOperators(level));
    }

    int totalRejected() {
        if (!enabled) return 0;
        int total = globalReasons.values().stream().mapToInt(Integer::intValue).sum();
        for (Map<SkipReason, Integer> sizeReasons : reasonsBySize.values()) {
            total += sizeReasons.values().stream().mapToInt(Integer::intValue).sum();
        }
        return total;
    }

    int count(TraceTemplates.TraceSize size, SkipReason reason) {
        if (!enabled) return 0;
        Map<SkipReason, Integer> sizeReasons = reasonsBySize.get(size);
        return sizeReasons == null ? 0 : sizeReasons.getOrDefault(reason, 0);
    }

    String summary() {
        if (!enabled) return "";
        return "Trace placement skipped for "
                + nodeId
                + " at "
                + dimensionId
                + " x="
                + nodePos.getX()
                + " z="
                + nodePos.getZ()
                + ": no valid plan after "
                + totalRejected()
                + " candidates; top reasons: "
                + topReasonsSummary()
                + "; sizes: "
                + sizesSummary();
    }

    String detailsSummary() {
        if (!enabled || detailSamples.isEmpty()) return "";
        EnumMap<SkipReason, Integer> totals = combinedReasons();
        if (totals.isEmpty()) return "";
        List<Map.Entry<SkipReason, Integer>> entries = new ArrayList<>(totals.entrySet());
        entries.sort(reasonComparator());

        StringJoiner joiner = new StringJoiner("; ");
        int inspected = 0;
        for (Map.Entry<SkipReason, Integer> entry : entries) {
            if (inspected++ >= TOP_REASON_LIMIT) break;
            DetailSample sample = detailSamples.get(entry.getKey());
            if (sample == null) continue;
            joiner.add(entry.getKey().name()
                    + "="
                    + entry.getValue()
                    + " ["
                    + sample.summary()
                    + "]");
        }
        return joiner.toString();
    }

    String teleportCommand() {
        if (!enabled) return "";
        return "/tp @s " + nodePos.getX() + " ~ " + nodePos.getZ();
    }

    Component chatMessage() {
        if (!enabled) return Component.empty();
        String command = teleportCommand();
        return Component.translatable(
                        "createreautomatedtraces.diagnostics.trace_skipped",
                        nodeId.toString(),
                        dimensionId.toString(),
                        nodePos.getX(),
                        nodePos.getZ(),
                        topReasonsSummary())
                .withStyle(style -> style
                        .withColor(ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.translatable(
                                        "createreautomatedtraces.diagnostics.trace_skipped.hover",
                                        command))));
    }

    private void sendChatMessageToOperators(ServerLevel level) {
        Component message = chatMessage();
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (!level.getServer().getPlayerList().isOp(player.getGameProfile())) continue;
            player.sendSystemMessage(message);
        }
    }

    private String topReasonsSummary() {
        EnumMap<SkipReason, Integer> totals = combinedReasons();
        if (totals.isEmpty()) return "none";
        List<Map.Entry<SkipReason, Integer>> entries = new ArrayList<>(totals.entrySet());
        entries.sort(reasonComparator());

        StringJoiner joiner = new StringJoiner(", ");
        int limit = Math.min(TOP_REASON_LIMIT, entries.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<SkipReason, Integer> entry = entries.get(i);
            joiner.add(entry.getKey().name() + "=" + entry.getValue());
        }
        return joiner.toString();
    }

    private String sizesSummary() {
        StringJoiner joiner = new StringJoiner(", ");
        for (TraceTemplates.TraceSize size : TraceTemplates.TraceSize.largestFirst()) {
            int attemptCount = attempts.getOrDefault(size, 0);
            Map<SkipReason, Integer> sizeReasons = reasonsBySize.get(size);
            int rejected = sizeReasons == null
                    ? 0
                    : sizeReasons.values().stream().mapToInt(Integer::intValue).sum();
            if (attemptCount == 0 && rejected == 0) {
                joiner.add(sizeLabel(size) + "[not_attempted]");
                continue;
            }

            StringJoiner sizeJoiner = new StringJoiner(",");
            sizeJoiner.add("attempts=" + attemptCount);
            sizeJoiner.add("rejected=" + rejected);
            if (sizeReasons != null && !sizeReasons.isEmpty()) {
                List<Map.Entry<SkipReason, Integer>> entries = new ArrayList<>(sizeReasons.entrySet());
                entries.sort(reasonComparator());
                for (Map.Entry<SkipReason, Integer> entry : entries) {
                    sizeJoiner.add(entry.getKey().name() + "=" + entry.getValue());
                }
            }
            joiner.add(sizeLabel(size) + "[" + sizeJoiner + "]");
        }
        return joiner.toString();
    }

    private EnumMap<SkipReason, Integer> combinedReasons() {
        EnumMap<SkipReason, Integer> totals = new EnumMap<>(SkipReason.class);
        for (Map.Entry<SkipReason, Integer> entry : globalReasons.entrySet()) {
            totals.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        for (Map<SkipReason, Integer> sizeReasons : reasonsBySize.values()) {
            for (Map.Entry<SkipReason, Integer> entry : sizeReasons.entrySet()) {
                totals.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        return totals;
    }

    private static Comparator<Map.Entry<SkipReason, Integer>> reasonComparator() {
        return Comparator
                .<Map.Entry<SkipReason, Integer>>comparingInt(Map.Entry::getValue)
                .reversed()
                .thenComparing(entry -> entry.getKey().name());
    }

    private static boolean isSteepTerrainFallbackReason(SkipReason reason) {
        return reason == SkipReason.TRACE_EMBEDDED
                || reason == SkipReason.TRACE_BURIED
                || reason == SkipReason.FOUNDATION_GAP_TOO_DEEP
                || reason == SkipReason.TERRAIN_EDIT_LIMIT
                || reason == SkipReason.CUT_DEPTH_LIMIT;
    }

    private static String sizeLabel(TraceTemplates.TraceSize size) {
        return size.name().toLowerCase(Locale.ROOT);
    }

    static String structureIntersectionDetail(
            ResourceLocation profileId,
            BlockPos anchor,
            BoundingBox templateBox,
            StructureGuard.StructureArea structureArea) {
        return "profile="
                + profileId
                + " anchor="
                + formatPos(anchor)
                + " templateBox="
                + formatBox(templateBox)
                + " structure="
                + structureArea.structureId()
                + " structureBox="
                + formatBox(structureArea.box());
    }

    static String structureContainmentDetail(
            String phase,
            BlockPos pos,
            StructureGuard.StructureArea structureArea) {
        return "phase="
                + phase
                + " pos="
                + formatPos(pos)
                + " structure="
                + structureArea.structureId()
                + " structureBox="
                + formatBox(structureArea.box());
    }

    static String blockDetail(String phase, BlockPos pos, BlockState state) {
        return "phase="
                + phase
                + " pos="
                + formatPos(pos)
                + " block="
                + BuiltInRegistries.BLOCK.getKey(state.getBlock())
                + " state="
                + state;
    }

    static String surfaceDetail(
            ResourceLocation profileId,
            String role,
            BlockPos pos,
            int candidateY,
            int surfaceY) {
        return "profile="
                + profileId
                + " "
                + role
                + "="
                + formatPos(pos)
                + " candidateY="
                + candidateY
                + " surfaceY="
                + formatSurfaceY(surfaceY);
    }

    static String formatPos(BlockPos pos) {
        return "[" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "]";
    }

    private static String formatBox(BoundingBox box) {
        return "["
                + box.minX()
                + ","
                + box.minY()
                + ","
                + box.minZ()
                + " -> "
                + box.maxX()
                + ","
                + box.maxY()
                + ","
                + box.maxZ()
                + "]";
    }

    private static String formatSurfaceY(int surfaceY) {
        return surfaceY == Integer.MIN_VALUE ? "invalid" : Integer.toString(surfaceY);
    }
}
