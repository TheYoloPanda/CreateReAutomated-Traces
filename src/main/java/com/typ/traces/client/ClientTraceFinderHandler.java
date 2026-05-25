package com.typ.traces.client;

import com.typ.traces.client.gui.TraceFinderScreen;
import com.typ.traces.network.TargetUpdatePayload;
import com.typ.traces.network.VisibleTracesPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

public final class ClientTraceFinderHandler {

    private ClientTraceFinderHandler() {}

    public static void openScreen(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new TraceFinderScreen(hand));
    }

    private static final int FADE_TICKS = 20;

    public static void onVisibleTraces(VisibleTracesPayload payload) {
        for (VisibleTracesPayload.Entry e : payload.added()) {
            ClientTraceState.INSTANCE.addOrUpdate(e.posLong(), e.nodeId(), e.colorArgb());
        }
        for (Long l : payload.removed()) {
            // Soft removal: trigger fade-out; the tick handler will reap fully-faded entries.
            // Fall back to immediate removal if the entry isn't there (defensive).
            if (!ClientTraceState.INSTANCE.markFade(l, FADE_TICKS)) {
                ClientTraceState.INSTANCE.removeImmediately(l);
            }
        }
    }

    public static void onClientTick() {
        ClientTraceState.INSTANCE.tickFade();
    }

    public static void onTargetUpdate(TargetUpdatePayload payload) {
        ClientTraceState.INSTANCE.setTarget(payload.target());
    }

    public static void onClientLevelUnload() {
        ClientTraceState.INSTANCE.clear();
    }
}
