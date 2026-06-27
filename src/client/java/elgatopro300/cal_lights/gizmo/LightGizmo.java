package elgatopro300.cal_lights.gizmo;

import elgatopro300.cal_lights.graphics.CLIcon;
import elgatopro300.cal_lights.graphics.CLTexture;
import elgatopro300.cal_lights.graphics.CalLightsIcons;
import elgatopro300.cal_lights.manager.CALUndoManager;
import elgatopro300.cal_lights.manager.LightInstance;
import elgatopro300.cal_lights.manager.LightManager;
import elgatopro300.cal_lights.ui.CALEditorScreen;
import elgatopro300.cal_lights.ui.CalSettings;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.*;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.Collection;

public class LightGizmo {
    public static final LightGizmo INSTANCE = new LightGizmo();

    private LightInstance selectedLight = null;
    private int activeAxis = -1; // -1: none, 0: X, 1: Y, 2: Z, 3: XZ, 4: XY, 5: ZY, 6: FREE
    private boolean dragging = false;
    private Vec3 dragStartMousePos = null;
    private Vec3 dragStartLightPos = null;
    private final Matrix4f projectionMatrix = new Matrix4f();

    private float lastSx = 1.0f;
    private float lastSy = 1.0f;
    private float lastSz = 1.0f;

    public static boolean renderLightIcons = true;
    public static boolean snapToGrid = false;

    public void init() {
        WorldRenderEvents.END_MAIN.register(this::render);
    }

    public void setSelectedLight(LightInstance light) {
        this.selectedLight = light;
    }

    public LightInstance getSelectedLight() {
        return this.selectedLight;
    }

