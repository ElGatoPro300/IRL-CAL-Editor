package elgatopro300.cal_lights;

import elgatopro300.cal_lights.gizmo.LightGizmo;
import elgatopro300.cal_lights.graphics.CalLightsIcons;
import elgatopro300.cal_lights.light.LightConfig;
import elgatopro300.cal_lights.light.PlacedLight;
import elgatopro300.cal_lights.light.auto.AutoLightManager;
import elgatopro300.cal_lights.manager.GoboManager;
import elgatopro300.cal_lights.manager.LightInstance;
import elgatopro300.cal_lights.manager.LightManager;
import elgatopro300.cal_lights.manager.LightSaveManager;
import elgatopro300.cal_lights.patcher.CALPatcherHost;
import elgatopro300.cal_lights.ui.CALEditorScreen;

import org.qualet.irl.light.LightBuffer;
import org.qualet.irl.light.LightRegistry;
import org.qualet.irl.patcher.Patcher;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CALLightsClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("IRL CAL Editor Client");
    public static KeyBinding editorKeyBinding;
    public static KeyBinding createLightKeyBinding;

    private static final int AUTO_SHADOW_RAMP_STEP = 2;
    private static int autoShadowRamp = 0;

    public static void resetAutoShadowRamp() {
        autoShadowRamp = 0;
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("IRL CAL Editor mod initialized on Client!");

        // Install the patcher host so the shared irl-core patcher can reach the game
        // dir / Iris shaderpacks dir / bundled .irlights patches.
        Patcher.install(new CALPatcherHost());

        editorKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cal.editor",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            KeyBinding.Category.MISC
        ));

        createLightKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cal.create_light",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            KeyBinding.Category.MISC
        ));

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            LOGGER.info("Initializing IRL CAL Editor Icons...");
            CalLightsIcons.init();

            LOGGER.info("Initializing IRL CAL Editor 3D Gizmo...");
            LightGizmo.INSTANCE.init();

            LOGGER.info("Initializing IRL CAL Editor Gobo Manager...");
            GoboManager.INSTANCE.init();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            LightSaveManager.tick(client);
            LightManager.INSTANCE.tick();

            if (client.world != null && client.player != null) {
                AutoLightManager.tick(client.world,
                    client.player.getX(), client.player.getEyeY(), client.player.getZ());
            }
            
            while (editorKeyBinding.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new CALEditorScreen());
                }
            }

            while (createLightKeyBinding.wasPressed()) {
                if (client.player != null) {
                    Vec3d p = client.gameRenderer.getCamera().getCameraPos();
                    int id = ThreadLocalRandom.current().nextInt(100000, 999999);
                    boolean isSpot = ThreadLocalRandom.current().nextBoolean();
                    if (isSpot) {
                        // angle=35, soft=10, distance=12 (IRL defaults)
                        LightInstance light = LightManager.INSTANCE.updateSpot(id, (float) p.x, (float) p.y, (float) p.z, 0f, -1f, 0f, 1f, 1f, 1f, 1.0f, 35.0f, 10.0f, 12.0f);
                        light.persistent = true;
                        client.player.sendMessage(Text.literal("Created Spot Light: " + id), true);
                    } else {
                        LightInstance light = LightManager.INSTANCE.updatePoint(id, (float) p.x, (float) p.y, (float) p.z, 1f, 1f, 1f, 1.0f, 6.0f);
                        light.persistent = true;
                        client.player.sendMessage(Text.literal("Created Point Light: " + id), true);
                    }
                }
            }
        });

        // Note: Light registration and shadow baking are now handled in GameRendererLightMixin at renderWorld HEAD.
    }

    /**
     * Feeds all managed lights (manual + auto) into the IRL Core LightRegistry
     * each frame. The parameter mapping matches the original IRL editor's
     * LightDriver exactly:
     *
     * Point: registerPoint(x, y, z, r, g, b, intensity, radius,
     *            entitiesOnly, blocksOnly, anisotropy, vlDensity,
     *            beamStrength, bulbSize, shadows, id)
     *
     * Spot:  registerSpot(x, y, z, dx, dy, dz, r, g, b, intensity,
     *            range, cosOuter, cosInner, entitiesOnly, blocksOnly,
     *            anisotropy, vlDensity, beamStrength, bulbSize, shadows,
     *            cookieLayer, cookieRot, cookieScale, cookieFlags, id)
     */
    public static void registerLightsToIrlCore() {
        LightRegistry.clear();

        // 1. Point lights
        for (LightInstance l : LightManager.INSTANCE.getPointLights()) {
            if (l.visible) {
                // beamStrength is 0 when volumetrics are disabled
                float bm = l.fogEnabled ? l.beamStrength : 0.0f;

                LightRegistry.registerPoint(
                    l.x, l.y, l.z,
                    l.r, l.g, l.b,
                    l.intensity, l.radius,
                    l.entitiesOnly, l.blocksOnly,
                    l.anisotropy, l.vlDensity, bm, l.bulbSize,
                    l.shadowEnabled, (long) l.id
                );
            }
        }

        // 2. Spot lights
        for (LightInstance l : LightManager.INSTANCE.getSpotLights()) {
            if (l.visible) {
                float sdx = l.dx, sdy = l.dy, sdz = l.dz;
                float len = (float) Math.sqrt(sdx * sdx + sdy * sdy + sdz * sdz);
                if (len > 1e-4f) {
                    sdx /= len;
                    sdy /= len;
                    sdz /= len;
                } else {
                    sdx = 0f;
                    sdy = -1f;
                    sdz = 0f;
                }

                // Derive inner/outer from angle + soft, exactly as the original:
                // outer = angle, inner = clamp(angle - soft, 1, angle)
                float outer = l.getOuterAngleDeg();
                float inner = l.getInnerAngleDeg();
                float cosOuter = (float) Math.cos(Math.toRadians(outer * 0.5f));
                float cosInner = (float) Math.cos(Math.toRadians(inner * 0.5f));

                int cookieLayer = GoboManager.INSTANCE.getGoboIndex(l.goboName);
                // Cookie rotation: stored in degrees in UI, passed in radians to API
                float cookieRot = (float) Math.toRadians(l.goboRotation);
                float cookieScale = l.cookieScale;
                float cookieFlags = l.cookieInvert ? 1.0f : 0.0f;

                // beamStrength is 0 when volumetrics are disabled
                float bm = l.fogEnabled ? l.beamStrength : 0.0f;

                LightRegistry.registerSpot(
                    l.x, l.y, l.z,
                    sdx, sdy, sdz,
                    l.r, l.g, l.b,
                    l.intensity, l.distance, cosOuter, cosInner,
                    l.entitiesOnly, l.blocksOnly,
                    l.anisotropy, l.vlDensity, bm, l.bulbSize,
                    l.shadowEnabled,
                    (float) cookieLayer, cookieRot, cookieScale, cookieFlags,
                    (long) l.id
                );
            }
        }

        // 3. Auto block-lights
        if (LightConfig.autoLights()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null && mc.world != null) {
                Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

                int headroom = Math.max(0, LightBuffer.MAX_LIGHTS - LightRegistry.getCount());
                int feedMax = Math.min(LightConfig.autoLightMax(), headroom);

                int manualShadowPoints = 0;
                for (LightInstance l : LightManager.INSTANCE.getPointLights()) {
                    if (l.visible && l.shadowEnabled) {
                        manualShadowPoints++;
                    }
                }

                autoShadowRamp = Math.min(16, autoShadowRamp + AUTO_SHADOW_RAMP_STEP);
                int shadowBudget = LightConfig.autoLightShadows()
                    ? Math.min(autoShadowRamp, Math.max(0, 16 - manualShadowPoints))
                    : 0;

                List<PlacedLight> autos =
                    AutoLightManager.nearest(cameraPos, feedMax);

                int granted = 0;
                for (PlacedLight l : autos) {
                    if (l == null) continue;
                    boolean wantShadow = l.autoShadowEligible && granted < shadowBudget;
                    l.shadows = wantShadow;
                    if (wantShadow) {
                        granted++;
                    }

                    LightRegistry.registerPoint(
                        (float) l.x, (float) l.y, (float) l.z,
                        l.r, l.g, l.b,
                        l.intensity, l.radius,
                        l.entitiesOnly, l.blocksOnly,
                        l.anisotropy, l.vlDensity, l.beamStrength, l.bulbSize,
                        l.shadows, l.id
                    );
                }
            }
        } else {
            autoShadowRamp = 0;
        }
    }
}
