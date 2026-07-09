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

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.SequencedMap;

public class LightGizmo {
    public static final LightGizmo INSTANCE = new LightGizmo();

    private static final int AXIS_X_COLOR = 0xFFFF2626;
    private static final int AXIS_Y_COLOR = 0xFF26FF26;
    private static final int AXIS_Z_COLOR = 0xFF2659FF;
    private static final int ACTIVE_COLOR = 0xFFFFFF00;
    private static final int FREE_COLOR = 0xFFFFFFFF;
    private static final float LINE_WIDTH = 2.5f;
    private static final float ICON_BASE_SIZE = 28.0f;
    private static final float ICON_SELECTED_SIZE = 34.0f;

    private LightInstance selectedLight = null;
    private int activeAxis = -1; // -1: none, 0: X, 1: Y, 2: Z, 3: XZ, 4: XY, 5: ZY, 6: FREE
    private boolean dragging = false;
    private Vec3d dragStartMousePos = null;
    private Vec3d dragStartLightPos = null;

    private float lastSx = 1.0f;
    private float lastSy = 1.0f;
    private float lastSz = 1.0f;

    private final Matrix4f overlayProj = new Matrix4f();
    private final Matrix4f overlayView = new Matrix4f();

    public static boolean renderLightIcons = true;
    public static boolean snapToGrid = false;

    public void init() {
        WorldRenderEvents.END_MAIN.register(this::renderWorldOverlay);
    }

    public void setSelectedLight(LightInstance light) {
        this.selectedLight = light;
    }

    public LightInstance getSelectedLight() {
        return this.selectedLight;
    }

    public void renderOverlay(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || !(client.currentScreen instanceof CALEditorScreen)) {
            return;
        }

        Camera camera = client.gameRenderer.getCamera();
        if (camera == null) {
            return;
        }

        Vec3d camPos = camera.getCameraPos();
        rebuildOverlayMatrices(client, camera);

        Collection<LightInstance> points = LightManager.INSTANCE.getPointLights();
        Collection<LightInstance> spots = LightManager.INSTANCE.getSpotLights();

