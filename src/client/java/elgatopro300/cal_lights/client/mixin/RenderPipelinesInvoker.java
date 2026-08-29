package elgatopro300.cal_lights.client.mixin;

import net.minecraft.client.renderer.RenderPipelines;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderPipelines.class)
public interface RenderPipelinesInvoker {
    @Invoker("register")
    static RenderPipeline invokeRegister(RenderPipeline pipeline) {
        throw new AssertionError();
    }
}
