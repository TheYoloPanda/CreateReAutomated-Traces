package com.typ.traces.client;

import com.typ.traces.CreateReAutomatedTraces;
import com.typ.traces.client.render.TraceBeamRenderer;
import com.typ.traces.client.render.TraceFinderItemProperty;
import com.typ.traces.config.Config;
import com.typ.traces.config.ModConfigScreen;
import com.typ.traces.registry.ModItems;

import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

public final class ClientModEvents {

    public static final ResourceLocation TARGET_PROPERTY =
            ResourceLocation.fromNamespaceAndPath(CreateReAutomatedTraces.MODID, "target");

    private ClientModEvents() {}

    public static void register(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ModConfigScreen::new);
        modBus.addListener(ClientModEvents::onClientSetup);
        NeoForge.EVENT_BUS.addListener(TraceBeamRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(ClientModEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientModEvents::onClientLoggingOut);
        NeoForge.EVENT_BUS.addListener(ClientModEvents::onClientLevelUnload);
    }

    private static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        ClientTraceFinderHandler.onClientTick();
    }

    private static void onClientLoggingOut(
            net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        ClientTraceState.INSTANCE.clear();
    }

    private static void onClientLevelUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ClientTraceState.INSTANCE.clear();
        }
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            BaseConfigScreen.setDefaultActionFor(CreateReAutomatedTraces.MODID, base -> base
                    .withSpecs(null, Config.common().specification, null));
            ItemProperties.register(
                    ModItems.TRACE_FINDER.get(),
                    TARGET_PROPERTY,
                    new TraceFinderItemProperty());
        });
    }
}
