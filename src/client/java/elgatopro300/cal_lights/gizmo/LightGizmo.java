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
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import org.lwjgl.opengl.GL11;

import java.util.Collection;

public class LightGizmo {
    public static final LightGizmo INSTANCE = new LightGizmo();

    private LightInstance selectedLight = null;
    private int activeAxis = -1; // -1: none, 0: X, 1: Y, 2: Z, 3: XZ, 4: XY, 5: ZY, 6: FREE
    private boolean dragging = false;
    private Vec3d dragStartMousePos = null;
    private Vec3d dragStartLightPos = null;
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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null)
            return;
        if (!(client.currentScreen instanceof CALEditorScreen)) {
            return;
        }

        Camera camera = client.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();

        // Compute projection matrix for picking (world-view matrices are not
        // exposed via RenderSystem in 1.21.11, so we reconstruct it from FOV).
        computeProjectionMatrix(client, camera);

        // Draw billboards and gizmos using the world renderer's consumer
        // provider and matrix stack.  In 1.21.11 there is no immediate-mode
        // rendering that shader packs can discard — geometry goes through the
        // deferred command queue — so we draw directly during END_MAIN.
        VertexConsumerProvider consumers = context.consumers();
        MatrixStack stack = context.matrices();

        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        Collection<LightInstance> points = LightManager.INSTANCE.getPointLights();
        Collection<LightInstance> spots = LightManager.INSTANCE.getSpotLights();

        drawBillboards(stack, camPos, consumers, points, false);
        drawBillboards(stack, camPos, consumers, spots, true);

        if (selectedLight != null) {
            drawLightIndicators(stack, camPos, consumers, selectedLight);
            drawAxes(stack, camPos, consumers, selectedLight);
        }

        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    private void computeProjectionMatrix(MinecraftClient client, Camera camera) {
        double fov = client.options.getFov().getValue();
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        float aspect = (float) width / (float) height;
        this.projectionMatrix.setPerspective((float) Math.toRadians(fov), aspect, 0.05f, 1000.0f);
    }

    private void drawBillboards(MatrixStack stack, Vec3d camPos, VertexConsumerProvider consumers, Collection<LightInstance> lights, boolean isSpot) {
        CLIcon icon = isSpot ? CalLightsIcons.SPOT_LIGHT : CalLightsIcons.POINT_LIGHT;
        if (icon == null)
            return;

        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        if (camera == null) {
            return;
        }
        Quaternionf camRot = camera.getRotation();

        for (LightInstance light : lights) {
            if (!renderLightIcons && light != selectedLight) {
                continue;
            }
            stack.push();
            stack.translate(light.x - camPos.x, light.y - camPos.y, light.z - camPos.z);
            stack.multiply(camRot);

            // Render a square billboard scaling with distance for comfortable
            // viewing/clicking
            double dist = camPos.distanceTo(new Vec3d(light.x, light.y, light.z));
            float size = (float) (0.3f * Math.max(1.0, dist * 0.15));
            if (light == selectedLight) {
                size = (float) (0.4f * Math.max(1.0, dist * 0.15)); // larger if selected
            }

            if (icon.staticTexture != null && icon.tintTexture != null) {
                int tintColor = (light == selectedLight) ? 0xFFFFAA00 : (0xFF000000 | ((int) (light.r * 255) << 16) | ((int) (light.g * 255) << 8) | (int) (light.b * 255));
                
                drawBillboardQuad(stack, consumers.getBuffer(RenderLayers.entityTranslucent(icon.tintTexture.identifier)), size, icon.tintTexture, icon.x, icon.y, icon.w, icon.h, tintColor);
                drawBillboardQuad(stack, consumers.getBuffer(RenderLayers.entityTranslucent(icon.staticTexture.identifier)), size, icon.staticTexture, icon.x, icon.y, icon.w, icon.h, 0xFFFFFFFF);
            } else if (icon.texture != null) {
                int color = (light == selectedLight) ? 0xFFFFAA00 : 0xFFFFFFFF;
                drawBillboardQuad(stack, consumers.getBuffer(RenderLayers.entityTranslucent(icon.texture.identifier)), size, icon.texture, icon.x, icon.y, icon.w, icon.h, color);
            }
            stack.pop();
        }
    }

    private void drawBillboardQuad(MatrixStack stack, VertexConsumer buf, float size, CLTexture texture, int texX, int texY, int texW, int texH, int color) {
        MatrixStack.Entry entry = stack.peek();
        Matrix4f matrix = entry.getPositionMatrix();

        float u1 = texX / (float) texture.width;
        float v1 = texY / (float) texture.height;
        float u2 = (texX + texW) / (float) texture.width;
        float v2 = (texY + texH) / (float) texture.height;

        float a = ((color >>> 24) & 0xFF) / 255.0f;
        float r = ((color >>> 16) & 0xFF) / 255.0f;
        float g = ((color >>> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        int overlay = OverlayTexture.DEFAULT_UV;

        buf.vertex(matrix, -size, -size, 0).color(r, g, b, a).texture(u1, v2).overlay(overlay).light(light).normal(entry, 0f, 0f, 1f);
        buf.vertex(matrix, size, -size, 0).color(r, g, b, a).texture(u2, v2).overlay(overlay).light(light).normal(entry, 0f, 0f, 1f);
        buf.vertex(matrix, size, size, 0).color(r, g, b, a).texture(u2, v1).overlay(overlay).light(light).normal(entry, 0f, 0f, 1f);
        buf.vertex(matrix, -size, size, 0).color(r, g, b, a).texture(u1, v1).overlay(overlay).light(light).normal(entry, 0f, 0f, 1f);
    }

    private void drawAxes(MatrixStack stack, Vec3d camPos, VertexConsumerProvider consumers, LightInstance light) {
        stack.push();
        stack.translate(light.x - camPos.x, light.y - camPos.y, light.z - camPos.z);

        double dist = camPos.distanceTo(new Vec3d(light.x, light.y, light.z));
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

        float[] xCol = (activeAxis == 0) ? new float[] { 1.0f, 1.0f, 0.0f } : new float[] { 1.0f, 0.15f, 0.15f };
        float[] yCol = (activeAxis == 1) ? new float[] { 1.0f, 1.0f, 0.0f } : new float[] { 0.15f, 1.0f, 0.15f };
        float[] zCol = (activeAxis == 2) ? new float[] { 1.0f, 1.0f, 0.0f } : new float[] { 0.15f, 0.35f, 1.0f };

        float axisLen = 0.35f;
        float width = 2.0f;
        float a = 1.0f;

        VertexConsumer buf = consumers.getBuffer(RenderLayers.linesTranslucent());
        MatrixStack.Entry e = stack.peek();

        line(buf, e, 0f, 0f, 0f, axisLen * sx, 0f, 0f, xCol[0], xCol[1], xCol[2], a, width);
        line(buf, e, 0f, 0f, 0f, 0f, axisLen * sy, 0f, yCol[0], yCol[1], yCol[2], a, width);
        line(buf, e, 0f, 0f, 0f, 0f, 0f, axisLen * sz, zCol[0], zCol[1], zCol[2], a, width);

        stack.pop();
    }

    private void drawLightIndicators(MatrixStack stack, Vec3d camPos, VertexConsumerProvider consumers, LightInstance light) {
        stack.push();
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

        float width = 2.0f;
        float a = 0.75f;

        VertexConsumer buf = consumers.getBuffer(RenderLayers.linesTranslucent());
        MatrixStack.Entry e = stack.peek();

        if (light.isSpot) {
            drawSpotGuide(buf, e, light, r, g, b, a, width);
        } else {
            drawPointGuide(buf, e, light.radius, r, g, b, a, width);
        }

        stack.pop();
    }

    private static void drawPointGuide(VertexConsumer buf, MatrixStack.Entry e, float radius, float r, float g, float b, float a, float width) {
        float rr = Math.max(0.5f, Math.min(radius, 16.0f));
        int seg = 48;

        float px = rr, pz = 0f;
        for (int i = 1; i <= seg; i++) {
            double ang = (Math.PI * 2.0) * i / seg;
            float x = rr * (float) Math.cos(ang);
            float z = rr * (float) Math.sin(ang);
            line(buf, e, px, 0f, pz, x, 0f, z, r, g, b, a, width);
            px = x;
            pz = z;
        }
    }

    private static void drawSpotGuide(VertexConsumer buf, MatrixStack.Entry e, LightInstance light, float r, float g, float b, float a, float width) {
        float dx = light.dx, dy = light.dy, dz = light.dz;
        float dlen = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dlen < 1e-4f) {
            dx = 0f;
            dy = -1f;
            dz = 0f;
            dlen = 1f;
        }
        dx /= dlen;
        dy /= dlen;
        dz /= dlen;

        float len = Math.max(1f, Math.min(light.distance, 32f));
        float radius = (float) (len * Math.tan(Math.toRadians(light.getOuterAngleDeg() * 0.5f)));

        float ex = dx * len;
        float ey = dy * len;
        float ez = dz * len;

        float rx, ry, rz;
        if (Math.abs(dy) < 0.99f) { rx = 0f; ry = 1f; rz = 0f; }
        else { rx = 1f; ry = 0f; rz = 0f; }
        float ux = dy * rz - dz * ry, uy = dz * rx - dx * rz, uz = dx * ry - dy * rx;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul; uy /= ul; uz /= ul;
        float vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;

        line(buf, e, 0f, 0f, 0f, ex, ey, ez, r, g, b, a, width);

        int seg = 28;
        float px = 0f, py = 0f, pz = 0f;
        for (int i = 0; i <= seg; i++) {
            double ang = (Math.PI * 2.0) * i / seg;
            float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
            float qx = ex + (ux * cos + vx * sin) * radius;
            float qy = ey + (uy * cos + vy * sin) * radius;
            float qz = ez + (uz * cos + vz * sin) * radius;

            if (i > 0) {
                line(buf, e, px, py, pz, qx, qy, qz, r, g, b, a, width);
            }
            px = qx;
            py = qy;
            pz = qz;
        }
    }

    private static void line(VertexConsumer buf, MatrixStack.Entry e, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a, float width) {
        float nx = x2 - x1, ny = y2 - y1, nz = z2 - z1;
        float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1e-5f) {
            nx = 0f; ny = 1f; nz = 0f;
        } else {
            nx /= nl; ny /= nl; nz /= nl;
        }
        buf.vertex(e.getPositionMatrix(), x1, y1, z1).color(r, g, b, a).normal(e, nx, ny, nz).lineWidth(width);
        buf.vertex(e.getPositionMatrix(), x2, y2, z2).color(r, g, b, a).normal(e, nx, ny, nz).lineWidth(width);
    }

    private void drawPointIndicator(BufferBuilder builder, MatrixStack stack, LightInstance light, float r, float g, float b, float a) {
        float radius = light.radius;
        int segments = 64;
        Matrix4f matrix = stack.peek().getPositionMatrix();

        // 1. Horizontal circle (XZ plane)
        for (int i = 0; i < segments; i++) {
            float angle1 = (float) (2.0 * Math.PI * i / segments);
            float angle2 = (float) (2.0 * Math.PI * (i + 1) / segments);

            float x1 = radius * (float) Math.cos(angle1);
            float z1 = radius * (float) Math.sin(angle1);
            float x2 = radius * (float) Math.cos(angle2);
            float z2 = radius * (float) Math.sin(angle2);

            builder.vertex(matrix, x1, 0, z1).color(r, g, b, a);
            builder.vertex(matrix, x2, 0, z2).color(r, g, b, a);
        }

        // 2. Vertical circle (XY plane)
        for (int i = 0; i < segments; i++) {
            float angle1 = (float) (2.0 * Math.PI * i / segments);
            float angle2 = (float) (2.0 * Math.PI * (i + 1) / segments);

            float x1 = radius * (float) Math.cos(angle1);
            float y1 = radius * (float) Math.sin(angle1);
            float x2 = radius * (float) Math.cos(angle2);
            float y2 = radius * (float) Math.sin(angle2);

            builder.vertex(matrix, x1, y1, 0).color(r, g, b, a);
            builder.vertex(matrix, x2, y2, 0).color(r, g, b, a);
        }

        // 3. Vertical circle (YZ plane)
        for (int i = 0; i < segments; i++) {
            float angle1 = (float) (2.0 * Math.PI * i / segments);
            float angle2 = (float) (2.0 * Math.PI * (i + 1) / segments);

            float y1 = radius * (float) Math.cos(angle1);
            float z1 = radius * (float) Math.sin(angle1);
            float y2 = radius * (float) Math.cos(angle2);
            float z2 = radius * (float) Math.sin(angle2);

            builder.vertex(matrix, 0, y1, z1).color(r, g, b, a);
            builder.vertex(matrix, 0, y2, z2).color(r, g, b, a);
        }
    }

    private void drawSpotIndicator(BufferBuilder builder, MatrixStack stack, LightInstance light, float r, float g, float b, float a) {
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

        float wx = 1f;
        float wy = 0f;
        float wz = 0f;
        if (Math.abs(ndx) > 0.9f) {
            wx = 0f;
            wy = 1f;
            wz = 0f;
        }

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

        float cx = ndx * dist;
        float cy = ndy * dist;
        float cz = ndz * dist;

        Matrix4f matrix = stack.peek().getPositionMatrix();
        int segments = 64;

        // 1. Draw Outer Base Circle
        for (int i = 0; i < segments; i++) {
            float angle1 = (float) (2.0 * Math.PI * i / segments);
            float angle2 = (float) (2.0 * Math.PI * (i + 1) / segments);

            float cos1 = (float) Math.cos(angle1);
            float sin1 = (float) Math.sin(angle1);
            float cos2 = (float) Math.cos(angle2);
            float sin2 = (float) Math.sin(angle2);

            float p1x = cx + outerRad * (cos1 * ux + sin1 * vx);
            float p1y = cy + outerRad * (cos1 * uy + sin1 * vy);
            float p1z = cz + outerRad * (cos1 * uz + sin1 * vz);

            float p2x = cx + outerRad * (cos2 * ux + sin2 * vx);
            float p2y = cy + outerRad * (cos2 * uy + sin2 * vy);
            float p2z = cz + outerRad * (cos2 * uz + sin2 * vz);

            builder.vertex(matrix, p1x, p1y, p1z).color(r, g, b, a);
            builder.vertex(matrix, p2x, p2y, p2z).color(r, g, b, a);
        }

        // 2. Draw Inner Base Circle (if distinct from outer circle)
        if (light.soft > 0.1f) {
            float innerA = a * 0.4f; // slightly lower opacity for inner angle boundary
            for (int i = 0; i < segments; i++) {
                float angle1 = (float) (2.0 * Math.PI * i / segments);
                float angle2 = (float) (2.0 * Math.PI * (i + 1) / segments);

                float cos1 = (float) Math.cos(angle1);
                float sin1 = (float) Math.sin(angle1);
                float cos2 = (float) Math.cos(angle2);
                float sin2 = (float) Math.sin(angle2);

                float p1x = cx + innerRad * (cos1 * ux + sin1 * vx);
                float p1y = cy + innerRad * (cos1 * uy + sin1 * vy);
                float p1z = cz + innerRad * (cos1 * uz + sin1 * vz);

                float p2x = cx + innerRad * (cos2 * ux + sin2 * vx);
                float p2y = cy + innerRad * (cos2 * uy + sin2 * vy);
                float p2z = cz + innerRad * (cos2 * uz + sin2 * vz);

                builder.vertex(matrix, p1x, p1y, p1z).color(r, g, b, innerA);
                builder.vertex(matrix, p2x, p2y, p2z).color(r, g, b, innerA);
            }
        }

        // 3. Draw 4 lines from Apex (0, 0, 0) to Outer Circle perimeter (at 0, 90, 180, 270 degrees)
        float[] angles = {0f, (float) (Math.PI / 2.0), (float) Math.PI, (float) (3.0 * Math.PI / 2.0)};
        for (float angle : angles) {
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            float px = cx + outerRad * (cos * ux + sin * vx);
            float py = cy + outerRad * (cos * uy + sin * vy);
            float pz = cz + outerRad * (cos * uz + sin * vz);

            builder.vertex(matrix, 0f, 0f, 0f).color(r, g, b, a);
            builder.vertex(matrix, px, py, pz).color(r, g, b, a);
        }
    }


    private void fillBox(BufferBuilder builder, MatrixStack stack, float x1, float y1, float z1, float x2, float y2,
            float z2, float r, float g, float b, float a) {
        /* X faces */
        fillQuad(builder, stack, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1, r, g, b, a);
        fillQuad(builder, stack, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, a);

        /* Y faces */
        fillQuad(builder, stack, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
        fillQuad(builder, stack, x2, y2, z1, x1, y2, z1, x1, y2, z2, x2, y2, z2, r, g, b, a);

        /* Z faces */
        fillQuad(builder, stack, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, r, g, b, a);
        fillQuad(builder, stack, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, a);
    }

    private void fillQuad(BufferBuilder builder, MatrixStack stack, float x1, float y1, float z1, float x2, float y2,
            float z2, float x3, float y3, float z3, float x4, float y4, float z4, float r, float g, float b, float a) {
        Matrix4f matrix4f = stack.peek().getPositionMatrix();

        /* Triangle 1 */
        builder.vertex(matrix4f, x1, y1, z1).color(r, g, b, a);
        builder.vertex(matrix4f, x2, y2, z2).color(r, g, b, a);
        builder.vertex(matrix4f, x3, y3, z3).color(r, g, b, a);

        /* Triangle 2 */
        builder.vertex(matrix4f, x1, y1, z1).color(r, g, b, a);
        builder.vertex(matrix4f, x3, y3, z3).color(r, g, b, a);
        builder.vertex(matrix4f, x4, y4, z4).color(r, g, b, a);
    }

    public boolean onMouseClicked(double mouseX, double mouseY, int btn) {
        if (btn != 0)
            return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null)
            return false;

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

    public Vec3d getRayDirection(double mouseX, double mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();

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
        Camera camera = client.gameRenderer.getCamera();
        rayEye.rotate(camera.getRotation());

        return new Vec3d(rayEye.x, rayEye.y, rayEye.z).normalize();
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

        // 1. Check FREE translation (Center box) -> index 6
        if (intersectAABB(localRayStart, localRayDir, -0.04, -0.04, -0.04, 0.04, 0.04, 0.04)) {
            return 6;
        }

        // 2. Check Plane handles
        // XZ Plane (index 3)
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
        // XY Plane (index 4)
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
        // ZY Plane (index 5)
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

        // 3. Check Axis handles (0: X, 1: Y, 2: Z)
        double dX = getDistanceToSegment(localRayStart, localRayDir, new Vec3d(0, 0, 0), new Vec3d(0.35 * sx, 0, 0));
        double dY = getDistanceToSegment(localRayStart, localRayDir, new Vec3d(0, 0, 0), new Vec3d(0, 0.35 * sy, 0));
        double dZ = getDistanceToSegment(localRayStart, localRayDir, new Vec3d(0, 0, 0), new Vec3d(0, 0, 0.35 * sz));

        double threshold = 0.04;
        if (dX < threshold && dX < dY && dX < dZ)
            return 0;
        if (dY < threshold && dY < dX && dY < dZ)
            return 1;
        if (dZ < threshold && dZ < dX && dZ < dY)
            return 2;

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
        if (Math.abs(denom) < 0.0001)
            return origin;

        double t2 = (p12v2 * v1v1 - p12v1 * v1v2) / denom;
        return origin.add(axisDir.multiply(t2));
    }

    private Vec3d getMouseProjectionOnPlane(Vec3d rayStart, Vec3d rayDir, Vec3d origin, int planeIndex) {
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
        return rayStart.add(rayDir.multiply(t));
    }

    private Vec3d getMouseProjectionFree(Vec3d rayStart, Vec3d rayDir, Vec3d origin, Vec3d normal) {
        double denom = rayDir.dotProduct(normal);
        if (Math.abs(denom) < 0.0001)
            return origin;
        double t = origin.subtract(rayStart).dotProduct(normal) / denom;
        return rayStart.add(rayDir.multiply(t));
    }

    private double getDistanceToPoint(Vec3d rayStart, Vec3d rayDir, Vec3d point) {
        Vec3d toPoint = point.subtract(rayStart);
        double projection = toPoint.dotProduct(rayDir);
        if (projection < 0)
            return Double.MAX_VALUE;
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

}
