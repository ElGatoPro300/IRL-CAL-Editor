package org.qualet.irl.light.shadow;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.joml.Quaternionf;
import org.joml.Vector3fc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.List;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

/**
 * Captures the REAL model geometry of a shadow occluder into a flat CPU triangle
 * buffer, so {@link ShadowRenderer} can rasterize a faithful silhouette into its
 * depth maps.
 *
 * <p>Single-threaded (render thread); one shared {@link Capture} + {@link CaptureQueue}.</p>
 */
public final class OccluderGeometryCapturer
{
    private OccluderGeometryCapturer()
    {}

    private static final Capture CAPTURE = new Capture();
    private static final CaptureQueue QUEUE = new CaptureQueue(CAPTURE);
    /** Reused, so a held armor-stand / mob silhouette pose is deterministic. */
    private static final CameraRenderState CAMERA_STATE = new CameraRenderState();
    /** Entity ids whose render threw hard inside MC's entity pipeline. */
    private static final IntOpenHashSet failedEntities = new IntOpenHashSet();

    private static final float[] EMPTY = new float[0];

    /**
     * Capture one entity's model as world-space POSITION triangles (3 floats per
     * vertex, 6 vertices per quad). Runs MC's real entity render through the
     * capturing queue. Returns an empty array on any failure (degrade to no
     * shadow for that entity — never crash the bake).
     */
    public static float[] captureEntityTris(Entity entity, float tickDelta)
    {
        if (entity == null || failedEntities.contains(entity.getId()))
        {
            return EMPTY;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null)
        {
            return EMPTY;
        }
        EntityRenderDispatcher mgr = mc.getEntityRenderDispatcher();
        if (mgr == null)
        {
            return EMPTY;
        }

        try
        {
            double wx = Mth.lerp(tickDelta, entity.xOld, entity.getX());
            double wy = Mth.lerp(tickDelta, entity.yOld, entity.getY());
            double wz = Mth.lerp(tickDelta, entity.zOld, entity.getZ());

            EntityRenderState state = mgr.extractEntity(entity, tickDelta);
            if (state == null)
            {
                return EMPTY;
            }

            Camera cam = mgr.camera;
            if (cam != null)
            {
                CAMERA_STATE.pos = cam.position();
                CAMERA_STATE.orientation.set(cam.rotation());
                CAMERA_STATE.initialized = true;
            }

            CAPTURE.reset();
            double ox = ShadowRenderer.currentOriginX();
            double oy = ShadowRenderer.currentOriginY();
            double oz = ShadowRenderer.currentOriginZ();
            mgr.submit(state, CAMERA_STATE, wx - ox, wy - oy, wz - oz, new PoseStack(), QUEUE);
            return CAPTURE.toTris(false);
        }
        catch (Throwable t)
        {
            failedEntities.add(entity.getId());
            return EMPTY;
        }
    }

    // --- Position(+UV) recording VertexConsumer -------------------------------

    /**
     * Records committed vertices as (x, y, z, u, v) and triangulates the implied
     * QUADS (every 4 vertices -> 2 triangles).
     */
    private static final class Capture implements VertexConsumer
    {
        private final FloatArrayList verts = new FloatArrayList(2048);
        private float cx, cy, cz, cu, cv;
        private boolean pending;

        void reset()
        {
            verts.clear();
            pending = false;
        }

        private void commit()
        {
            if (pending)
            {
                verts.add(cx);
                verts.add(cy);
                verts.add(cz);
                verts.add(cu);
                verts.add(cv);
                pending = false;
            }
        }

        /** Flush the last pending vertex and triangulate. {@code withUv} keeps the
         *  UV pair (cutout, stride 5) else POSITION only (entities, stride 3). */
        float[] toTris(boolean withUv)
        {
            commit();
            int n = verts.size() / 5;       // committed vertices
            int quads = n / 4;              // 4 verts per quad (model + block + item)
            if (quads == 0)
            {
                return EMPTY;
            }
            int per = withUv ? 5 : 3;
            float[] out = new float[quads * 6 * per];
            float[] v = verts.elements();
            int w = 0;
            for (int q = 0; q < quads; q++)
            {
                int b = q * 4 * 5; // base float index of this quad's first vertex
                // triangle (0,1,2) then (0,2,3)
                w = put(out, w, v, b, 0, withUv);
                w = put(out, w, v, b, 1, withUv);
                w = put(out, w, v, b, 2, withUv);
                w = put(out, w, v, b, 0, withUv);
                w = put(out, w, v, b, 2, withUv);
                w = put(out, w, v, b, 3, withUv);
            }
            return out;
        }

        private static int put(float[] out, int w, float[] v, int quadBase, int corner, boolean withUv)
        {
            int s = quadBase + corner * 5;
            out[w++] = v[s];
            out[w++] = v[s + 1];
            out[w++] = v[s + 2];
            if (withUv)
            {
                out[w++] = v[s + 3];
                out[w++] = v[s + 4];
            }
            return w;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z)
        {
            commit();
            cx = x; cy = y; cz = z; cu = 0f; cv = 0f;
            pending = true;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v)
        {
            cu = u; cv = v;
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha)
        {
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb)
        {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v)
        {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v)
        {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z)
        {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width)
        {
            return this;
        }
    }

