package elgatopro300.cal_lights;

import elgatopro300.cal_lights.gizmo.LightGizmo;
import elgatopro300.cal_lights.graphics.CalLightsIcons;
import elgatopro300.cal_lights.integration.bbs.CALLightsBbsIntegration;
import elgatopro300.cal_lights.manager.GoboManager;
import elgatopro300.cal_lights.manager.LightInstance;
import elgatopro300.cal_lights.manager.LightManager;
import elgatopro300.cal_lights.manager.LightSaveManager;
import elgatopro300.cal_lights.shaders.LightSSBO;
import elgatopro300.cal_lights.shaders.VoxelShadowSSBO;
import elgatopro300.cal_lights.ui.CALEditorScreen;

import mchorse.bbs_mod.BBS;

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

            LOGGER.info("Initializing CAL Lights Voxel Shadow SSBO...");
            VoxelShadowSSBO.init();
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
            LightSSBO.upload();
            VoxelShadowSSBO.upload();
        });

        try {
            Class.forName("mchorse.bbs_mod.BBS");
            CALLightsBbsIntegration.initialize();
            LOGGER.info("Successfully registered BBS CML Edition integration!");
        } catch (ClassNotFoundException ignored) {
            LOGGER.info("BBS CML Edition not detected, continuing standalone.");
        }
    }
}