    private void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null)
            return;
        if (!(client.screen instanceof CALEditorScreen)) {
            return;
        }

        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 camPos = camera.position();

        // Compute projection matrix for picking (world-view matrices are not
        // exposed via RenderSystem in 1.21.11, so we reconstruct it from FOV).
        computeProjectionMatrix(client, camera);

        // Draw billboards and gizmos using the world renderer's consumer
        // provider and matrix stack.  In 1.21.11 there is no immediate-mode
        // rendering that shader packs can discard — geometry goes through the
        // deferred command queue — so we draw directly during END_MAIN.
        MultiBufferSource consumers = context.consumers();
        PoseStack stack = context.matrices();

        Collection<LightInstance> points = LightManager.INSTANCE.getPointLights();
        Collection<LightInstance> spots = LightManager.INSTANCE.getSpotLights();

        drawBillboards(stack, camPos, consumers, points, false);
        drawBillboards(stack, camPos, consumers, spots, true);

        if (selectedLight != null) {
            drawLightIndicators(stack, camPos, consumers, selectedLight);
            drawAxes(stack, camPos, consumers, selectedLight);
        }

    }

    private void computeProjectionMatrix(Minecraft client, Camera camera) {
        double fov = client.options.fov().get();
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        float aspect = (float) width / (float) height;
        this.projectionMatrix.setPerspective((float) Math.toRadians(fov), aspect, 0.05f, 1000.0f);
    }

    private void drawBillboards(PoseStack stack, Vec3 camPos, MultiBufferSource consumers, Collection<LightInstance> lights, boolean isSpot) {
        CLIcon icon = isSpot ? CalLightsIcons.SPOT_LIGHT : CalLightsIcons.POINT_LIGHT;
        if (icon == null)
            return;

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (camera == null) {
            return;
        }
        Quaternionf camRot = camera.rotation();

        for (LightInstance light : lights) {
            if (!renderLightIcons && light != selectedLight) {
                continue;
            }
            stack.pushPose();
            stack.translate(light.x - camPos.x, light.y - camPos.y, light.z - camPos.z);
            stack.mulPose(camRot);

            // Render a square billboard scaling with distance for comfortable
            // viewing/clicking
            double dist = camPos.distanceTo(new Vec3(light.x, light.y, light.z));
            float size = (float) (0.3f * Math.max(1.0, dist * 0.15));
            if (light == selectedLight) {
                size = (float) (0.4f * Math.max(1.0, dist * 0.15)); // larger if selected
            }

            if (icon.staticTexture != null && icon.tintTexture != null) {
                int tintColor = (light == selectedLight) ? 0xFFFFAA00 : (0xFF000000 | ((int) (light.r * 255) << 16) | ((int) (light.g * 255) << 8) | (int) (light.b * 255));
                
                drawBillboardQuad(stack, consumers.getBuffer(RenderTypes.entityTranslucent(icon.tintTexture.identifier)), size, icon.tintTexture, icon.x, icon.y, icon.w, icon.h, tintColor);
                drawBillboardQuad(stack, consumers.getBuffer(RenderTypes.entityTranslucent(icon.staticTexture.identifier)), size, icon.staticTexture, icon.x, icon.y, icon.w, icon.h, 0xFFFFFFFF);
            } else if (icon.texture != null) {
                int color = (light == selectedLight) ? 0xFFFFAA00 : 0xFFFFFFFF;
                drawBillboardQuad(stack, consumers.getBuffer(RenderTypes.entityTranslucent(icon.texture.identifier)), size, icon.texture, icon.x, icon.y, icon.w, icon.h, color);
            }
            stack.popPose();
        }
    }

    private void drawBillboardQuad(PoseStack stack, VertexConsumer buf, float size, CLTexture texture, int texX, int texY, int texW, int texH, int color) {
        PoseStack.Pose entry = stack.last();
        Matrix4f matrix = entry.pose();

        float u1 = texX / (float) texture.width;
        float v1 = texY / (float) texture.height;
        float u2 = (texX + texW) / (float) texture.width;
        float v2 = (texY + texH) / (float) texture.height;

        float a = ((color >>> 24) & 0xFF) / 255.0f;
        float r = ((color >>> 16) & 0xFF) / 255.0f;
        float g = ((color >>> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        int light = LightTexture.FULL_BRIGHT;
        int overlay = OverlayTexture.NO_OVERLAY;

        buf.addVertex(matrix, -size, -size, 0).setColor(r, g, b, a).setUv(u1, v2).setOverlay(overlay).setLight(light).setNormal(entry, 0f, 0f, 1f);
        buf.addVertex(matrix, size, -size, 0).setColor(r, g, b, a).setUv(u2, v2).setOverlay(overlay).setLight(light).setNormal(entry, 0f, 0f, 1f);
        buf.addVertex(matrix, size, size, 0).setColor(r, g, b, a).setUv(u2, v1).setOverlay(overlay).setLight(light).setNormal(entry, 0f, 0f, 1f);
        buf.addVertex(matrix, -size, size, 0).setColor(r, g, b, a).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(entry, 0f, 0f, 1f);
    }

    private static final Identifier WHITE_TEX = Identifier.withDefaultNamespace("textures/block/white_concrete.png");

    private void drawAxes(PoseStack stack, Vec3 camPos, MultiBufferSource consumers, LightInstance light) {
        stack.pushPose();
        stack.translate(light.x - camPos.x, light.y - camPos.y, light.z - camPos.z);

        double dist = camPos.distanceTo(new Vec3(light.x, light.y, light.z));
        float scale = (float) (0.4f * Math.max(0.5, dist * 0.12) * CalSettings.INSTANCE.gizmoSize);
        stack.scale(scale, scale, scale);

        float sx = (camPos.x - light.x) >= 0 ? 1.0f : -1.0f;
        float sy = (camPos.y - light.y) >= 0 ? 1.0f : -1.0f;
        float sz = (camPos.z - light.z) >= 0 ? 1.0f : -1.0f;
        if (!dragging) {
            this.lastSx = sx;
            this.lastSy = sy;
            this.lastSz = sz;
        } else {
            sx = this.lastSx;
            sy = this.lastSy;
            sz = this.lastSz;
        }

        boolean activeX = activeAxis == -1 || activeAxis == 0;
        boolean activeY = activeAxis == -1 || activeAxis == 1;
        boolean activeZ = activeAxis == -1 || activeAxis == 2;
        boolean activeXZ = activeAxis == -1 || activeAxis == 3;
        boolean activeXY = activeAxis == -1 || activeAxis == 4;
        boolean activeZY = activeAxis == -1 || activeAxis == 5;
        boolean activeFree = activeAxis == -1 || activeAxis == 6;

        float[] xCol = (activeAxis == 0) ? new float[] { 1.0f, 1.0f, 0.0f } : new float[] { 1.0f, 0.15f, 0.15f };
        float[] yCol = (activeAxis == 1) ? new float[] { 1.0f, 1.0f, 0.0f } : new float[] { 0.15f, 1.0f, 0.15f };
        float[] zCol = (activeAxis == 2) ? new float[] { 1.0f, 1.0f, 0.0f } : new float[] { 0.15f, 0.35f, 1.0f };
        float[] xzCol = (activeAxis == 3) ? new float[] { 1.0f, 1.0f, 0.0f } : new float[] { 0.15f, 1.0f, 0.6f };
        float[] xyCol = (activeAxis == 4) ? new float[] { 1.0f, 1.0f, 0.0f } : new float[] { 0.6f, 0.15f, 1.0f };
        float[] zyCol = (activeAxis == 5) ? new float[] { 1.0f, 1.0f, 0.0f } : new float[] { 1.0f, 0.4f, 0.15f };
        float[] freeCol = (activeAxis == 6) ? new float[] { 1.0f, 1.0f, 0.0f } : new float[] { 1.0f, 1.0f, 1.0f };

        float axisSize = 0.30f;
        float axisOffset = 0.012f;
        float planeInner = 0.08f;
        float planeOuter = 0.20f;
        float offset = 0.001f;

        VertexConsumer buf = consumers.getBuffer(RenderTypes.entityTranslucentEmissive(WHITE_TEX));
        PoseStack.Pose e = stack.last();

        if (activeX) fillBox(buf, e, 0, -axisOffset, -axisOffset, axisSize * sx, axisOffset, axisOffset, xCol[0], xCol[1], xCol[2], 1.0f);
        if (activeY) fillBox(buf, e, -axisOffset, 0, -axisOffset, axisOffset, axisSize * sy, axisOffset, yCol[0], yCol[1], yCol[2], 1.0f);
        if (activeZ) fillBox(buf, e, -axisOffset, -axisOffset, 0, axisOffset, axisOffset, axisSize * sz, zCol[0], zCol[1], zCol[2], 1.0f);

        if (activeXZ) fillBox(buf, e, planeInner * sx, -offset, planeInner * sz, planeOuter * sx, offset, planeOuter * sz, xzCol[0], xzCol[1], xzCol[2], 0.4f);
        if (activeXY) fillBox(buf, e, planeInner * sx, planeInner * sy, -offset, planeOuter * sx, planeOuter * sy, offset, xyCol[0], xyCol[1], xyCol[2], 0.4f);
        if (activeZY) fillBox(buf, e, -offset, planeInner * sy, planeInner * sz, offset, planeOuter * sy, planeOuter * sz, zyCol[0], zyCol[1], zyCol[2], 0.4f);

        if (activeFree) fillBox(buf, e, -axisOffset, -axisOffset, -axisOffset, axisOffset, axisOffset, axisOffset, freeCol[0], freeCol[1], freeCol[2], 1.0f);

        stack.popPose();
    }

    private void drawLightIndicators(PoseStack stack, Vec3 camPos, MultiBufferSource consumers, LightInstance light) {
        stack.pushPose();
        stack.translate(light.x - camPos.x, light.y - camPos.y, light.z - camPos.z);

        float r = light.r;
        float g = light.g;
        float b = light.b;
        float maxVal = Math.max(r, Math.max(g, b));
        if (maxVal < 0.2f) {
            r = 1.0f;
            g = 0.66f;
            b = 0.0f;
        }
        float a = 0.75f;

        VertexConsumer buf = consumers.getBuffer(RenderTypes.entityTranslucentEmissive(WHITE_TEX));
        PoseStack.Pose e = stack.last();

        if (light.isSpot) {
            drawSpotIndicator(buf, e, light, r, g, b, a);
        } else {
            drawPointIndicator(buf, e, light, r, g, b, a);
        }

        stack.popPose();
    }

    private void vert(VertexConsumer buf, Matrix4f m, float x, float y, float z, float r, float g, float b, float a) {
        buf.addVertex(m, x, y, z).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
    }

    private void lineQuad(Matrix4f m, VertexConsumer buf, float x1, float y1, float z1, float x2, float y2, float z2, float hw, float r, float g, float b, float a) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dl < 1e-5f) return;
        dx /= dl; dy /= dl; dz /= dl;
        float upx = 0, upy = 1, upz = 0;
        if (Math.abs(dy) > 0.99f) { upx = 1; upy = 0; upz = 0; }
        float wx = dy * upz - dz * upy;
        float wy = dz * upx - dx * upz;
        float wz = dx * upy - dy * upx;
        float wl = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
        if (wl < 1e-5f) return;
        wx = wx / wl * hw; wy = wy / wl * hw; wz = wz / wl * hw;
        vert(buf, m, x1 - wx, y1 - wy, z1 - wz, r, g, b, a);
        vert(buf, m, x1 + wx, y1 + wy, z1 + wz, r, g, b, a);
        vert(buf, m, x2 + wx, y2 + wy, z2 + wz, r, g, b, a);
        vert(buf, m, x1 - wx, y1 - wy, z1 - wz, r, g, b, a);
        vert(buf, m, x2 + wx, y2 + wy, z2 + wz, r, g, b, a);
        vert(buf, m, x2 - wx, y2 - wy, z2 - wz, r, g, b, a);
    }

    private void lineQuadCross(Matrix4f m, VertexConsumer buf,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float hw, float r, float g, float b, float a) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dl < 1e-5f) return;
        dx /= dl; dy /= dl; dz /= dl;
        float upx = 0, upy = 1, upz = 0;
        if (Math.abs(dy) > 0.99f) { upx = 1; upy = 0; upz = 0; }
        float b1x = dy * upz - dz * upy, b1y = dz * upx - dx * upz, b1z = dx * upy - dy * upx;
        float b1l = (float) Math.sqrt(b1x * b1x + b1y * b1y + b1z * b1z);
        if (b1l < 1e-5f) return;
        b1x = b1x / b1l * hw; b1y = b1y / b1l * hw; b1z = b1z / b1l * hw;
        float b2x = dy * b1z - dz * b1y, b2y = dz * b1x - dx * b1z, b2z = dx * b1y - dy * b1x;
        float b2l = (float) Math.sqrt(b2x * b2x + b2y * b2y + b2z * b2z);
        if (b2l > 1e-5f) { b2x = b2x / b2l * hw; b2y = b2y / b2l * hw; b2z = b2z / b2l * hw; }
        vert(buf, m, x1 - b1x, y1 - b1y, z1 - b1z, r, g, b, a);
        vert(buf, m, x1 + b1x, y1 + b1y, z1 + b1z, r, g, b, a);
        vert(buf, m, x2 + b1x, y2 + b1y, z2 + b1z, r, g, b, a);
        vert(buf, m, x1 - b1x, y1 - b1y, z1 - b1z, r, g, b, a);
        vert(buf, m, x2 + b1x, y2 + b1y, z2 + b1z, r, g, b, a);
        vert(buf, m, x2 - b1x, y2 - b1y, z2 - b1z, r, g, b, a);
        vert(buf, m, x1 - b2x, y1 - b2y, z1 - b2z, r, g, b, a);
        vert(buf, m, x1 + b2x, y1 + b2y, z1 + b2z, r, g, b, a);
        vert(buf, m, x2 + b2x, y2 + b2y, z2 + b2z, r, g, b, a);
        vert(buf, m, x1 - b2x, y1 - b2y, z1 - b2z, r, g, b, a);
        vert(buf, m, x2 + b2x, y2 + b2y, z2 + b2z, r, g, b, a);
        vert(buf, m, x2 - b2x, y2 - b2y, z2 - b2z, r, g, b, a);
    }

    private void drawPointIndicator(VertexConsumer buf, PoseStack.Pose e, LightInstance light, float r, float g, float b, float a) {
        float radius = Math.max(0.5f, Math.min(light.radius, 16.0f));
        int seg = 64;
        float hw = 0.015f;
        Matrix4f m = e.pose();
        for (int i = 0; i < seg; i++) {
            double a1 = Math.PI * 2.0 * i / seg;
            double a2 = Math.PI * 2.0 * (i + 1) / seg;
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
            lineQuadCross(m, buf, radius * c1, 0, radius * s1, radius * c2, 0, radius * s2, hw, r, g, b, a);
            lineQuadCross(m, buf, radius * c1, radius * s1, 0, radius * c2, radius * s2, 0, hw, r, g, b, a);
            lineQuadCross(m, buf, 0, radius * c1, radius * s1, 0, radius * c2, radius * s2, hw, r, g, b, a);
        }
    }

    private void drawSpotIndicator(VertexConsumer buf, PoseStack.Pose e, LightInstance light, float r, float g, float b, float a) {
        float dx = light.dx, dy = light.dy, dz = light.dz;
        float dlen = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dlen < 1e-4f) { dx = 0f; dy = -1f; dz = 0f; dlen = 1f; }
        dx /= dlen; dy /= dlen; dz /= dlen;

        float len = Math.max(1f, Math.min(light.distance, 32f));
        float outerRad = (float) (len * Math.tan(Math.toRadians(light.getOuterAngleDeg() * 0.5f)));
        float cx = dx * len, cy = dy * len, cz = dz * len;

        float rx, ry, rz;
        if (Math.abs(dy) < 0.99f) { rx = 0f; ry = 1f; rz = 0f; }
        else { rx = 1f; ry = 0f; rz = 0f; }
        float ux = dy * rz - dz * ry, uy = dz * rx - dx * rz, uz = dx * ry - dy * rx;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul; uy /= ul; uz /= ul;
        float vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;

        Matrix4f m = e.pose();
        int seg = 64;
        float hw = 0.015f;

        for (int i = 0; i < seg; i++) {
            double a1 = Math.PI * 2.0 * i / seg;
            double a2 = Math.PI * 2.0 * (i + 1) / seg;
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
            float p1x = cx + outerRad * (c1 * ux + s1 * vx), p1y = cy + outerRad * (c1 * uy + s1 * vy), p1z = cz + outerRad * (c1 * uz + s1 * vz);
            float p2x = cx + outerRad * (c2 * ux + s2 * vx), p2y = cy + outerRad * (c2 * uy + s2 * vy), p2z = cz + outerRad * (c2 * uz + s2 * vz);
            lineQuadCross(m, buf, p1x, p1y, p1z, p2x, p2y, p2z, hw, r, g, b, a);
        }

        if (light.soft > 0.1f) {
            float innerRad = (float) (len * Math.tan(Math.toRadians(light.getInnerAngleDeg() * 0.5f)));
            float innerA = a * 0.4f;
            for (int i = 0; i < seg; i++) {
                double a1 = Math.PI * 2.0 * i / seg;
                double a2 = Math.PI * 2.0 * (i + 1) / seg;
                float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                float c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
                float p1x = cx + innerRad * (c1 * ux + s1 * vx), p1y = cy + innerRad * (c1 * uy + s1 * vy), p1z = cz + innerRad * (c1 * uz + s1 * vz);
                float p2x = cx + innerRad * (c2 * ux + s2 * vx), p2y = cy + innerRad * (c2 * uy + s2 * vy), p2z = cz + innerRad * (c2 * uz + s2 * vz);
                lineQuadCross(m, buf, p1x, p1y, p1z, p2x, p2y, p2z, hw, r, g, b, innerA);
            }
        }

        float[] angles = {0f, (float) (Math.PI / 2.0), (float) Math.PI, (float) (3.0 * Math.PI / 2.0)};
        for (float angle : angles) {
            float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
            float px = cx + outerRad * (cos * ux + sin * vx);
            float py = cy + outerRad * (cos * uy + sin * vy);
            float pz = cz + outerRad * (cos * uz + sin * vz);
            lineQuadCross(m, buf, 0, 0, 0, px, py, pz, hw, r, g, b, a);
        }
    }

    private void fillBox(VertexConsumer buf, PoseStack.Pose entry, float x1, float y1, float z1, float x2, float y2,
            float z2, float r, float g, float b, float a) {
        Matrix4f m = entry.pose();
        vert(buf, m, x1, y1, z2, r, g, b, a); vert(buf, m, x1, y2, z2, r, g, b, a); vert(buf, m, x1, y2, z1, r, g, b, a);
        vert(buf, m, x1, y1, z2, r, g, b, a); vert(buf, m, x1, y2, z1, r, g, b, a); vert(buf, m, x1, y1, z1, r, g, b, a);
        vert(buf, m, x2, y1, z1, r, g, b, a); vert(buf, m, x2, y2, z1, r, g, b, a); vert(buf, m, x2, y2, z2, r, g, b, a);
        vert(buf, m, x2, y1, z1, r, g, b, a); vert(buf, m, x2, y2, z2, r, g, b, a); vert(buf, m, x2, y1, z2, r, g, b, a);
        vert(buf, m, x1, y1, z1, r, g, b, a); vert(buf, m, x2, y1, z1, r, g, b, a); vert(buf, m, x2, y1, z2, r, g, b, a);
        vert(buf, m, x1, y1, z1, r, g, b, a); vert(buf, m, x2, y1, z2, r, g, b, a); vert(buf, m, x1, y1, z2, r, g, b, a);
        vert(buf, m, x2, y2, z1, r, g, b, a); vert(buf, m, x1, y2, z1, r, g, b, a); vert(buf, m, x1, y2, z2, r, g, b, a);
        vert(buf, m, x2, y2, z1, r, g, b, a); vert(buf, m, x1, y2, z2, r, g, b, a); vert(buf, m, x2, y2, z2, r, g, b, a);
        vert(buf, m, x2, y1, z1, r, g, b, a); vert(buf, m, x1, y1, z1, r, g, b, a); vert(buf, m, x1, y2, z1, r, g, b, a);
        vert(buf, m, x2, y1, z1, r, g, b, a); vert(buf, m, x1, y2, z1, r, g, b, a); vert(buf, m, x2, y2, z1, r, g, b, a);
        vert(buf, m, x1, y1, z2, r, g, b, a); vert(buf, m, x2, y1, z2, r, g, b, a); vert(buf, m, x2, y2, z2, r, g, b, a);
        vert(buf, m, x1, y1, z2, r, g, b, a); vert(buf, m, x2, y2, z2, r, g, b, a); vert(buf, m, x1, y2, z2, r, g, b, a);
    }

    public boolean onMouseClicked(double mouseX, double mouseY, int btn) {
        if (btn != 0)
            return false;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null)
            return false;

        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 rayDir = getRayDirection(mouseX, mouseY);
        Vec3 rayStart = camera.position();

        if (selectedLight != null) {
            int clickedAxis = checkAxisClickLocal(rayStart, rayDir, selectedLight);
            if (clickedAxis != -1) {
                activeAxis = clickedAxis;
                CALUndoManager.pushState();
                dragging = true;
                dragStartLightPos = new Vec3(selectedLight.x, selectedLight.y, selectedLight.z);

                if (activeAxis >= 0 && activeAxis <= 2) {
                    dragStartMousePos = getMouseProjectionOnAxis(rayStart, rayDir, dragStartLightPos, activeAxis);
                } else if (activeAxis >= 3 && activeAxis <= 5) {
                    dragStartMousePos = getMouseProjectionOnPlane(rayStart, rayDir, dragStartLightPos, activeAxis);
                } else if (activeAxis == 6) {
                    Vec3 camDir = Vec3.directionFromRotation(camera.xRot(), camera.yRot());
                    dragStartMousePos = getMouseProjectionFree(rayStart, rayDir, dragStartLightPos, camDir);
                }
                return true;
            }
        }

        LightInstance clicked = checkBillboardClick(rayStart, rayDir);
        if (clicked != null) {
            selectedLight = clicked;
            activeAxis = -1;
            return true;
        }

        selectedLight = null;
        activeAxis = -1;
        return false;
    }

    public boolean onMouseReleased(double mouseX, double mouseY, int btn) {
        if (btn == 0 && dragging) {
            dragging = false;
            activeAxis = -1;
            return true;
        }
        return false;
    }

    public boolean onMouseDragged(double mouseX, double mouseY, int btn, double dx, double dy) {
        if (btn == 0 && dragging && selectedLight != null && activeAxis != -1) {
            Minecraft client = Minecraft.getInstance();
            Camera camera = client.gameRenderer.getMainCamera();
            Vec3 rayDir = getRayDirection(mouseX, mouseY);
            Vec3 rayStart = camera.position();

            Vec3 currentProj = null;
            if (activeAxis >= 0 && activeAxis <= 2) {
                currentProj = getMouseProjectionOnAxis(rayStart, rayDir, dragStartLightPos, activeAxis);
            } else if (activeAxis >= 3 && activeAxis <= 5) {
                currentProj = getMouseProjectionOnPlane(rayStart, rayDir, dragStartLightPos, activeAxis);
            } else if (activeAxis == 6) {
                Vec3 camDir = Vec3.directionFromRotation(camera.xRot(), camera.yRot());
                currentProj = getMouseProjectionFree(rayStart, rayDir, dragStartLightPos, camDir);
            }

            if (currentProj != null && dragStartMousePos != null) {
                Vec3 delta = currentProj.subtract(dragStartMousePos);
                float nx = (float) (dragStartLightPos.x + delta.x);
                float ny = (float) (dragStartLightPos.y + delta.y);
                float nz = (float) (dragStartLightPos.z + delta.z);

                if (snapToGrid) {
                    nx = Math.round(nx * 2.0f) / 2.0f;
                    ny = Math.round(ny * 2.0f) / 2.0f;
                    nz = Math.round(nz * 2.0f) / 2.0f;
                }

                if (activeAxis == 0) {
                    selectedLight.x = nx;
                } else if (activeAxis == 1) {
                    selectedLight.y = ny;
                } else if (activeAxis == 2) {
                    selectedLight.z = nz;
                } else if (activeAxis == 3) { // XZ Plane
                    selectedLight.x = nx;
                    selectedLight.z = nz;
                } else if (activeAxis == 4) { // XY Plane
                    selectedLight.x = nx;
                    selectedLight.y = ny;
                } else if (activeAxis == 5) { // ZY Plane
                    selectedLight.z = nz;
                    selectedLight.y = ny;
                } else if (activeAxis == 6) { // FREE
                    selectedLight.x = nx;
                    selectedLight.y = ny;
                    selectedLight.z = nz;
                }
            }
            return true;
        }
        return false;
    }

    public Vec3 getRayDirection(double mouseX, double mouseY) {
        Minecraft client = Minecraft.getInstance();
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();

        float x = (2.0f * (float) mouseX) / width - 1.0f;
        float y = 1.0f - (2.0f * (float) mouseY) / height;

        Matrix4f invProj = new Matrix4f(projectionMatrix).invert();

        // Unproject from clip space to camera space
        Vector4f rayClip = new Vector4f(x, y, -1.0f, 1.0f);
        rayClip.mul(invProj);

        Vector3f rayEye = new Vector3f(rayClip.x / rayClip.w, rayClip.y / rayClip.w, rayClip.z / rayClip.w);

        // Transform from camera space to world space using the camera rotation.
        // In 1.21.11 the projection/view are GPU-internal, so we reconstruct the
        // view transform from the camera's orientation quaternion.
        Camera camera = client.gameRenderer.getMainCamera();
        rayEye.rotate(camera.rotation());

        return new Vec3(rayEye.x, rayEye.y, rayEye.z).normalize();
    }

    public LightInstance checkBillboardClickExternal(Vec3 rayStart, Vec3 rayDir) {
        return checkBillboardClick(rayStart, rayDir);
    }

    private LightInstance checkBillboardClick(Vec3 rayStart, Vec3 rayDir) {
        LightInstance bestMatch = null;
        double bestDist = Double.MAX_VALUE;

        Collection<LightInstance> allLights = LightManager.INSTANCE.getPointLights();
        for (LightInstance light : allLights) {
            Vec3 pos = new Vec3(light.x, light.y, light.z);
            double distToLine = getDistanceToPoint(rayStart, rayDir, pos);
            double dist = rayStart.distanceTo(pos);
            double size = 0.35f * Math.max(1.0, dist * 0.15);
            double maxDist = size * 0.8;
            if (distToLine < maxDist && distToLine < bestDist) {
                bestDist = distToLine;
                bestMatch = light;
            }
        }
        Collection<LightInstance> spots = LightManager.INSTANCE.getSpotLights();
        for (LightInstance light : spots) {
            Vec3 pos = new Vec3(light.x, light.y, light.z);
            double distToLine = getDistanceToPoint(rayStart, rayDir, pos);
            double dist = rayStart.distanceTo(pos);
            double size = 0.35f * Math.max(1.0, dist * 0.15);
            double maxDist = size * 0.8;
            if (distToLine < maxDist && distToLine < bestDist) {
                bestDist = distToLine;
                bestMatch = light;
            }
        }
        return bestMatch;
    }

    private int checkAxisClickLocal(Vec3 rayStart, Vec3 rayDir, LightInstance light) {
        Vec3 pos = new Vec3(light.x, light.y, light.z);
        double dist = rayStart.distanceTo(pos);
        double scale = 0.4 * Math.max(0.5, dist * 0.12) * CalSettings.INSTANCE.gizmoSize;

        Vec3 localRayStart = rayStart.subtract(pos).scale(1.0 / scale);
        Vec3 localRayDir = rayDir;

        float sx = this.lastSx;
        float sy = this.lastSy;
        float sz = this.lastSz;

        // 1. Check FREE translation (Center box) -> index 6
        if (intersectAABB(localRayStart, localRayDir, -0.04, -0.04, -0.04, 0.04, 0.04, 0.04)) {
            return 6;
        }

        // 2. Check Plane handles
        // XZ Plane (index 3)
        if (Math.abs(localRayDir.y) > 0.0001) {
            double t = -localRayStart.y / localRayDir.y;
            if (t >= 0) {
                Vec3 inter = localRayStart.add(localRayDir.scale(t));
                double minX = Math.min(0.06 * sx, 0.24 * sx);
                double maxX = Math.max(0.06 * sx, 0.24 * sx);
                double minZ = Math.min(0.06 * sz, 0.24 * sz);
                double maxZ = Math.max(0.06 * sz, 0.24 * sz);
                if (inter.x >= minX && inter.x <= maxX && inter.z >= minZ && inter.z <= maxZ) {
                    return 3;
                }
            }
        }
        // XY Plane (index 4)
        if (Math.abs(localRayDir.z) > 0.0001) {
            double t = -localRayStart.z / localRayDir.z;
            if (t >= 0) {
                Vec3 inter = localRayStart.add(localRayDir.scale(t));
                double minX = Math.min(0.06 * sx, 0.24 * sx);
                double maxX = Math.max(0.06 * sx, 0.24 * sx);
                double minY = Math.min(0.06 * sy, 0.24 * sy);
                double maxY = Math.max(0.06 * sy, 0.24 * sy);
                if (inter.x >= minX && inter.x <= maxX && inter.y >= minY && inter.y <= maxY) {
                    return 4;
                }
            }
        }
        // ZY Plane (index 5)
        if (Math.abs(localRayDir.x) > 0.0001) {
            double t = -localRayStart.x / localRayDir.x;
            if (t >= 0) {
                Vec3 inter = localRayStart.add(localRayDir.scale(t));
                double minZ = Math.min(0.06 * sz, 0.24 * sz);
                double maxZ = Math.max(0.06 * sz, 0.24 * sz);
                double minY = Math.min(0.06 * sy, 0.24 * sy);
                double maxY = Math.max(0.06 * sy, 0.24 * sy);
                if (inter.z >= minZ && inter.z <= maxZ && inter.y >= minY && inter.y <= maxY) {
                    return 5;
                }
            }
        }

        // 3. Check Axis handles (0: X, 1: Y, 2: Z)
        double dX = getDistanceToSegment(localRayStart, localRayDir, new Vec3(0, 0, 0), new Vec3(0.35 * sx, 0, 0));
        double dY = getDistanceToSegment(localRayStart, localRayDir, new Vec3(0, 0, 0), new Vec3(0, 0.35 * sy, 0));
        double dZ = getDistanceToSegment(localRayStart, localRayDir, new Vec3(0, 0, 0), new Vec3(0, 0, 0.35 * sz));

        double threshold = 0.04;
        if (dX < threshold && dX < dY && dX < dZ)
            return 0;
        if (dY < threshold && dY < dX && dY < dZ)
            return 1;
        if (dZ < threshold && dZ < dX && dZ < dY)
            return 2;

        return -1;
    }

    private boolean intersectAABB(Vec3 rayStart, Vec3 rayDir, double minX, double minY, double minZ, double maxX,
            double maxY, double maxZ) {
        double tx1 = (minX - rayStart.x) / rayDir.x;
        double tx2 = (maxX - rayStart.x) / rayDir.x;
        double tmin = Math.min(tx1, tx2);
        double tmax = Math.max(tx1, tx2);

        double ty1 = (minY - rayStart.y) / rayDir.y;
        double ty2 = (maxY - rayStart.y) / rayDir.y;
        tmin = Math.max(tmin, Math.min(ty1, ty2));
        tmax = Math.min(tmax, Math.max(ty1, ty2));

        double tz1 = (minZ - rayStart.z) / rayDir.z;
        double tz2 = (maxZ - rayStart.z) / rayDir.z;
        tmin = Math.max(tmin, Math.min(tz1, tz2));
        tmax = Math.min(tmax, Math.max(tz1, tz2));

        return tmax >= Math.max(0.0, tmin);
    }

    private Vec3 getMouseProjectionOnAxis(Vec3 rayStart, Vec3 rayDir, Vec3 origin, int axis) {
        Vec3 axisDir = new Vec3(axis == 0 ? 1 : 0, axis == 1 ? 1 : 0, axis == 2 ? 1 : 0);

        Vec3 p12 = rayStart.subtract(origin);
        double v1v1 = rayDir.dot(rayDir);
        double v1v2 = rayDir.dot(axisDir);
        double v2v2 = axisDir.dot(axisDir);
        double p12v1 = p12.dot(rayDir);
        double p12v2 = p12.dot(axisDir);

        double denom = v1v1 * v2v2 - v1v2 * v1v2;
        if (Math.abs(denom) < 0.0001)
            return origin;

        double t2 = (p12v2 * v1v1 - p12v1 * v1v2) / denom;
        return origin.add(axisDir.scale(t2));
    }

    private Vec3 getMouseProjectionOnPlane(Vec3 rayStart, Vec3 rayDir, Vec3 origin, int planeIndex) {
        double t;
        if (planeIndex == 3) { // XZ Plane
            if (Math.abs(rayDir.y) < 0.0001)
                return origin;
            t = (origin.y - rayStart.y) / rayDir.y;
        } else if (planeIndex == 4) { // XY Plane
            if (Math.abs(rayDir.z) < 0.0001)
                return origin;
            t = (origin.z - rayStart.z) / rayDir.z;
        } else { // 5 ZY Plane
            if (Math.abs(rayDir.x) < 0.0001)
                return origin;
            t = (origin.x - rayStart.x) / rayDir.x;
        }
        return rayStart.add(rayDir.scale(t));
    }

    private Vec3 getMouseProjectionFree(Vec3 rayStart, Vec3 rayDir, Vec3 origin, Vec3 normal) {
        double denom = rayDir.dot(normal);
        if (Math.abs(denom) < 0.0001)
            return origin;
        double t = origin.subtract(rayStart).dot(normal) / denom;
        return rayStart.add(rayDir.scale(t));
    }

    private double getDistanceToPoint(Vec3 rayStart, Vec3 rayDir, Vec3 point) {
        Vec3 toPoint = point.subtract(rayStart);
        double projection = toPoint.dot(rayDir);
        if (projection < 0)
            return Double.MAX_VALUE;
        Vec3 closestPoint = rayStart.add(rayDir.scale(projection));
        return closestPoint.distanceTo(point);
    }

    private double getDistanceToSegment(Vec3 rayStart, Vec3 rayDir, Vec3 s1, Vec3 s2) {
        Vec3 u = rayDir;
        Vec3 v = s2.subtract(s1);
        Vec3 w = rayStart.subtract(s1);

        double a = u.dot(u);
        double b = u.dot(v);
        double c = v.dot(v);
        double d = u.dot(w);
        double e = v.dot(w);

        double denom = a * c - b * b;
        double tRatio = 0.0;
        if (Math.abs(denom) > 0.00001) {
            tRatio = (a * e - b * d) / denom;
        }
        tRatio = Math.max(0.0, Math.min(1.0, tRatio));

        Vec3 closestOnSegment = s1.add(v.scale(tRatio));
        return getDistanceToPoint(rayStart, rayDir, closestOnSegment);
    }

}
