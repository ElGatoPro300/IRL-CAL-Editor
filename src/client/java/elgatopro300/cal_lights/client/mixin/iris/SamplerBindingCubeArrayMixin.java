package elgatopro300.cal_lights.client.mixin.iris;

import elgatopro300.cal_lights.light.shadow.PointShadowArray;
import elgatopro300.cal_lights.manager.GoboManager;

import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;

import java.util.function.IntSupplier;

import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.sampler.SamplerBinding;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SamplerBinding.class, remap = false)
public abstract class SamplerBindingCubeArrayMixin {
    @Shadow
    @Final
    private int textureUnit;

    @Shadow
    @Final
    private IntSupplier texture;

    @Inject(method = "updateSampler", at = @At("HEAD"), cancellable = true)
    private void irlite$bindCubeArrayInsteadOf2D(CallbackInfo ci) {
        int id = this.texture.getAsInt();
        if (id == 0) {
            return;
        }

        int cubeArrayId = PointShadowArray.getGlTextureId();
        if (cubeArrayId != 0 && id == cubeArrayId) {
            IrisRenderSystem.bindTextureToUnit(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, this.textureUnit, id);
            ci.cancel();
            return;
        }

        int cookieArrayId = GoboManager.INSTANCE.getTextureArrayId();
        if (cookieArrayId != 0 && id == cookieArrayId) {
            IrisRenderSystem.bindTextureToUnit(GL30.GL_TEXTURE_2D_ARRAY, this.textureUnit, id);
            ci.cancel();
        }
    }
}
