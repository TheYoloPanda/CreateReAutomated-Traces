package com.typ.traces.network;

import java.util.HashSet;
import java.util.Set;

import com.typ.traces.CreateReAutomatedTraces;
import com.typ.traces.component.TrackedNodesComponent;
import com.typ.traces.item.TraceFinderItem;
import com.typ.traces.registry.ModDataComponents;
import com.typ.traces.worldgen.TraceBlockDataMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModPayloads {

    private static final int MAX_SELECTED_NODES = 512;

    private ModPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar(CreateReAutomatedTraces.MODID).versioned("3");
        reg.playToClient(
                OpenTraceFinderPayload.TYPE,
                OpenTraceFinderPayload.STREAM_CODEC,
                ModPayloads::handleOpenTraceFinder);
        reg.playToClient(
                VisibleTracesPayload.TYPE,
                VisibleTracesPayload.STREAM_CODEC,
                ModPayloads::handleVisibleTraces);
        reg.playToClient(
                TargetUpdatePayload.TYPE,
                TargetUpdatePayload.STREAM_CODEC,
                ModPayloads::handleTargetUpdate);
        reg.playToServer(
                SelectionUpdatePayload.TYPE,
                SelectionUpdatePayload.STREAM_CODEC,
                ModPayloads::handleSelectionUpdate);
    }

    private static void handleOpenTraceFinder(OpenTraceFinderPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        context.enqueueWork(() -> com.typ.traces.client.ClientTraceFinderHandler.openScreen(payload.hand()));
    }

    private static void handleVisibleTraces(VisibleTracesPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        context.enqueueWork(() -> com.typ.traces.client.ClientTraceFinderHandler.onVisibleTraces(payload));
    }

    private static void handleTargetUpdate(TargetUpdatePayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        context.enqueueWork(() -> com.typ.traces.client.ClientTraceFinderHandler.onTargetUpdate(payload));
    }

    private static void handleSelectionUpdate(SelectionUpdatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            ItemStack stack = player.getItemInHand(payload.hand());
            if (!(stack.getItem() instanceof TraceFinderItem)) return;
            TrackedNodesComponent prev = stack.getOrDefault(
                    ModDataComponents.TRACKED_NODES.get(), TrackedNodesComponent.EMPTY);
            stack.set(ModDataComponents.TRACKED_NODES.get(), prev.withSelected(validSelections(payload.selected())));
        });
    }

    private static Set<ResourceLocation> validSelections(Set<ResourceLocation> requested) {
        Set<ResourceLocation> valid = new HashSet<>();
        for (ResourceLocation id : requested) {
            if (valid.size() >= MAX_SELECTED_NODES) break;
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (block.builtInRegistryHolder().getData(TraceBlockDataMap.TYPE) == null) continue;
            valid.add(id);
        }
        return valid;
    }
}
