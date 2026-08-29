package elgatopro300.cal_lights.gizmo;

import elgatopro300.cal_lights.graphics.CALLayers;
import elgatopro300.cal_lights.graphics.CLIcon;
import elgatopro300.cal_lights.graphics.CLTexture;
import elgatopro300.cal_lights.graphics.CalLightsIcons;
import elgatopro300.cal_lights.manager.CALUndoManager;
import elgatopro300.cal_lights.manager.LightInstance;
import elgatopro300.cal_lights.manager.LightManager;
import elgatopro300.cal_lights.ui.CALEditorScreen;
import elgatopro300.cal_lights.ui.CalSettings;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.world.phys.Vec3;

import org.joml.Intersectiond;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

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
        TRANSLATE, ROTATE, COMBINED, TOP
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
    public static boolean snapAngles = false;

    private Mode mode = Mode.TRANSLATE;
    private int activeHandle = STENCIL_NONE;
    private int hoveredHandle = STENCIL_NONE;
    private LightInstance selectedLight = null;

    // Captured matrices & viewing vectors
    private final Matrix4f lastProjectionMatrix = new Matrix4f();
    private final Matrix4f capturedModelView = new Matrix4f();
    private boolean captured = false;
    private final ProjectionMatrixBuffer rawProjection = new ProjectionMatrixBuffer("cal_gizmo");

    private float lastSx = 1F;
    private float lastSy = 1F;
    private float lastSz = 1F;
    private final Vector3f lastCamDir = new Vector3f(0f, 1f, 0f);

    // Drag start states
    private boolean dragging = false;
    private Vec3 dragStartLightPos = Vec3.ZERO;
    private Vec3 dragStartMousePos = null;
    private float dragStartRadius = 0f;
    private float dragStartDistance = 0f;
    private double dragStartMouseAngle = 0.0;
    private double dragStart3DAngle = 0.0;

    // Rotation Arc visual feedback
    private boolean arcActive = false;
    private Axis arcAxis = Axis.Y;
    private boolean arcView = false;
    private float arcStartU = 0f;
    private float arcSweep = 0f;

    // Drag progress indicator
    private boolean dragProgressActive = false;
    private final Vector3f dragProgressStart = new Vector3f();
    private final Vector3f dragProgressEnd = new Vector3f();

    public void init() {
        LevelRenderEvents.END_MAIN.register(context -> {
            if (context.levelState() != null && context.levelState().cameraRenderState != null && context.levelState().cameraRenderState.projectionMatrix != null) {
                this.lastProjectionMatrix.set(context.levelState().cameraRenderState.projectionMatrix);
                this.captured = true;
            }
        });
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

    public Matrix4f getViewMatrix(Camera camera) {
        return new Matrix4f().rotation(new Quaternionf(camera.rotation()).conjugate());
    }

    public void renderInWorld() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        if (!(client.screen instanceof CALEditorScreen)) return;

        Camera camera = client.gameRenderer.getMainCamera();
        if (camera != null) {
            this.capturedModelView.set(getViewMatrix(camera));
            this.captured = true;
        }

        renderOverlay();
    }

    public void renderOverlay(GuiGraphicsExtractor drawContext) {
        renderOverlay();
    }

    /**
     * Draws light billboards, indicators, and the full 3D Gizmo overlay in the GUI phase.
     */
    public void renderOverlay() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !captured) return;
        if (!(client.screen instanceof CALEditorScreen)) return;

        if (CalSettings.INSTANCE.gizmoMode >= 0 && CalSettings.INSTANCE.gizmoMode < Mode.values().length) {
            this.mode = Mode.values()[CalSettings.INSTANCE.gizmoMode];
        }

        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 camPos = camera.position();
        Matrix4f viewMatrix = getViewMatrix(camera);

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(this.rawProjection.getBuffer(lastProjectionMatrix), ProjectionType.PERSPECTIVE);

        Matrix4fStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushMatrix();
        mvStack.set(viewMatrix);

        PoseStack stack = new PoseStack();

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

        mvStack.popMatrix();
        RenderSystem.restoreProjectionMatrix();
    }

    private void drawBillboards(PoseStack stack, Vec3 camPos, Collection<LightInstance> lights, boolean isSpot) {
        CLIcon icon = isSpot ? CalLightsIcons.SPOT_LIGHT : CalLightsIcons.POINT_LIGHT;
        if (icon == null) return;

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Quaternionf camRot = camera.rotation();

        for (LightInstance light : lights) {
            if (!renderLightIcons && light != selectedLight) continue;

            stack.pushPose();
            stack.translate(light.x - camPos.x, light.y - camPos.y, light.z - camPos.z);
            stack.mulPose(camRot);

            double dist = camPos.distanceTo(new Vec3(light.x, light.y, light.z));
            float size = (float) (0.3f * Math.max(1.0, dist * 0.15));
            if (light == selectedLight) {
                size = (float) (0.4f * Math.max(1.0, dist * 0.15));
            }

            if (icon.staticTexture != null && icon.tintTexture != null) {
                int tintColor = (light == selectedLight) ? 0xFFFFAA00 : (0xFF000000 | ((int) (light.r * 255) << 16) | ((int) (light.g * 255) << 8) | (int) (light.b * 255));
                drawBillboardQuad(stack, size, icon.tintTexture, icon.x, icon.y, icon.w, icon.h, tintColor);
                drawBillboardQuad(stack, size, icon.staticTexture, icon.x, icon.y, icon.w, icon.h, 0xFFFFFFFF);
            } else if (icon.texture != null) {
                int color = (light == selectedLight) ? 0xFFFFAA00 : 0xFFFFFFFF;
                drawBillboardQuad(stack, size, icon.texture, icon.x, icon.y, icon.w, icon.h, color);
            }
            stack.popPose();
        }
    }

    private void drawBillboardQuad(PoseStack stack, float size, CLTexture texture, int texX, int texY, int texW, int texH, int color) {
        if (texture == null) return;
        Matrix4f matrix = stack.last().pose();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        float u1 = texX / (float) texture.width;
        float v1 = texY / (float) texture.height;
        float u2 = (texX + texW) / (float) texture.width;
        float v2 = (texY + texH) / (float) texture.height;

        builder.addVertex(matrix, -size, -size, 0).setUv(u1, v2).setColor(color);
        builder.addVertex(matrix, size, -size, 0).setUv(u2, v2).setColor(color);
        builder.addVertex(matrix, size, size, 0).setUv(u2, v1).setColor(color);
        builder.addVertex(matrix, -size, size, 0).setUv(u1, v1).setColor(color);

        CALLayers.flush(builder, CALLayers.getPositionTexColorNoDepthLayer(texture.identifier));
    }

    private void drawLightIndicators(PoseStack stack, Vec3 camPos, LightInstance light) {
        stack.pushPose();
        stack.translate(light.x - camPos.x, light.y - camPos.y, light.z - camPos.z);

        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

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

        CALLayers.flushLines(builder);
        stack.popPose();
    }

    private void drawPointIndicator(BufferBuilder builder, PoseStack stack, LightInstance light, float r, float g, float b, float a) {
        float radius = light.radius;
        int segments = 64;
        Matrix4f matrix = stack.last().pose();

        for (int i = 0; i < segments; i++) {
            float angle1 = (float) (2.0 * Math.PI * i / segments);
            float angle2 = (float) (2.0 * Math.PI * (i + 1) / segments);

            float x1 = radius * (float) Math.cos(angle1);
            float z1 = radius * (float) Math.sin(angle1);
            float x2 = radius * (float) Math.cos(angle2);
            float z2 = radius * (float) Math.sin(angle2);

            builder.addVertex(matrix, x1, 0, z1).setColor(r, g, b, a);
            builder.addVertex(matrix, x2, 0, z2).setColor(r, g, b, a);
        }

        for (int i = 0; i < segments; i++) {
            float angle1 = (float) (2.0 * Math.PI * i / segments);
            float angle2 = (float) (2.0 * Math.PI * (i + 1) / segments);

            float x1 = radius * (float) Math.cos(angle1);
            float y1 = radius * (float) Math.sin(angle1);
            float x2 = radius * (float) Math.cos(angle2);
            float y2 = radius * (float) Math.sin(angle2);

            builder.addVertex(matrix, x1, y1, 0).setColor(r, g, b, a);
            builder.addVertex(matrix, x2, y2, 0).setColor(r, g, b, a);
        }

        for (int i = 0; i < segments; i++) {
            float angle1 = (float) (2.0 * Math.PI * i / segments);
            float angle2 = (float) (2.0 * Math.PI * (i + 1) / segments);

            float y1 = radius * (float) Math.cos(angle1);
            float z1 = radius * (float) Math.sin(angle1);
            float y2 = radius * (float) Math.cos(angle2);
            float z2 = radius * (float) Math.sin(angle2);

            builder.addVertex(matrix, 0, y1, z1).setColor(r, g, b, a);
            builder.addVertex(matrix, 0, y2, z2).setColor(r, g, b, a);
        }
    }

    private void drawSpotIndicator(BufferBuilder builder, PoseStack stack, LightInstance light, float r, float g, float b, float a) {
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

        Matrix4f matrix = stack.last().pose();
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

            builder.addVertex(matrix, p1x, p1y, p1z).setColor(r, g, b, a);
            builder.addVertex(matrix, p2x, p2y, p2z).setColor(r, g, b, a);
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

                builder.addVertex(matrix, p1x, p1y, p1z).setColor(r, g, b, innerA);
                builder.addVertex(matrix, p2x, p2y, p2z).setColor(r, g, b, innerA);
            }
        }

        float[] angles = {0f, (float) (Math.PI / 2.0), (float) Math.PI, (float) (3.0 * Math.PI / 2.0)};
        for (float angle : angles) {
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            float px = cx + outerRad * (cos * ux + sin * vx);
            float py = cy + outerRad * (cos * uy + sin * vy);
            float pz = cz + outerRad * (cos * uz + sin * vz);

            builder.addVertex(matrix, 0f, 0f, 0f).setColor(r, g, b, a);
            builder.addVertex(matrix, px, py, pz).setColor(r, g, b, a);
        }
    }

    /* ---- BBS CML-STYLE 3D GIZMO RENDERING ---- */

    private void drawGizmo3D(PoseStack stack, Vec3 camPos, LightInstance light) {
        stack.pushPose();
        stack.translate(light.x - camPos.x, light.y - camPos.y, light.z - camPos.z);

        double dist = camPos.distanceTo(new Vec3(light.x, light.y, light.z));
        float distanceFactor = (float) (dist * 0.12);
        float scale = (float) (1.4f * Math.max(0.5f, distanceFactor) * (CalSettings.INSTANCE.gizmoSize / 10.0f));

        this.updateFlipSigns(camPos, light);

        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        if (this.mode == Mode.ROTATE) drawRotate(builder, stack, scale);
        else if (this.mode == Mode.COMBINED) drawCombined(builder, stack, scale);
        else if (this.mode == Mode.TOP) drawTop(builder, stack, scale);
        else drawTranslate(builder, stack, scale);

        drawActiveGuide(builder, stack, scale);
        drawDragProgress(builder, stack, scale);

        try {
            CALLayers.flushTrianglesNoDepth(builder);
        } catch (IllegalStateException ignored) {}

        stack.popPose();
    }

    private void updateFlipSigns(Vec3 camPos, LightInstance light) {
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

    private void drawTranslate(BufferBuilder builder, PoseStack stack, float scale) {
        float moveScale = scale * COMBINED_MOVE_SCALE;
        float axisSize = 0.22F * COMBINED_ROTATE_SCALE * scale * 0.50F;
        float axisOffset = 0.0075F * moveScale;
        float planeInner = 0.08F * moveScale;
        float planeOuter = 0.18F * moveScale;
        float offset = 0.001F * moveScale;

        drawMoveBars(builder, stack, axisSize, axisOffset);
        drawMovePlanes(builder, stack, planeInner, planeOuter, offset);
        drawScreenCube(builder, stack, axisOffset);
    }

    private void drawMoveBars(BufferBuilder builder, PoseStack stack, float axisSize, float axisOffset) {
        float[] xCol = pickColor(STENCIL_X, COLOR_X_IDLE, COLOR_X_HOVER);
        float[] yCol = pickColor(STENCIL_Y, COLOR_Y_IDLE, COLOR_Y_HOVER);
        float[] zCol = pickColor(STENCIL_Z, COLOR_Z_IDLE, COLOR_Z_HOVER);

        float tipLength = Math.abs(axisSize) * 0.35F;
        float tipRadius = axisOffset * 2.4F;
        int coneSegments = 10;

        if (showHandle(STENCIL_X)) {
            fillBox(builder, stack, 0, -axisOffset, -axisOffset, axisSize * this.lastSx, axisOffset, axisOffset, xCol[0], xCol[1], xCol[2], 1F);
            cone(builder, stack, (axisSize + tipLength) * this.lastSx, 0, 0, axisSize * this.lastSx, 0, 0, tipRadius, coneSegments, xCol[0], xCol[1], xCol[2], 1F);
        }

        if (showHandle(STENCIL_Y)) {
            fillBox(builder, stack, -axisOffset, 0, -axisOffset, axisOffset, axisSize * this.lastSy, axisOffset, yCol[0], yCol[1], yCol[2], 1F);
            cone(builder, stack, 0, (axisSize + tipLength) * this.lastSy, 0, 0, axisSize * this.lastSy, 0, tipRadius, coneSegments, yCol[0], yCol[1], yCol[2], 1F);
        }

        if (showHandle(STENCIL_Z)) {
            fillBox(builder, stack, -axisOffset, -axisOffset, 0, axisOffset, axisOffset, axisSize * this.lastSz, zCol[0], zCol[1], zCol[2], 1F);
            cone(builder, stack, 0, 0, (axisSize + tipLength) * this.lastSz, 0, 0, axisSize * this.lastSz, tipRadius, coneSegments, zCol[0], zCol[1], zCol[2], 1F);
        }
    }

    private void drawMovePlanes(BufferBuilder builder, PoseStack stack, float planeInner, float planeOuter, float offset) {
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

    private void drawScreenCube(BufferBuilder builder, PoseStack stack, float axisOffset) {
        if (!showHandle(STENCIL_FREE)) return;
        float[] color = pickColor(STENCIL_FREE, COLOR_FREE_IDLE, COLOR_FREE_IDLE);
        fillBox(builder, stack, -axisOffset, -axisOffset, -axisOffset, axisOffset, axisOffset, axisOffset, color[0], color[1], color[2], 1F);
    }

    /* ---- Scale handles ---- */

    private void drawScale(BufferBuilder builder, PoseStack stack, float scale) {
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

    private void drawTop(BufferBuilder builder, PoseStack stack, float scale) {
        float radius = 0.22F * scale;
        float topRadius = radius * 1.85F * 0.5F;
        boolean active = this.activeHandle == STENCIL_TRACKBALL;
        float sa = active ? 0.32F : 0.16F;
        sphere(builder, stack, topRadius, 16, 24, 0.92F, 0.92F, 0.92F, sa);
    }

    private void drawRotate(BufferBuilder builder, PoseStack stack, float scale) {
        float rotateRadius = 0.22F * scale * COMBINED_ROTATE_SCALE;
        float ringThickness = 0.010F * scale;
        drawTrackball(builder, stack, rotateRadius * 1.85F * 0.5F);
        drawRings(builder, stack, rotateRadius, ringThickness, STENCIL_ROTATE_X, STENCIL_ROTATE_Y, STENCIL_ROTATE_Z);
        drawViewRing(builder, stack, rotateRadius * VIEW_RING_SCALE, ringThickness);
    }

    private void drawRings(BufferBuilder builder, PoseStack stack, float radius, float ringThickness, int idX, int idY, int idZ) {
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

    private void drawTrackball(BufferBuilder builder, PoseStack stack, float radius) {
        if (!showHandle(STENCIL_TRACKBALL)) return;
        boolean active = this.activeHandle == STENCIL_TRACKBALL;
        float alpha = active ? 0.28F : 0.14F;
        sphere(builder, stack, radius, 16, 24, 0.92F, 0.92F, 0.92F, alpha);
    }

    private void drawViewRing(BufferBuilder builder, PoseStack stack, float radius, float ringThickness) {
        if (!showHandle(STENCIL_VIEW)) return;
        float[] color = pickColor(STENCIL_VIEW, COLOR_VIEW_IDLE, COLOR_VIEW_HOVER);

        stack.pushPose();
        stack.mulPose(new Quaternionf().rotationTo(0F, 1F, 0F, this.lastCamDir.x, this.lastCamDir.y, this.lastCamDir.z));
        arc3D(builder, stack, Axis.Y, radius, ringThickness, color[0], color[1], color[2], 0F, 360F);

        if (this.arcActive && this.arcView && this.activeHandle == STENCIL_VIEW) {
            drawRotationSweepArc(builder, stack, Axis.Y, radius * 0.9F, ringThickness * 1.5F, true);
        }

        stack.popPose();
    }

    private void drawRotationSweepArc(BufferBuilder builder, PoseStack stack, Axis axis, float radius, float thickness, boolean viewRing) {
        if (Math.abs(this.arcSweep) <= 0.01F) return;
        float[] color = viewRing ? COLOR_ACTIVE : (axis == Axis.X ? COLOR_X_HOVER : axis == Axis.Y ? COLOR_Y_HOVER : COLOR_Z_HOVER);
        arc3D(builder, stack, axis, radius, thickness, color[0], color[1], color[2], this.arcStartU, this.arcSweep);
    }

    /* ---- Combined mode ---- */

    private void drawCombined(BufferBuilder builder, PoseStack stack, float scale) {
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

        drawMoveBars(builder, stack, axisSize, axisOffset);
        drawMovePlanes(builder, stack, planeInner, planeOuter, offset);
        drawScreenCube(builder, stack, axisOffset);

        drawRings(builder, stack, rotateRadius, ringThickness, STENCIL_ROTATE_X, STENCIL_ROTATE_Y, STENCIL_ROTATE_Z);
        drawViewRing(builder, stack, rotateRadius * VIEW_RING_SCALE, ringThickness);
    }

    /* ---- Active guide & progress lines ---- */

    private void drawActiveGuide(BufferBuilder builder, PoseStack stack, float scale) {
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

    private void drawGuideLine(BufferBuilder builder, PoseStack stack, float scale, Axis axis, float[] color) {
        float length = 10F * scale * 2F;
        float t = 0.0025F * scale * 2F;
        if (axis == Axis.X) fillBox(builder, stack, -length, -t, -t, length, t, t, color[0], color[1], color[2], 0.35F);
        else if (axis == Axis.Y) fillBox(builder, stack, -t, -length, -t, t, length, t, color[0], color[1], color[2], 0.35F);
        else fillBox(builder, stack, -t, -t, -length, t, t, length, color[0], color[1], color[2], 0.35F);
    }

    private void drawDragProgress(BufferBuilder builder, PoseStack stack, float scale) {
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

    private void cone(BufferBuilder builder, PoseStack stack, float apexX, float apexY, float apexZ, float baseX, float baseY, float baseZ, float radius, int segments, float r, float g, float b, float a) {
        Matrix4f mat = stack.last().pose();

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

            builder.addVertex(mat, apexX, apexY, apexZ).setColor(r, g, b, a);
            builder.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);
            builder.addVertex(mat, x2, y2, z2).setColor(r, g, b, a);

            builder.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);
            builder.addVertex(mat, baseX, baseY, baseZ).setColor(r, g, b, a);
            builder.addVertex(mat, x2, y2, z2).setColor(r, g, b, a);
        }
    }

    private void sphere(BufferBuilder builder, PoseStack stack, float radius, int rings, int sectors, float r, float g, float b, float a) {
        Matrix4f mat = stack.last().pose();

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

                builder.addVertex(mat, x11, y11, z11).setColor(r, g, b, a);
                builder.addVertex(mat, x12, y12, z12).setColor(r, g, b, a);
                builder.addVertex(mat, x22, y22, z22).setColor(r, g, b, a);

                builder.addVertex(mat, x11, y11, z11).setColor(r, g, b, a);
                builder.addVertex(mat, x22, y22, z22).setColor(r, g, b, a);
                builder.addVertex(mat, x21, y21, z21).setColor(r, g, b, a);
            }
        }
    }

    private void arc3D(BufferBuilder builder, PoseStack stack, Axis axis, float radius, float thickness, float r, float g, float b, float startDeg, float sweepDeg) {
        float absSweep = Math.abs(sweepDeg);
        if (absSweep < 0.01F || thickness <= 0F || radius <= 0F) return;

        int segU = Math.max(12, Math.round(64F * absSweep / 360F));
        segU = Math.min(segU, SCRATCH_COS_U.length - 1);
        int segV = 10;
        segV = Math.min(segV, SCRATCH_COS_V.length - 1);

        double u0 = Math.toRadians(startDeg);
        double uStep = Math.toRadians(sweepDeg / (double) segU);
        double vStep = Math.PI * 2D / (double) segV;

        stack.pushPose();
        if (axis == Axis.X) stack.mulPose(com.mojang.math.Axis.ZP.rotation((float) (Math.PI / 2F)));
        if (axis == Axis.Z) stack.mulPose(com.mojang.math.Axis.XN.rotation((float) (Math.PI / 2F)));

        float tubeR = thickness * 0.5F;
        Matrix4f mat = stack.last().pose();

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

                builder.addVertex(mat, x11, y1, z11).setColor(r, g, b, 1F);
                builder.addVertex(mat, x12, y2, z12).setColor(r, g, b, 1F);
                builder.addVertex(mat, x22, y2, z22).setColor(r, g, b, 1F);

                builder.addVertex(mat, x11, y1, z11).setColor(r, g, b, 1F);
                builder.addVertex(mat, x22, y2, z22).setColor(r, g, b, 1F);
                builder.addVertex(mat, x21, y1, z21).setColor(r, g, b, 1F);
            }
        }
        stack.popPose();
    }

    private void fillBoxTo(BufferBuilder builder, PoseStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float thickness, float r, float g, float b, float a) {
        float dx = x2 - x1; float dy = y2 - y1; float dz = z2 - z1;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float pitch = (float) Math.toDegrees(Math.asin(-dy / Math.max(0.0001, distance)));
        float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));

        stack.pushPose();
        stack.translate(x1, y1, z1);
        stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
        stack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pitch));

        fillBox(builder, stack, -thickness / 2, -thickness / 2, 0, thickness / 2, thickness / 2, (float) distance, r, g, b, a);
        stack.popPose();
    }

    private void fillBox(BufferBuilder builder, PoseStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
        fillQuad(builder, stack, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1, r, g, b, a);
        fillQuad(builder, stack, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, a);
        fillQuad(builder, stack, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
        fillQuad(builder, stack, x2, y2, z1, x1, y2, z1, x1, y2, z2, x2, y2, z2, r, g, b, a);
        fillQuad(builder, stack, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, r, g, b, a);
        fillQuad(builder, stack, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, a);
    }

    private void fillQuad(BufferBuilder builder, PoseStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float r, float g, float b, float a) {
        Matrix4f matrix4f = stack.last().pose();
        builder.addVertex(matrix4f, x1, y1, z1).setColor(r, g, b, a);
        builder.addVertex(matrix4f, x2, y2, z2).setColor(r, g, b, a);
        builder.addVertex(matrix4f, x3, y3, z3).setColor(r, g, b, a);
        builder.addVertex(matrix4f, x1, y1, z1).setColor(r, g, b, a);
        builder.addVertex(matrix4f, x3, y3, z3).setColor(r, g, b, a);
        builder.addVertex(matrix4f, x4, y4, z4).setColor(r, g, b, a);
    }

    /* ---- MOUSE PICKING & HOVER INTERACTION ---- */

    public void updateHover(double mouseX, double mouseY) {
        if (dragging || selectedLight == null) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            hoveredHandle = STENCIL_NONE;
            return;
        }

        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 rayDir = getRayDirection(mouseX, mouseY);
        Vec3 rayStart = camera.position();

        int picked = checkAxisClickLocal(rayStart, rayDir, selectedLight, mouseX, mouseY);
        this.hoveredHandle = picked;
    }

    public boolean onMouseClicked(double mouseX, double mouseY, int btn) {
        if (btn != 0) return false;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return false;

        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 rayDir = getRayDirection(mouseX, mouseY);
        Vec3 rayStart = camera.position();

        if (selectedLight != null) {
            int clickedAxis = checkAxisClickLocal(rayStart, rayDir, selectedLight, mouseX, mouseY);
            if (clickedAxis != STENCIL_NONE) {
                this.activeHandle = clickedAxis;
                CALUndoManager.pushState();
                this.dragging = true;
                this.dragStartLightPos = new Vec3(selectedLight.x, selectedLight.y, selectedLight.z);
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
                        Vec3 camDir = Vec3.directionFromRotation(camera.xRot(), camera.yRot());
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
            Minecraft client = Minecraft.getInstance();
            Camera camera = client.gameRenderer.getMainCamera();
            Vec3 rayDir = getRayDirection(mouseX, mouseY);
            Vec3 rayStart = camera.position();

            if (isTranslateHandle(activeHandle)) {
                Vec3 currentProj = null;
                if (activeHandle == STENCIL_X || activeHandle == STENCIL_Y || activeHandle == STENCIL_Z) {
                    int axisIdx = activeHandle == STENCIL_X ? 0 : activeHandle == STENCIL_Y ? 1 : 2;
                    currentProj = getMouseProjectionOnAxis(rayStart, rayDir, dragStartLightPos, axisIdx);
                } else if (activeHandle == STENCIL_XZ || activeHandle == STENCIL_XY || activeHandle == STENCIL_ZY) {
                    int planeIdx = activeHandle == STENCIL_XZ ? 3 : activeHandle == STENCIL_XY ? 4 : 5;
                    currentProj = getMouseProjectionOnPlane(rayStart, rayDir, dragStartLightPos, planeIdx);
                } else if (activeHandle == STENCIL_FREE) {
                    Vec3 camDir = Vec3.directionFromRotation(camera.xRot(), camera.yRot());
                    currentProj = getMouseProjectionFree(rayStart, rayDir, dragStartLightPos, camDir);
                }

                if (currentProj != null && dragStartMousePos != null) {
                    Vec3 delta = currentProj.subtract(dragStartMousePos);
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

                    Vec3 camFwd = new Vec3(-this.lastCamDir.x, -this.lastCamDir.y, -this.lastCamDir.z);

                    Quaternionf rot = new Quaternionf().rotationAxis(
                        (float) Math.toRadians(deltaDeg),
                        (float) camFwd.x, (float) camFwd.y, (float) camFwd.z
                    );
                    Vector3f d = new Vector3f(selectedLight.dx, selectedLight.dy, selectedLight.dz).rotate(rot);
                    selectedLight.dx = d.x; selectedLight.dy = d.y; selectedLight.dz = d.z;
                    updateEulerFromSpotDirection(selectedLight);
                } else if (activeHandle == STENCIL_TRACKBALL) {
                    float yawRad = (float) Math.toRadians(camera.yRot());
                    float pitchRad = (float) Math.toRadians(camera.xRot());
                    Vec3 camRight = new Vec3(-Math.cos(yawRad), 0, -Math.sin(yawRad));
                    Vec3 camUp = new Vec3(-Math.sin(pitchRad) * Math.sin(yawRad), Math.cos(pitchRad), Math.sin(pitchRad) * Math.cos(yawRad));

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
                Vec3 currentProj = getMouseProjectionOnAxis(rayStart, rayDir, dragStartLightPos, axisIdx);
                if (currentProj != null && dragStartMousePos != null) {
                    Vec3 delta = currentProj.subtract(dragStartMousePos);
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

    public Vec3 getRayDirection(double mouseX, double mouseY) {
        Minecraft client = Minecraft.getInstance();
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();

        float x = (2.0f * (float) mouseX) / width - 1.0f;
        float y = 1.0f - (2.0f * (float) mouseY) / height;

        Matrix4f invProj = new Matrix4f(lastProjectionMatrix).invert();
        Vector4f rayClip = new Vector4f(x, y, -1.0f, 1.0f).mul(invProj);
        Vector3f rayEye = new Vector3f(rayClip.x / rayClip.w, rayClip.y / rayClip.w, rayClip.z / rayClip.w);

        Camera camera = client.gameRenderer.getMainCamera();
        Matrix4f invView = (camera != null ? getViewMatrix(camera) : new Matrix4f(capturedModelView)).invert();
        Vector4f rayWorld4 = new Vector4f(rayEye.x, rayEye.y, rayEye.z, 0.0f).mul(invView);

        Vector3f rayWorld = new Vector3f(rayWorld4.x, rayWorld4.y, rayWorld4.z).normalize();
        return new Vec3(rayWorld.x, rayWorld.y, rayWorld.z);
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

    private int checkAxisClickLocal(Vec3 rayStart, Vec3 rayDir, LightInstance light, double mouseX, double mouseY) {
        Vec3 pos = new Vec3(light.x, light.y, light.z);
        double dist = rayStart.distanceTo(pos);
        double distanceFactor = dist * 0.12;
        double scale = 1.4 * Math.max(0.5, distanceFactor) * (CalSettings.INSTANCE.gizmoSize / 10.0);

        Vec3 localRayStart = rayStart.subtract(pos).scale(1.0 / scale);
        Vec3 localRayDir = rayDir;

        float sx = this.lastSx;
        float sy = this.lastSy;
        float sz = this.lastSz;

        // 2D Screen Space check for View Ring (STENCIL_VIEW)
        if (mode == Mode.ROTATE || mode == Mode.COMBINED) {
            Vector2d center2D = getLightScreenPos(pos);
            if (center2D != null) {
                Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
                float yawRad = (float) Math.toRadians(camera.yRot());
                Vec3 camRight = new Vec3(-Math.cos(yawRad), 0, -Math.sin(yawRad));
                double viewRingWorldR = 0.22 * COMBINED_ROTATE_SCALE * VIEW_RING_SCALE * scale;
                Vector2d edge2D = getLightScreenPos(pos.add(camRight.scale(viewRingWorldR)));

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
        if ((mode == Mode.TRANSLATE || mode == Mode.COMBINED) &&
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

    private boolean hitsTrackball(Vec3 localOrigin, Vec3 localDir, double radius) {
        Vector2d hit = new Vector2d();
        return Intersectiond.intersectRaySphere(
                localOrigin.x, localOrigin.y, localOrigin.z,
                localDir.x, localDir.y, localDir.z,
                0D, 0D, 0D,
                radius * radius, hit
        );
    }

    private boolean intersectViewRing(Vec3 localRayStart, Vec3 localRayDir, double radius, double thickness) {
        Vec3 normal = new Vec3(this.lastCamDir.x, this.lastCamDir.y, this.lastCamDir.z);
        double denom = normal.dot(localRayDir);
        if (Math.abs(denom) < 1e-6) return false;

        double t = -localRayStart.dot(normal) / denom;
        if (t < 0) return false;

        Vec3 hit = localRayStart.add(localRayDir.scale(t));
        double dist = hit.length();
        return Math.abs(dist - radius) <= thickness * 1.5;
    }

    private boolean intersectTorus(Vec3 rayStart, Vec3 rayDir, Axis axis, double majorR, double minorR) {
        int samples = 32;
        for (int i = 0; i < samples; i++) {
            double ang = Math.PI * 2.0 * i / samples;
            double px = 0, py = 0, pz = 0;
            if (axis == Axis.X) { py = majorR * Math.cos(ang); pz = majorR * Math.sin(ang); }
            else if (axis == Axis.Y) { px = majorR * Math.cos(ang); pz = majorR * Math.sin(ang); }
            else { px = majorR * Math.cos(ang); py = majorR * Math.sin(ang); }

            double dist = getDistanceToPoint(rayStart, rayDir, new Vec3(px, py, pz));
            if (dist <= minorR * 1.5) return true;
        }
        return false;
    }

    private boolean intersectAABB(Vec3 rayStart, Vec3 rayDir, double minX, double minY, double minZ, double maxX,
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

    private double getDistanceToPoint(Vec3 rayStart, Vec3 rayDir, Vec3 point) {
        Vec3 w = point.subtract(rayStart);
        double c1 = w.dot(rayDir);
        double c2 = rayDir.dot(rayDir);
        double b = c1 / c2;
        Vec3 pb = rayStart.add(rayDir.scale(b));
        return point.distanceTo(pb);
    }

    private Vec3 getMouseProjectionOnAxis(Vec3 rayStart, Vec3 rayDir, Vec3 axisOrigin, int axis) {
        Vec3 axisDir = (axis == 0) ? new Vec3(1, 0, 0) : (axis == 1) ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1);
        Vec3 w0 = axisOrigin.subtract(rayStart);
        double a = axisDir.dot(axisDir);
        double b = axisDir.dot(rayDir);
        double c = rayDir.dot(rayDir);
        double d = axisDir.dot(w0);
        double e = rayDir.dot(w0);

        double denom = a * c - b * b;
        if (Math.abs(denom) < 1e-5) return axisOrigin;

        double s = (b * e - c * d) / denom;
        return axisOrigin.add(axisDir.scale(s));
    }

    private Vec3 getMouseProjectionOnPlane(Vec3 rayStart, Vec3 rayDir, Vec3 planeOrigin, int plane) {
        Vec3 normal = (plane == 3) ? new Vec3(0, 1, 0) : (plane == 4) ? new Vec3(0, 0, 1) : new Vec3(1, 0, 0);
        double denom = normal.dot(rayDir);
        if (Math.abs(denom) < 1e-5) return planeOrigin;

        double t = (planeOrigin.subtract(rayStart)).dot(normal) / denom;
        return rayStart.add(rayDir.scale(t));
    }

    private Vec3 getMouseProjectionFree(Vec3 rayStart, Vec3 rayDir, Vec3 origin, Vec3 camDir) {
        double denom = camDir.dot(rayDir);
        if (Math.abs(denom) < 1e-5) return origin;

        double t = (origin.subtract(rayStart)).dot(camDir) / denom;
        return rayStart.add(rayDir.scale(t));
    }

    private Vector2d getLightScreenPos(Vec3 lightPos) {
        if (lightPos == null) return null;
        Minecraft client = Minecraft.getInstance();
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();

        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 camPos = camera.position();

        Matrix4f viewMatrix = camera != null ? getViewMatrix(camera) : capturedModelView;
        Vector4f eye = new Vector4f(
            (float)(lightPos.x - camPos.x),
            (float)(lightPos.y - camPos.y),
            (float)(lightPos.z - camPos.z),
            1.0f
        ).mul(viewMatrix);

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
        Vector2d center = getLightScreenPos(new Vec3(light.x, light.y, light.z));
        if (center == null) return 0.0;
        return Math.toDegrees(Math.atan2(mouseY - center.y, mouseX - center.x));
    }

    private double get3DRingAngle(Vec3 rayStart, Vec3 rayDir, LightInstance light, Axis axis) {
        if (light == null) return 0.0;
        Vec3 lightPos = new Vec3(light.x, light.y, light.z);
        Vec3 normal;

        if (axis == Axis.X) normal = new Vec3(1, 0, 0);
        else if (axis == Axis.Y) normal = new Vec3(0, 1, 0);
        else normal = new Vec3(0, 0, 1);

        double denom = normal.dot(rayDir);
        if (Math.abs(denom) < 1e-6) return 0.0;

        double t = lightPos.subtract(rayStart).dot(normal) / denom;
        Vec3 hitPoint = rayStart.add(rayDir.scale(t));
        Vec3 local = hitPoint.subtract(lightPos);

        double rad;
        if (axis == Axis.X) rad = Math.atan2(local.z, local.y);
        else if (axis == Axis.Y) rad = Math.atan2(local.z, local.x);
        else rad = Math.atan2(local.y, local.x);

        return Math.toDegrees(rad);
    }
}
