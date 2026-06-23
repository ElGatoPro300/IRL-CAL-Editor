package elgatopro300.cal_lights.client.mixin.iris;

import net.irisshaders.iris.gl.program.ProgramSamplers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import elgatopro300.cal_lights.manager.GoboManager;
import elgatopro300.cal_lights.light.shadow.PointShadowArray;
import elgatopro300.cal_lights.light.shadow.SpotlightDepthAtlas;

@Mixin(value = ProgramSamplers.Builder.class, remap = false)
public class ProgramSamplersBuilderMixin {
    @Inject(method = "build", at = @At("HEAD"))
    private void irlite$bindShadowSamplers(CallbackInfoReturnable<ProgramSamplers> cir) {
        ProgramSamplers.Builder self = (ProgramSamplers.Builder) (Object) this;
        self.addDynamicSampler(SpotlightDepthAtlas::getGlTextureId, "irl_spotShadowAtlas");
        self.addDynamicSampler(PointShadowArray::getGlTextureId, "irl_pointShadowArray");
        self.addDynamicSampler(GoboManager.INSTANCE::getTextureArrayId, "irl_cookieArray");
    }
}
