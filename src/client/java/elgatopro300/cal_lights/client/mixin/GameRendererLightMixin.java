package elgatopro300.cal_lights.client.mixin;

import elgatopro300.cal_lights.light.LightDriver;

import org.qualet.irl.light.FramePipeline;
import org.qualet.irl.light.iris.IrisShadersState;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRenderer.class, priority = 900)
public class GameRendererLightMixin {
    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void irlite$collectLights(RenderTickCounter tickCounter, CallbackInfo ci) {
        if (FabricLoader.getInstance().isModLoaded("irlite")) {
            return;
        }

        float tickDelta = tickCounter.getTickProgress(true);
        FramePipeline.frame(
            tickDelta,
            IrisShadersState::shadersDisabled,
            LightDriver::collect,
            LightDriver::resetAutoShadowRamp
        );
    }

    @Inject(
        method = "renderWorld",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;update(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V",
            shift = At.Shift.AFTER
        )
    )
    private void irlite$flushPendingSSBO(RenderTickCounter tickCounter, CallbackInfo ci) {
        if (FabricLoader.getInstance().isModLoaded("irlite")) {
            return;
        }
        FramePipeline.uploadIfPending();
    }
}