        drawBillboards(context, camPos, points, false);
        drawBillboards(context, camPos, spots, true);
    }

    private void renderWorldOverlay(WorldRenderContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || !(client.currentScreen instanceof CALEditorScreen) || selectedLight == null) {
            return;
        }

        Camera cam = ctx.gameRenderer().getCamera();
        MatrixStack ms = ctx.matrices();
        if (cam == null || ms == null) {
            return;
        }

        Vec3d camPos = cam.getCameraPos();
        MatrixStack.Entry entry = ms.peek();

        RenderLayer lineLayer = RenderLayers.lines();
        RenderLayer quadLayer = RenderLayers.lightning();
        SequencedMap<RenderLayer, BufferAllocator> layerBuffers = new LinkedHashMap<>();
        layerBuffers.put(lineLayer, new BufferAllocator(4096));
        layerBuffers.put(quadLayer, new BufferAllocator(4096));

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(layerBuffers, new BufferAllocator(256));
        VertexConsumer lineBuf = immediate.getBuffer(lineLayer);
        VertexConsumer quadBuf = immediate.getBuffer(quadLayer);

        drawLightIndicators(lineBuf, entry, camPos, selectedLight);
        drawAxes(lineBuf, quadBuf, entry, camPos, selectedLight);
        immediate.draw();
    }

    private void rebuildOverlayMatrices(MinecraftClient client, Camera camera) {
        int fbWidth = Math.max(1, client.getWindow().getFramebufferWidth());
        int fbHeight = Math.max(1, client.getWindow().getFramebufferHeight());
        float aspect = (float) fbWidth / (float) fbHeight;
        double fovDeg = client.options.getFov().getValue();

        overlayView.identity()
            .rotateX((float) Math.toRadians(camera.getPitch()))
            .rotateY((float) Math.toRadians(camera.getYaw() + 180.0));
        overlayProj.identity()
            .perspective((float) Math.toRadians(fovDeg), aspect, 0.05f, 1000f);
    }

    private void drawBillboards(DrawContext context, Vec3d camPos, Collection<LightInstance> lights, boolean isSpot) {
        CLIcon icon = isSpot ? CalLightsIcons.SPOT_LIGHT : CalLightsIcons.POINT_LIGHT;
        if (icon == null) {
            return;
        }

        for (LightInstance light : lights) {
            if (!renderLightIcons && light != selectedLight) {
                continue;
            }
            ScreenPoint p = project(light.x, light.y, light.z);
            if (p == null) {
                continue;
            }

            double dist = camPos.distanceTo(new Vec3d(light.x, light.y, light.z));
            float base = light == selectedLight ? ICON_SELECTED_SIZE : ICON_BASE_SIZE;
            int size = Math.round(base + (float) Math.min(14.0, Math.sqrt(dist) * 1.7));
            size = Math.max(Math.round(base), Math.min(48, size));
            int x = Math.round(p.x) - size / 2;
            int y = Math.round(p.y) - size / 2;

            if (icon.staticTexture != null && icon.tintTexture != null) {
                int tintColor = (light == selectedLight)
                    ? 0xFFFFAA00
                    : (0xFF000000 | ((int) (light.r * 255) << 16) | ((int) (light.g * 255) << 8) | (int) (light.b * 255));
                drawTextureLayer(context, icon.tintTexture, icon.x, icon.y, icon.w, icon.h, x, y, size, size, tintColor);
                drawTextureLayer(context, icon.staticTexture, icon.x, icon.y, icon.w, icon.h, x, y, size, size, 0xFFFFFFFF);
            } else if (icon.texture != null) {
                int color = (light == selectedLight) ? 0xFFFFAA00 : 0xFFFFFFFF;
                drawTextureLayer(context, icon.texture, icon.x, icon.y, icon.w, icon.h, x, y, size, size, color);
            }
        }
    }

    private void drawTextureLayer(DrawContext context, CLTexture texture, int texX, int texY, int texW, int texH,
                                  int x, int y, int w, int h, int color) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture.identifier, x, y, texX, texY, w, h, texW, texH, texture.width, texture.height, color);
    }

    private void drawAxes(VertexConsumer lineBuf, VertexConsumer quadBuf, MatrixStack.Entry entry, Vec3d camPos, LightInstance light) {
        double dist = camPos.distanceTo(new Vec3d(light.x, light.y, light.z));
        float scale = (float) (0.48f * Math.max(0.55, dist * 0.13) * CalSettings.INSTANCE.gizmoSize);

        float sx = (camPos.x - light.x) >= 0 ? 1.0f : -1.0f;
        float sy = (camPos.y - light.y) >= 0 ? 1.0f : -1.0f;
        float sz = (camPos.z - light.z) >= 0 ? 1.0f : -1.0f;

        if (!dragging) {
            lastSx = sx;
            lastSy = sy;
            lastSz = sz;
        } else {
            sx = lastSx;
            sy = lastSy;
            sz = lastSz;
        }

        drawAxisBar(lineBuf, quadBuf, entry, camPos, light.x, light.y, light.z,
            light.x + 0.34f * scale * sx, light.y, light.z,
            0.015f * scale, colorForAxis(0, AXIS_X_COLOR));
        drawAxisBar(lineBuf, quadBuf, entry, camPos, light.x, light.y, light.z,
            light.x, light.y + 0.34f * scale * sy, light.z,
            0.015f * scale, colorForAxis(1, AXIS_Y_COLOR));
        drawAxisBar(lineBuf, quadBuf, entry, camPos, light.x, light.y, light.z,
            light.x, light.y, light.z + 0.34f * scale * sz,
            0.015f * scale, colorForAxis(2, AXIS_Z_COLOR));

        drawPlaneHandle(lineBuf, quadBuf, entry, camPos, light, scale, sx, sz, 3, 0xAA26FF99);
        drawPlaneHandle(lineBuf, quadBuf, entry, camPos, light, scale, sx, sy, 4, 0xAA9926FF);
        drawPlaneHandle(lineBuf, quadBuf, entry, camPos, light, scale, sz, sy, 5, 0xAAFF7A26);
        drawCenterCube(lineBuf, quadBuf, entry, camPos, light, 0.042f * scale, colorForAxis(6, FREE_COLOR));
    }

    private void drawPlaneHandle(VertexConsumer lineBuf, VertexConsumer quadBuf, MatrixStack.Entry entry, Vec3d camPos, LightInstance light, float scale, float sa, float sb, int plane, int color) {
        float inner = 0.095f * scale;
        float outer = 0.205f * scale;
        int c = colorForAxis(plane, color);

        if (plane == 3) { // XZ
            quadWorldDoubleSided(quadBuf, entry, camPos,
                light.x + inner * sa, light.y, light.z + inner * sb,
                light.x + outer * sa, light.y, light.z + inner * sb,
                light.x + outer * sa, light.y, light.z + outer * sb,
                light.x + inner * sa, light.y, light.z + outer * sb,
                c);
            lineWorld(lineBuf, entry, camPos, light.x + inner * sa, light.y, light.z + inner * sb, light.x + outer * sa, light.y, light.z + inner * sb, c);
            lineWorld(lineBuf, entry, camPos, light.x + outer * sa, light.y, light.z + inner * sb, light.x + outer * sa, light.y, light.z + outer * sb, c);
            lineWorld(lineBuf, entry, camPos, light.x + outer * sa, light.y, light.z + outer * sb, light.x + inner * sa, light.y, light.z + outer * sb, c);
            lineWorld(lineBuf, entry, camPos, light.x + inner * sa, light.y, light.z + outer * sb, light.x + inner * sa, light.y, light.z + inner * sb, c);
        } else if (plane == 4) { // XY
            quadWorldDoubleSided(quadBuf, entry, camPos,
                light.x + inner * sa, light.y + inner * sb, light.z,
                light.x + outer * sa, light.y + inner * sb, light.z,
                light.x + outer * sa, light.y + outer * sb, light.z,
                light.x + inner * sa, light.y + outer * sb, light.z,
                c);
            lineWorld(lineBuf, entry, camPos, light.x + inner * sa, light.y + inner * sb, light.z, light.x + outer * sa, light.y + inner * sb, light.z, c);
            lineWorld(lineBuf, entry, camPos, light.x + outer * sa, light.y + inner * sb, light.z, light.x + outer * sa, light.y + outer * sb, light.z, c);
            lineWorld(lineBuf, entry, camPos, light.x + outer * sa, light.y + outer * sb, light.z, light.x + inner * sa, light.y + outer * sb, light.z, c);
            lineWorld(lineBuf, entry, camPos, light.x + inner * sa, light.y + outer * sb, light.z, light.x + inner * sa, light.y + inner * sb, light.z, c);
        } else { // ZY
            quadWorldDoubleSided(quadBuf, entry, camPos,
                light.x, light.y + inner * sb, light.z + inner * sa,
                light.x, light.y + outer * sb, light.z + inner * sa,
                light.x, light.y + outer * sb, light.z + outer * sa,
                light.x, light.y + inner * sb, light.z + outer * sa,
                c);
            lineWorld(lineBuf, entry, camPos, light.x, light.y + inner * sb, light.z + inner * sa, light.x, light.y + outer * sb, light.z + inner * sa, c);
            lineWorld(lineBuf, entry, camPos, light.x, light.y + outer * sb, light.z + inner * sa, light.x, light.y + outer * sb, light.z + outer * sa, c);
            lineWorld(lineBuf, entry, camPos, light.x, light.y + outer * sb, light.z + outer * sa, light.x, light.y + inner * sb, light.z + outer * sa, c);
            lineWorld(lineBuf, entry, camPos, light.x, light.y + inner * sb, light.z + outer * sa, light.x, light.y + inner * sb, light.z + inner * sa, c);
        }
    }

    private void drawCenterCube(VertexConsumer lineBuf, VertexConsumer quadBuf, MatrixStack.Entry entry, Vec3d camPos, LightInstance light, float half, int color) {
        double x = light.x;
        double y = light.y;
        double z = light.z;
        int fill = (color & 0x00FFFFFF) | 0x44FFFFFF;
        quadWorldDoubleSided(quadBuf, entry, camPos, x - half, y - half, z - half, x + half, y - half, z - half, x + half, y + half, z - half, x - half, y + half, z - half, fill);
        quadWorldDoubleSided(quadBuf, entry, camPos, x - half, y - half, z + half, x + half, y - half, z + half, x + half, y + half, z + half, x - half, y + half, z + half, fill);
        quadWorldDoubleSided(quadBuf, entry, camPos, x - half, y - half, z - half, x - half, y - half, z + half, x - half, y + half, z + half, x - half, y + half, z - half, fill);
        quadWorldDoubleSided(quadBuf, entry, camPos, x + half, y - half, z - half, x + half, y - half, z + half, x + half, y + half, z + half, x + half, y + half, z - half, fill);
        quadWorldDoubleSided(quadBuf, entry, camPos, x - half, y - half, z - half, x + half, y - half, z - half, x + half, y - half, z + half, x - half, y - half, z + half, fill);
        quadWorldDoubleSided(quadBuf, entry, camPos, x - half, y + half, z - half, x + half, y + half, z - half, x + half, y + half, z + half, x - half, y + half, z + half, fill);

        lineWorld(lineBuf, entry, camPos, x - half, y - half, z - half, x + half, y - half, z - half, color);
        lineWorld(lineBuf, entry, camPos, x + half, y - half, z - half, x + half, y - half, z + half, color);
        lineWorld(lineBuf, entry, camPos, x + half, y - half, z + half, x - half, y - half, z + half, color);
        lineWorld(lineBuf, entry, camPos, x - half, y - half, z + half, x - half, y - half, z - half, color);
        lineWorld(lineBuf, entry, camPos, x - half, y + half, z - half, x + half, y + half, z - half, color);
        lineWorld(lineBuf, entry, camPos, x + half, y + half, z - half, x + half, y + half, z + half, color);
        lineWorld(lineBuf, entry, camPos, x + half, y + half, z + half, x - half, y + half, z + half, color);
        lineWorld(lineBuf, entry, camPos, x - half, y + half, z + half, x - half, y + half, z - half, color);
        lineWorld(lineBuf, entry, camPos, x - half, y - half, z - half, x - half, y + half, z - half, color);
        lineWorld(lineBuf, entry, camPos, x + half, y - half, z - half, x + half, y + half, z - half, color);
        lineWorld(lineBuf, entry, camPos, x + half, y - half, z + half, x + half, y + half, z + half, color);
        lineWorld(lineBuf, entry, camPos, x - half, y - half, z + half, x - half, y + half, z + half, color);
    }

    private int colorForAxis(int axis, int baseColor) {
        return activeAxis == axis ? ACTIVE_COLOR : baseColor;
    }

    private void drawLightIndicators(VertexConsumer buf, MatrixStack.Entry entry, Vec3d camPos, LightInstance light) {
        float r = light.r;
        float g = light.g;
        float b = light.b;
        float maxVal = Math.max(r, Math.max(g, b));
        if (maxVal < 0.2f) {
            r = 1.0f;
            g = 0.66f;
            b = 0.0f;
        }
        int color = argb(0.65f, r, g, b);

        if (light.isSpot) {
            drawSpotIndicator(buf, entry, camPos, light, color);
        } else {
            drawPointIndicator(buf, entry, camPos, light, color);
        }
    }

    private void drawPointIndicator(VertexConsumer buf, MatrixStack.Entry entry, Vec3d camPos, LightInstance light, int color) {
        float radius = light.radius;
        int segments = 64;
        for (int i = 0; i < segments; i++) {
            float a1 = (float) (2.0 * Math.PI * i / segments);
            float a2 = (float) (2.0 * Math.PI * (i + 1) / segments);
            lineWorld(buf, entry, camPos, light.x + radius * Math.cos(a1), light.y, light.z + radius * Math.sin(a1),
                light.x + radius * Math.cos(a2), light.y, light.z + radius * Math.sin(a2), color);
            lineWorld(buf, entry, camPos, light.x + radius * Math.cos(a1), light.y + radius * Math.sin(a1), light.z,
                light.x + radius * Math.cos(a2), light.y + radius * Math.sin(a2), light.z, color);
            lineWorld(buf, entry, camPos, light.x, light.y + radius * Math.cos(a1), light.z + radius * Math.sin(a1),
                light.x, light.y + radius * Math.cos(a2), light.z + radius * Math.sin(a2), color);
        }
    }

    private void drawSpotIndicator(VertexConsumer buf, MatrixStack.Entry entry, Vec3d camPos, LightInstance light, int color) {
        float dx = light.dx;
        float dy = light.dy;
        float dz = light.dz;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.0001f) {
            dx = 0f;
            dy = -1f;
            dz = 0f;
            len = 1f;
        }
        float ndx = dx / len;
        float ndy = dy / len;
        float ndz = dz / len;

        float wx = Math.abs(ndx) > 0.9f ? 0f : 1f;
        float wy = Math.abs(ndx) > 0.9f ? 1f : 0f;
        float wz = 0f;

        float ux = ndy * wz - ndz * wy;
        float uy = ndz * wx - ndx * wz;
        float uz = ndx * wy - ndy * wx;
        float uLen = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (uLen > 0.0001f) {
            ux /= uLen;
            uy /= uLen;
            uz /= uLen;
        }
        float vx = ndy * uz - ndz * uy;
        float vy = ndz * ux - ndx * uz;
        float vz = ndx * uy - ndy * ux;

        float dist = light.distance;
        float outerRad = (float) (dist * Math.tan(Math.toRadians(light.getOuterAngleDeg() * 0.5f)));
        float innerRad = (float) (dist * Math.tan(Math.toRadians(light.getInnerAngleDeg() * 0.5f)));
        float cx = light.x + ndx * dist;
        float cy = light.y + ndy * dist;
        float cz = light.z + ndz * dist;

        int segments = 64;
        for (int i = 0; i < segments; i++) {
            float a1 = (float) (2.0 * Math.PI * i / segments);
            float a2 = (float) (2.0 * Math.PI * (i + 1) / segments);
            float cos1 = (float) Math.cos(a1);
            float sin1 = (float) Math.sin(a1);
            float cos2 = (float) Math.cos(a2);
            float sin2 = (float) Math.sin(a2);

            float p1x = cx + outerRad * (cos1 * ux + sin1 * vx);
            float p1y = cy + outerRad * (cos1 * uy + sin1 * vy);
            float p1z = cz + outerRad * (cos1 * uz + sin1 * vz);
            float p2x = cx + outerRad * (cos2 * ux + sin2 * vx);
            float p2y = cy + outerRad * (cos2 * uy + sin2 * vy);
            float p2z = cz + outerRad * (cos2 * uz + sin2 * vz);
            lineWorld(buf, entry, camPos, p1x, p1y, p1z, p2x, p2y, p2z, color);
        }

        if (light.soft > 0.1f) {
            int innerColor = (color & 0x00FFFFFF) | 0x66000000;
            for (int i = 0; i < segments; i++) {
                float a1 = (float) (2.0 * Math.PI * i / segments);
                float a2 = (float) (2.0 * Math.PI * (i + 1) / segments);
                float cos1 = (float) Math.cos(a1);
                float sin1 = (float) Math.sin(a1);
                float cos2 = (float) Math.cos(a2);
                float sin2 = (float) Math.sin(a2);
                float p1x = cx + innerRad * (cos1 * ux + sin1 * vx);
                float p1y = cy + innerRad * (cos1 * uy + sin1 * vy);
                float p1z = cz + innerRad * (cos1 * uz + sin1 * vz);
                float p2x = cx + innerRad * (cos2 * ux + sin2 * vx);
                float p2y = cy + innerRad * (cos2 * uy + sin2 * vy);
                float p2z = cz + innerRad * (cos2 * uz + sin2 * vz);
                lineWorld(buf, entry, camPos, p1x, p1y, p1z, p2x, p2y, p2z, innerColor);
            }
        }

        float[] angles = {0f, (float) (Math.PI / 2.0), (float) Math.PI, (float) (3.0 * Math.PI / 2.0)};
        for (float angle : angles) {
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            float px = cx + outerRad * (cos * ux + sin * vx);
            float py = cy + outerRad * (cos * uy + sin * vy);
            float pz = cz + outerRad * (cos * uz + sin * vz);
            lineWorld(buf, entry, camPos, light.x, light.y, light.z, px, py, pz, color);
        }
    }

    private void lineWorld(VertexConsumer buf, MatrixStack.Entry entry, Vec3d camPos,
                           double x1, double y1, double z1, double x2, double y2, double z2, int color) {
        float ax = (float) (x1 - camPos.x);
        float ay = (float) (y1 - camPos.y);
        float az = (float) (z1 - camPos.z);
        float bx = (float) (x2 - camPos.x);
        float by = (float) (y2 - camPos.y);
        float bz = (float) (z2 - camPos.z);

        float nx = bx - ax;
        float ny = by - ay;
        float nz = bz - az;
        float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1e-5f) {
            nx = 0f;
            ny = 1f;
            nz = 0f;
        } else {
            nx /= nl;
            ny /= nl;
            nz /= nl;
        }

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >>> 24) & 0xFF) / 255f;

        buf.vertex(entry.getPositionMatrix(), ax, ay, az).color(r, g, b, a).normal(entry, nx, ny, nz).lineWidth(LINE_WIDTH);
        buf.vertex(entry.getPositionMatrix(), bx, by, bz).color(r, g, b, a).normal(entry, nx, ny, nz).lineWidth(LINE_WIDTH);
    }

    private void drawAxisBar(VertexConsumer lineBuf, VertexConsumer quadBuf, MatrixStack.Entry entry, Vec3d camPos,
                             double x1, double y1, double z1, double x2, double y2, double z2, float halfWidth, int color) {
        lineWorld(lineBuf, entry, camPos, x1, y1, z1, x2, y2, z2, color);

        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-5) {
            return;
        }

        double nx = dx / len;
        double ny = dy / len;
        double nz = dz / len;

        double ax = Math.abs(nx);
        double ay = Math.abs(ny);
        double az = Math.abs(nz);

        if (ax >= ay && ax >= az) {
            drawBoxWorld(lineBuf, quadBuf, entry, camPos, x1, y1 - halfWidth, z1 - halfWidth, x2, y2 + halfWidth, z2 + halfWidth, color);
        } else if (ay >= ax && ay >= az) {
            drawBoxWorld(lineBuf, quadBuf, entry, camPos, x1 - halfWidth, y1, z1 - halfWidth, x2 + halfWidth, y2, z2 + halfWidth, color);
        } else {
            drawBoxWorld(lineBuf, quadBuf, entry, camPos, x1 - halfWidth, y1 - halfWidth, z1, x2 + halfWidth, y2 + halfWidth, z2, color);
        }
    }

    private void drawBoxWorld(VertexConsumer lineBuf, VertexConsumer quadBuf, MatrixStack.Entry entry, Vec3d camPos,
                              double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int edgeColor) {
        int fillColor = (edgeColor & 0x00FFFFFF) | 0x38FFFFFF;
        quadWorldDoubleSided(quadBuf, entry, camPos, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, fillColor);
        quadWorldDoubleSided(quadBuf, entry, camPos, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, fillColor);
        quadWorldDoubleSided(quadBuf, entry, camPos, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, fillColor);
        quadWorldDoubleSided(quadBuf, entry, camPos, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, fillColor);
        quadWorldDoubleSided(quadBuf, entry, camPos, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, fillColor);
        quadWorldDoubleSided(quadBuf, entry, camPos, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ, fillColor);

        lineWorld(lineBuf, entry, camPos, minX, minY, minZ, maxX, minY, minZ, edgeColor);
        lineWorld(lineBuf, entry, camPos, maxX, minY, minZ, maxX, minY, maxZ, edgeColor);
        lineWorld(lineBuf, entry, camPos, maxX, minY, maxZ, minX, minY, maxZ, edgeColor);
        lineWorld(lineBuf, entry, camPos, minX, minY, maxZ, minX, minY, minZ, edgeColor);
        lineWorld(lineBuf, entry, camPos, minX, maxY, minZ, maxX, maxY, minZ, edgeColor);
        lineWorld(lineBuf, entry, camPos, maxX, maxY, minZ, maxX, maxY, maxZ, edgeColor);
        lineWorld(lineBuf, entry, camPos, maxX, maxY, maxZ, minX, maxY, maxZ, edgeColor);
        lineWorld(lineBuf, entry, camPos, minX, maxY, maxZ, minX, maxY, minZ, edgeColor);
        lineWorld(lineBuf, entry, camPos, minX, minY, minZ, minX, maxY, minZ, edgeColor);
        lineWorld(lineBuf, entry, camPos, maxX, minY, minZ, maxX, maxY, minZ, edgeColor);
        lineWorld(lineBuf, entry, camPos, maxX, minY, maxZ, maxX, maxY, maxZ, edgeColor);
        lineWorld(lineBuf, entry, camPos, minX, minY, maxZ, minX, maxY, maxZ, edgeColor);
    }

    private void quadWorld(VertexConsumer buf, MatrixStack.Entry entry, Vec3d camPos,
                           double x1, double y1, double z1,
                           double x2, double y2, double z2,
                           double x3, double y3, double z3,
                           double x4, double y4, double z4,
                           int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >>> 24) & 0xFF) / 255f;
        vertexColor(buf, entry, camPos, x1, y1, z1, r, g, b, a);
        vertexColor(buf, entry, camPos, x2, y2, z2, r, g, b, a);
        vertexColor(buf, entry, camPos, x3, y3, z3, r, g, b, a);
        vertexColor(buf, entry, camPos, x4, y4, z4, r, g, b, a);
    }

    private void quadWorldDoubleSided(VertexConsumer buf, MatrixStack.Entry entry, Vec3d camPos,
                                      double x1, double y1, double z1,
                                      double x2, double y2, double z2,
                                      double x3, double y3, double z3,
                                      double x4, double y4, double z4,
                                      int color) {
        quadWorld(buf, entry, camPos, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, color);
    }

    private void vertexColor(VertexConsumer buf, MatrixStack.Entry entry, Vec3d camPos,
                             double x, double y, double z, float r, float g, float b, float a) {
        buf.vertex(entry.getPositionMatrix(), (float) (x - camPos.x), (float) (y - camPos.y), (float) (z - camPos.z))
            .color(r, g, b, a);
    }

    private ScreenPoint project(float wx, float wy, float wz) {
        MinecraftClient client = MinecraftClient.getInstance();
        Camera camera = client.gameRenderer == null ? null : client.gameRenderer.getCamera();
        if (camera == null) {
            return null;
        }

        Vec3d cp = camera.getCameraPos();
        Vector4f clip = new Vector4f((float) (wx - cp.x), (float) (wy - cp.y), (float) (wz - cp.z), 1.0f);
        clip.mul(overlayView).mul(overlayProj);
        if (clip.w <= 0.0f) {
            return null;
        }

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        float screenX = (ndcX * 0.5f + 0.5f) * client.getWindow().getScaledWidth();
        float screenY = (1.0f - (ndcY * 0.5f + 0.5f)) * client.getWindow().getScaledHeight();
        return new ScreenPoint(screenX, screenY);
    }

    private static int argb(float a, float r, float g, float b) {
        int ai = Math.max(0, Math.min(255, Math.round(a * 255f)));
        int ri = Math.max(0, Math.min(255, Math.round(r * 255f)));
        int gi = Math.max(0, Math.min(255, Math.round(g * 255f)));
        int bi = Math.max(0, Math.min(255, Math.round(b * 255f)));
        return (ai << 24) | (ri << 16) | (gi << 8) | bi;
    }

    public boolean onMouseClicked(double mouseX, double mouseY, int btn) {
        if (btn != 0) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return false;
        }

        Camera camera = client.gameRenderer.getCamera();
        Vec3d rayDir = getRayDirection(mouseX, mouseY);
        Vec3d rayStart = camera.getCameraPos();

        if (selectedLight != null) {
            int clickedAxis = checkAxisClickLocal(rayStart, rayDir, selectedLight);
            if (clickedAxis != -1) {
                activeAxis = clickedAxis;
                CALUndoManager.pushState();
                dragging = true;
                dragStartLightPos = new Vec3d(selectedLight.x, selectedLight.y, selectedLight.z);

                if (activeAxis >= 0 && activeAxis <= 2) {
                    dragStartMousePos = getMouseProjectionOnAxis(rayStart, rayDir, dragStartLightPos, activeAxis);
                } else if (activeAxis >= 3 && activeAxis <= 5) {
                    dragStartMousePos = getMouseProjectionOnPlane(rayStart, rayDir, dragStartLightPos, activeAxis);
                } else if (activeAxis == 6) {
                    Vec3d camDir = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
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
            MinecraftClient client = MinecraftClient.getInstance();
            Camera camera = client.gameRenderer.getCamera();
            Vec3d rayDir = getRayDirection(mouseX, mouseY);
            Vec3d rayStart = camera.getCameraPos();

            Vec3d currentProj = null;
            if (activeAxis >= 0 && activeAxis <= 2) {
                currentProj = getMouseProjectionOnAxis(rayStart, rayDir, dragStartLightPos, activeAxis);
            } else if (activeAxis >= 3 && activeAxis <= 5) {
                currentProj = getMouseProjectionOnPlane(rayStart, rayDir, dragStartLightPos, activeAxis);
            } else if (activeAxis == 6) {
                Vec3d camDir = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
                currentProj = getMouseProjectionFree(rayStart, rayDir, dragStartLightPos, camDir);
            }

            if (currentProj != null && dragStartMousePos != null) {
                Vec3d delta = currentProj.subtract(dragStartMousePos);
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
                } else if (activeAxis == 3) {
                    selectedLight.x = nx;
                    selectedLight.z = nz;
                } else if (activeAxis == 4) {
                    selectedLight.x = nx;
                    selectedLight.y = ny;
                } else if (activeAxis == 5) {
                    selectedLight.z = nz;
                    selectedLight.y = ny;
                } else if (activeAxis == 6) {
                    selectedLight.x = nx;
                    selectedLight.y = ny;
                    selectedLight.z = nz;
                }
            }
            return true;
        }
        return false;
    }

    public Vec3d getRayDirection(double mouseX, double mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        Camera camera = client.gameRenderer == null ? null : client.gameRenderer.getCamera();
        if (camera == null) {
            return Vec3d.ZERO;
        }

        rebuildOverlayMatrices(client, camera);

        float x = (2.0f * (float) mouseX) / width - 1.0f;
        float y = 1.0f - (2.0f * (float) mouseY) / height;

        Matrix4f invProj = new Matrix4f(overlayProj).invert();
        Vector4f rayClip = new Vector4f(x, y, -1.0f, 1.0f);
        rayClip.mul(invProj);

        Vector3f rayEye = new Vector3f(rayClip.x / rayClip.w, rayClip.y / rayClip.w, rayClip.z / rayClip.w);
        Matrix4f invView = new Matrix4f(overlayView).invert();
        Vector4f rayWorld4 = new Vector4f(rayEye.x, rayEye.y, rayEye.z, 0.0f);
        rayWorld4.mul(invView);

        Vector3f rayWorld = new Vector3f(rayWorld4.x, rayWorld4.y, rayWorld4.z).normalize();
        return new Vec3d(rayWorld.x, rayWorld.y, rayWorld.z);
    }

    public LightInstance checkBillboardClickExternal(Vec3d rayStart, Vec3d rayDir) {
        return checkBillboardClick(rayStart, rayDir);
    }

    private LightInstance checkBillboardClick(Vec3d rayStart, Vec3d rayDir) {
        LightInstance bestMatch = null;
        double bestDist = Double.MAX_VALUE;

        Collection<LightInstance> allLights = LightManager.INSTANCE.getPointLights();
        for (LightInstance light : allLights) {
            Vec3d pos = new Vec3d(light.x, light.y, light.z);
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
            Vec3d pos = new Vec3d(light.x, light.y, light.z);
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

    private int checkAxisClickLocal(Vec3d rayStart, Vec3d rayDir, LightInstance light) {
        Vec3d pos = new Vec3d(light.x, light.y, light.z);
        double dist = rayStart.distanceTo(pos);
        double scale = 0.4 * Math.max(0.5, dist * 0.12) * CalSettings.INSTANCE.gizmoSize;

        Vec3d localRayStart = rayStart.subtract(pos).multiply(1.0 / scale);
        Vec3d localRayDir = rayDir;

        float sx = this.lastSx;
        float sy = this.lastSy;
        float sz = this.lastSz;

        if (intersectAABB(localRayStart, localRayDir, -0.04, -0.04, -0.04, 0.04, 0.04, 0.04)) {
            return 6;
        }

        if (Math.abs(localRayDir.y) > 0.0001) {
            double t = -localRayStart.y / localRayDir.y;
            if (t >= 0) {
                Vec3d inter = localRayStart.add(localRayDir.multiply(t));
                double minX = Math.min(0.06 * sx, 0.24 * sx);
                double maxX = Math.max(0.06 * sx, 0.24 * sx);
                double minZ = Math.min(0.06 * sz, 0.24 * sz);
                double maxZ = Math.max(0.06 * sz, 0.24 * sz);
                if (inter.x >= minX && inter.x <= maxX && inter.z >= minZ && inter.z <= maxZ) {
                    return 3;
                }
            }
        }

        if (Math.abs(localRayDir.z) > 0.0001) {
            double t = -localRayStart.z / localRayDir.z;
            if (t >= 0) {
                Vec3d inter = localRayStart.add(localRayDir.multiply(t));
                double minX = Math.min(0.06 * sx, 0.24 * sx);
                double maxX = Math.max(0.06 * sx, 0.24 * sx);
                double minY = Math.min(0.06 * sy, 0.24 * sy);
                double maxY = Math.max(0.06 * sy, 0.24 * sy);
                if (inter.x >= minX && inter.x <= maxX && inter.y >= minY && inter.y <= maxY) {
                    return 4;
                }
            }
        }

        if (Math.abs(localRayDir.x) > 0.0001) {
            double t = -localRayStart.x / localRayDir.x;
            if (t >= 0) {
                Vec3d inter = localRayStart.add(localRayDir.multiply(t));
                double minZ = Math.min(0.06 * sz, 0.24 * sz);
                double maxZ = Math.max(0.06 * sz, 0.24 * sz);
                double minY = Math.min(0.06 * sy, 0.24 * sy);
                double maxY = Math.max(0.06 * sy, 0.24 * sy);
                if (inter.z >= minZ && inter.z <= maxZ && inter.y >= minY && inter.y <= maxY) {
                    return 5;
                }
            }
        }

        double dX = getDistanceToSegment(localRayStart, localRayDir, new Vec3d(0, 0, 0), new Vec3d(0.35 * sx, 0, 0));
        double dY = getDistanceToSegment(localRayStart, localRayDir, new Vec3d(0, 0, 0), new Vec3d(0, 0.35 * sy, 0));
        double dZ = getDistanceToSegment(localRayStart, localRayDir, new Vec3d(0, 0, 0), new Vec3d(0, 0, 0.35 * sz));

        double threshold = 0.04;
        if (dX < threshold && dX < dY && dX < dZ) return 0;
        if (dY < threshold && dY < dX && dY < dZ) return 1;
        if (dZ < threshold && dZ < dX && dZ < dY) return 2;

        return -1;
    }

    private boolean intersectAABB(Vec3d rayStart, Vec3d rayDir, double minX, double minY, double minZ, double maxX,
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

    private Vec3d getMouseProjectionOnAxis(Vec3d rayStart, Vec3d rayDir, Vec3d origin, int axis) {
        Vec3d axisDir = new Vec3d(axis == 0 ? 1 : 0, axis == 1 ? 1 : 0, axis == 2 ? 1 : 0);

        Vec3d p12 = rayStart.subtract(origin);
        double v1v1 = rayDir.dotProduct(rayDir);
        double v1v2 = rayDir.dotProduct(axisDir);
        double v2v2 = axisDir.dotProduct(axisDir);
        double p12v1 = p12.dotProduct(rayDir);
        double p12v2 = p12.dotProduct(axisDir);

        double denom = v1v1 * v2v2 - v1v2 * v1v2;
        if (Math.abs(denom) < 0.0001) {
            return origin;
        }

        double t2 = (p12v2 * v1v1 - p12v1 * v1v2) / denom;
        return origin.add(axisDir.multiply(t2));
    }

    private Vec3d getMouseProjectionOnPlane(Vec3d rayStart, Vec3d rayDir, Vec3d origin, int planeIndex) {
        double t;
        if (planeIndex == 3) {
            if (Math.abs(rayDir.y) < 0.0001) return origin;
            t = (origin.y - rayStart.y) / rayDir.y;
        } else if (planeIndex == 4) {
            if (Math.abs(rayDir.z) < 0.0001) return origin;
            t = (origin.z - rayStart.z) / rayDir.z;
        } else {
            if (Math.abs(rayDir.x) < 0.0001) return origin;
            t = (origin.x - rayStart.x) / rayDir.x;
        }
        return rayStart.add(rayDir.multiply(t));
    }

    private Vec3d getMouseProjectionFree(Vec3d rayStart, Vec3d rayDir, Vec3d origin, Vec3d normal) {
        double denom = rayDir.dotProduct(normal);
        if (Math.abs(denom) < 0.0001) {
            return origin;
        }
        double t = origin.subtract(rayStart).dotProduct(normal) / denom;
        return rayStart.add(rayDir.multiply(t));
    }

    private double getDistanceToPoint(Vec3d rayStart, Vec3d rayDir, Vec3d point) {
        Vec3d toPoint = point.subtract(rayStart);
        double projection = toPoint.dotProduct(rayDir);
        if (projection < 0) {
            return Double.MAX_VALUE;
        }
        Vec3d closestPoint = rayStart.add(rayDir.multiply(projection));
        return closestPoint.distanceTo(point);
    }

    private double getDistanceToSegment(Vec3d rayStart, Vec3d rayDir, Vec3d s1, Vec3d s2) {
        Vec3d u = rayDir;
        Vec3d v = s2.subtract(s1);
        Vec3d w = rayStart.subtract(s1);

        double a = u.dotProduct(u);
        double b = u.dotProduct(v);
        double c = v.dotProduct(v);
        double d = u.dotProduct(w);
        double e = v.dotProduct(w);

        double denom = a * c - b * b;
        double tRatio = 0.0;
        if (Math.abs(denom) > 0.00001) {
            tRatio = (a * e - b * d) / denom;
        }
        tRatio = Math.max(0.0, Math.min(1.0, tRatio));

        Vec3d closestOnSegment = s1.add(v.multiply(tRatio));
        return getDistanceToPoint(rayStart, rayDir, closestOnSegment);
    }

    private record ScreenPoint(float x, float y) {}
}
