package elgatopro300.cal_lights.client.mixin.iris;

import elgatopro300.cal_lights.light.shadow.PointShadowArray;
import elgatopro300.cal_lights.light.shadow.SpotlightDepthAtlas;
import elgatopro300.cal_lights.manager.GoboManager;

import net.fabricmc.loader.api.FabricLoader;

import net.irisshaders.iris.gl.program.ProgramSamplers;
import net.irisshaders.iris.gl.texture.TextureType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ProgramSamplers.Builder.class, remap = false)
public class ProgramSamplersBuilderMixin {
    @Inject(method = "build", at = @At("HEAD"))
    private void irlite$bindShadowSamplers(CallbackInfoReturnable<ProgramSamplers> cir) {
        if (FabricLoader.getInstance().isModLoaded("irlite")) {
            return;
        }
        ProgramSamplers.Builder self = (ProgramSamplers.Builder) (Object) this;
        self.addDynamicSampler(TextureType.TEXTURE_2D, SpotlightDepthAtlas::getGlTextureId, () -> null, "irl_spotShadowAtlas");
        self.addDynamicSampler(TextureType.TEXTURE_2D, PointShadowArray::getGlTextureId, () -> null, "irl_pointShadowArray");
        self.addDynamicSampler(TextureType.TEXTURE_2D, GoboManager.INSTANCE::getTextureArrayId, () -> null, "irl_cookieArray");
    }
}
