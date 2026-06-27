package elgatopro300.cal_lights.client.mixin;

import elgatopro300.cal_lights.CALLightsClient;
import elgatopro300.cal_lights.light.IrisShadersState;
import elgatopro300.cal_lights.light.shadow.ShadowBaker;

import org.qualet.irl.light.LightBuffer;
import org.qualet.irl.light.LightRegistry;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRenderer.class, priority = 900)
public class GameRendererLightMixin {
    @Unique
    private static boolean irlite$dormant;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void irlite$collectLights(DeltaTracker tickCounter, CallbackInfo ci) {
        float tickDelta = tickCounter.getGameTimeDeltaPartialTick(true);

        if (IrisShadersState.shadersDisabled()) {
            LightRegistry.clear();

            if (!irlite$dormant) {
                irlite$dormant = true;
                LightBuffer.uploadEmpty();
                ShadowBaker.onShadersDisabled();
                CALLightsClient.resetAutoShadowRamp();
            }
            return;
        }

        irlite$dormant = false;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel world = mc.level;
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera != null ? camera.position() : Vec3.ZERO;
        Vec3 cameraForward = camera != null ? Vec3.directionFromRotation(camera.xRot(), camera.yRot()) : null;

        // Register every light up front
        CALLightsClient.registerLightsToIrlCore();

        if (FabricLoader.getInstance().isModLoaded("irlite")) {
            return;
        }

        // Configure dispatcher before Iris activates
        if (world != null && camera != null) {
            mc.getEntityRenderDispatcher().prepare(camera, mc.getCameraEntity());
        }

        ShadowBaker.bake(world, cameraPos, cameraForward, tickDelta);

        LightRegistry.flush();
    }
}
