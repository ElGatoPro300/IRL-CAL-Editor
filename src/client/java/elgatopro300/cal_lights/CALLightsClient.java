package elgatopro300.cal_lights;

import elgatopro300.cal_lights.gizmo.LightGizmo;
import elgatopro300.cal_lights.graphics.CalLightsIcons;
import elgatopro300.cal_lights.manager.GoboManager;
import elgatopro300.cal_lights.manager.LightInstance;
import elgatopro300.cal_lights.manager.LightManager;
import elgatopro300.cal_lights.manager.LightSaveManager;
import org.qualet.irl.light.LightRegistry;
import elgatopro300.cal_lights.ui.CALEditorScreen;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CALLightsClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("CAL Lights Client");
    public static KeyBinding editorKeyBinding;
    public static KeyBinding createLightKeyBinding;

    @Override
    public void onInitializeClient() {
        LOGGER.info("CAL Lights mod initialized on Client!");

        editorKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cal.editor",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            "category.cal"
        ));

        createLightKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cal.create_light",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            "category.cal"
        ));

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            LOGGER.info("Initializing CAL Lights Icons...");
            CalLightsIcons.init();

            LOGGER.info("Initializing CAL Lights 3D Gizmo...");
            LightGizmo.INSTANCE.init();

            LOGGER.info("Initializing CAL Lights Gobo Manager...");
            GoboManager.INSTANCE.init();

            // VoxelShadowSSBO is deleted
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            LightSaveManager.tick(client);
            LightManager.INSTANCE.tick();
            
            while (editorKeyBinding.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new CALEditorScreen());
                }
            }

            while (createLightKeyBinding.wasPressed()) {
                if (client.player != null) {
                    Vec3d p = client.gameRenderer.getCamera().getPos();
                    int id = ThreadLocalRandom.current().nextInt(100000, 999999);
                    boolean isSpot = ThreadLocalRandom.current().nextBoolean();
                    if (isSpot) {
                        LightInstance light = LightManager.INSTANCE.updateSpot(id, (float) p.x, (float) p.y, (float) p.z, 0f, -1f, 0f, 1f, 1f, 1f, 1.0f, 15.0f, 30.0f, 15.0f);
                        light.persistent = true;
                        client.player.sendMessage(Text.literal("Created Spot Light: " + id), true);
                    } else {
                        LightInstance light = LightManager.INSTANCE.updatePoint(id, (float) p.x, (float) p.y, (float) p.z, 1f, 1f, 1f, 1.0f, 10.0f);
                        light.persistent = true;
                        client.player.sendMessage(Text.literal("Created Point Light: " + id), true);
                    }
                }
            }
        });

        WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
            GoboManager.INSTANCE.bind();
            registerLightsToIrlCore();
        });
    }

    private static void registerLightsToIrlCore() {
        LightRegistry.clear();

        // 1. Point lights
        for (LightInstance l : LightManager.INSTANCE.getPointLights()) {
            if (l.visible) {
                LightRegistry.registerPoint(
                    l.x, l.y, l.z,
                    l.r, l.g, l.b,
                    l.intensity, l.radius,
                    l.entitiesOnly, l.blocksOnly,
                    l.fogAnisotropy, l.fogDensity, l.fogEnabled ? l.fogDispersion : 0.0f,
                    l.shadowSoftness, l.shadowEnabled, (long) l.id
                );
            }
        }

        // 2. Spot lights
        for (LightInstance l : LightManager.INSTANCE.getSpotLights()) {
            if (l.visible) {
                float dx = l.dx, dy = l.dy, dz = l.dz;
                float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len > 1e-4f) {
                    dx /= len;
                    dy /= len;
                    dz /= len;
                } else {
                    dx = 0f;
                    dy = -1f;
                    dz = 0f;
                }

                float outer = l.outerAngle;
                float inner = Math.min(l.innerAngle, outer);
                float cosOuter = (float) Math.cos(Math.toRadians(outer * 0.5f));
                float cosInner = (float) Math.cos(Math.toRadians(inner * 0.5f));

                int cookieLayer = GoboManager.INSTANCE.getGoboIndex(l.goboName);
                float cookieRot = (float) Math.toRadians(l.goboRotation);
                float cookieScale = l.cookieScale;
                float cookieFlags = l.cookieInvert ? 1.0f : 0.0f;

                LightRegistry.registerSpot(
                    l.x, l.y, l.z,
                    dx, dy, dz,
                    l.r, l.g, l.b,
                    l.intensity, l.distance, cosOuter, cosInner,
                    l.entitiesOnly, l.blocksOnly,
                    l.fogAnisotropy, l.fogDensity, l.fogEnabled ? l.fogDispersion : 0.0f,
                    l.shadowSoftness, l.shadowEnabled,
                    (float) cookieLayer, cookieRot, cookieScale, cookieFlags,
                    (long) l.id
                );
            }
        }

        LightRegistry.flush();
    }
}
