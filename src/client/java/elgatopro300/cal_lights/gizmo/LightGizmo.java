package elgatopro300.cal_lights.gizmo;

import elgatopro300.cal_lights.graphics.CLIcon;
import elgatopro300.cal_lights.graphics.CLTexture;
import elgatopro300.cal_lights.graphics.CalLightsIcons;
import elgatopro300.cal_lights.manager.CALUndoManager;
import elgatopro300.cal_lights.manager.LightInstance;
import elgatopro300.cal_lights.manager.LightManager;
import elgatopro300.cal_lights.ui.CALEditorScreen;
import elgatopro300.cal_lights.ui.CalSettings;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import org.joml.Intersectiond;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;

import java.util.Collection;

/**
 * Modern CML/BBS-inspired 3D Gizmo for CAL Lights.
 *
 * Supports Translate, Rotate, Scale, Combined, and Trackball modes with 3D cone tips,
 * torus rotation rings, frosted trackball sphere, billboarded view ring, guide lines,
 * rotation sweep arcs, camera flip signs, and accurate ray intersection math.
 */
public class LightGizmo {
    public static final LightGizmo INSTANCE = new LightGizmo();

    public enum Mode {
        TRANSLATE, ROTATE, SCALE, COMBINED, TOP
    }

    public enum Axis {
        X, Y, Z
    }

    /* Handle IDs matching BBS CML gizmo constants */
    public static final int STENCIL_NONE = -1;
    public static final int STENCIL_X = 1;
    public static final int STENCIL_Y = 2;
    public static final int STENCIL_Z = 3;
    public static final int STENCIL_XZ = 4;
    public static final int STENCIL_XY = 5;
    public static final int STENCIL_ZY = 6;
    public static final int STENCIL_FREE = 7;

    public static final int STENCIL_SCALE_X = 8;
    public static final int STENCIL_SCALE_Y = 9;
    public static final int STENCIL_SCALE_Z = 10;
    public static final int STENCIL_ROTATE_X = 11;
    public static final int STENCIL_ROTATE_Y = 12;
    public static final int STENCIL_ROTATE_Z = 13;

    public static final int STENCIL_TRACKBALL = 14;
    public static final int STENCIL_VIEW = 16;

    /* Colors matching BBS CML */
    private static final float[] COLOR_ACTIVE = { 1.00F, 1.00F, 1.00F };
    private static final float[] COLOR_X_IDLE = { 0.80F, 0.28F, 0.28F };
    private static final float[] COLOR_X_HOVER = { 1.00F, 0.35F, 0.35F };
    private static final float[] COLOR_Y_IDLE = { 0.30F, 0.75F, 0.35F };
    private static final float[] COLOR_Y_HOVER = { 0.40F, 1.00F, 0.45F };
    private static final float[] COLOR_Z_IDLE = { 0.28F, 0.50F, 0.95F };
    private static final float[] COLOR_Z_HOVER = { 0.35F, 0.62F, 1.00F };

    private static final float[] COLOR_XZ_IDLE = { 0.85F, 0.25F, 0.85F };
    private static final float[] COLOR_XZ_HOVER = { 1.00F, 0.35F, 1.00F };
    private static final float[] COLOR_XY_IDLE = { 0.85F, 0.80F, 0.20F };
    private static final float[] COLOR_XY_HOVER = { 1.00F, 0.95F, 0.25F };
    private static final float[] COLOR_ZY_IDLE = { 0.20F, 0.75F, 0.80F };
    private static final float[] COLOR_ZY_HOVER = { 0.30F, 0.90F, 0.95F };

    private static final float[] COLOR_FREE_IDLE = { 1.00F, 1.00F, 1.00F };
    private static final float[] COLOR_VIEW_IDLE = { 0.80F, 0.80F, 0.80F };
    private static final float[] COLOR_VIEW_HOVER = { 1.00F, 1.00F, 1.00F };

    private static final float PLANE_ALPHA_IDLE = 0.55F;
    private static final float PLANE_ALPHA_HOVER = 0.80F;
    private static final float PLANE_ALPHA_ACTIVE = 0.95F;

    private static final float COMBINED_MOVE_SCALE = 0.85F;
    private static final float COMBINED_ROTATE_SCALE = 1.55F;
    private static final float VIEW_RING_SCALE = 1.12F;

    /* Render scratch arrays for torus & sphere geometry */
    private static final float[] SCRATCH_COS_U = new float[65];
    private static final float[] SCRATCH_SIN_U = new float[65];
    private static final float[] SCRATCH_COS_V = new float[25];
    private static final float[] SCRATCH_SIN_V = new float[25];

    // Settings & State
    public static boolean renderLightIcons = true;
    public static boolean snapToGrid = false;

    private LightInstance selectedLight = null;
    private Mode mode = Mode.COMBINED;

    private int activeHandle = STENCIL_NONE;
    private int hoveredHandle = STENCIL_NONE;
    private boolean dragging = false;

    // Captured matrices & viewing vectors
    private final Matrix4f lastProjectionMatrix = new Matrix4f();
    private final Matrix4f capturedModelView = new Matrix4f();
    private boolean captured = false;

    private float lastSx = 1.0f;
    private float lastSy = 1.0f;
    private float lastSz = 1.0f;
    private final Vector3f lastCamDir = new Vector3f(0f, 1f, 0f);

    // Drag start states
    private Vec3d dragStartMousePos = null;
    private double dragStartMouseAngle = 0.0;
    private double dragStart3DAngle = 0.0;
    private Vec3d dragStartLightPos = null;
    private float dragStartRadius = 6.0f;
    private float dragStartDistance = 12.0f;

    // Rotation Arc visual feedback
    private boolean arcActive = false;
    private boolean arcView = false;
    private Axis arcAxis = Axis.Y;
    private float arcStartU = 0f;
    private float arcSweep = 0f;

    // Drag progress indicator
    private boolean dragProgressActive = false;
    private final Vector3f dragProgressStart = new Vector3f();
    private final Vector3f dragProgressEnd = new Vector3f();

