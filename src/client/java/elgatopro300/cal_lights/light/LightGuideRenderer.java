package elgatopro300.cal_lights.light;

import elgatopro300.cal_lights.manager.LightInstance;
import elgatopro300.cal_lights.manager.LightManager;
import elgatopro300.cal_lights.ui.CALEditorScreen;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;

public final class LightGuideRenderer
{
    private static final int CONE_SEGMENTS = 20;
    private static final int CONE_SPOKES = 4;
    private static final float POINT_CROSS = 0.4f;
    private static final float MAX_CONE_LEN = 16f;

    private LightGuideRenderer()
    {}

    public static void register()
    {
        WorldRenderEvents.LAST.register(LightGuideRenderer::onRender);
    }

    private static void onRender(WorldRenderContext ctx)
    {
        if (!LightConfig.showGuides || (LightManager.INSTANCE.getPointLights().isEmpty() && LightManager.INSTANCE.getSpotLights().isEmpty()))
        {
            return;
        }

        // When the editor overlay is up it draws the richer guides itself
        // suppress this in-world wire pass so they don't double up.
        if (MinecraftClient.getInstance().currentScreen instanceof CALEditorScreen)
        {
            return;
        }

        Camera cam = ctx.camera();
        if (cam == null)
        {
            return;
        }

        Vec3d c = cam.getPos();
        MatrixStack ms = ctx.matrixStack();
        Matrix4f m = ms != null
            ? ms.peek().getPositionMatrix()
            : new Matrix4f()
                .rotateX((float) Math.toRadians(cam.getPitch()))
                .rotateY((float) Math.toRadians(cam.getYaw() + 180.0));

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.lineWidth(2.0f);

        BufferBuilder buf = Tessellator.getInstance()
            .begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        for (LightInstance l : LightManager.INSTANCE.getPointLights())
        {
            if (l == null || !l.visible)
            {
                continue;
            }
            float r = vis(l.r), g = vis(l.g), b = vis(l.b);
            float lx = (float) (l.x - c.x);
            float ly = (float) (l.y - c.y);
            float lz = (float) (l.z - c.z);
            drawPoint(buf, m, lx, ly, lz, r, g, b);
        }

        for (LightInstance l : LightManager.INSTANCE.getSpotLights())
        {
            if (l == null || !l.visible)
            {
                continue;
            }
            float r = vis(l.r), g = vis(l.g), b = vis(l.b);
            float lx = (float) (l.x - c.x);
            float ly = (float) (l.y - c.y);
            float lz = (float) (l.z - c.z);
            drawSpot(buf, m, lx, ly, lz, l, r, g, b);
        }

        BuiltBuffer built = buf.endNullable();
        if (built != null)
        {
            BufferRenderer.drawWithGlobalProgram(built);
        }

        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    private static void drawPoint(BufferBuilder buf, Matrix4f m, float x, float y, float z, float r, float g, float b)
    {
        line(buf, m, x - POINT_CROSS, y, z, x + POINT_CROSS, y, z, r, g, b);
        line(buf, m, x, y - POINT_CROSS, z, x, y + POINT_CROSS, z, r, g, b);
        line(buf, m, x, y, z - POINT_CROSS, x, y, z + POINT_CROSS, r, g, b);
    }

    private static void drawSpot(BufferBuilder buf, Matrix4f m, float x, float y, float z, LightInstance l, float r, float g, float b)
    {
        // Normalize direction vector (defaults straight down when degenerate).
        float dx = l.dx, dy = l.dy, dz = l.dz;
        float dlen = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dlen > 0.0001f) {
            dx /= dlen; dy /= dlen; dz /= dlen;
        } else {
            dx = 0f; dy = -1f; dz = 0f;
        }

        float len = Math.max(1f, Math.min(l.distance, MAX_CONE_LEN));
        float radius = (float) (len * Math.tan(Math.toRadians(l.angle * 0.5f)));

        // End-cap centre.
        float ex = x + dx * len, ey = y + dy * len, ez = z + dz * len;

        // Orthonormal basis (u, v) spanning the end-cap plane.
        float rx, ry, rz;
        if (Math.abs(dy) < 0.99f) { rx = 0f; ry = 1f; rz = 0f; }
        else { rx = 1f; ry = 0f; rz = 0f; }
        float ux = dy * rz - dz * ry, uy = dz * rx - dx * rz, uz = dx * ry - dy * rx;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ul > 0.0001f) {
            ux /= ul; uy /= ul; uz /= ul;
        }
        float vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
        float vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (vl > 0.0001f) {
            vx /= vl; vy /= vl; vz /= vl;
        }

        // Centre axis line (the direction indicator).
        line(buf, m, x, y, z, ex, ey, ez, r, g, b);

        // End ring + spokes from the apex.
        float px = 0f, py = 0f, pz = 0f;
        for (int i = 0; i <= CONE_SEGMENTS; i++)
        {
            double a = (Math.PI * 2.0) * i / CONE_SEGMENTS;
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            float qx = ex + (ux * cos + vx * sin) * radius;
            float qy = ey + (uy * cos + vy * sin) * radius;
            float qz = ez + (uz * cos + vz * sin) * radius;

            if (i > 0)
            {
                line(buf, m, px, py, pz, qx, qy, qz, r, g, b);
            }
            if (i % (CONE_SEGMENTS / CONE_SPOKES) == 0)
            {
                line(buf, m, x, y, z, qx, qy, qz, r, g, b);
            }
            px = qx; py = qy; pz = qz;
        }
    }

    private static void line(BufferBuilder buf, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b)
    {
        buf.vertex(m, x1, y1, z1).color(r, g, b, 1f);
        buf.vertex(m, x2, y2, z2).color(r, g, b, 1f);
    }

    private static float vis(float v)
    {
        float c = v < 0f ? 0f : Math.min(v, 1f);
        return Math.max(c, 0.25f);
    }
}
