package com.typ.traces.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.typ.traces.client.ClientTraceState;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class TraceBeamRenderer {

    private static final int BASE_HEIGHT = 256;
    private static final int FADE_TICKS = 20;

    private TraceBeamRenderer() {}

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        ClientTraceState state = ClientTraceState.INSTANCE;
        if (state.size() == 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        long gameTime = mc.level.getGameTime();

        boolean rendered = false;
        for (ClientTraceState.VisibleEntry entry : state.entries()) {
            int height;
            if (entry.isFading()) {
                if (entry.fadeTicksRemaining <= 0) continue;
                height = Math.max(1, BASE_HEIGHT * entry.fadeTicksRemaining / FADE_TICKS);
            } else {
                height = BASE_HEIGHT;
            }
            int color = entry.colorArgb;

            ps.pushPose();
            ps.translate(entry.pos.getX() - cam.x, entry.pos.getY() - cam.y, entry.pos.getZ() - cam.z);
            BeaconRenderer.renderBeaconBeam(
                    ps, buf, BeaconRenderer.BEAM_LOCATION,
                    partialTick, 1.0f, gameTime,
                    0, height, color,
                    0.2f, 0.25f);
            ps.popPose();
            rendered = true;
        }

        if (rendered) {
            buf.endBatch(RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, false));
            buf.endBatch(RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, true));
        }
    }
}
