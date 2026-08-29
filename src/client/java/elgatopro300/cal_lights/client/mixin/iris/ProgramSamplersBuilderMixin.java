package elgatopro300.cal_lights.client.mixin.iris;

import org.qualet.irl.light.iris.IrlSamplersBind;

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
        IrlSamplersBind.bindAll((ProgramSamplers.Builder) (Object) this);
    }
}
