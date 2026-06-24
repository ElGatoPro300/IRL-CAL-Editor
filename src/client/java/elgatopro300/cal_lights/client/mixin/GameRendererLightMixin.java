package elgatopro300.cal_lights.client.mixin;

import elgatopro300.cal_lights.CALLightsClient;
import elgatopro300.cal_lights.light.IrisShadersState;
import elgatopro300.cal_lights.light.shadow.ShadowBaker;

import org.qualet.irl.light.LightBuffer;
import org.qualet.irl.light.LightRegistry;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRenderer.class, priority = 900)
public class GameRendererLightMixin {
    @Unique
    private static boolean irlite$dormant;

    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void irlite$collectLights(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci) {

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

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d cameraPos = camera != null ? camera.getPos() : Vec3d.ZERO;
        Vec3d cameraForward = camera != null ? Vec3d.fromPolar(camera.getPitch(), camera.getYaw()) : null;

        // Register every light up front
        CALLightsClient.registerLightsToIrlCore();

        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("irlite")) {
            return;
        }

        // Configure dispatcher before Iris activates
        if (world != null && camera != null) {
            mc.getEntityRenderDispatcher().configure(world, camera, mc.getCameraEntity());
        }

        ShadowBaker.bake(world, cameraPos, cameraForward, tickDelta);

        LightRegistry.flush();
    }
}