    public void init() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(this::render);
    }

    public void setSelectedLight(LightInstance light) {
        this.selectedLight = light;
        if (light == null) {
            this.activeHandle = STENCIL_NONE;
            this.hoveredHandle = STENCIL_NONE;
            this.dragging = false;
            this.arcActive = false;
            this.dragProgressActive = false;
        }
    }

    public LightInstance getSelectedLight() {
        return this.selectedLight;
    }

    public Mode getMode() {
        return this.mode;
    }

    public void setMode(Mode mode) {
        if (mode != null) {
            this.mode = mode;
            CalSettings.INSTANCE.gizmoMode = mode.ordinal();
        }
    }

    public int getActiveHandle() {
        return this.activeHandle;
    }

    public int getHoveredHandle() {
        return this.hoveredHandle;
    }

    private void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        if (!(client.currentScreen instanceof CALEditorScreen)) return;

        this.lastProjectionMatrix.set(context.projectionMatrix());
        this.capturedModelView.set(RenderSystem.getModelViewMatrix());
        this.captured = true;
    }

    /**
     * Draws light billboards, indicators, and the full 3D Gizmo overlay in the GUI phase.
     */
    public void renderOverlay() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || !captured) return;
        if (!(client.currentScreen instanceof CALEditorScreen)) return;

        if (CalSettings.INSTANCE.gizmoMode >= 0 && CalSettings.INSTANCE.gizmoMode < Mode.values().length) {
            this.mode = Mode.values()[CalSettings.INSTANCE.gizmoMode];
        }

        Camera camera = client.gameRenderer.getCamera();
        Vec3d camPos = camera.getPos();

        Matrix4f prevProjection = RenderSystem.getProjectionMatrix();
        VertexSorter prevSorter = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(lastProjectionMatrix, VertexSorter.BY_DISTANCE);

        Matrix4fStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushMatrix();
        mvStack.set(capturedModelView);
        RenderSystem.applyModelViewMatrix();

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        MatrixStack stack = new MatrixStack();

        // 1. Draw billboards for all point & spot lights
        Collection<LightInstance> points = LightManager.INSTANCE.getPointLights();
        Collection<LightInstance> spots = LightManager.INSTANCE.getSpotLights();

        drawBillboards(stack, camPos, points, false);
        drawBillboards(stack, camPos, spots, true);

        // 2. Draw indicators and 3D gizmo for selected light
        if (selectedLight != null) {
            drawLightIndicators(stack, camPos, selectedLight);
            drawGizmo3D(stack, camPos, selectedLight);
        }

        RenderSystem.enableDepthTest();

        mvStack.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(prevProjection, prevSorter);
    }

    private void drawBillboards(MatrixStack stack, Vec3d camPos, Collection<LightInstance> lights, boolean isSpot) {
        CLIcon icon = isSpot ? CalLightsIcons.SPOT_LIGHT : CalLightsIcons.POINT_LIGHT;
        if (icon == null) return;

        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        Quaternionf camRot = camera.getRotation();

        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        for (LightInstance light : lights) {
            if (!renderLightIcons && light != selectedLight) continue;

            stack.push();
            stack.translate(light.x - camPos.x, light.y - camPos.y, light.z - camPos.z);
            stack.multiply(camRot);

            double dist = camPos.distanceTo(new Vec3d(light.x, light.y, light.z));
            float size = (float) (0.3f * Math.max(1.0, dist * 0.15));
            if (light == selectedLight) {
                size = (float) (0.4f * Math.max(1.0, dist * 0.15));
            }

            if (icon.staticTexture != null && icon.tintTexture != null) {
                int tintColor = (light == selectedLight) ? 0xFFFFAA00 : (0xFF000000 | ((int) (light.r * 255) << 16) | ((int) (light.g * 255) << 8) | (int) (light.b * 255));
                icon.tintTexture.bind();
                RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
                drawBillboardQuad(stack, size, icon.tintTexture, icon.x, icon.y, icon.w, icon.h, tintColor);

                icon.staticTexture.bind();
                RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
                drawBillboardQuad(stack, size, icon.staticTexture, icon.x, icon.y, icon.w, icon.h, 0xFFFFFFFF);
            } else if (icon.texture != null) {
                icon.texture.bind();
                RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
                int color = (light == selectedLight) ? 0xFFFFAA00 : 0xFFFFFFFF;
                drawBillboardQuad(stack, size, icon.texture, icon.x, icon.y, icon.w, icon.h, color);
            }
            stack.pop();
        }

        RenderSystem.enableCull();
    }

    private void drawBillboardQuad(MatrixStack stack, float size, CLTexture texture, int texX, int texY, int texW, int texH, int color) {
        Matrix4f matrix = stack.peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        float u1 = texX / (float) texture.width;
        float v1 = texY / (float) texture.height;
        float u2 = (texX + texW) / (float) texture.width;
        float v2 = (texY + texH) / (float) texture.height;

        builder.vertex(matrix, -size, -size, 0).texture(u1, v2).color(color);
        builder.vertex(matrix, size, -size, 0).texture(u2, v2).color(color);
        builder.vertex(matrix, size, size, 0).texture(u2, v1).color(color);
        builder.vertex(matrix, -size, size, 0).texture(u1, v1).color(color);

        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    private void drawLightIndicators(MatrixStack stack, Vec3d camPos, LightInstance light) {
        stack.push();
        stack.translate(light.x - camPos.x, light.y - camPos.y, light.z - camPos.z);

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        float r = light.r;
        float g = light.g;
        float b = light.b;
        float maxVal = Math.max(r, Math.max(g, b));
        if (maxVal < 0.2f) {
            r = 1.0f;
            g = 0.66f;
            b = 0.0f;
        }
        float alpha = 0.65f;

        if (light.isSpot) {
            drawSpotIndicator(builder, stack, light, r, g, b, alpha);
        } else {
            drawPointIndicator(builder, stack, light, r, g, b, alpha);
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());
        RenderSystem.enableCull();
        stack.pop();
    }

    private void drawPointIndicator(BufferBuilder builder, MatrixStack stack, LightInstance light, float r, float g, float b, float a) {
        float radius = light.radius;
        int segments = 64;
        Matrix4f matrix = stack.peek().getPositionMatrix();

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
            dx = 0f; dy = -1f; dz = 0f; len = 1f;
        }
        float ndx = dx / len;
        float ndy = dy / len;
        float ndz = dz / len;

        float wx = 1f, wy = 0f, wz = 0f;
        if (Math.abs(ndx) > 0.9f) {
            wx = 0f; wy = 1f; wz = 0f;
        }

        float ux = ndy * wz - ndz * wy;
        float uy = ndz * wx - ndx * wz;
        float uz = ndx * wy - ndy * wx;
        float uLen = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (uLen > 0.0001f) {
            ux /= uLen; uy /= uLen; uz /= uLen;
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

        if (light.soft > 0.1f) {
            float innerA = a * 0.4f;
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

    /* ---- BBS CML-STYLE 3D GIZMO RENDERING ---- */

    private void drawGizmo3D(MatrixStack stack, Vec3d camPos, LightInstance light) {
        stack.push();
        stack.translate(light.x - camPos.x, light.y - camPos.y, light.z - camPos.z);

        double dist = camPos.distanceTo(new Vec3d(light.x, light.y, light.z));
        float distanceFactor = (float) (dist * 0.12);
        float scale = (float) (1.4f * Math.max(0.5f, distanceFactor) * (CalSettings.INSTANCE.gizmoSize / 10.0f));

        this.updateFlipSigns(camPos, light);

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        if (this.mode == Mode.ROTATE) drawRotate(builder, stack, scale);
        else if (this.mode == Mode.SCALE) drawScale(builder, stack, scale);
        else if (this.mode == Mode.COMBINED) drawCombined(builder, stack, scale);
        else if (this.mode == Mode.TOP) drawTop(builder, stack, scale);
        else drawTranslate(builder, stack, scale);

        drawActiveGuide(builder, stack, scale);
        drawDragProgress(builder, stack, scale);

        try {
            BufferRenderer.drawWithGlobalProgram(builder.end());
        } catch (IllegalStateException ignored) {}

        RenderSystem.enableCull();
        stack.pop();
    }

    private void updateFlipSigns(Vec3d camPos, LightInstance light) {
        if (!this.dragging) {
            this.lastSx = (camPos.x - light.x) >= 0 ? 1F : -1F;
            this.lastSy = (camPos.y - light.y) >= 0 ? 1F : -1F;
            this.lastSz = (camPos.z - light.z) >= 0 ? 1F : -1F;

            double dx = camPos.x - light.x;
            double dy = camPos.y - light.y;
            double dz = camPos.z - light.z;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > 1e-6) {
                this.lastCamDir.set((float) (dx / len), (float) (dy / len), (float) (dz / len));
            }
        }
    }

    private boolean showHandle(int handleId) {
        return this.activeHandle == STENCIL_NONE || this.activeHandle == handleId;
    }

    private float[] pickColor(int handleId, float[] idle, float[] hover) {
        if (this.activeHandle == handleId) return COLOR_ACTIVE;
        if (this.activeHandle == STENCIL_NONE && this.hoveredHandle == handleId) return hover;
        return idle;
    }

    private float pickPlaneAlpha(int handleId) {
        if (this.activeHandle == handleId) return PLANE_ALPHA_ACTIVE;
        if (this.activeHandle == STENCIL_NONE && this.hoveredHandle == handleId) return PLANE_ALPHA_HOVER;
        return PLANE_ALPHA_IDLE;
    }

    /* ---- Translation handles ---- */

    private void drawTranslate(BufferBuilder builder, MatrixStack stack, float scale) {
        float moveScale = scale * COMBINED_MOVE_SCALE;
        float axisSize = 0.22F * COMBINED_ROTATE_SCALE * scale * 0.50F;
        float axisOffset = 0.0075F * moveScale;
        float planeInner = 0.08F * moveScale;
        float planeOuter = 0.18F * moveScale;
        float offset = 0.001F * moveScale;

        drawMoveBars(builder, stack, axisSize, axisOffset, false, 0f, 0f);
        drawMovePlanes(builder, stack, planeInner, planeOuter, offset);
        drawScreenCube(builder, stack, axisOffset);
    }

    private void drawMoveBars(BufferBuilder builder, MatrixStack stack, float axisSize, float axisOffset, boolean scaleCubesAtTip, float cubeHalf, float rotateCageRadius) {
        float[] xCol = pickColor(STENCIL_X, COLOR_X_IDLE, COLOR_X_HOVER);
        float[] yCol = pickColor(STENCIL_Y, COLOR_Y_IDLE, COLOR_Y_HOVER);
        float[] zCol = pickColor(STENCIL_Z, COLOR_Z_IDLE, COLOR_Z_HOVER);

        float tipLength = Math.abs(axisSize) * (scaleCubesAtTip ? 0.18F : 0.35F);
        float tipRadius = axisOffset * 2.4F;
        int coneSegments = 10;

        if (showHandle(STENCIL_X) || (scaleCubesAtTip && showHandle(STENCIL_SCALE_X))) {
            if (showHandle(STENCIL_X)) {
                fillBox(builder, stack, 0, -axisOffset, -axisOffset, axisSize * this.lastSx, axisOffset, axisOffset, xCol[0], xCol[1], xCol[2], 1F);
            }
            if (scaleCubesAtTip) {
                drawCombinedAxisTip(builder, stack, Axis.X, axisSize * this.lastSx, tipRadius, tipLength, cubeHalf, coneSegments, xCol, rotateCageRadius);
            } else if (showHandle(STENCIL_X)) {
                cone(builder, stack, (axisSize + tipLength) * this.lastSx, 0, 0, axisSize * this.lastSx, 0, 0, tipRadius, coneSegments, xCol[0], xCol[1], xCol[2], 1F);
            }
        }

        if (showHandle(STENCIL_Y) || (scaleCubesAtTip && showHandle(STENCIL_SCALE_Y))) {
            if (showHandle(STENCIL_Y)) {
                fillBox(builder, stack, -axisOffset, 0, -axisOffset, axisOffset, axisSize * this.lastSy, axisOffset, yCol[0], yCol[1], yCol[2], 1F);
            }
            if (scaleCubesAtTip) {
                drawCombinedAxisTip(builder, stack, Axis.Y, axisSize * this.lastSy, tipRadius, tipLength, cubeHalf, coneSegments, yCol, rotateCageRadius);
            } else if (showHandle(STENCIL_Y)) {
                cone(builder, stack, 0, (axisSize + tipLength) * this.lastSy, 0, 0, axisSize * this.lastSy, 0, tipRadius, coneSegments, yCol[0], yCol[1], yCol[2], 1F);
            }
        }

        if (showHandle(STENCIL_Z) || (scaleCubesAtTip && showHandle(STENCIL_SCALE_Z))) {
            if (showHandle(STENCIL_Z)) {
                fillBox(builder, stack, -axisOffset, -axisOffset, 0, axisOffset, axisOffset, axisSize * this.lastSz, zCol[0], zCol[1], zCol[2], 1F);
            }
            if (scaleCubesAtTip) {
                drawCombinedAxisTip(builder, stack, Axis.Z, axisSize * this.lastSz, tipRadius, tipLength, cubeHalf, coneSegments, zCol, rotateCageRadius);
            } else if (showHandle(STENCIL_Z)) {
                cone(builder, stack, 0, 0, (axisSize + tipLength) * this.lastSz, 0, 0, axisSize * this.lastSz, tipRadius, coneSegments, zCol[0], zCol[1], zCol[2], 1F);
            }
        }
    }

    private void drawCombinedAxisTip(BufferBuilder builder, MatrixStack stack, Axis axis, float shaftEnd, float tipRadius, float tipLength, float cubeHalf, int coneSegments, float[] translateColor, float rotateCageRadius) {
        float sign = shaftEnd >= 0F ? 1F : -1F;
        float absEnd = Math.abs(shaftEnd);
        float coneBase = absEnd;
        float coneApex = absEnd + tipLength;
        float tipCubeGap = cubeHalf * 1.35F;
        float cubeCenter = coneApex + tipCubeGap + cubeHalf;

        if (this.showHandle(axis == Axis.X ? STENCIL_X : axis == Axis.Y ? STENCIL_Y : STENCIL_Z)) {
            if (axis == Axis.X) cone(builder, stack, coneApex * sign, 0, 0, coneBase * sign, 0, 0, tipRadius, coneSegments, translateColor[0], translateColor[1], translateColor[2], 1F);
            else if (axis == Axis.Y) cone(builder, stack, 0, coneApex * sign, 0, 0, coneBase * sign, 0, tipRadius, coneSegments, translateColor[0], translateColor[1], translateColor[2], 1F);
            else cone(builder, stack, 0, 0, coneApex * sign, 0, 0, coneBase * sign, tipRadius, coneSegments, translateColor[0], translateColor[1], translateColor[2], 1F);
        }

        drawScaleTipCube(builder, stack, axis, cubeCenter * sign, cubeHalf);
    }

    private void drawScaleTipCube(BufferBuilder builder, MatrixStack stack, Axis axis, float tip, float half) {
        int id = axis == Axis.X ? STENCIL_SCALE_X : axis == Axis.Y ? STENCIL_SCALE_Y : STENCIL_SCALE_Z;
        if (!showHandle(id)) return;

        float[] color = pickColor(id, axis == Axis.X ? COLOR_X_IDLE : axis == Axis.Y ? COLOR_Y_IDLE : COLOR_Z_IDLE,
                axis == Axis.X ? COLOR_X_HOVER : axis == Axis.Y ? COLOR_Y_HOVER : COLOR_Z_HOVER);

        if (axis == Axis.X) fillBox(builder, stack, tip - half, -half, -half, tip + half, half, half, color[0], color[1], color[2], 1F);
        else if (axis == Axis.Y) fillBox(builder, stack, -half, tip - half, -half, half, tip + half, half, color[0], color[1], color[2], 1F);
        else fillBox(builder, stack, -half, -half, tip - half, half, half, tip + half, color[0], color[1], color[2], 1F);
    }

    private void drawMovePlanes(BufferBuilder builder, MatrixStack stack, float planeInner, float planeOuter, float offset) {
        float xzAlpha = pickPlaneAlpha(STENCIL_XZ);
        float xyAlpha = pickPlaneAlpha(STENCIL_XY);
        float zyAlpha = pickPlaneAlpha(STENCIL_ZY);

        float[] xzCol = (this.activeHandle == STENCIL_NONE && this.hoveredHandle == STENCIL_XZ) ? COLOR_XZ_HOVER : (this.activeHandle == STENCIL_XZ ? COLOR_ACTIVE : COLOR_XZ_IDLE);
        float[] xyCol = (this.activeHandle == STENCIL_NONE && this.hoveredHandle == STENCIL_XY) ? COLOR_XY_HOVER : (this.activeHandle == STENCIL_XY ? COLOR_ACTIVE : COLOR_XY_IDLE);
        float[] zyCol = (this.activeHandle == STENCIL_NONE && this.hoveredHandle == STENCIL_ZY) ? COLOR_ZY_HOVER : (this.activeHandle == STENCIL_ZY ? COLOR_ACTIVE : COLOR_ZY_IDLE);

        if (showHandle(STENCIL_XZ)) fillBox(builder, stack, planeInner * this.lastSx, -offset, planeInner * this.lastSz, planeOuter * this.lastSx, offset, planeOuter * this.lastSz, xzCol[0], xzCol[1], xzCol[2], xzAlpha);
        if (showHandle(STENCIL_XY)) fillBox(builder, stack, planeInner * this.lastSx, planeInner * this.lastSy, -offset, planeOuter * this.lastSx, planeOuter * this.lastSy, offset, xyCol[0], xyCol[1], xyCol[2], xyAlpha);
        if (showHandle(STENCIL_ZY)) fillBox(builder, stack, -offset, planeInner * this.lastSy, planeInner * this.lastSz, offset, planeOuter * this.lastSy, planeOuter * this.lastSz, zyCol[0], zyCol[1], zyCol[2], zyAlpha);
    }

    private void drawScreenCube(BufferBuilder builder, MatrixStack stack, float axisOffset) {
        if (!showHandle(STENCIL_FREE)) return;
        float[] color = pickColor(STENCIL_FREE, COLOR_FREE_IDLE, COLOR_FREE_IDLE);
        fillBox(builder, stack, -axisOffset, -axisOffset, -axisOffset, axisOffset, axisOffset, axisOffset, color[0], color[1], color[2], 1F);
    }

    /* ---- Scale handles ---- */

    private void drawScale(BufferBuilder builder, MatrixStack stack, float scale) {
        float moveScale = scale * COMBINED_MOVE_SCALE;
        float axisSize = 0.22F * COMBINED_ROTATE_SCALE * scale * 0.50F;
        float axisOffset = 0.0075F * moveScale;
        float half = 0.016F * scale;

        float[] xCol = pickColor(STENCIL_X, COLOR_X_IDLE, COLOR_X_HOVER);
        float[] yCol = pickColor(STENCIL_Y, COLOR_Y_IDLE, COLOR_Y_HOVER);
        float[] zCol = pickColor(STENCIL_Z, COLOR_Z_IDLE, COLOR_Z_HOVER);
        float[] freeCol = pickColor(STENCIL_FREE, COLOR_FREE_IDLE, COLOR_FREE_IDLE);

        if (showHandle(STENCIL_X)) {
            fillBox(builder, stack, 0, -axisOffset, -axisOffset, axisSize * this.lastSx, axisOffset, axisOffset, xCol[0], xCol[1], xCol[2], 1F);
            fillBox(builder, stack, axisSize * this.lastSx - half, -half, -half, axisSize * this.lastSx + half, half, half, xCol[0], xCol[1], xCol[2], 1F);
        }
        if (showHandle(STENCIL_Y)) {
            fillBox(builder, stack, -axisOffset, 0, -axisOffset, axisOffset, axisSize * this.lastSy, axisOffset, yCol[0], yCol[1], yCol[2], 1F);
            fillBox(builder, stack, -half, axisSize * this.lastSy - half, -half, half, axisSize * this.lastSy + half, half, yCol[0], yCol[1], yCol[2], 1F);
        }
        if (showHandle(STENCIL_Z)) {
            fillBox(builder, stack, -axisOffset, -axisOffset, 0, axisOffset, axisOffset, axisSize * this.lastSz, zCol[0], zCol[1], zCol[2], 1F);
            fillBox(builder, stack, -half, -half, axisSize * this.lastSz - half, half, half, axisSize * this.lastSz + half, zCol[0], zCol[1], zCol[2], 1F);
        }
        if (showHandle(STENCIL_FREE)) {
            fillBox(builder, stack, -axisOffset, -axisOffset, -axisOffset, axisOffset, axisOffset, axisOffset, freeCol[0], freeCol[1], freeCol[2], 1F);
        }
    }

    /* ---- Rotate handles ---- */

    private void drawTop(BufferBuilder builder, MatrixStack stack, float scale) {
        float radius = 0.22F * scale;
        float topRadius = radius * 1.85F * 0.5F;
        boolean active = this.activeHandle == STENCIL_TRACKBALL;
        float sa = active ? 0.32F : 0.16F;
        sphere(builder, stack, topRadius, 16, 24, 0.92F, 0.92F, 0.92F, sa);
    }

    private void drawRotate(BufferBuilder builder, MatrixStack stack, float scale) {
        float rotateRadius = 0.22F * scale * COMBINED_ROTATE_SCALE;
        float ringThickness = 0.010F * scale;
        drawTrackball(builder, stack, rotateRadius * 1.85F * 0.5F);
        drawRings(builder, stack, rotateRadius, ringThickness, STENCIL_ROTATE_X, STENCIL_ROTATE_Y, STENCIL_ROTATE_Z);
        drawViewRing(builder, stack, rotateRadius * VIEW_RING_SCALE, ringThickness);
    }

    private void drawRings(BufferBuilder builder, MatrixStack stack, float radius, float ringThickness, int idX, int idY, int idZ) {
        float[] xCol = pickColor(idX, COLOR_X_IDLE, COLOR_X_HOVER);
        float[] yCol = pickColor(idY, COLOR_Y_IDLE, COLOR_Y_HOVER);
        float[] zCol = pickColor(idZ, COLOR_Z_IDLE, COLOR_Z_HOVER);

        if (showHandle(idZ)) arc3D(builder, stack, Axis.Z, radius, ringThickness, zCol[0], zCol[1], zCol[2], 0F, 360F);
        if (showHandle(idX)) arc3D(builder, stack, Axis.X, radius, ringThickness, xCol[0], xCol[1], xCol[2], 0F, 360F);
        if (showHandle(idY)) arc3D(builder, stack, Axis.Y, radius, ringThickness, yCol[0], yCol[1], yCol[2], 0F, 360F);

        if (this.arcActive && !this.arcView && (this.activeHandle == idX || this.activeHandle == idY || this.activeHandle == idZ)) {
            drawRotationSweepArc(builder, stack, this.arcAxis, radius * 0.85F, ringThickness * 1.5F, false);
        }
    }

    private void drawTrackball(BufferBuilder builder, MatrixStack stack, float radius) {
        if (!showHandle(STENCIL_TRACKBALL)) return;
        boolean active = this.activeHandle == STENCIL_TRACKBALL;
        float alpha = active ? 0.28F : 0.14F;
        sphere(builder, stack, radius, 16, 24, 0.92F, 0.92F, 0.92F, alpha);
    }

    private void drawViewRing(BufferBuilder builder, MatrixStack stack, float radius, float ringThickness) {
        if (!showHandle(STENCIL_VIEW)) return;
        float[] color = pickColor(STENCIL_VIEW, COLOR_VIEW_IDLE, COLOR_VIEW_HOVER);

        stack.push();
        stack.multiply(new Quaternionf().rotationTo(0F, 1F, 0F, this.lastCamDir.x, this.lastCamDir.y, this.lastCamDir.z));
        arc3D(builder, stack, Axis.Y, radius, ringThickness, color[0], color[1], color[2], 0F, 360F);

        if (this.arcActive && this.arcView && this.activeHandle == STENCIL_VIEW) {
            drawRotationSweepArc(builder, stack, Axis.Y, radius * 0.9F, ringThickness * 1.5F, true);
        }

        stack.pop();
    }

    private void drawRotationSweepArc(BufferBuilder builder, MatrixStack stack, Axis axis, float radius, float thickness, boolean viewRing) {
        if (Math.abs(this.arcSweep) <= 0.01F) return;
        float[] color = viewRing ? COLOR_ACTIVE : (axis == Axis.X ? COLOR_X_HOVER : axis == Axis.Y ? COLOR_Y_HOVER : COLOR_Z_HOVER);
        arc3D(builder, stack, axis, radius, thickness, color[0], color[1], color[2], this.arcStartU, this.arcSweep);
    }

    /* ---- Combined mode ---- */

    private void drawCombined(BufferBuilder builder, MatrixStack stack, float scale) {
        float moveScale = scale * COMBINED_MOVE_SCALE;
        float rotateRadius = 0.22F * scale * COMBINED_ROTATE_SCALE;
        float axisSize = rotateRadius * 0.50F;
        float axisOffset = 0.0075F * moveScale;
        float cubeHalf = 0.016F * scale;
        float planeInner = 0.08F * moveScale;
        float planeOuter = 0.18F * moveScale;
        float offset = 0.001F * moveScale;
        float ringThickness = 0.010F * scale;

        drawTrackball(builder, stack, rotateRadius * 1.85F * 0.5F);

        drawMoveBars(builder, stack, axisSize, axisOffset, true, cubeHalf, rotateRadius);
        drawMovePlanes(builder, stack, planeInner, planeOuter, offset);
        drawScreenCube(builder, stack, axisOffset);

        drawRings(builder, stack, rotateRadius, ringThickness, STENCIL_ROTATE_X, STENCIL_ROTATE_Y, STENCIL_ROTATE_Z);
        drawViewRing(builder, stack, rotateRadius * VIEW_RING_SCALE, ringThickness);
    }

    /* ---- Active guide & progress lines ---- */

    private void drawActiveGuide(BufferBuilder builder, MatrixStack stack, float scale) {
        if (this.activeHandle == STENCIL_XY) {
            drawGuideLine(builder, stack, scale, Axis.X, COLOR_X_HOVER);
            drawGuideLine(builder, stack, scale, Axis.Y, COLOR_Y_HOVER);
            return;
        } else if (this.activeHandle == STENCIL_XZ) {
            drawGuideLine(builder, stack, scale, Axis.X, COLOR_X_HOVER);
            drawGuideLine(builder, stack, scale, Axis.Z, COLOR_Z_HOVER);
            return;
        } else if (this.activeHandle == STENCIL_ZY) {
            drawGuideLine(builder, stack, scale, Axis.Z, COLOR_Z_HOVER);
            drawGuideLine(builder, stack, scale, Axis.Y, COLOR_Y_HOVER);
            return;
        }

        Axis axis = null;
        float[] color = null;
        if (this.activeHandle == STENCIL_X || this.activeHandle == STENCIL_SCALE_X || this.activeHandle == STENCIL_ROTATE_X) {
            axis = Axis.X; color = COLOR_X_HOVER;
        } else if (this.activeHandle == STENCIL_Y || this.activeHandle == STENCIL_SCALE_Y || this.activeHandle == STENCIL_ROTATE_Y) {
            axis = Axis.Y; color = COLOR_Y_HOVER;
        } else if (this.activeHandle == STENCIL_Z || this.activeHandle == STENCIL_SCALE_Z || this.activeHandle == STENCIL_ROTATE_Z) {
            axis = Axis.Z; color = COLOR_Z_HOVER;
        }

        if (axis != null) {
            drawGuideLine(builder, stack, scale, axis, color);
        }
    }

    private void drawGuideLine(BufferBuilder builder, MatrixStack stack, float scale, Axis axis, float[] color) {
        float length = 10F * scale * 2F;
        float t = 0.0025F * scale * 2F;
        if (axis == Axis.X) fillBox(builder, stack, -length, -t, -t, length, t, t, color[0], color[1], color[2], 0.35F);
        else if (axis == Axis.Y) fillBox(builder, stack, -t, -length, -t, t, length, t, color[0], color[1], color[2], 0.35F);
        else fillBox(builder, stack, -t, -t, -length, t, t, length, color[0], color[1], color[2], 0.35F);
    }

    private void drawDragProgress(BufferBuilder builder, MatrixStack stack, float scale) {
        if (!this.dragProgressActive) return;
        float dx = this.dragProgressEnd.x - this.dragProgressStart.x;
        float dy = this.dragProgressEnd.y - this.dragProgressStart.y;
        float dz = this.dragProgressEnd.z - this.dragProgressStart.z;
        if (dx * dx + dy * dy + dz * dz < 1.0E-10F) return;

        float t = 0.006F * scale * 2F;
        fillBoxTo(builder, stack, this.dragProgressStart.x, this.dragProgressStart.y, this.dragProgressStart.z,
                this.dragProgressEnd.x, this.dragProgressEnd.y, this.dragProgressEnd.z, t, COLOR_ACTIVE[0], COLOR_ACTIVE[1], COLOR_ACTIVE[2], 0.9F);
    }

    /* ---- 3D Primitive Rendering Helpers (BBS CML Draw) ---- */

    private void cone(BufferBuilder builder, MatrixStack stack, float apexX, float apexY, float apexZ, float baseX, float baseY, float baseZ, float radius, int segments, float r, float g, float b, float a) {
        Matrix4f mat = stack.peek().getPositionMatrix();

        float dx = baseX - apexX;
        float dy = baseY - apexY;
        float dz = baseZ - apexZ;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6F) return;

        dx /= len; dy /= len; dz /= len;
        float upx = 0F, upy = 1F, upz = 0F;
        if (Math.abs(dy) > 0.99F) {
            upx = 1F; upy = 0F; upz = 0F;
        }

        float rx = dy * upz - dz * upy;
        float ry = dz * upx - dx * upz;
        float rz = dx * upy - dy * upx;
        float rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        rx /= rl; ry /= rl; rz /= rl;

        float ux = ry * dz - rz * dy;
        float uy = rz * dx - rx * dz;
        float uz = rx * dy - ry * dx;

        for (int i = 0; i < segments; i++) {
            double ang = Math.PI * 2D * i / segments;
            SCRATCH_COS_U[i] = (float) Math.cos(ang);
            SCRATCH_SIN_U[i] = (float) Math.sin(ang);
        }

        for (int i = 0; i < segments; i++) {
            int i2 = (i + 1) % segments;
            float c1 = SCRATCH_COS_U[i]; float s1 = SCRATCH_SIN_U[i];
            float c2 = SCRATCH_COS_U[i2]; float s2 = SCRATCH_SIN_U[i2];

            float x1 = baseX + (rx * c1 + ux * s1) * radius;
            float y1 = baseY + (ry * c1 + uy * s1) * radius;
            float z1 = baseZ + (rz * c1 + uz * s1) * radius;

            float x2 = baseX + (rx * c2 + ux * s2) * radius;
            float y2 = baseY + (ry * c2 + uy * s2) * radius;
            float z2 = baseZ + (rz * c2 + uz * s2) * radius;

            builder.vertex(mat, apexX, apexY, apexZ).color(r, g, b, a);
            builder.vertex(mat, x1, y1, z1).color(r, g, b, a);
            builder.vertex(mat, x2, y2, z2).color(r, g, b, a);

            builder.vertex(mat, x1, y1, z1).color(r, g, b, a);
            builder.vertex(mat, baseX, baseY, baseZ).color(r, g, b, a);
            builder.vertex(mat, x2, y2, z2).color(r, g, b, a);
        }
    }

    private void sphere(BufferBuilder builder, MatrixStack stack, float radius, int rings, int sectors, float r, float g, float b, float a) {
        Matrix4f mat = stack.peek().getPositionMatrix();

        for (int i = 0; i <= rings; i++) {
            double v = Math.PI * i / rings;
            SCRATCH_SIN_V[i] = (float) Math.sin(v);
            SCRATCH_COS_V[i] = (float) Math.cos(v);
        }

        for (int j = 0; j <= sectors; j++) {
            double u = Math.PI * 2D * j / sectors;
            SCRATCH_SIN_U[j] = (float) Math.sin(u);
            SCRATCH_COS_U[j] = (float) Math.cos(u);
        }

        for (int i = 0; i < rings; i++) {
            float sv1 = SCRATCH_SIN_V[i]; float cv1 = SCRATCH_COS_V[i];
            float sv2 = SCRATCH_SIN_V[i + 1]; float cv2 = SCRATCH_COS_V[i + 1];

            for (int j = 0; j < sectors; j++) {
                float cu1 = SCRATCH_COS_U[j]; float su1 = SCRATCH_SIN_U[j];
                float cu2 = SCRATCH_COS_U[j + 1]; float su2 = SCRATCH_SIN_U[j + 1];

                float x11 = sv1 * cu1 * radius; float y11 = cv1 * radius; float z11 = sv1 * su1 * radius;
                float x12 = sv2 * cu1 * radius; float y12 = cv2 * radius; float z12 = sv2 * su1 * radius;
                float x21 = sv1 * cu2 * radius; float y21 = y11; float z21 = sv1 * su2 * radius;
                float x22 = sv2 * cu2 * radius; float y22 = y12; float z22 = sv2 * su2 * radius;

                builder.vertex(mat, x11, y11, z11).color(r, g, b, a);
                builder.vertex(mat, x12, y12, z12).color(r, g, b, a);
                builder.vertex(mat, x22, y22, z22).color(r, g, b, a);

                builder.vertex(mat, x11, y11, z11).color(r, g, b, a);
                builder.vertex(mat, x22, y22, z22).color(r, g, b, a);
                builder.vertex(mat, x21, y21, z21).color(r, g, b, a);
            }
        }
    }

    private void arc3D(BufferBuilder builder, MatrixStack stack, Axis axis, float radius, float thickness, float r, float g, float b, float startDeg, float sweepDeg) {
        float absSweep = Math.abs(sweepDeg);
        if (absSweep < 0.01F || thickness <= 0F || radius <= 0F) return;

        int segU = Math.max(12, Math.round(64F * absSweep / 360F));
        segU = Math.min(segU, SCRATCH_COS_U.length - 1);
        int segV = 10;
        segV = Math.min(segV, SCRATCH_COS_V.length - 1);

        double u0 = Math.toRadians(startDeg);
        double uStep = Math.toRadians(sweepDeg / (double) segU);
        double vStep = Math.PI * 2D / (double) segV;

        stack.push();
        if (axis == Axis.X) stack.multiply(RotationAxis.POSITIVE_Z.rotation((float) (Math.PI / 2F)));
        if (axis == Axis.Z) stack.multiply(RotationAxis.NEGATIVE_X.rotation((float) (Math.PI / 2F)));

        float tubeR = thickness * 0.5F;
        Matrix4f mat = stack.peek().getPositionMatrix();

        for (int iv = 0; iv <= segV; iv++) {
            double v = vStep * iv;
            SCRATCH_COS_V[iv] = (float) Math.cos(v);
            SCRATCH_SIN_V[iv] = (float) Math.sin(v);
        }

        for (int iu = 0; iu <= segU; iu++) {
            double u = u0 + uStep * iu;
            SCRATCH_COS_U[iu] = (float) Math.cos(u);
            SCRATCH_SIN_U[iu] = (float) Math.sin(u);
        }

        for (int iu = 0; iu < segU; iu++) {
            float cu1 = SCRATCH_COS_U[iu]; float su1 = SCRATCH_SIN_U[iu];
            float cu2 = SCRATCH_COS_U[iu + 1]; float su2 = SCRATCH_SIN_U[iu + 1];

            for (int iv = 0; iv < segV; iv++) {
                float ring1 = radius + tubeR * SCRATCH_COS_V[iv];
                float ring2 = radius + tubeR * SCRATCH_COS_V[iv + 1];
                float y1 = tubeR * SCRATCH_SIN_V[iv];
                float y2 = tubeR * SCRATCH_SIN_V[iv + 1];

                float x11 = ring1 * cu1; float z11 = ring1 * su1;
                float x12 = ring2 * cu1; float z12 = ring2 * su1;
                float x21 = ring1 * cu2; float z21 = ring1 * su2;
                float x22 = ring2 * cu2; float z22 = ring2 * su2;

                builder.vertex(mat, x11, y1, z11).color(r, g, b, 1F);
                builder.vertex(mat, x12, y2, z12).color(r, g, b, 1F);
                builder.vertex(mat, x22, y2, z22).color(r, g, b, 1F);

                builder.vertex(mat, x11, y1, z11).color(r, g, b, 1F);
                builder.vertex(mat, x22, y2, z22).color(r, g, b, 1F);
                builder.vertex(mat, x21, y1, z21).color(r, g, b, 1F);
            }
        }
        stack.pop();
    }

    private void fillBoxTo(BufferBuilder builder, MatrixStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float thickness, float r, float g, float b, float a) {
        float dx = x2 - x1; float dy = y2 - y1; float dz = z2 - z1;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float pitch = (float) Math.toDegrees(Math.asin(-dy / Math.max(0.0001, distance)));
        float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));

        stack.push();
        stack.translate(x1, y1, z1);
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));

        fillBox(builder, stack, -thickness / 2, -thickness / 2, 0, thickness / 2, thickness / 2, (float) distance, r, g, b, a);
        stack.pop();
    }

    private void fillBox(BufferBuilder builder, MatrixStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
        fillQuad(builder, stack, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1, r, g, b, a);
        fillQuad(builder, stack, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, a);
        fillQuad(builder, stack, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
        fillQuad(builder, stack, x2, y2, z1, x1, y2, z1, x1, y2, z2, x2, y2, z2, r, g, b, a);
        fillQuad(builder, stack, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, r, g, b, a);
        fillQuad(builder, stack, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, a);
    }

    private void fillQuad(BufferBuilder builder, MatrixStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float r, float g, float b, float a) {
        Matrix4f matrix4f = stack.peek().getPositionMatrix();
        builder.vertex(matrix4f, x1, y1, z1).color(r, g, b, a);
        builder.vertex(matrix4f, x2, y2, z2).color(r, g, b, a);
        builder.vertex(matrix4f, x3, y3, z3).color(r, g, b, a);
        builder.vertex(matrix4f, x1, y1, z1).color(r, g, b, a);
        builder.vertex(matrix4f, x3, y3, z3).color(r, g, b, a);
        builder.vertex(matrix4f, x4, y4, z4).color(r, g, b, a);
    }

    /* ---- MOUSE PICKING & HOVER INTERACTION ---- */

    public void updateHover(double mouseX, double mouseY) {
        if (dragging || selectedLight == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            hoveredHandle = STENCIL_NONE;
            return;
        }

        Camera camera = client.gameRenderer.getCamera();
        Vec3d rayDir = getRayDirection(mouseX, mouseY);
        Vec3d rayStart = camera.getPos();

        int picked = checkAxisClickLocal(rayStart, rayDir, selectedLight, mouseX, mouseY);
        this.hoveredHandle = picked;
    }

    public boolean onMouseClicked(double mouseX, double mouseY, int btn) {
        if (btn != 0) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return false;

        Camera camera = client.gameRenderer.getCamera();
        Vec3d rayDir = getRayDirection(mouseX, mouseY);
        Vec3d rayStart = camera.getPos();

        if (selectedLight != null) {
            int clickedAxis = checkAxisClickLocal(rayStart, rayDir, selectedLight, mouseX, mouseY);
            if (clickedAxis != STENCIL_NONE) {
                this.activeHandle = clickedAxis;
                CALUndoManager.pushState();
                this.dragging = true;
                this.dragStartLightPos = new Vec3d(selectedLight.x, selectedLight.y, selectedLight.z);
                this.dragStartRadius = selectedLight.radius;
                this.dragStartDistance = selectedLight.distance;

                if (isTranslateHandle(activeHandle)) {
                    if (activeHandle == STENCIL_X || activeHandle == STENCIL_Y || activeHandle == STENCIL_Z) {
                        int axisIdx = activeHandle == STENCIL_X ? 0 : activeHandle == STENCIL_Y ? 1 : 2;
                        dragStartMousePos = getMouseProjectionOnAxis(rayStart, rayDir, dragStartLightPos, axisIdx);
                    } else if (activeHandle == STENCIL_XZ || activeHandle == STENCIL_XY || activeHandle == STENCIL_ZY) {
                        int planeIdx = activeHandle == STENCIL_XZ ? 3 : activeHandle == STENCIL_XY ? 4 : 5;
                        dragStartMousePos = getMouseProjectionOnPlane(rayStart, rayDir, dragStartLightPos, planeIdx);
                    } else if (activeHandle == STENCIL_FREE) {
                        Vec3d camDir = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
                        dragStartMousePos = getMouseProjectionFree(rayStart, rayDir, dragStartLightPos, camDir);
                    }
                } else if (isRotateHandle(activeHandle)) {
                    this.arcActive = true;
                    this.arcSweep = 0f;
                    if (activeHandle == STENCIL_ROTATE_X) { this.arcAxis = Axis.X; this.arcView = false; }
                    else if (activeHandle == STENCIL_ROTATE_Y) { this.arcAxis = Axis.Y; this.arcView = false; }
                    else if (activeHandle == STENCIL_ROTATE_Z) { this.arcAxis = Axis.Z; this.arcView = false; }
                    else if (activeHandle == STENCIL_VIEW) { this.arcAxis = Axis.Y; this.arcView = true; }

                    if (activeHandle == STENCIL_VIEW || activeHandle == STENCIL_TRACKBALL) {
                        this.dragStartMouseAngle = getMouseAngleAroundLight(mouseX, mouseY, selectedLight);
                    } else {
                        this.dragStart3DAngle = get3DRingAngle(rayStart, rayDir, selectedLight, this.arcAxis);
                        this.arcStartU = (float) this.dragStart3DAngle;
                    }
                } else if (isScaleHandle(activeHandle)) {
                    int axisIdx = activeHandle == STENCIL_SCALE_X ? 0 : activeHandle == STENCIL_SCALE_Y ? 1 : 2;
                    dragStartMousePos = getMouseProjectionOnAxis(rayStart, rayDir, dragStartLightPos, axisIdx);
                    dragProgressActive = true;
                    dragProgressStart.set(0f, 0f, 0f);
                    dragProgressEnd.set(0f, 0f, 0f);
                }

                return true;
            }
        }

        LightInstance clicked = checkBillboardClick(rayStart, rayDir);
        if (clicked != null) {
            setSelectedLight(clicked);
            return true;
        }

        setSelectedLight(null);
        return false;
    }

    public boolean onMouseReleased(double mouseX, double mouseY, int btn) {
        if (btn == 0 && dragging) {
            dragging = false;
            activeHandle = STENCIL_NONE;
            arcActive = false;
            dragProgressActive = false;
            return true;
        }
        return false;
    }

    public boolean onMouseDragged(double mouseX, double mouseY, int btn, double dx, double dy) {
        if (btn == 0 && dragging && selectedLight != null && activeHandle != STENCIL_NONE) {
            MinecraftClient client = MinecraftClient.getInstance();
            Camera camera = client.gameRenderer.getCamera();
            Vec3d rayDir = getRayDirection(mouseX, mouseY);
            Vec3d rayStart = camera.getPos();

            if (isTranslateHandle(activeHandle)) {
                Vec3d currentProj = null;
                if (activeHandle == STENCIL_X || activeHandle == STENCIL_Y || activeHandle == STENCIL_Z) {
                    int axisIdx = activeHandle == STENCIL_X ? 0 : activeHandle == STENCIL_Y ? 1 : 2;
                    currentProj = getMouseProjectionOnAxis(rayStart, rayDir, dragStartLightPos, axisIdx);
                } else if (activeHandle == STENCIL_XZ || activeHandle == STENCIL_XY || activeHandle == STENCIL_ZY) {
                    int planeIdx = activeHandle == STENCIL_XZ ? 3 : activeHandle == STENCIL_XY ? 4 : 5;
                    currentProj = getMouseProjectionOnPlane(rayStart, rayDir, dragStartLightPos, planeIdx);
                } else if (activeHandle == STENCIL_FREE) {
                    Vec3d camDir = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
                    currentProj = getMouseProjectionFree(rayStart, rayDir, dragStartLightPos, camDir);
                }

                if (currentProj != null && dragStartMousePos != null) {
                    Vec3d delta = currentProj.subtract(dragStartMousePos);
                    double nx = dragStartLightPos.x + delta.x;
                    double ny = dragStartLightPos.y + delta.y;
                    double nz = dragStartLightPos.z + delta.z;

                    if (snapToGrid) {
                        nx = Math.round(nx * 2.0) / 2.0;
                        ny = Math.round(ny * 2.0) / 2.0;
                        nz = Math.round(nz * 2.0) / 2.0;
                    }

                    if (activeHandle == STENCIL_X) selectedLight.x = nx;
                    else if (activeHandle == STENCIL_Y) selectedLight.y = ny;
                    else if (activeHandle == STENCIL_Z) selectedLight.z = nz;
                    else if (activeHandle == STENCIL_XZ) { selectedLight.x = nx; selectedLight.z = nz; }
                    else if (activeHandle == STENCIL_XY) { selectedLight.x = nx; selectedLight.y = ny; }
                    else if (activeHandle == STENCIL_ZY) { selectedLight.z = nz; selectedLight.y = ny; }
                    else if (activeHandle == STENCIL_FREE) { selectedLight.x = nx; selectedLight.y = ny; selectedLight.z = nz; }
                }
            } else if (isRotateHandle(activeHandle)) {
                if (activeHandle == STENCIL_VIEW) {
                    double currentAngle = getMouseAngleAroundLight(mouseX, mouseY, selectedLight);
                    double deltaDeg = currentAngle - dragStartMouseAngle;
                    while (deltaDeg > 180.0) deltaDeg -= 360.0;
                    while (deltaDeg < -180.0) deltaDeg += 360.0;
                    dragStartMouseAngle = currentAngle;

                    arcSweep += (float) deltaDeg;

                    Vec3d camFwd = new Vec3d(-this.lastCamDir.x, -this.lastCamDir.y, -this.lastCamDir.z);

                    Quaternionf rot = new Quaternionf().rotationAxis(
                        (float) Math.toRadians(deltaDeg),
                        (float) camFwd.x, (float) camFwd.y, (float) camFwd.z
                    );
                    Vector3f d = new Vector3f(selectedLight.dx, selectedLight.dy, selectedLight.dz).rotate(rot);
                    selectedLight.dx = d.x; selectedLight.dy = d.y; selectedLight.dz = d.z;
                    updateEulerFromSpotDirection(selectedLight);
                } else if (activeHandle == STENCIL_TRACKBALL) {
                    float yawRad = (float) Math.toRadians(camera.getYaw());
                    float pitchRad = (float) Math.toRadians(camera.getPitch());
                    Vec3d camRight = new Vec3d(-Math.cos(yawRad), 0, -Math.sin(yawRad));
                    Vec3d camUp = new Vec3d(-Math.sin(pitchRad) * Math.sin(yawRad), Math.cos(pitchRad), Math.sin(pitchRad) * Math.cos(yawRad));

                    double rotX = dy * 0.4;
                    double rotY = dx * 0.4;

                    Quaternionf qY = new Quaternionf().rotationAxis((float) Math.toRadians(rotY), (float) camUp.x, (float) camUp.y, (float) camUp.z);
                    Quaternionf qX = new Quaternionf().rotationAxis((float) Math.toRadians(rotX), (float) camRight.x, (float) camRight.y, (float) camRight.z);

                    Vector3f d = new Vector3f(selectedLight.dx, selectedLight.dy, selectedLight.dz).rotate(qY).rotate(qX);
                    selectedLight.dx = d.x; selectedLight.dy = d.y; selectedLight.dz = d.z;
                    updateEulerFromSpotDirection(selectedLight);
                } else {
                    double currentAngle = get3DRingAngle(rayStart, rayDir, selectedLight, this.arcAxis);
                    double deltaDeg = currentAngle - dragStart3DAngle;
                    while (deltaDeg > 180.0) deltaDeg -= 360.0;
                    while (deltaDeg < -180.0) deltaDeg += 360.0;
                    dragStart3DAngle = currentAngle;

                    arcSweep += (float) deltaDeg;

                    if (activeHandle == STENCIL_ROTATE_X) selectedLight.rx += (float) deltaDeg;
                    else if (activeHandle == STENCIL_ROTATE_Y) selectedLight.ry += (float) deltaDeg;
                    else if (activeHandle == STENCIL_ROTATE_Z) selectedLight.rz += (float) deltaDeg;

                    recalculateSpotDirection(selectedLight);
                }
            } else if (isScaleHandle(activeHandle)) {
                int axisIdx = activeHandle == STENCIL_SCALE_X ? 0 : activeHandle == STENCIL_SCALE_Y ? 1 : 2;
                Vec3d currentProj = getMouseProjectionOnAxis(rayStart, rayDir, dragStartLightPos, axisIdx);
                if (currentProj != null && dragStartMousePos != null) {
                    Vec3d delta = currentProj.subtract(dragStartMousePos);
                    double distDelta = delta.x + delta.y + delta.z;

                    if (!selectedLight.isSpot) {
                        selectedLight.radius = (float) Math.max(0.1, dragStartRadius + distDelta * 2.0);
                    } else {
                        selectedLight.distance = (float) Math.max(0.5, dragStartDistance + distDelta * 3.0);
                    }

                    float scale = 0.3f;
                    if (axisIdx == 0) dragProgressEnd.set((float) delta.x * scale, 0f, 0f);
                    else if (axisIdx == 1) dragProgressEnd.set(0f, (float) delta.y * scale, 0f);
                    else dragProgressEnd.set(0f, 0f, (float) delta.z * scale);
                }
            }

            return true;
        }
        return false;
    }

    private void recalculateSpotDirection(LightInstance light) {
        float radX = (float) Math.toRadians(light.rx);
        float radY = (float) Math.toRadians(light.ry);
        float radZ = (float) Math.toRadians(light.rz);

        float x = 0f, y = 0f, z = -1f;

        float cosX = (float) Math.cos(radX); float sinX = (float) Math.sin(radX);
        float y1 = y * cosX - z * sinX;
        float z1 = y * sinX + z * cosX;
        float x1 = x;

        float cosY = (float) Math.cos(radY); float sinY = (float) Math.sin(radY);
        float x2 = x1 * cosY + z1 * sinY;
        float y2 = y1;
        float z2 = -x1 * sinY + z1 * cosY;

        float cosZ = (float) Math.cos(radZ); float sinZ = (float) Math.sin(radZ);
        float x3 = x2 * cosZ - y2 * sinZ;
        float y3 = x2 * sinZ + y2 * cosZ;
        float z3 = z2;

        light.dx = x3; light.dy = y3; light.dz = z3;
    }

    private void updateEulerFromSpotDirection(LightInstance light) {
        float dy = Math.max(-1.0f, Math.min(1.0f, light.dy));
        light.rx = (float) Math.toDegrees(Math.asin(dy));
        light.ry = (float) Math.toDegrees(Math.atan2(-light.dx, -light.dz));
    }

    private boolean isTranslateHandle(int handle) {
        return handle == STENCIL_X || handle == STENCIL_Y || handle == STENCIL_Z
                || handle == STENCIL_XZ || handle == STENCIL_XY || handle == STENCIL_ZY || handle == STENCIL_FREE;
    }

    private boolean isRotateHandle(int handle) {
        return handle == STENCIL_ROTATE_X || handle == STENCIL_ROTATE_Y || handle == STENCIL_ROTATE_Z
                || handle == STENCIL_TRACKBALL || handle == STENCIL_VIEW;
    }

    private boolean isScaleHandle(int handle) {
        return handle == STENCIL_SCALE_X || handle == STENCIL_SCALE_Y || handle == STENCIL_SCALE_Z;
    }

    public Vec3d getRayDirection(double mouseX, double mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();

        float x = (2.0f * (float) mouseX) / width - 1.0f;
        float y = 1.0f - (2.0f * (float) mouseY) / height;

        Matrix4f invProj = new Matrix4f(lastProjectionMatrix).invert();
        Vector4f rayClip = new Vector4f(x, y, -1.0f, 1.0f).mul(invProj);
        Vector3f rayEye = new Vector3f(rayClip.x / rayClip.w, rayClip.y / rayClip.w, rayClip.z / rayClip.w);

        Matrix4f invView = new Matrix4f(capturedModelView).invert();
        Vector4f rayWorld4 = new Vector4f(rayEye.x, rayEye.y, rayEye.z, 0.0f).mul(invView);

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

    private int checkAxisClickLocal(Vec3d rayStart, Vec3d rayDir, LightInstance light, double mouseX, double mouseY) {
        Vec3d pos = new Vec3d(light.x, light.y, light.z);
        double dist = rayStart.distanceTo(pos);
        double distanceFactor = dist * 0.12;
        double scale = 1.4 * Math.max(0.5, distanceFactor) * (CalSettings.INSTANCE.gizmoSize / 10.0);

        Vec3d localRayStart = rayStart.subtract(pos).multiply(1.0 / scale);
        Vec3d localRayDir = rayDir;

        float sx = this.lastSx;
        float sy = this.lastSy;
        float sz = this.lastSz;

        // 2D Screen Space check for View Ring (STENCIL_VIEW)
        if (mode == Mode.ROTATE || mode == Mode.COMBINED) {
            Vector2d center2D = getLightScreenPos(pos);
            if (center2D != null) {
                Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
                float yawRad = (float) Math.toRadians(camera.getYaw());
                Vec3d camRight = new Vec3d(-Math.cos(yawRad), 0, -Math.sin(yawRad));
                double viewRingWorldR = 0.22 * COMBINED_ROTATE_SCALE * VIEW_RING_SCALE * scale;
                Vector2d edge2D = getLightScreenPos(pos.add(camRight.multiply(viewRingWorldR)));

                if (edge2D != null) {
                    double ringScreenRadius = center2D.distance(edge2D);
                    double mouseDist = center2D.distance(new Vector2d(mouseX, mouseY));
                    if (Math.abs(mouseDist - ringScreenRadius) <= 14.0) {
                        return STENCIL_VIEW;
                    }
                }
            }
        }

        // Center Free Translate Box
        if ((mode == Mode.TRANSLATE || mode == Mode.COMBINED || mode == Mode.SCALE) &&
                intersectAABB(localRayStart, localRayDir, -0.05, -0.05, -0.05, 0.05, 0.05, 0.05)) {
            return STENCIL_FREE;
        }

        // Plane Handles
        if (mode == Mode.TRANSLATE || mode == Mode.COMBINED) {
            double pIn = 0.08 * COMBINED_MOVE_SCALE;
            double pOut = 0.18 * COMBINED_MOVE_SCALE;
            double off = 0.005;

            if (intersectAABB(localRayStart, localRayDir, Math.min(pIn*sx, pOut*sx), -off, Math.min(pIn*sz, pOut*sz), Math.max(pIn*sx, pOut*sx), off, Math.max(pIn*sz, pOut*sz))) {
                return STENCIL_XZ;
            }
            if (intersectAABB(localRayStart, localRayDir, Math.min(pIn*sx, pOut*sx), Math.min(pIn*sy, pOut*sy), -off, Math.max(pIn*sx, pOut*sx), Math.max(pIn*sy, pOut*sy), off)) {
                return STENCIL_XY;
            }
            if (intersectAABB(localRayStart, localRayDir, -off, Math.min(pIn*sy, pOut*sy), Math.min(pIn*sz, pOut*sz), off, Math.max(pIn*sy, pOut*sy), Math.max(pIn*sz, pOut*sz))) {
                return STENCIL_ZY;
            }
        }

        // Scale Cubes at Tip (Combined or Scale mode)
        if (mode == Mode.SCALE || mode == Mode.COMBINED) {
            double rotateRadius = 0.22 * COMBINED_ROTATE_SCALE;
            double axisLength = rotateRadius * 0.50;
            double half = 0.03;
            double tipPos = (axisLength + 0.05) * sx;

            if (intersectAABB(localRayStart, localRayDir, tipPos - half, -half, -half, tipPos + half, half, half)) return STENCIL_SCALE_X;
            tipPos = (axisLength + 0.05) * sy;
            if (intersectAABB(localRayStart, localRayDir, -half, tipPos - half, -half, half, tipPos + half, half)) return STENCIL_SCALE_Y;
            tipPos = (axisLength + 0.05) * sz;
            if (intersectAABB(localRayStart, localRayDir, -half, -half, tipPos - half, half, half, tipPos + half)) return STENCIL_SCALE_Z;
        }

        // Translate Axis Bars / Cones
        if (mode == Mode.TRANSLATE || mode == Mode.COMBINED) {
            double axisLength = 0.22 * COMBINED_ROTATE_SCALE * 0.50;
            double thick = 0.03;

            if (intersectAABB(localRayStart, localRayDir, Math.min(0, axisLength*sx), -thick, -thick, Math.max(0, axisLength*sx), thick, thick)) return STENCIL_X;
            if (intersectAABB(localRayStart, localRayDir, -thick, Math.min(0, axisLength*sy), -thick, thick, Math.max(0, axisLength*sy), thick)) return STENCIL_Y;
            if (intersectAABB(localRayStart, localRayDir, -thick, -thick, Math.min(0, axisLength*sz), thick, thick, Math.max(0, axisLength*sz))) return STENCIL_Z;
        }

        // Rotation Rings & Trackball
        if (mode == Mode.ROTATE || mode == Mode.COMBINED || mode == Mode.TOP) {
            double ringR = 0.22 * COMBINED_ROTATE_SCALE;
            double ringT = 0.04;
            double viewRingR = ringR * VIEW_RING_SCALE;

            if (intersectViewRing(localRayStart, localRayDir, viewRingR, ringT)) return STENCIL_VIEW;
            if (intersectTorus(localRayStart, localRayDir, Axis.X, ringR, ringT)) return STENCIL_ROTATE_X;
            if (intersectTorus(localRayStart, localRayDir, Axis.Y, ringR, ringT)) return STENCIL_ROTATE_Y;
            if (intersectTorus(localRayStart, localRayDir, Axis.Z, ringR, ringT)) return STENCIL_ROTATE_Z;

            double trackR = ringR * 1.85 * 0.5;
            if (hitsTrackball(localRayStart, localRayDir, trackR)) {
                return STENCIL_TRACKBALL;
            }
        }

        return STENCIL_NONE;
    }

    private boolean hitsTrackball(Vec3d localOrigin, Vec3d localDir, double radius) {
        Vector2d hit = new Vector2d();
        return Intersectiond.intersectRaySphere(
                localOrigin.x, localOrigin.y, localOrigin.z,
                localDir.x, localDir.y, localDir.z,
                0D, 0D, 0D,
                radius * radius, hit
        );
    }

    private boolean intersectViewRing(Vec3d localRayStart, Vec3d localRayDir, double radius, double thickness) {
        Vec3d normal = new Vec3d(this.lastCamDir.x, this.lastCamDir.y, this.lastCamDir.z);
        double denom = normal.dotProduct(localRayDir);
        if (Math.abs(denom) < 1e-6) return false;

        double t = -localRayStart.dotProduct(normal) / denom;
        if (t < 0) return false;

        Vec3d hit = localRayStart.add(localRayDir.multiply(t));
        double dist = hit.length();
        return Math.abs(dist - radius) <= thickness * 1.5;
    }

    private boolean intersectTorus(Vec3d rayStart, Vec3d rayDir, Axis axis, double majorR, double minorR) {
        int samples = 32;
        for (int i = 0; i < samples; i++) {
            double ang = Math.PI * 2.0 * i / samples;
            double px = 0, py = 0, pz = 0;
            if (axis == Axis.X) { py = majorR * Math.cos(ang); pz = majorR * Math.sin(ang); }
            else if (axis == Axis.Y) { px = majorR * Math.cos(ang); pz = majorR * Math.sin(ang); }
            else { px = majorR * Math.cos(ang); py = majorR * Math.sin(ang); }

            double dist = getDistanceToPoint(rayStart, rayDir, new Vec3d(px, py, pz));
            if (dist <= minorR * 1.5) return true;
        }
        return false;
    }

    private boolean intersectAABB(Vec3d rayStart, Vec3d rayDir, double minX, double minY, double minZ, double maxX,
            double maxY, double maxZ) {
        double tmin = (minX - rayStart.x) / rayDir.x;
        double tmax = (maxX - rayStart.x) / rayDir.x;
        if (tmin > tmax) { double temp = tmin; tmin = tmax; tmax = temp; }

        double tymin = (minY - rayStart.y) / rayDir.y;
        double tymax = (maxY - rayStart.y) / rayDir.y;
        if (tymin > tymax) { double temp = tymin; tymin = tymax; tymax = temp; }

        if ((tmin > tymax) || (tymin > tmax)) return false;
        if (tymin > tmin) tmin = tymin;
        if (tymax < tmax) tmax = tymax;

        double tzmin = (minZ - rayStart.z) / rayDir.z;
        double tzmax = (maxZ - rayStart.z) / rayDir.z;
        if (tzmin > tzmax) { double temp = tzmin; tzmin = tzmax; tzmax = temp; }

        if ((tmin > tzmax) || (tzmin > tmax)) return false;
        if (tzmin > tmin) tmin = tzmin;
        if (tzmax < tmax) tmax = tzmax;

        return tmax >= 0;
    }

    private double getDistanceToPoint(Vec3d rayStart, Vec3d rayDir, Vec3d point) {
        Vec3d w = point.subtract(rayStart);
        double c1 = w.dotProduct(rayDir);
        double c2 = rayDir.dotProduct(rayDir);
        double b = c1 / c2;
        Vec3d pb = rayStart.add(rayDir.multiply(b));
        return point.distanceTo(pb);
    }

    private Vec3d getMouseProjectionOnAxis(Vec3d rayStart, Vec3d rayDir, Vec3d axisOrigin, int axis) {
        Vec3d axisDir = (axis == 0) ? new Vec3d(1, 0, 0) : (axis == 1) ? new Vec3d(0, 1, 0) : new Vec3d(0, 0, 1);
        Vec3d w0 = axisOrigin.subtract(rayStart);
        double a = axisDir.dotProduct(axisDir);
        double b = axisDir.dotProduct(rayDir);
        double c = rayDir.dotProduct(rayDir);
        double d = axisDir.dotProduct(w0);
        double e = rayDir.dotProduct(w0);

        double denom = a * c - b * b;
        if (Math.abs(denom) < 1e-5) return axisOrigin;

        double s = (b * e - c * d) / denom;
        return axisOrigin.add(axisDir.multiply(s));
    }

    private Vec3d getMouseProjectionOnPlane(Vec3d rayStart, Vec3d rayDir, Vec3d planeOrigin, int plane) {
        Vec3d normal = (plane == 3) ? new Vec3d(0, 1, 0) : (plane == 4) ? new Vec3d(0, 0, 1) : new Vec3d(1, 0, 0);
        double denom = normal.dotProduct(rayDir);
        if (Math.abs(denom) < 1e-5) return planeOrigin;

        double t = (planeOrigin.subtract(rayStart)).dotProduct(normal) / denom;
        return rayStart.add(rayDir.multiply(t));
    }

    private Vec3d getMouseProjectionFree(Vec3d rayStart, Vec3d rayDir, Vec3d origin, Vec3d camDir) {
        double denom = camDir.dotProduct(rayDir);
        if (Math.abs(denom) < 1e-5) return origin;

        double t = (origin.subtract(rayStart)).dotProduct(camDir) / denom;
        return rayStart.add(rayDir.multiply(t));
    }

    private Vector2d getLightScreenPos(Vec3d lightPos) {
        if (lightPos == null) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();

        Camera camera = client.gameRenderer.getCamera();
        Vec3d camPos = camera.getPos();

        Vector4f eye = new Vector4f(
            (float)(lightPos.x - camPos.x),
            (float)(lightPos.y - camPos.y),
            (float)(lightPos.z - camPos.z),
            1.0f
        ).mul(capturedModelView);

        Vector4f clip = new Vector4f(eye).mul(lastProjectionMatrix);
        if (clip.w <= 1e-6f) return null;

        double ndcX = clip.x / clip.w;
        double ndcY = clip.y / clip.w;

        double centerX = (ndcX * 0.5 + 0.5) * width;
        double centerY = (0.5 - ndcY * 0.5) * height;

        return new Vector2d(centerX, centerY);
    }

    private double getMouseAngleAroundLight(double mouseX, double mouseY, LightInstance light) {
        if (light == null) return 0.0;
        Vector2d center = getLightScreenPos(new Vec3d(light.x, light.y, light.z));
        if (center == null) return 0.0;
        return Math.toDegrees(Math.atan2(mouseY - center.y, mouseX - center.x));
    }

    private double get3DRingAngle(Vec3d rayStart, Vec3d rayDir, LightInstance light, Axis axis) {
        if (light == null) return 0.0;
        Vec3d lightPos = new Vec3d(light.x, light.y, light.z);
        Vec3d normal;

        if (axis == Axis.X) normal = new Vec3d(1, 0, 0);
        else if (axis == Axis.Y) normal = new Vec3d(0, 1, 0);
        else normal = new Vec3d(0, 0, 1);

        double denom = normal.dotProduct(rayDir);
        if (Math.abs(denom) < 1e-6) return 0.0;

        double t = lightPos.subtract(rayStart).dotProduct(normal) / denom;
        Vec3d hitPoint = rayStart.add(rayDir.multiply(t));
        Vec3d local = hitPoint.subtract(lightPos);

        double rad;
        if (axis == Axis.X) rad = Math.atan2(local.z, local.y);
        else if (axis == Axis.Y) rad = Math.atan2(local.z, local.x);
        else rad = Math.atan2(local.y, local.x);

        return Math.toDegrees(rad);
    }
}
