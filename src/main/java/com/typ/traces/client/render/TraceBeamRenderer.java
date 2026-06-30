package com.typ.traces.client.render;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.typ.traces.client.ClientTraceState;

import com.mojang.math.Axis;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class TraceBeamRenderer {

    private static final int BASE_HEIGHT = 256;
    private static final int FADE_TICKS = 20;
    private static final int FULL_BRIGHT = 15728880;
    private static final float TEXTURE_SCALE = 1.0F;
    private static final float CORE_RADIUS = 0.2F;
    private static final float GLOW_RADIUS = 0.25F;

    private record BeamRender(ClientTraceState.VisibleEntry entry, int height) {}

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
        Frustum frustum = event.getFrustum();

        List<BeamRender> beams = new ArrayList<>(state.size());
        for (ClientTraceState.VisibleEntry entry : state.entries()) {
            int height = beamHeight(entry);
            if (height <= 0) continue;
            if (!isVisible(frustum, entry, height)) continue;
            beams.add(new BeamRender(entry, height));
        }
        if (beams.isEmpty()) return;

        RenderType solidType = RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, false);
        VertexConsumer solidConsumer = buf.getBuffer(solidType);
        for (BeamRender beam : beams) {
            renderSolidBeam(ps, solidConsumer, cam, beam, partialTick, gameTime);
        }
        buf.endBatch(solidType);

        RenderType glowType = RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, true);
        VertexConsumer glowConsumer = buf.getBuffer(glowType);
        for (BeamRender beam : beams) {
            renderGlowBeam(ps, glowConsumer, cam, beam, partialTick, gameTime);
        }
        buf.endBatch(glowType);
    }

    private static int beamHeight(ClientTraceState.VisibleEntry entry) {
        if (!entry.isFading()) return BASE_HEIGHT;
        if (entry.fadeTicksRemaining <= 0) return 0;
        return Math.max(1, BASE_HEIGHT * entry.fadeTicksRemaining / FADE_TICKS);
    }

    private static boolean isVisible(Frustum frustum, ClientTraceState.VisibleEntry entry, int height) {
        if (frustum == null) return true;
        AABB bounds = new AABB(
                entry.pos.getX(),
                entry.pos.getY(),
                entry.pos.getZ(),
                entry.pos.getX() + 1.0D,
                entry.pos.getY() + height,
                entry.pos.getZ() + 1.0D);
        return frustum.isVisible(bounds);
    }

    private static void renderSolidBeam(PoseStack ps, VertexConsumer consumer, Vec3 cam, BeamRender beam,
                                        float partialTick, long gameTime) {
        ClientTraceState.VisibleEntry entry = beam.entry();
        int height = beam.height();
        float time = (float) Math.floorMod(gameTime, 40) + partialTick;
        float scroll = Mth.frac(-time * 0.2F - (float) Mth.floor(-time * 0.1F));
        float minV = -1.0F + scroll;
        float maxV = height * TEXTURE_SCALE * (0.5F / CORE_RADIUS) + minV;

        ps.pushPose();
        translateToBeam(ps, cam, entry);
        ps.mulPose(Axis.YP.rotationDegrees(time * 2.25F - 45.0F));
        renderPart(
                ps,
                consumer,
                entry.colorArgb,
                0,
                height,
                0.0F,
                CORE_RADIUS,
                CORE_RADIUS,
                0.0F,
                -CORE_RADIUS,
                0.0F,
                0.0F,
                -CORE_RADIUS,
                0.0F,
                1.0F,
                maxV,
                minV);
        ps.popPose();
    }

    private static void renderGlowBeam(PoseStack ps, VertexConsumer consumer, Vec3 cam, BeamRender beam,
                                       float partialTick, long gameTime) {
        ClientTraceState.VisibleEntry entry = beam.entry();
        int height = beam.height();
        float time = (float) Math.floorMod(gameTime, 40) + partialTick;
        float scroll = Mth.frac(-time * 0.2F - (float) Mth.floor(-time * 0.1F));
        float minV = -1.0F + scroll;
        float maxV = height * TEXTURE_SCALE + minV;

        ps.pushPose();
        translateToBeam(ps, cam, entry);
        renderPart(
                ps,
                consumer,
                FastColor.ARGB32.color(32, entry.colorArgb),
                0,
                height,
                -GLOW_RADIUS,
                -GLOW_RADIUS,
                GLOW_RADIUS,
                -GLOW_RADIUS,
                -GLOW_RADIUS,
                GLOW_RADIUS,
                GLOW_RADIUS,
                GLOW_RADIUS,
                0.0F,
                1.0F,
                maxV,
                minV);
        ps.popPose();
    }

    private static void translateToBeam(PoseStack ps, Vec3 cam, ClientTraceState.VisibleEntry entry) {
        ps.translate(entry.pos.getX() - cam.x + 0.5D, entry.pos.getY() - cam.y, entry.pos.getZ() - cam.z + 0.5D);
    }

    private static void renderPart(PoseStack ps, VertexConsumer consumer, int color, int minY, int maxY,
                                   float x1, float z1, float x2, float z2,
                                   float x3, float z3, float x4, float z4,
                                   float minU, float maxU, float minV, float maxV) {
        PoseStack.Pose pose = ps.last();
        renderQuad(pose, consumer, color, minY, maxY, x1, z1, x2, z2, minU, maxU, minV, maxV);
        renderQuad(pose, consumer, color, minY, maxY, x4, z4, x3, z3, minU, maxU, minV, maxV);
        renderQuad(pose, consumer, color, minY, maxY, x2, z2, x4, z4, minU, maxU, minV, maxV);
        renderQuad(pose, consumer, color, minY, maxY, x3, z3, x1, z1, minU, maxU, minV, maxV);
    }

    private static void renderQuad(PoseStack.Pose pose, VertexConsumer consumer, int color, int minY, int maxY,
                                   float minX, float minZ, float maxX, float maxZ,
                                   float minU, float maxU, float minV, float maxV) {
        addVertex(pose, consumer, color, maxY, minX, minZ, maxU, minV);
        addVertex(pose, consumer, color, minY, minX, minZ, maxU, maxV);
        addVertex(pose, consumer, color, minY, maxX, maxZ, minU, maxV);
        addVertex(pose, consumer, color, maxY, maxX, maxZ, minU, minV);
    }

    private static void addVertex(PoseStack.Pose pose, VertexConsumer consumer, int color,
                                  int y, float x, float z, float u, float v) {
        consumer.addVertex(pose, x, (float) y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
