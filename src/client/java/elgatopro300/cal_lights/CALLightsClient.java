package elgatopro300.cal_lights;

import elgatopro300.cal_lights.gizmo.LightGizmo;
import elgatopro300.cal_lights.graphics.CalLightsIcons;
import elgatopro300.cal_lights.light.LightConfig;
import elgatopro300.cal_lights.light.auto.AutoLightManager;
import elgatopro300.cal_lights.manager.GoboManager;
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

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL30;

import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CALLightsClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("IRL CAL Editor Client");
    public static KeyBinding editorKeyBinding;
    public static KeyBinding createLightKeyBinding;

    @Override
    public void onInitializeClient() {
        LOGGER.info("IRL CAL Editor mod initialized on Client!");

        // Install the patcher host so the shared irl-core patcher can reach the game
        // dir / Iris shaderpacks dir / bundled .irlights patches.
        Patcher.install(new CALPatcherHost());
        ShadowEngine.install(new RedactorEntityCasterSource(), LightConfig.SHADOW);
        IrlSamplers.register("irl_cookieArray", GoboManager.INSTANCE::getTextureArrayId, GL30.GL_TEXTURE_2D_ARRAY);

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
                if (client.player != null && client.currentScreen == null) {
                    client.setScreen(new CALEditorScreen());
                }
            }

            while (createLightKeyBinding.wasPressed()) {
                if (client.player != null) {
                    Vec3d p = client.gameRenderer.getCamera().getPos();
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
