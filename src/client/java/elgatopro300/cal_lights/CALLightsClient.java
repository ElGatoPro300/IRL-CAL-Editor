package elgatopro300.cal_lights;

import elgatopro300.cal_lights.gizmo.LightGizmo;
import elgatopro300.cal_lights.graphics.CalLightsIcons;
import elgatopro300.cal_lights.light.LightConfig;
import elgatopro300.cal_lights.light.LightGuideRenderer;
import elgatopro300.cal_lights.light.auto.AutoLightManager;
import elgatopro300.cal_lights.light.cookie.CookieArray;
import elgatopro300.cal_lights.manager.LightInstance;
import elgatopro300.cal_lights.manager.LightManager;
import elgatopro300.cal_lights.manager.LightSaveManager;
import elgatopro300.cal_lights.patcher.CALPatcherHost;
import elgatopro300.cal_lights.ui.CALEditorScreen;

import org.qualet.irl.light.IrlSamplers;
import org.qualet.irl.light.shadow.RedactorEntityCasterSource;
import org.qualet.irl.light.shadow.ShadowEngine;
import org.qualet.irl.patcher.Patcher;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.KeyMapping;

import com.mojang.blaze3d.platform.InputConstants;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL30;

import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CALLightsClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("IRL CAL Editor Client");
    public static KeyMapping editorKeyBinding;
    public static KeyMapping createLightKeyBinding;

    @Override
    public void onInitializeClient() {
        LOGGER.info("IRL CAL Editor mod initialized on Client!");
        LightGuideRenderer.register();

        boolean irlitePresent = FabricLoader.getInstance().isModLoaded("irlite");

        // When irlite is present it owns the shared irl-core singletons with composite
        // adapters that also feed CAL lights/shadows/patches. Installing again here
        // would overwrite BBS integration and mute the addon.
        if (!irlitePresent) {
            Patcher.install(new CALPatcherHost());
            ShadowEngine.install(new RedactorEntityCasterSource(), LightConfig.SHADOW);
            IrlSamplers.register("irl_cookieArray", CookieArray::getGlTextureId, GL30.GL_TEXTURE_2D_ARRAY);
        } else {
            LOGGER.info("irlite detected — deferring shared irl-core wiring to the BBS addon.");
        }

        editorKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.cal.editor",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            KeyMapping.Category.MISC
        ));

        createLightKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.cal.create_light",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            KeyMapping.Category.MISC
        ));

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            LOGGER.info("Initializing IRL CAL Editor Icons...");
            CalLightsIcons.init();

            LOGGER.info("Initializing IRL CAL Editor 3D Gizmo...");
            LightGizmo.INSTANCE.init();

            LOGGER.info("Initializing IRL CAL Editor cookie array...");
            if (irlitePresent) {
                try {
                    Class.forName("qualet.irlite.client.compat.IrliteCalCompat")
                        .getMethod("ensureCookiesReady")
                        .invoke(null);
                } catch (ReflectiveOperationException e) {
                    LOGGER.warn("Could not init unified cookies with irlite; using CAL-only array.", e);
                    CookieArray.init();
                }
            } else {
                CookieArray.init();
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            LightSaveManager.tick(client);
            LightManager.INSTANCE.tick();

            if (client.world != null && client.player != null) {
                AutoLightManager.tick(client.world,
                    client.player.getX(), client.player.getEyeY(), client.player.getZ());
            }
            
            while (editorKeyBinding.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
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

        // Light registration and shadow baking are handled by FramePipeline in GameRendererLightMixin.
    }
}
