package com.typ.traces;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.typ.traces.api.TraceWorldgenExclusions;
import com.typ.traces.config.Config;
import com.typ.traces.network.ModPayloads;
import com.typ.traces.registry.ModDataComponents;
import com.typ.traces.registry.ModItems;
import com.typ.traces.server.TraceFinderTickHandler;
import com.typ.traces.worldgen.TraceBlockDataMap;
import com.typ.traces.worldgen.TraceForNodesFeature;
import com.typ.traces.worldgen.TracePlaceholderProcessor;
import com.typ.traces.worldgen.TraceTemplates;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

@Mod(CreateReAutomatedTraces.MODID)
public class CreateReAutomatedTraces {
    public static final String MODID = "createreautomatedtraces";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateReAutomatedTraces(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(TracePlaceholderProcessor::onRegister);
        modEventBus.addListener(TraceForNodesFeature::onRegister);
        modEventBus.addListener(TraceBlockDataMap::register);
        modEventBus.addListener(ModItems::addCreativeTabContents);
        modEventBus.addListener(ModPayloads::register);
        modEventBus.addListener(Config::onLoad);
        modEventBus.addListener(Config::onReload);
        Config.register(modContainer);
        ModDataComponents.register(modEventBus);
        ModItems.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(TraceTemplates::onAddReloadListener);
        NeoForge.EVENT_BUS.addListener(TraceWorldgenExclusions::onServerStopping);
        NeoForge.EVENT_BUS.addListener(TraceFinderTickHandler::onServerTick);
        NeoForge.EVENT_BUS.addListener(TraceFinderTickHandler::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(TraceFinderTickHandler::onPlayerChangedDimension);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            initClient(modEventBus, modContainer);
        }
    }

    private void initClient(IEventBus modEventBus, ModContainer modContainer) {
        com.typ.traces.client.ClientModEvents.register(modEventBus, modContainer);
    }
}
