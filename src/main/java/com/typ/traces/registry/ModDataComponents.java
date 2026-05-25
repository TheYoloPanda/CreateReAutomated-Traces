package com.typ.traces.registry;

import com.typ.traces.CreateReAutomatedTraces;
import com.typ.traces.component.TrackedNodesComponent;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {

    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CreateReAutomatedTraces.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TrackedNodesComponent>> TRACKED_NODES =
            DATA_COMPONENTS.registerComponentType("tracked_nodes", builder -> builder
                    .persistent(TrackedNodesComponent.CODEC)
                    .networkSynchronized(TrackedNodesComponent.STREAM_CODEC));

    private ModDataComponents() {}

    public static void register(IEventBus modBus) {
        DATA_COMPONENTS.register(modBus);
    }
}
