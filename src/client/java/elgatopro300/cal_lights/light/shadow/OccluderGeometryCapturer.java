package elgatopro300.cal_lights.light.shadow;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

/**
 * Captures the REAL model geometry of a shadow occluder into a flat CPU triangle
 * buffer, so {@link ShadowRenderer} can rasterize a faithful silhouette into its
 * depth maps — the 1.21.11 equivalent of the {@code dispatcher.render(...,
 * Immediate, ...)} path used on the 1.20.4 / 1.21.1 redactor lines.
 *
 * <p><b>Why this exists.</b> 1.21.11 (post 1.21.5 RenderPipeline + 1.21.9
 * {@code EntityRenderState}) no longer rasterizes entities through an immediate
 * {@code VertexConsumerProvider}; entity renderers instead <i>submit deferred
 * commands</i> to an {@link SubmitNodeCollector} that MC later executes
 * against its {@code GpuDevice}. There is no reachable immediate-to-FBO path, so
 * the earlier port fell back to an AABB box occluder. But the queue is an
 * <i>interface</i>, and MC's executor simply does {@code model.setAngles(state);
 * model.render(matrices, vertexConsumer, ...)}. So a capturing queue can run the
 * real entity render and redirect its geometry into a position-recording
 * {@link VertexConsumer}, reconstructing the true model silhouette — no GL is
 * issued here (the consumer just records floats); {@link ShadowRenderer} draws
 * the captured triangles itself.</p>
 *
 * <p><b>Coordinate space.</b> Geometry is captured in absolute world coordinates
 * (the entity is rendered at its lerped world position; cutout blocks at their
 * block origin), matching the world-space light view/projection the depth bake
 * uses. {@code ModelPart}/{@code quad} hand the consumer positions already
 * transformed by the matrix, so the consumer applies no matrix of its own.</p>
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
    /** Entity ids whose render threw hard inside MC's entity pipeline (which wraps
     *  the failure in a CrashReport + CrashException). Skipped on later bakes so a
     *  consistently-broken (e.g. buggy modded) entity is not re-attempted — and its
     *  crash report rebuilt — every frame. Per-entity granularity; a healthy entity
     *  is never skipped. (Id reuse after despawn could skip a new entity sharing the
     *  id; benign — at worst one missing shadow, vs. per-frame crash-report spam.) */
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

            // Orientation/pos for any camera-aligned sub-render; bodies of living
            // entities pose from the render state itself. Default (identity) is
            // safe when the camera is not yet configured.
            Camera cam = mgr.camera;
            if (cam != null)
            {
                CAMERA_STATE.pos = cam.position();
                CAMERA_STATE.orientation.set(cam.rotation());
                CAMERA_STATE.initialized = true;
            }

            CAPTURE.reset();
            // Fresh identity stack: the queue renders at the world offset, so
            // captured vertices come out in absolute world space.
            mgr.submit(state, CAMERA_STATE, wx, wy, wz, new PoseStack(), QUEUE);
            return CAPTURE.toTris(false);
        }
        catch (Throwable t)
        {
            // A single bad entity must not abort the bake. MC wraps a render
            // failure in a CrashReport + CrashException, so skip this entity on
            // later bakes instead of rebuilding that report every frame.
            failedEntities.add(entity.getId());
            return EMPTY;
        }
    }

    /**
     * Capture one cutout block as world-space POSITION+UV triangles (5 floats per
     * vertex) through MC's real model tessellation
     * (neighbour-culled, correct atlas UVs) — replacing the earlier hand-rolled
     * {@code BakedQuad} extraction. {@link ShadowRenderer} draws these with the
     * atlas-sampling, alpha-discarding cutout program. Empty array on failure.
     */
    public static float[] captureCutoutBlockTris(BlockAndTintGetter world, BlockPos pos, BlockState state, RandomSource random)
    {
        if (world == null || state == null)
        {
            return EMPTY;
        }
        try
        {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return EMPTY;
            BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(state);
            if (model == null)
            {
                return EMPTY;
            }
            List<BlockStateModelPart> parts = new ArrayList<>();
            random.setSeed(state.getSeed(pos));
            model.collectParts(random, parts);
            if (parts.isEmpty())
            {
                return EMPTY;
            }

            PoseStack ms = new PoseStack();
            // Pre-translate to the absolute block origin: BlockModelRenderer only
            // applies the block's model offset, never its position.
            ms.translate(pos.getX(), pos.getY(), pos.getZ());

            CAPTURE.reset();
            PoseStack.Pose e = ms.last();
            QuadInstance qi = new QuadInstance();
            qi.setColor(0xFFFFFFFF);
            qi.setLightCoords(0xF000F0);
            qi.setOverlayCoords(0);
            for (BlockStateModelPart part : parts)
            {
                for (Direction dir : Direction.values())
                {
                    List<BakedQuad> quads = part.getQuads(dir);
                    for (BakedQuad q : quads)
                    {
                        if (q != null)
                            CAPTURE.putBakedQuad(e, q, qi);
                    }
                }
            }
            return CAPTURE.toTris(true);
        }
        catch (Throwable t)
        {
            return EMPTY;
        }
    }

    // --- Position(+UV) recording VertexConsumer -------------------------------

    /**
     * Records committed vertices as (x, y, z, u, v) and triangulates the implied
     * QUADS (every 4 vertices -> 2 triangles). Positions arrive already
     * transformed (see {@link ModelPart.Cube#compile} and the default
     * {@link VertexConsumer#putBulkData}); only POSITION (+ UV for cutout) is kept, every
     * other element is dropped. A vertex is committed when the next {@link
     * #addVertex(float, float, float)} starts or {@link #toTris} flushes the last.
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
     * An {@link SubmitNodeCollector} that, instead of deferring, immediately
     * renders submitted model geometry into the {@link Capture} consumer. Only the
     * geometry-bearing submits (model / model part / item) are handled; text /
     * labels / fire / leashes / shadow pieces / custom layers are dropped (they
     * are not part of an occluder silhouette). Mirrors {@code
     * OrderedRenderCommandQueueImpl}'s method surface.
     */
    private static final class CaptureQueue implements SubmitNodeCollector
    {
        private final Capture capture;

        CaptureQueue(Capture capture)
        {
            this.capture = capture;
        }

        @Override
        public OrderedSubmitNodeCollector order(int priority)
        {
            return this;
        }

        @Override
        public <S> void submitModel(Model<? super S> model, S state, PoseStack matrices, RenderType renderType,
                                    int light, int overlay, int tintedColor, TextureAtlasSprite sprite,
                                    int atlasWidth, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay)
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
                // skip one bad model; the silhouette degrades, the bake survives
            }
        }

        @Override
        public void submitModelPart(ModelPart part, PoseStack matrices, RenderType renderType, int light, int overlay,
                                    TextureAtlasSprite sprite, boolean sheeted, boolean hasGlint, int tintedColor,
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
                QuadInstance qi = new QuadInstance();
                qi.setColor(0xFFFFFFFF);
                qi.setLightCoords(light);
                qi.setOverlayCoords(overlay);
                for (int i = 0, n = quads.size(); i < n; i++)
                {
                    BakedQuad q = quads.get(i);
                    if (q != null)
                    {
                        capture.putBakedQuad(e, q, qi);
                    }
                }
            }
            catch (Throwable ignored)
            {
            }
        }

        // --- no-op submits (not part of an occluder silhouette) ---------------

        @Override
        public void submitShadow(PoseStack matrices, float shadowRadius, List<EntityRenderState.ShadowPiece> shadowPieces)
        {
        }

        @Override
        public void submitNameTag(PoseStack matrices, Vec3 nameLabelPos, int y, Component label, boolean notSneaking,
                                  int light, double squaredDistanceToCamera, CameraRenderState cameraState)
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
        public void submitBlockModel(PoseStack matrices, RenderType renderType,
                                     List<BlockStateModelPart> parts, int[] tintLayers,
                                     int light, int overlay, int outlineColor)
        {
            if (parts == null || parts.isEmpty())
            {
                return;
            }
            try
            {
                PoseStack.Pose e = matrices.last();
                QuadInstance qi = new QuadInstance();
                qi.setColor(0xFFFFFFFF);
                qi.setLightCoords(light);
                qi.setOverlayCoords(overlay);
                for (BlockStateModelPart part : parts)
                {
                    List<BakedQuad> quads = part.getQuads(null);
                    for (BakedQuad q : quads)
                    {
                        if (q != null)
                            capture.putBakedQuad(e, q, qi);
                    }
                    for (Direction dir : Direction.values())
                    {
                        quads = part.getQuads(dir);
                        for (BakedQuad q : quads)
                        {
                            if (q != null)
                                capture.putBakedQuad(e, q, qi);
                        }
                    }
                }
            }
            catch (Throwable ignored)
            {
            }
        }

        @Override
        public void submitMovingBlock(PoseStack matrices, MovingBlockRenderState state)
        {
            // Falling/piston blocks are not living-mob attachments (their entities
            // are not collected as occluders), so nothing to capture here.
        }

        @Override
        public void submitBreakingBlockModel(PoseStack matrices, BlockStateModel model, long seed, int overlay)
        {
            // Breaking-progress overlay — not needed for occluder silhouette.
        }

        @Override
        public void submitCustomGeometry(PoseStack matrices, RenderType renderLayer, SubmitNodeCollector.CustomGeometryRenderer customRenderer)
        {
        }

        @Override
        public void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer customRenderer)
        {
        }
    }
}
