package elgatopro300.cal_lights.graphics;

import elgatopro300.cal_lights.client.mixin.RenderPipelinesInvoker;
import elgatopro300.cal_lights.client.mixin.RenderTypeInvoker;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CALLayers {
    private static final RenderPipeline POSITION_COLOR_LINES = RenderPipelinesInvoker.invokeRegister(
        RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("cal", "pipeline/draw_position_color_lines"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.DEBUG_LINES)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build()
    );

    private static final RenderPipeline POSITION_COLOR_TRIS_NO_DEPTH = RenderPipelinesInvoker.invokeRegister(
        RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("cal", "pipeline/draw_position_color_no_depth"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build()
    );

    private static RenderType positionColorLinesLayer;
    private static RenderType positionColorNoDepthLayer;
    private static final Map<Identifier, RenderType> BILLBOARD_LAYERS = new ConcurrentHashMap<>();

    public static RenderType getPositionColorLinesLayer() {
        if (positionColorLinesLayer == null) {
            positionColorLinesLayer = RenderTypeInvoker.invokeCreate("cal_draw_position_color_lines",
                RenderSetup.builder(POSITION_COLOR_LINES).sortOnUpload().createRenderSetup());
        }
        return positionColorLinesLayer;
    }

    public static RenderType getPositionColorNoDepthLayer() {
        if (positionColorNoDepthLayer == null) {
            positionColorNoDepthLayer = RenderTypeInvoker.invokeCreate("cal_draw_position_color_no_depth",
                RenderSetup.builder(POSITION_COLOR_TRIS_NO_DEPTH).sortOnUpload().createRenderSetup());
        }
        return positionColorNoDepthLayer;
    }

    public static RenderType getPositionTexColorNoDepthLayer(Identifier texture) {
        return BILLBOARD_LAYERS.computeIfAbsent(texture, tex -> {
            RenderSetup setup = RenderSetup.builder(RenderPipelines.GUI_TEXTURED)
                .withTexture("Sampler0", tex)
                .sortOnUpload()
                .createRenderSetup();
            return RenderTypeInvoker.invokeCreate("cal_billboard_" + tex.getPath(), setup);
        });
    }

    public static void flush(BufferBuilder builder, RenderType layer) {
        MeshData built = builder.build();
        if (built != null) {
            layer.draw(built);
        }
    }

    public static void flushLines(BufferBuilder builder) {
        flush(builder, getPositionColorLinesLayer());
    }

    public static void flushTrianglesNoDepth(BufferBuilder builder) {
        flush(builder, getPositionColorNoDepthLayer());
    }

    private CALLayers() {}
}
