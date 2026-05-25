package com.typ.traces.registry;

import com.typ.traces.CreateReAutomatedTraces;
import com.typ.traces.component.TrackedNodesComponent;
import com.typ.traces.item.TraceFinderItem;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreateReAutomatedTraces.MODID);

    public static final DeferredItem<TraceFinderItem> TRACE_FINDER = ITEMS.register("trace_finder",
            () -> new TraceFinderItem(new Item.Properties()
                    .stacksTo(1)
                    .component(ModDataComponents.TRACKED_NODES.get(), TrackedNodesComponent.EMPTY)));

    private ModItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    public static void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(TRACE_FINDER.get());
        }
    }
}