    // --- Capturing render-command queue ---------------------------------------

    /**
     * A {@link SubmitNodeCollector} that, instead of deferring, immediately
     * renders submitted model geometry into the {@link Capture} consumer.
     */
    private static final class CaptureQueue implements SubmitNodeCollector
    {
        private final Capture capture;

        CaptureQueue(Capture capture)
        {
            this.capture = capture;
        }

        @Override
        public OrderedSubmitNodeCollector order(int order)
        {
            return this;
        }

        @Override
        public <S> void submitModel(Model<? super S> model, S state, PoseStack matrices, RenderType renderLayer,
                                    int light, int overlay, int tintedColor, TextureAtlasSprite sprite, int outlineColor,
                                    ModelFeatureRenderer.CrumblingOverlay crumblingOverlay)
        {
            if (model == null)
            {
                return;
            }
            try
            {
                model.setupAnim(state);
                model.renderToBuffer(matrices, capture, light, overlay, tintedColor);
            }
            catch (Throwable ignored)
            {
            }
        }

        @Override
        public void submitModelPart(ModelPart part, PoseStack matrices, RenderType renderLayer, int light, int overlay,
                                    TextureAtlasSprite sprite, int tintedColor,
                                    ModelFeatureRenderer.CrumblingOverlay crumblingOverlay, int i)
        {
            if (part == null)
            {
                return;
            }
            try
            {
                part.render(matrices, capture, light, overlay, tintedColor);
            }
            catch (Throwable ignored)
            {
            }
        }

        @Override
        public void submitItem(PoseStack matrices, ItemDisplayContext displayContext, int light, int overlay,
                               int outlineColors, int[] tintLayers, List<BakedQuad> quads,
                               ItemStackRenderState.FoilType glintType)
        {
            if (quads == null || quads.isEmpty())
            {
                return;
            }
            try
            {
                PoseStack.Pose e = matrices.last();
                for (int qi = 0, n = quads.size(); qi < n; qi++)
                {
                    BakedQuad q = quads.get(qi);
                    if (q != null)
                    {
                        for (int vi = 0; vi < 4; vi++)
                        {
                            capture.addVertex(e, q.position(vi));
                        }
                    }
                }
            }
            catch (Throwable ignored)
            {
            }
        }

        @Override
        public void submitBlockModel(PoseStack matrices, RenderType renderLayer, List<BlockStateModelPart> parts,
                                     int[] tintColors, int light, int overlay, int outlineColor)
        {
            if (parts == null || parts.isEmpty())
            {
                return;
            }
            try
            {
                PoseStack.Pose e = matrices.last();
                for (BlockStateModelPart part : parts)
                {
                    if (part == null) continue;
                    for (Direction dir : Direction.values())
                    {
                        List<BakedQuad> quads = part.getQuads(dir);
                        if (quads != null)
                        {
                            for (BakedQuad q : quads)
                            {
                                if (q != null)
                                {
                                    for (int vi = 0; vi < 4; vi++)
                                    {
                                        capture.addVertex(e, q.position(vi));
                                    }
                                }
                            }
                        }
                    }
                    List<BakedQuad> unculled = part.getQuads(null);
                    if (unculled != null)
                    {
                        for (BakedQuad q : unculled)
                        {
                            if (q != null)
                            {
                                for (int vi = 0; vi < 4; vi++)
                                {
                                    capture.addVertex(e, q.position(vi));
                                }
                            }
                        }
                    }
                }
            }
            catch (Throwable ignored)
            {
            }
        }

        @Override
        public void submitBreakingBlockModel(PoseStack matrices, List<BlockStateModelPart> parts, int outlineColor)
        {
        }

        @Override
        public void submitShadow(PoseStack matrices, float shadowRadius, List<EntityRenderState.ShadowPiece> shadowPieces)
        {
        }

        @Override
        public void submitNameTag(PoseStack matrices, Vec3 nameLabelPos, int y, Component label, boolean notSneaking,
                                  int light, CameraRenderState cameraState)
        {
        }

        @Override
        public void submitText(PoseStack matrices, float x, float y, FormattedCharSequence text, boolean dropShadow,
                               Font.DisplayMode layerType, int light, int color, int backgroundColor, int outlineColor)
        {
        }

        @Override
        public void submitFlame(PoseStack matrices, EntityRenderState renderState, Quaternionf rotation)
        {
        }

        @Override
        public void submitLeash(PoseStack matrices, EntityRenderState.LeashState leashData)
        {
        }

        @Override
        public void submitMovingBlock(PoseStack matrices, MovingBlockRenderState state, int outlineColor)
        {
        }

        @Override
        public void submitShapeOutline(PoseStack matrices, VoxelShape shape,
                                       RenderType renderType, int color, float lineWidth, boolean throughWalls)
        {
        }

        @Override
        public void submitCustomGeometry(PoseStack matrices, RenderType renderLayer, SubmitNodeCollector.CustomGeometryRenderer customRenderer)
        {
        }

        @Override
        public void submitQuadParticleGroup(QuadParticleRenderState state)
        {
        }

        @Override
        public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group group,
                                          CameraRenderState cameraState, boolean throughWalls)
        {
        }
    }
}
