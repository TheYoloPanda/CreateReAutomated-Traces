package com.typ.traces;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.typ.traces.event.ChunkLoadHandler;
import com.typ.traces.worldgen.TracePlaceholderProcessor;
import com.typ.traces.worldgen.TraceTemplates;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(CreateReAutomatedTraces.MODID)
public class CreateReAutomatedTraces {
    public static final String MODID = "createreautomatedtraces";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateReAutomatedTraces(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(TracePlaceholderProcessor::onRegister);
        NeoForge.EVENT_BUS.addListener(TraceTemplates::onAddReloadListener);
        NeoForge.EVENT_BUS.register(new ChunkLoadHandler());
    }
}
