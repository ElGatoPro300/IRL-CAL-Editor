package elgatopro300.cal_lights.integration.bbs;

import elgatopro300.cal_lights.manager.LightInstance;
import elgatopro300.cal_lights.manager.LightManager;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.client.renderer.item.GunItemRenderer;
import mchorse.bbs_mod.client.renderer.item.ModelBlockItemRenderer;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.resources.packs.InternalAssetsSourcePack;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class CALLightsBbsIntegration {
    public static final Link FORM_LINK = Link.bbs("cal_light");
    public static final Map<Integer, IEntity> idToEntityMap = new HashMap<>();

    private static Field modelBlockItemRendererField;
    private static Field gunItemRendererField;
    private static Field modelRendererMapField;
    private static Field gunRendererMapField;

    static {
        try {
            modelBlockItemRendererField = BBSModClient.class.getDeclaredField("modelBlockItemRenderer");
            modelBlockItemRendererField.setAccessible(true);
            gunItemRendererField = BBSModClient.class.getDeclaredField("gunItemRenderer");
            gunItemRendererField.setAccessible(true);
            modelRendererMapField = ModelBlockItemRenderer.class.getDeclaredField("map");
            modelRendererMapField.setAccessible(true);
            gunRendererMapField = GunItemRenderer.class.getDeclaredField("map");
            gunRendererMapField.setAccessible(true);
        } catch (Throwable ignored) {}
    }

    public static boolean isBbsEntityActive(IEntity entity) {
        if (entity == null) {
            return false;
        }

        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            return false;
        }

        // 1. Real Minecraft entity (if MCEntity)
        if (entity instanceof MCEntity) {
            Entity mcEnt = ((MCEntity) entity).getMcEntity();
            if (mcEnt == null || mcEnt.isRemoved() || mcEnt.getWorld() != world) {
                return false;
            }
            double x = entity.getX();
            double z = entity.getZ();
            int chunkX = ((int) x) >> 4;
            int chunkZ = ((int) z) >> 4;
            if (!world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                return false;
            }
            return true;
        }

        // 2. Active playing film controllers
        try {
            if (BBSModClient.getFilms() != null && BBSModClient.getFilms().getControllers() != null) {
                for (BaseFilmController controller : BBSModClient.getFilms().getControllers()) {
                    if (controller != null && controller.getEntities() != null) {
                        if (controller.getEntities().containsValue(entity)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 3. Active dashboard panels
        try {
            if (UIScreen.getCurrentMenu() == BBSModClient.getDashboard()) {
                if (BBSModClient.getDashboard() != null && BBSModClient.getDashboard().getPanels() != null) {
                    UIDashboardPanel panel = BBSModClient.getDashboard().getPanels().panel;
                    if (panel instanceof UIFilmPanel) {
                        UIFilmController filmCtrl = ((UIFilmPanel) panel).getController();
                        if (filmCtrl != null) {
                            if (filmCtrl.getEntities() != null && filmCtrl.getEntities().containsValue(entity)) {
                                return true;
                            }
                            if (filmCtrl.getCurrentEntity() == entity || filmCtrl.getControlled() == entity) {
                                return true;
                            }
                        }
                    } else if (panel instanceof UIModelBlockPanel) {
                        ModelBlockEntity modelBlock = ((UIModelBlockPanel) panel).getModelBlock();
                        if (modelBlock != null && modelBlock.getEntity() == entity) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 4. ModelBlockEntity tile entities in loaded chunks
        try {
            double x = entity.getX();
            double z = entity.getZ();
            int chunkX = ((int) x) >> 4;
            int chunkZ = ((int) z) >> 4;
            if (world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                WorldChunk chunk = world.getChunk(chunkX, chunkZ);
                if (chunk != null) {
                    Map<BlockPos, BlockEntity> blockEntities = chunk.getBlockEntities();
                    if (blockEntities != null) {
                        for (BlockEntity be : blockEntities.values()) {
                            if (be instanceof ModelBlockEntity) {
                                if (((ModelBlockEntity) be).getEntity() == entity) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 5. Item Renderers (ModelBlockItemRenderer & GunItemRenderer maps)
        try {
            if (modelBlockItemRendererField != null && modelRendererMapField != null) {
                ModelBlockItemRenderer modelRenderer = (ModelBlockItemRenderer) modelBlockItemRendererField.get(null);
                if (modelRenderer != null) {
                    Map<?, ?> map = (Map<?, ?>) modelRendererMapField.get(modelRenderer);
                    if (map != null) {
                        for (Object itemObj : map.values()) {
                            if (itemObj instanceof ModelBlockItemRenderer.Item) {
                                if (((ModelBlockItemRenderer.Item) itemObj).formEntity == entity) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        try {
            if (gunItemRendererField != null && gunRendererMapField != null) {
                GunItemRenderer gunRenderer = (GunItemRenderer) gunItemRendererField.get(null);
                if (gunRenderer != null) {
                    Map<?, ?> map = (Map<?, ?>) gunRendererMapField.get(gunRenderer);
                    if (map != null) {
                        for (Object itemObj : map.values()) {
                            if (itemObj instanceof GunItemRenderer.Item) {
                                if (((GunItemRenderer.Item) itemObj).formEntity == entity) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    public static void initialize() {
        try {
            // Register asset source pack with BBS so it can resolve textures and languages under namespace "cal"
            BBSMod.getProvider().register(new InternalAssetsSourcePack("cal", "assets/cal", CALLightsBbsIntegration.class));

            // Manually register our Client Addon in case the Fabric entrypoints are bypassed/fail under hybrid/loader systems
            mchorse.bbs_mod.BBS.getEvents().register(new BbsClientAddon());

            // Add the CAL Light Form directly into the BBS Extra (Miscellaneous) Form list on Client Startup
            ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
                try {
                    if (mchorse.bbs_mod.BBS.getForms() != null) {
                        mchorse.bbs_mod.BBS.getForms().register(FORM_LINK, CALBbsForm.class);
                    }
                } catch (Throwable ignored) {}

                try {
                    if (BBSModClient.getFormCategories() != null &&
                        BBSModClient.getFormCategories().getExtraForms() != null &&
                        BBSModClient.getFormCategories().getExtraForms().getExtraCategory() != null) {
                        BBSModClient.getFormCategories().getExtraForms().getExtraCategory().addForm(new CALBbsForm());
                    }
                } catch (Throwable ignored) {}
            });

            // Register Client Tick for BBS Lights Cleanup
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                try {
                    idToEntityMap.clear();
                    BbsLightTracker.cleanUp();
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    public static class BbsLightTracker {
        private static final ReferenceQueue<IEntity> queue = new ReferenceQueue<>();
        private static final List<LightRef> refs = new CopyOnWriteArrayList<>();

        private static class LightRef extends WeakReference<IEntity> {
            final int id;

            LightRef(IEntity referent, int id, ReferenceQueue<? super IEntity> q) {
                super(referent, q);
                this.id = id;
            }
        }

        public static void track(IEntity entity, int id) {
            cleanUp();

            for (LightRef ref : refs) {
                IEntity e = ref.get();
                if (e == entity) {
                    if (ref.id != id) {
                        refs.remove(ref);
                        refs.add(new LightRef(entity, id, queue));
                    }
                    return;
                }
            }

            refs.add(new LightRef(entity, id, queue));
        }

        public static void cleanUp() {
            // Process GC queue
            Reference<? extends IEntity> ref;
            while ((ref = queue.poll()) != null) {
                if (ref instanceof LightRef) {
                    LightRef lref = (LightRef) ref;
                    LightManager.INSTANCE.removePoint(lref.id);
                    LightManager.INSTANCE.removeSpot(lref.id);
                    refs.remove(lref);
                }
            }

            // Active checks for loaded world/chunks and active contexts
            for (LightRef lref : refs) {
                IEntity entity = lref.get();
                if (entity == null || !isBbsEntityActive(entity)) {
                    LightManager.INSTANCE.removePoint(lref.id);
                    LightManager.INSTANCE.removeSpot(lref.id);
                    refs.remove(lref);
                }
            }
        }
    }

    public static class CALBbsFormRenderer extends FormRenderer<CALBbsForm> {
        public CALBbsFormRenderer(CALBbsForm form) {
            super(form);
        }

        @Override
        protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2) {
            try {
                boolean isSpot = form.isSpot.get();
                Link tintLink = new Link("cal", isSpot ? "assets/textures/spot_texture_tint.png" : "assets/textures/point_texture_tint.png");
                Link staticLink = new Link("cal", isSpot ? "assets/textures/spot_texture_static.png" : "assets/textures/point_texture_static.png");
                
                Texture tintTex = context.render.getTextures().getTexture(tintLink);
                Texture staticTex = context.render.getTextures().getTexture(staticLink);
                
                float min = Math.min(tintTex.width, tintTex.height);
                if (min <= 0) min = 1;

                int ow = (x2 - x1) - 4;
                int oh = (y2 - y1) - 4;

                int w = (int) ((tintTex.width / min) * ow);
                int h = (int) ((tintTex.height / min) * ow);

                int x = x1 + (ow - w) / 2 + 2;
                int y = y1 + (oh - h) / 2 + 2;
                
                int color = form.color.get().getARGBColor();
                
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                tintTex.setFilter(GL11.GL_NEAREST);
                staticTex.setFilter(GL11.GL_NEAREST);
                
                context.batcher.fullTexturedBox(tintTex, color, x, y, w, h);
                context.batcher.fullTexturedBox(staticTex, Colors.WHITE, x, y, w, h);
            } catch (Throwable e) {
                // Fallback to text if texture fails to load
                MatrixStack matrices = context.batcher.getContext().getMatrices();
                matrices.push();
                matrices.translate(x1 + 4, y1 + 4, 0);
                context.batcher.text("CAL Light", 0, 0, 0xFFFFFFFF);
                matrices.pop();
            }
        }



        @Override
        protected void render3D(FormRenderingContext context) {
            super.render3D(context);
            if (context.ui) return;

            int id = form.runtimeId.get();
            if (id <= 0) {
                id = ThreadLocalRandom.current().nextInt(100000, 999999);
                form.runtimeId.set(id);
            }

            if (context.entity != null) {
                IEntity existingEntity = idToEntityMap.get(id);
                if (existingEntity != null && existingEntity != context.entity) {
                    id = ThreadLocalRandom.current().nextInt(100000, 999999);
                    form.runtimeId.set(id);
                }
                idToEntityMap.put(id, context.entity);
                BbsLightTracker.track(context.entity, id);
            }

            Matrix4f matrix;
            if (context.type == FormRenderType.PREVIEW) {
                // In preview mode, we need to apply the camera rotation to keep the light aligned with the view
                matrix = new Matrix4f().rotation(MinecraftClient.getInstance().gameRenderer.getCamera().getRotation());
                matrix.mul(context.stack.peek().getPositionMatrix());
            } else {
                // In world mode, the stack provided by BBS is already world-aligned (relative to camera)
                matrix = new Matrix4f(context.stack.peek().getPositionMatrix());
            }

            double finalX, finalY, finalZ;
            double shaderFinalX, shaderFinalY, shaderFinalZ;
            Vector3f dir3, shaderDir3;

            if (context.type == FormRenderType.PREVIEW) {
                // Preview mode: keep simple camera-relative alignment
                net.minecraft.client.render.Camera realCam = MinecraftClient.getInstance().gameRenderer.getCamera();
                Vector3f translation = matrix.getTranslation(new Vector3f());
                finalX = translation.x + (float) realCam.getPos().x;
                finalY = translation.y + (float) realCam.getPos().y;
                finalZ = translation.z + (float) realCam.getPos().z;

                shaderFinalX = finalX;
                shaderFinalY = finalY;
                shaderFinalZ = finalZ;

                Vector4f dir4 = new Vector4f(0.0F, 0.0F, -1.0F, 0.0F);
                matrix.transform(dir4);
                dir3 = new Vector3f(dir4.x, dir4.y, dir4.z);
                if (dir3.lengthSquared() > 0) dir3.normalize();
                shaderDir3 = dir3;
            } else {
                // World rendering (replays, model blocks, actors, etc.)
                // 1. For Java/flare rendering (unrotated):
                Vector3f absPos = matrix.getTranslation(new Vector3f());
                finalX = absPos.x + context.camera.position.x;
                finalY = absPos.y + context.camera.position.y;
                finalZ = absPos.z + context.camera.position.z;

                Vector4f dir4 = new Vector4f(0.0F, 0.0F, -1.0F, 0.0F);
                matrix.transform(dir4);
                dir3 = new Vector3f(dir4.x, dir4.y, dir4.z);
                if (dir3.lengthSquared() > 0) dir3.normalize();

                // 2. For Shader rendering:
                // We dynamically account for any mismatch between the active viewport camera and the spectator camera.
                // If they differ (such as inside the editor's preview viewport), we apply the spectator-relative rotation.
                // If they are the same (such as rendering outside the panel, or standard model blocks), it cancels out to identity.
                boolean isEditorPreview = false;
                try {
                    if (UIScreen.getCurrentMenu() == BBSModClient.getDashboard()) {
                        if (BBSModClient.getDashboard() != null && BBSModClient.getDashboard().getPanels() != null) {
                            isEditorPreview = BBSModClient.getDashboard().getPanels().panel instanceof UIFilmPanel;
                        }
                    }
                } catch (Throwable ignored) {}

                if (isEditorPreview && context.type == FormRenderType.ENTITY) {
                    Matrix4f viewMat = context.viewMatrix != null ? context.viewMatrix : 
                                       (context.camera != null && context.camera.view != null ? context.camera.view : new Matrix4f());
                    Matrix4f shaderMatrix = new Matrix4f(viewMat).invert();
                    shaderMatrix.mul(matrix);

                    Vector3f shaderPos = shaderMatrix.getTranslation(new Vector3f());
                    shaderFinalX = shaderPos.x + context.camera.position.x;
                    shaderFinalY = shaderPos.y + context.camera.position.y;
                    shaderFinalZ = shaderPos.z + context.camera.position.z;

                    Vector4f shaderDir4 = new Vector4f(0.0F, 0.0F, -1.0F, 0.0F);
                    shaderMatrix.transform(shaderDir4);
                    shaderDir3 = new Vector3f(shaderDir4.x, shaderDir4.y, shaderDir4.z);
                    if (shaderDir3.lengthSquared() > 0) shaderDir3.normalize();
                } else {
                    shaderFinalX = finalX;
                    shaderFinalY = finalY;
                    shaderFinalZ = finalZ;
                    shaderDir3 = dir3;
                }
            }

            float px = (float) finalX;
            float py = (float) finalY;
            float pz = (float) finalZ;

            Color c = form.color.get();
            float r = c.r;
            float g = c.g;
            float bCol = c.b;
            float intensity = form.intensity.get();

            if (form.isSpot.get()) {
                LightInstance light = LightManager.INSTANCE.updateSpot(id, px, py, pz, dir3.x, dir3.y, dir3.z, r, g, bCol, intensity, form.innerAngle.get(), form.outerAngle.get(), form.distance.get());
                copyProperties(light, form);
                light.shaderX = (float) shaderFinalX;
                light.shaderY = (float) shaderFinalY;
                light.shaderZ = (float) shaderFinalZ;
                light.shaderDx = shaderDir3.x;
                light.shaderDy = shaderDir3.y;
                light.shaderDz = shaderDir3.z;
                if (context.entity != null) {
                    light.persistent = true;
                }
            } else {
                LightInstance light = LightManager.INSTANCE.updatePoint(id, px, py, pz, r, g, bCol, intensity, form.radius.get());
                copyProperties(light, form);
                light.shaderX = (float) shaderFinalX;
                light.shaderY = (float) shaderFinalY;
                light.shaderZ = (float) shaderFinalZ;
                light.shaderDx = null;
                light.shaderDy = null;
                light.shaderDz = null;
                if (context.entity != null) {
                    light.persistent = true;
                }
            }

            if (form.showIndicator.get()) {
                drawBbsLightIndicators(context.stack, form);
            }
        }

        private static void copyProperties(LightInstance light, CALBbsForm form) {
            light.fogEnabled = form.fogEnabled.get();
            light.fogDispersion = form.fogDispersion.get();
            light.fogDensity = form.fogDensity.get();
            light.fogAnisotropy = form.fogAnisotropy.get();
            light.shadowEnabled = form.shadowEnabled.get();
            light.shadowSoftness = form.shadowSoftness.get();
            light.shadowIntensity = form.shadowIntensity.get();
            light.flareEnabled = form.flareEnabled.get();
            light.flareSize = form.flareSize.get();
            light.flareGlowSize = form.flareGlowSize.get();
            light.flareGlowIntensity = form.flareGlowIntensity.get();
            light.flareRayLength = form.flareRayLength.get();
            light.flareRayThickness = form.flareRayThickness.get();
            light.flareRayLength2 = form.flareRayLength2.get();
            light.flareRayThickness2 = form.flareRayThickness2.get();
            light.flareRayLength3 = form.flareRayLength3.get();
            light.flareRayThickness3 = form.flareRayThickness3.get();
            light.flareRotation = form.flareRotation.get();
            light.flareStartAngle = form.flareStartAngle.get();
            light.flareEndAngle = form.flareEndAngle.get();
            light.rimEnabled = form.rimEnabled.get();
            light.rimIntensity = form.rimIntensity.get();
            light.rimPower = form.rimPower.get();
            light.rimHardness = form.rimHardness.get();
            light.rimDirection = form.rimDirection.get();
            light.outlineEnabled = form.outlineEnabled.get();
            light.outlineIntensity = form.outlineIntensity.get();
            light.outlineThickness = form.outlineThickness.get();
        }

        private void drawBbsLightIndicators(MatrixStack stack, CALBbsForm form) {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();

            BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES,
                    VertexFormats.POSITION_COLOR);

            Color c = form.color.get();
            float r = c.r;
            float g = c.g;
            float b = c.b;
            float maxVal = Math.max(r, Math.max(g, b));
            if (maxVal < 0.2f) {
                r = 1.0f;
                g = 0.66f;
                b = 0.0f;
            }
            float alpha = 0.65f;

            if (form.isSpot.get()) {
                drawBbsSpotIndicator(builder, stack, form, r, g, b, alpha);
            } else {
                drawBbsPointIndicator(builder, stack, form, r, g, b, alpha);
            }

            BufferRenderer.drawWithGlobalProgram(builder.end());
            RenderSystem.enableCull();
        }

        private void drawBbsPointIndicator(BufferBuilder builder, MatrixStack stack, CALBbsForm form, float r, float g, float b, float a) {
            float radius = form.radius.get();
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

        private void drawBbsSpotIndicator(BufferBuilder builder, MatrixStack stack, CALBbsForm form, float r, float g, float b, float a) {
            float ndx = 0f;
            float ndy = 0f;
            float ndz = -1f;

            float ux = 1f;
            float uy = 0f;
            float uz = 0f;

            float vx = 0f;
            float vy = 1f;
            float vz = 0f;

            float dist = form.distance.get();
            float outerRad = (float) (dist * Math.tan(Math.toRadians(form.outerAngle.get())));
            float innerRad = (float) (dist * Math.tan(Math.toRadians(form.innerAngle.get())));

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

            // 2. Draw Inner Base Circle (if distinct)
            if (Math.abs(form.innerAngle.get() - form.outerAngle.get()) > 0.1f) {
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

            // 3. Draw 4 lines from Apex (0, 0, 0) to Outer Circle perimeter
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
    }

    public static class UICALBbsForm extends UIForm<CALBbsForm> {
        public UICALBbsForm() {
            super();
            this.defaultPanel = new UICALBbsFormPanel(this);
            this.registerPanel(this.defaultPanel, L10n.lang("cal.bbs.panel_title"), Icons.SUN);
            this.registerDefaultPanels();
        }
    }

    public static class UICALBbsFormPanel extends UIFormPanel<CALBbsForm> {
        private UIToggle isSpot;
        private UIColor color;
        private UITrackpad intensity;
        private UITrackpad radius;
        private UITrackpad innerAngle;
        private UITrackpad outerAngle;
        private UITrackpad distance;
        private UILabel runtimeIdLabel;

        // Volumetric Fog elements for BBS Panel
        private UIToggle fogEnabled;
        private UITrackpad fogDispersion;
        private UITrackpad fogDensity;
        private UITrackpad fogAnisotropy;

        // Indicator Toggle
        private UIToggle showIndicator;

        // Shadow elements for BBS Panel
        private UIToggle shadowEnabled;
        private UITrackpad shadowSoftness;
        private UITrackpad shadowIntensity;

        // Flare elements for BBS Panel
        private UIToggle flareEnabled;
        private UITrackpad flareSize;
        private UITrackpad flareGlowSize;
        private UITrackpad flareGlowIntensity;
        private UITrackpad flareRayLength;
        private UITrackpad flareRayThickness;
        private UITrackpad flareRayLength2;
        private UITrackpad flareRayThickness2;
        private UITrackpad flareRayLength3;
        private UITrackpad flareRayThickness3;
        private UITrackpad flareRotation;
        private UITrackpad flareStartAngle;
        private UITrackpad flareEndAngle;

        // Rim elements for BBS Panel
        private UIToggle rimEnabled;
        private UITrackpad rimIntensity;
        private UITrackpad rimPower;
        private UITrackpad rimHardness;
        private UITrackpad rimDirection;

        // Outline elements for BBS Panel
        private UIToggle outlineEnabled;
        private UITrackpad outlineIntensity;
        private UITrackpad outlineThickness;

        public UICALBbsFormPanel(UIForm editor) {
            super(editor);

            this.isSpot = new UIToggle(L10n.lang("cal.bbs.is_spot"), (toggle) -> this.form.isSpot.set(toggle.getValue()));
            this.color = new UIColor((c) -> this.form.color.set(Color.rgba(c | 0xFF000000)));
            
            this.intensity = new UITrackpad((v) -> this.form.intensity.set(v.floatValue()));
            this.radius = new UITrackpad((v) -> this.form.radius.set(v.floatValue()));
            this.innerAngle = new UITrackpad((v) -> this.form.innerAngle.set(v.floatValue()));
            this.outerAngle = new UITrackpad((v) -> this.form.outerAngle.set(v.floatValue()));
            this.distance = new UITrackpad((v) -> this.form.distance.set(v.floatValue()));

            // Volumetric Fog elements
            this.fogEnabled = new UIToggle(L10n.lang("cal.bbs.fog_enabled"), (toggle) -> this.form.fogEnabled.set(toggle.getValue()));
            this.fogDispersion = new UITrackpad((v) -> this.form.fogDispersion.set(v.floatValue()));
            this.fogDensity = new UITrackpad((v) -> this.form.fogDensity.set(v.floatValue()));
            this.fogAnisotropy = new UITrackpad((v) -> this.form.fogAnisotropy.set(v.floatValue()));

            // Indicator Toggle
            this.showIndicator = new UIToggle(L10n.lang("cal.bbs.show_indicator"), (toggle) -> this.form.showIndicator.set(toggle.getValue()));

            // Runtime ID Label
            this.runtimeIdLabel = UI.label(L10n.lang("cal.bbs.runtime_id").format(0));

            // Shadow elements
            this.shadowEnabled = new UIToggle(L10n.lang("cal.bbs.shadow_enabled"), (toggle) -> this.form.shadowEnabled.set(toggle.getValue()));
            this.shadowSoftness = new UITrackpad((v) -> this.form.shadowSoftness.set(v.floatValue()));
            this.shadowIntensity = new UITrackpad((v) -> this.form.shadowIntensity.set(v.floatValue()));

            // Flare elements
            this.flareEnabled = new UIToggle(L10n.lang("cal.bbs.flare_enabled"), (toggle) -> this.form.flareEnabled.set(toggle.getValue()));
            this.flareSize = new UITrackpad((v) -> this.form.flareSize.set(v.floatValue()));
            this.flareGlowSize = new UITrackpad((v) -> this.form.flareGlowSize.set(v.floatValue()));
            this.flareGlowIntensity = new UITrackpad((v) -> this.form.flareGlowIntensity.set(v.floatValue()));
            this.flareRayLength = new UITrackpad((v) -> this.form.flareRayLength.set(v.floatValue()));
            this.flareRayThickness = new UITrackpad((v) -> this.form.flareRayThickness.set(v.floatValue()));
            this.flareRayLength2 = new UITrackpad((v) -> this.form.flareRayLength2.set(v.floatValue()));
            this.flareRayThickness2 = new UITrackpad((v) -> this.form.flareRayThickness2.set(v.floatValue()));
            this.flareRayLength3 = new UITrackpad((v) -> this.form.flareRayLength3.set(v.floatValue()));
            this.flareRayThickness3 = new UITrackpad((v) -> this.form.flareRayThickness3.set(v.floatValue()));
            this.flareRotation = new UITrackpad((v) -> this.form.flareRotation.set(v.floatValue()));
            this.flareStartAngle = new UITrackpad((v) -> this.form.flareStartAngle.set(v.floatValue()));
            this.flareEndAngle = new UITrackpad((v) -> this.form.flareEndAngle.set(v.floatValue()));

            // Rim elements
            this.rimEnabled = new UIToggle(L10n.lang("cal.bbs.rim_enabled"), (toggle) -> this.form.rimEnabled.set(toggle.getValue()));
            this.rimIntensity = new UITrackpad((v) -> this.form.rimIntensity.set(v.floatValue()));
            this.rimPower = new UITrackpad((v) -> this.form.rimPower.set(v.floatValue()));
            this.rimHardness = new UITrackpad((v) -> this.form.rimHardness.set(v.floatValue()));
            this.rimDirection = new UITrackpad((v) -> this.form.rimDirection.set(v.floatValue()));

            // Outline elements
            this.outlineEnabled = new UIToggle(L10n.lang("cal.bbs.outline_enabled"), (toggle) -> this.form.outlineEnabled.set(toggle.getValue()));
            this.outlineIntensity = new UITrackpad((v) -> this.form.outlineIntensity.set(v.floatValue()));
            this.outlineThickness = new UITrackpad((v) -> this.form.outlineThickness.set(v.floatValue()));

            // Tooltips
            this.intensity.tooltip(L10n.lang("cal.bbs.tooltip.intensity"));
            this.radius.tooltip(L10n.lang("cal.bbs.tooltip.radius"));
            this.innerAngle.tooltip(L10n.lang("cal.bbs.tooltip.inner_angle"));
            this.outerAngle.tooltip(L10n.lang("cal.bbs.tooltip.outer_angle"));
            this.distance.tooltip(L10n.lang("cal.bbs.tooltip.distance"));

            this.fogEnabled.tooltip(L10n.lang("cal.bbs.tooltip.fog_enabled"));
            this.fogDispersion.tooltip(L10n.lang("cal.bbs.tooltip.fog_dispersion"));
            this.fogDensity.tooltip(L10n.lang("cal.bbs.tooltip.fog_density"));
            this.fogAnisotropy.tooltip(L10n.lang("cal.bbs.tooltip.fog_anisotropy"));

            this.shadowEnabled.tooltip(L10n.lang("cal.bbs.tooltip.shadow_enabled"));
            this.shadowSoftness.tooltip(L10n.lang("cal.bbs.tooltip.shadow_softness"));
            this.shadowIntensity.tooltip(L10n.lang("cal.bbs.tooltip.shadow_intensity"));

            this.flareEnabled.tooltip(L10n.lang("cal.bbs.tooltip.flare_enabled"));
            this.flareSize.tooltip(L10n.lang("cal.bbs.tooltip.flare_size"));
            this.flareGlowSize.tooltip(L10n.lang("cal.bbs.tooltip.flare_glow_size"));
            this.flareGlowIntensity.tooltip(L10n.lang("cal.bbs.tooltip.flare_glow_intensity"));
            this.flareRayLength.tooltip(L10n.lang("cal.bbs.tooltip.flare_ray_length"));
            this.flareRayThickness.tooltip(L10n.lang("cal.bbs.tooltip.flare_ray_thickness"));
            this.flareRayLength2.tooltip(L10n.lang("cal.bbs.tooltip.flare_ray_length2"));
            this.flareRayThickness2.tooltip(L10n.lang("cal.bbs.tooltip.flare_ray_thickness2"));
            this.flareRayLength3.tooltip(L10n.lang("cal.bbs.tooltip.flare_ray_length3"));
            this.flareRayThickness3.tooltip(L10n.lang("cal.bbs.tooltip.flare_ray_thickness3"));
            this.flareRotation.tooltip(L10n.lang("cal.bbs.tooltip.flare_rotation"));
            this.flareStartAngle.tooltip(L10n.lang("cal.bbs.tooltip.flare_start_angle"));
            this.flareEndAngle.tooltip(L10n.lang("cal.bbs.tooltip.flare_end_angle"));

            this.rimEnabled.tooltip(L10n.lang("cal.bbs.tooltip.rim_enabled"));
            this.rimIntensity.tooltip(L10n.lang("cal.bbs.tooltip.rim_intensity"));
            this.rimPower.tooltip(L10n.lang("cal.bbs.tooltip.rim_power"));
            this.rimHardness.tooltip(L10n.lang("cal.bbs.tooltip.rim_hardness"));
            this.rimDirection.tooltip(L10n.lang("cal.bbs.tooltip.rim_direction"));

            this.outlineEnabled.tooltip(L10n.lang("cal.bbs.tooltip.outline_enabled"));
            this.outlineIntensity.tooltip(L10n.lang("cal.bbs.tooltip.outline_intensity"));
            this.outlineThickness.tooltip(L10n.lang("cal.bbs.tooltip.outline_thickness"));

            this.showIndicator.tooltip(L10n.lang("cal.bbs.tooltip.show_indicator"));
            this.runtimeIdLabel.tooltip(L10n.lang("cal.bbs.tooltip.runtime_id"));

            // Layout
            this.options.add(new SectionLabel(L10n.lang("cal.bbs.type_settings")).marginTop(8));
            this.options.add(this.isSpot);
            this.options.add(this.showIndicator);
            this.options.add(this.runtimeIdLabel);
            
            this.options.add(new SectionLabel(L10n.lang("cal.bbs.color_intensity")).marginTop(8));
            this.options.add(this.color);
            this.options.add(UI.label(L10n.lang("cal.bbs.intensity")));
            this.options.add(this.intensity);
            
            this.options.add(new SectionLabel(L10n.lang("cal.bbs.point_settings")).marginTop(8));
            this.options.add(UI.label(L10n.lang("cal.bbs.radius")));
            this.options.add(this.radius);
            
            this.options.add(new SectionLabel(L10n.lang("cal.bbs.spot_settings")).marginTop(8));
            this.options.add(UI.label(L10n.lang("cal.bbs.inner_angle")));
            this.options.add(this.innerAngle);
            this.options.add(UI.label(L10n.lang("cal.bbs.outer_angle")));
            this.options.add(this.outerAngle);
            this.options.add(UI.label(L10n.lang("cal.bbs.distance")));
            this.options.add(this.distance);

            // Volumetric Fog Section Layout
            this.options.add(new SectionLabel(L10n.lang("cal.bbs.fog_settings")).marginTop(8));
            this.options.add(this.fogEnabled);
            this.options.add(UI.label(L10n.lang("cal.bbs.fog_dispersion")));
            this.options.add(this.fogDispersion);
            this.options.add(UI.label(L10n.lang("cal.bbs.fog_density")));
            this.options.add(this.fogDensity);
            this.options.add(UI.label(L10n.lang("cal.bbs.fog_anisotropy")));
            this.options.add(this.fogAnisotropy);

            // Shadow Section Layout
            this.options.add(new SectionLabel(L10n.lang("cal.bbs.shadow_settings")).marginTop(8));
            this.options.add(this.shadowEnabled);
            this.options.add(UI.label(L10n.lang("cal.bbs.shadow_softness")));
            this.options.add(this.shadowSoftness);
            this.options.add(UI.label(L10n.lang("cal.bbs.shadow_intensity")));
            this.options.add(this.shadowIntensity);

            // Flare Section Layout
            this.options.add(new SectionLabel(L10n.lang("cal.bbs.flare_settings")).marginTop(8));
            this.options.add(this.flareEnabled);
            this.options.add(UI.label(L10n.lang("cal.bbs.flare_size")));
            this.options.add(this.flareSize);
            this.options.add(UI.label(L10n.lang("cal.bbs.flare_glow_size")));
            this.options.add(this.flareGlowSize);
            this.options.add(UI.label(L10n.lang("cal.bbs.flare_glow_intensity")));
            this.options.add(this.flareGlowIntensity);
            this.options.add(UI.label(L10n.lang("cal.bbs.flare_ray_length")));
            this.options.add(this.flareRayLength);
            this.options.add(UI.label(L10n.lang("cal.bbs.flare_ray_thickness")));
            this.options.add(this.flareRayThickness);
            this.options.add(UI.label(L10n.lang("cal.bbs.flare_ray_length2")));
            this.options.add(this.flareRayLength2);
            this.options.add(UI.label(L10n.lang("cal.bbs.flare_ray_thickness2")));
            this.options.add(this.flareRayThickness2);
            this.options.add(UI.label(L10n.lang("cal.bbs.flare_ray_length3")));
            this.options.add(this.flareRayLength3);
            this.options.add(UI.label(L10n.lang("cal.bbs.flare_ray_thickness3")));
            this.options.add(this.flareRayThickness3);
            this.options.add(UI.label(L10n.lang("cal.bbs.flare_rotation")));
            this.options.add(this.flareRotation);
            this.options.add(UI.label(L10n.lang("cal.bbs.flare_start_angle")));
            this.options.add(this.flareStartAngle);
            this.options.add(UI.label(L10n.lang("cal.bbs.flare_end_angle")));
            this.options.add(this.flareEndAngle);

            // Rim Section Layout
            this.options.add(new SectionLabel(L10n.lang("cal.bbs.rim_settings")).marginTop(8));
            this.options.add(this.rimEnabled);
            this.options.add(UI.label(L10n.lang("cal.bbs.rim_intensity")));
            this.options.add(this.rimIntensity);
            this.options.add(UI.label(L10n.lang("cal.bbs.rim_power")));
            this.options.add(this.rimPower);
            this.options.add(UI.label(L10n.lang("cal.bbs.rim_hardness")));
            this.options.add(this.rimHardness);
            this.options.add(UI.label(L10n.lang("cal.bbs.rim_direction")));
            this.options.add(this.rimDirection);

            // Outline Section Layout
            this.options.add(new SectionLabel(L10n.lang("cal.bbs.outline_settings")).marginTop(8));
            this.options.add(this.outlineEnabled);
            this.options.add(UI.label(L10n.lang("cal.bbs.outline_intensity")));
            this.options.add(this.outlineIntensity);
            this.options.add(UI.label(L10n.lang("cal.bbs.outline_thickness")));
            this.options.add(this.outlineThickness);
        }

        @Override
        public void startEdit(CALBbsForm form) {
            super.startEdit(form);

            this.intensity.limit(form.intensity);
            this.radius.limit(form.radius);
            this.innerAngle.limit(form.innerAngle);
            this.outerAngle.limit(form.outerAngle);
            this.distance.limit(form.distance);

            this.fogDispersion.limit(form.fogDispersion);
            this.fogDensity.limit(form.fogDensity);
            this.fogAnisotropy.limit(form.fogAnisotropy);

            this.shadowSoftness.limit(form.shadowSoftness);
            this.shadowIntensity.limit(form.shadowIntensity);

            this.flareSize.limit(form.flareSize);
            this.flareGlowSize.limit(form.flareGlowSize);
            this.flareGlowIntensity.limit(form.flareGlowIntensity);
            this.flareRayLength.limit(form.flareRayLength);
            this.flareRayThickness.limit(form.flareRayThickness);
            this.flareRayLength2.limit(form.flareRayLength2);
            this.flareRayThickness2.limit(form.flareRayThickness2);
            this.flareRayLength3.limit(form.flareRayLength3);
            this.flareRayThickness3.limit(form.flareRayThickness3);
            this.flareRotation.limit(form.flareRotation);
            this.flareStartAngle.limit(form.flareStartAngle);
            this.flareEndAngle.limit(form.flareEndAngle);

            this.rimIntensity.limit(form.rimIntensity);
            this.rimPower.limit(form.rimPower);
            this.rimHardness.limit(form.rimHardness);
            this.rimDirection.limit(form.rimDirection);

            this.outlineIntensity.limit(form.outlineIntensity);
            this.outlineThickness.limit(form.outlineThickness);

            this.isSpot.setValue(form.isSpot.get());
            this.color.setColor(form.color.get().getARGBColor());
            this.intensity.setValue(form.intensity.get());
            this.radius.setValue(form.radius.get());
            this.innerAngle.setValue(form.innerAngle.get());
            this.outerAngle.setValue(form.outerAngle.get());
            this.distance.setValue(form.distance.get());
            this.runtimeIdLabel.label = L10n.lang("cal.bbs.runtime_id").format(form.runtimeId.get());

            this.fogEnabled.setValue(form.fogEnabled.get());
            this.fogDispersion.setValue(form.fogDispersion.get());
            this.fogDensity.setValue(form.fogDensity.get());
            this.fogAnisotropy.setValue(form.fogAnisotropy.get());

            this.shadowEnabled.setValue(form.shadowEnabled.get());
            this.shadowSoftness.setValue(form.shadowSoftness.get());
            this.shadowIntensity.setValue(form.shadowIntensity.get());

            this.flareEnabled.setValue(form.flareEnabled.get());
            this.flareSize.setValue(form.flareSize.get());
            this.flareGlowSize.setValue(form.flareGlowSize.get());
            this.flareGlowIntensity.setValue(form.flareGlowIntensity.get());
            this.flareRayLength.setValue(form.flareRayLength.get());
            this.flareRayThickness.setValue(form.flareRayThickness.get());
            this.flareRayLength2.setValue(form.flareRayLength2.get());
            this.flareRayThickness2.setValue(form.flareRayThickness2.get());
            this.flareRayLength3.setValue(form.flareRayLength3.get());
            this.flareRayThickness3.setValue(form.flareRayThickness3.get());
            this.flareRotation.setValue(form.flareRotation.get());
            this.flareStartAngle.setValue(form.flareStartAngle.get());
            this.flareEndAngle.setValue(form.flareEndAngle.get());

            this.rimEnabled.setValue(form.rimEnabled.get());
            this.rimIntensity.setValue(form.rimIntensity.get());
            this.rimPower.setValue(form.rimPower.get());
            this.rimHardness.setValue(form.rimHardness.get());
            this.rimDirection.setValue(form.rimDirection.get());

            this.outlineEnabled.setValue(form.outlineEnabled.get());
            this.outlineIntensity.setValue(form.outlineIntensity.get());
            this.outlineThickness.setValue(form.outlineThickness.get());

            this.showIndicator.setValue(form.showIndicator.get());
        }
    }

    public static class SectionLabel extends UIElement {
        private final IKey label;

        public SectionLabel(IKey label) {
            this.label = label;
            this.h(16);
        }

        @Override
        public void render(UIContext context) {
            int color = BBSSettings.primaryColor(Colors.A50);

            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), color);
            context.batcher.text(this.label.get(), this.area.x + 6, this.area.y + 4, 0xFFFFFFFF);

            super.render(context);
        }
    }
}
